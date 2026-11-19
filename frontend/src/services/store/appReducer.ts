import { createInitialCatalog } from '@/constants/catalog';
import { FIELD_STAGE_LOG_ACTION, FIELD_STAGE_LOG_LABEL, FIELD_STAGE_ORDER, isTruckOnly, nextFieldStage, STATUS_META } from '@/constants/order';
import { AUTO_CANCEL_MS, DEPOSIT_AMOUNT, RESCHEDULE_LOCK_HOURS, totalVehicles, vehicleLinesLabel } from '@/constants/pricing';
import { hoursLabel, orderShift, PLATE_REGEX, VEHICLE_INCIDENT_KINDS } from '@/constants/resources';
import { isSurchargeBlocking, SURCHARGE_BLOCKING, SURCHARGE_TYPE_META, surchargeAmount, sumSurcharges, systemUnitPrice } from '@/constants/surcharge';
import { getAssignmentRequirement, validateAssignment } from '@/services/dispatch';
import {
  crewIdsOf,
  describeCrew,
  driveBlockReason,
  isActiveStaff,
  leaderOf,
  staffChangeBlocker,
  validateCrew,
  validateStaffProfile,
  WORK_STATUS_META,
} from '@/services/staff';
import { formatVND, uid } from '@/services/format';
import {
  buildMaterialPlan,
  discrepancyOf,
  emptyMaterialSlip,
  hasMaterialsOutside,
  usedOf,
  validateIssue,
  validateReceive,
  validateReturn,
  validateUsage,
  withAddedLines,
} from '@/services/materials';
import { canCloseOrder, canFileClaim, computeTotals, selectSettlement } from '@/services/pricing';
import {
  audit,
  findOrder,
  findSurcharge,
  nextOrderId,
  patchOrder,
  releaseVehicle,
  shortId,
  statusIndex,
  toast,
  withLog,
} from '@/services/store/helpers';
import { createInitialState } from '@/services/store/initialState';
import { appointmentLabel, isMediaSubmitted, missingVideoPhotos, SURVEY_STAGE_META, type SurveyStageKind, surveyStageOf } from '@/features/order/surveyStage';
import { cleanSurveyItems } from '@/features/order/surveyItems';
import { addDaysISO, DOWNTIME_REASON_META, downtimeLabel, downtimeOn, downtimeRangeLabel, todayLocalISO } from '@/services/downtime';
import type {
  AppAction,
  AppState,
  AuditLogEntry,
  ClaimTicket,
  FieldStage,
  MaterialLine,
  MaterialSlip,
  MaterialSlipStatus,
  Order,
  OrderStatus,
  QuoteItem,
  Surcharge,
  SurchargeType,
  SurveyRecord,
  SurveyMethod,
  ToastTone,
  Vehicle,
} from '@/types';

/** Lệnh đang thực hiện — được báo phát sinh hiện trường. */
const SURCHARGE_OPEN_STATUSES: OrderStatus[] = ['ASSIGNED', 'IN_TRANSIT', 'PAUSED_WAITING_CUSTOMER'];

/**
 * Tự đóng hồ sơ khi đủ điều kiện (đã bàn giao, công nợ = 0, vật tư đã quyết toán) — không bắt đơn chờ Admin bấm tay.
 * Gọi sau mọi thao tác có thể làm đơn đủ điều kiện: khách thanh toán, kho kiểm nhận vật tư.
 */
const autoCloseIfReady = (state: AppState, orderId: string): { state: AppState; closed: boolean } => {
  const o = findOrder(state, orderId);
  if (!o || o.status !== 'PENDING_PAYMENT_OR_CLAIM' || !canCloseOrder(selectSettlement(o))) return { state, closed: false };
  const revenue = computeTotals(o).grandTotal;
  const next = patchOrder(state, orderId, (ord) =>
    withLog(
      { ...ord, status: 'COMPLETED', finalRevenue: revenue },
      audit('SYSTEM', 'Tự động đóng hồ sơ', `Khách đã thanh toán đủ & vật tư đã quyết toán — doanh thu ${formatVND(revenue)} được khóa.`),
    ),
  );
  return { state: next, closed: true };
};

const SLIP_RANK: MaterialSlipStatus[] =['NONE', 'DRAFT', 'ISSUED', 'RECEIVED', 'USAGE_REPORTED', 'RETURNED'];

/** Mô tả ngắn các dòng phiếu cho nhật ký, VD: "Thùng carton 20 thùng, Xe đẩy hàng 1 chiếc". */
const describeLines = (lines: MaterialLine[], qty: (l: MaterialLine) => number): string =>
  lines
    .filter((l) => qty(l) > 0)
    .map((l) => `${l.name} ${qty(l)} ${l.unit}`)
    .join(', ') || 'không có dòng nào';

/**
 * Chế độ demo (JUMP_STATUS): dựng phiếu vật tư khớp trạng thái đích, KHÔNG đụng tồn kho thật.
 * Chỉ tiến phiếu lên, không lùi — phiếu đã đi xa hơn trạng thái đích thì giữ nguyên.
 */
const simulateMaterials = (o: Order, status: OrderStatus): MaterialSlip => {
  if (status === 'CANCELLED_EXPIRED' || statusIndex(status) < statusIndex('ASSIGNED')) return o.materials;
  const lines = o.materials.lines.length
    ? o.materials.lines
    : buildMaterialPlan(o.quoteItems, createInitialCatalog().inventory);
  if (lines.length === 0) return emptyMaterialSlip();
  const target: MaterialSlipStatus =
    status === 'ASSIGNED' ? 'DRAFT' : status === 'COMPLETED' ? 'RETURNED' : 'RECEIVED';
  if (SLIP_RANK.indexOf(o.materials.status) >= SLIP_RANK.indexOf(target)) return o.materials;
  const now = Date.now();
  if (target === 'DRAFT') return { ...emptyMaterialSlip(), status: 'DRAFT', lines };
  const held = lines.map((l) => ({ ...l, issued: l.planned, received: l.planned }));
  if (target === 'RECEIVED') return { ...o.materials, status: 'RECEIVED', lines: held, issuedAt: now, receivedAt: now };
  return {
    ...o.materials,
    status: 'RETURNED',
    lines: held.map((l) => ({ ...l, used: l.kind === 'TOOL' ? 0 : l.planned, returned: l.kind === 'TOOL' ? l.planned : 0, lost: 0 })),
    issuedAt: now,
    receivedAt: now,
    usageReportedAt: now,
    returnedAt: now,
    resolutionNote: 'Mô phỏng: quyết toán đủ.',
  };
};

/** Phương thức ghi nhận vào báo giá theo hình thức khảo sát của đơn. */
const SURVEY_METHOD_OF: Record<SurveyStageKind, SurveyMethod> = {
  TRUCK_CONFIRM: 'TRUCK_CONFIRM',
  VIDEO_CALL: 'VIDEO_CALL',
  ON_SITE: 'ON_SITE',
  PHOTOS: 'PHOTO',
};

const patchClaim = (state: AppState, claimId: string, patch: Partial<ClaimTicket>, message?: string, tone: ToastTone = 'success'): AppState => {
  const next = { ...state, claims: state.claims.map((c) => (c.id === claimId ? { ...c, ...patch } : c)) };
  return message ? toast(next, tone, message) : next;
};

export const appReducer = (state: AppState, action: AppAction): AppState => {
  switch (action.type) {
    case 'SET_ACTIVE_ORDER':
      return { ...state, activeOrderId: action.orderId };

    case 'SET_FLAGS': {
      return patchOrder(state, action.orderId, (o) => ({ ...o, flags: { ...o.flags, ...action.patch } }));
    }

    case 'JUMP_STATUS': {
      const idx = statusIndex(action.status);
      let next = patchOrder(state, action.orderId, (o) => {
        const updated: Order = { ...o, status: action.status };
        if (idx >= statusIndex('QUOTED_PENDING_SCHEDULE') && updated.depositPaid === 0 && action.status !== 'CANCELLED_EXPIRED') {
          updated.depositPaid = DEPOSIT_AMOUNT;
          updated.autoCancelDeadline = null;
        }
        if (action.status === 'SURVEYED_PENDING_CONFIRM' && !updated.autoCancelDeadline) updated.autoCancelDeadline = Date.now() + AUTO_CANCEL_MS;
        if (idx >= statusIndex('ASSIGNED') && action.status !== 'CANCELLED_EXPIRED' && !updated.vehicleId) {
          updated.vehicleId = 'veh-1';
          updated.vehicleIds = ['veh-1'];
          // Đội mẫu: st-1 lái xe kiêm trưởng nhóm, st-3 / st-4 bốc xếp.
          updated.crew = [
            { staffId: 'st-1', role: 'DRIVER', vehicleId: 'veh-1' },
            { staffId: 'st-3', role: 'LOADER', vehicleId: null },
            { staffId: 'st-4', role: 'LOADER', vehicleId: null },
          ];
          updated.leaderId = 'st-1';
          updated.crewIds = crewIdsOf(updated.crew);
        }
        const stageFor: Partial<Record<OrderStatus, FieldStage>> = {
          NEW_PENDING_SURVEY: 'NOT_STARTED',
          SURVEYED_PENDING_CONFIRM: 'NOT_STARTED',
          QUOTED_PENDING_SCHEDULE: 'NOT_STARTED',
          ASSIGNED: 'NOT_STARTED',
          IN_TRANSIT: 'MOVING',
          PAUSED_WAITING_CUSTOMER: 'ARRIVED',
          PENDING_PAYMENT_OR_CLAIM: 'HANDED_OVER',
          COMPLETED: 'HANDED_OVER',
        };
        updated.fieldStage = stageFor[action.status] ?? updated.fieldStage;
        updated.materials = simulateMaterials(updated, action.status);
        if (action.status === 'COMPLETED') {
          updated.balancePaid = Math.max(0, computeTotals(updated).grandTotal - updated.depositPaid);
          updated.finalRevenue = computeTotals(updated).grandTotal;
        }
        return withLog(updated, audit('SYSTEM', 'Mô phỏng trạng thái', `Chuyển trạng thái → ${STATUS_META[action.status].label}`));
      });
      const order = findOrder(next, action.orderId);
      if (order && order.vehicleIds.length > 0 && idx >= statusIndex('ASSIGNED') && idx <= statusIndex('PAUSED_WAITING_CUSTOMER')) {
        next = {
          ...next,
          resources: {
            ...next.resources,
            vehicles: next.resources.vehicles.map((v) =>
              order.vehicleIds.includes(v.id) ? { ...v, status: 'BUSY', busyOrderId: shortId(order.id), currentShiftTime: orderShift(order) } : v,
            ),
          },
        };
      }
      return next;
    }

    case 'CREATE_ORDER': {
      const id = nextOrderId(state.orders);
      const order: Order = {
        id,
        customerName: action.input.customerName,
        phone: action.input.phone,
        fromAddress: action.input.fromAddress,
        toAddress: action.input.toAddress,
        houseType: action.input.houseType,
        scheduledDate: action.input.scheduledDate,
        timeSlot: action.input.timeSlot,
        status: 'NEW_PENDING_SURVEY',
        customerPhotos: action.input.photoNames.map((name) => ({ category: 'OTHER' as const, name })),
        items: action.input.items,
        requestedServices: action.input.requestedServices,
        referenceQuote: action.input.referenceQuote,
        surveyAssignment: null,
        surveyReport: null,
        estimatedHours: null,
        vehicleIncident: null,
        negotiation: null,
        surveyMethod: null,
        quoteItems: [],
        discountPercent: 0,
        surcharges: [],
        surveyRecord: null,
        depositRequired: DEPOSIT_AMOUNT,
        depositPaid: 0,
        balancePaid: 0,
        // Gói chỉ thuê xe: phiếu rỗng → coi như đã quyết toán vật tư (isMaterialsSettled).
        materials: emptyMaterialSlip(),
        finalRevenue: null,
        flags: { isVehicleIncident: false, waitingMinutes: 0, hoursUntilDeparture: 72 },
        fieldStage: 'NOT_STARTED',
        vehicleId: null,
        vehicleIds: [],
        crewIds: [],
        crew: [],
        leaderId: null,
        auditLog: [audit('CUSTOMER', 'Tạo yêu cầu vận chuyển', `Khách gửi yêu cầu: ${action.input.items.length} món đồ, ${action.input.requestedServices.length} dịch vụ, ${action.input.photoNames.length} ảnh.`)],
        autoCancelDeadline: null,
        depositReservedUntil: null,
        createdAt: Date.now(),
        bookingDetails: action.input.details ?? null,
      };
      return toast({ ...state, activeOrderId: id, orders: [order, ...state.orders] }, 'success', `Đã gửi yêu cầu ${id}. Báo giá tham khảo đã sẵn sàng; báo giá chính thức sẽ gửi sau khi khảo sát.`);
    }

    case 'ASSIGN_SURVEYOR': {
      const o = findOrder(state, action.orderId);
      if (!o || o.status !== 'NEW_PENDING_SURVEY') return toast(state, 'warning', 'Chỉ phân người khảo sát cho đơn đang chờ khảo sát.');
      if (o.bookingDetails?.serviceMode === 'TRUCK_ONLY') return toast(state, 'warning', 'Gói chỉ thuê xe không cần khảo sát — gọi khách xác nhận xe rồi gửi báo giá.');
      if (o.surveyReport) return toast(state, 'info', `${o.id} đã có kết quả khảo sát.`);
      const surveyor = state.resources.staff.find((s) => s.id === action.surveyorId);
      if (!surveyor) return toast(state, 'warning', 'Cần chọn người khảo sát.');
      if (!isActiveStaff(surveyor)) return toast(state, 'warning', `${surveyor.name} đang ${WORK_STATUS_META[surveyor.status].label.toLowerCase()}, hãy chọn người khác.`);
      const surveyDay = o.bookingDetails?.surveyDate || todayLocalISO();
      const surveyorOff = downtimeOn(state.resources.downtimes, 'STAFF', surveyor.id, surveyDay);
      if (surveyorOff) return toast(state, 'warning', `${surveyor.name}: ${downtimeLabel(surveyorOff).toLowerCase()} — hãy chọn người khác.`);
      const appointment = appointmentLabel(o);
      const note = action.note.trim();
      const kind = SURVEY_STAGE_META[surveyStageOf(o)].label.toLowerCase();
      const reassign = o.surveyAssignment && o.surveyAssignment.surveyorId !== surveyor.id;
      const next = patchOrder(state, action.orderId, (ord) =>
        withLog(
          { ...ord, surveyAssignment: { assignedAt: Date.now(), note, surveyorId: surveyor.id } },
          audit(
            'COORDINATOR',
            reassign ? 'Đổi người khảo sát' : 'Phân người khảo sát',
            `Phân ${surveyor.name} (${surveyor.code}) — ${kind}${appointment ? `, lịch khách hẹn ${appointment}` : ''}.${note ? ` Ghi chú: ${note}.` : ''}`,
          ),
        ),
      );
      return toast(next, 'success', `Đã phân ${surveyor.name} khảo sát ${o.id}.`);
    }

    case 'UNASSIGN_SURVEYOR': {
      const o = findOrder(state, action.orderId);
      if (!o?.surveyAssignment || o.status !== 'NEW_PENDING_SURVEY' || o.surveyReport) return state;
      const next = patchOrder(state, action.orderId, (ord) =>
        withLog({ ...ord, surveyAssignment: null }, audit('COORDINATOR', 'Hủy phân công khảo sát', 'Đơn quay lại chờ phân người khảo sát.')),
      );
      return toast(next, 'info', `${o.id}: đã hủy phân công khảo sát.`);
    }

    case 'SUBMIT_SURVEY_REPORT': {
      const o = findOrder(state, action.orderId);
      if (!o || o.status !== 'NEW_PENDING_SURVEY') return toast(state, 'warning', 'Đơn không còn ở trạng thái chờ khảo sát.');
      if (!o.surveyAssignment) return toast(state, 'warning', 'Đơn chưa được phân người khảo sát.');
      if (surveyStageOf(o) === 'PHOTOS' && !isMediaSubmitted(o)) return toast(state, 'warning', 'Khách chưa gửi ảnh / video — chưa thể gửi kết quả khảo sát.');
      if (cleanSurveyItems(action.items).length === 0) return toast(state, 'warning', 'Chưa có món đồ nào trong danh sách khảo sát.');
      if (totalVehicles(action.proposal.vehicles) === 0) return toast(state, 'warning', 'Đề xuất ít nhất một xe.');
      if (!(action.proposal.estimatedHours > 0)) return toast(state, 'warning', 'Ghi tổng thời gian dự kiến của chuyến.');
      const surveyor = state.resources.staff.find((s) => s.id === o.surveyAssignment?.surveyorId);
      const p = action.proposal;
      const next = patchOrder(state, action.orderId, (ord) =>
        withLog(
          {
            ...ord,
            surveyReport: {
              submittedAt: Date.now(),
              surveyorId: ord.surveyAssignment!.surveyorId,
              items: cleanSurveyItems(action.items),
              note: action.note.trim(),
              photos: action.photos,
              proposal: p,
            },
          },
          audit(
            'STAFF',
            'Gửi kết quả khảo sát',
            `${surveyor?.name ?? 'Người khảo sát'} đề xuất ${vehicleLinesLabel(p.vehicles)}, ${p.workers} người, ${p.distanceKm} km${action.photos.length ? `, ${action.photos.length} ảnh hiện trường` : ''}.`,
          ),
        ),
      );
      return toast(next, 'success', `Đã gửi kết quả khảo sát ${o.id} cho Điều phối lập báo giá.`);
    }

    case 'CUSTOMER_ADD_SURVEY_MEDIA': {
      const o = findOrder(state, action.orderId);
      if (!o || o.status !== 'NEW_PENDING_SURVEY' || action.files.length === 0) return state;
      const next = patchOrder(state, action.orderId, (ord) =>
        withLog({ ...ord, customerPhotos: [...ord.customerPhotos, ...action.files] }, audit('CUSTOMER', 'Tải ảnh / video khảo sát', `Khách tải thêm ${action.files.length} tệp.`)),
      );
      return toast(next, 'success', `Đã tải lên ${action.files.length} tệp.`);
    }

    case 'CUSTOMER_SUBMIT_SURVEY_MEDIA': {
      const o = findOrder(state, action.orderId);
      if (!o || o.status !== 'NEW_PENDING_SURVEY') return state;
      if (o.customerPhotos.length === 0) return toast(state, 'warning', 'Hãy tải lên ít nhất 1 ảnh hoặc video trước khi gửi.');
      const next = patchOrder(state, action.orderId, (ord) =>
        withLog({ ...ord, surveyMediaSubmittedAt: Date.now() }, audit('CUSTOMER', 'Gửi ảnh / video khảo sát', `Khách gửi ${ord.customerPhotos.length} tệp để lập báo giá.`)),
      );
      return toast(next, 'success', 'Đã gửi ảnh / video cho chuyên viên. Báo giá sẽ được gửi sớm nhất.');
    }

    case 'CUSTOMER_UPDATE_SURVEY': {
      const o = findOrder(state, action.orderId);
      if (!o?.bookingDetails || o.status !== 'NEW_PENDING_SURVEY') return state;
      const appointment = action.preference !== 'PHOTOS_ONLY';
      const label = { VIDEO_CALL: 'Video call', ON_SITE: 'Trực tiếp tận nơi', PHOTOS_ONLY: 'Gửi ảnh / video' }[action.preference];
      const next = patchOrder(state, action.orderId, (ord) =>
        withLog(
          {
            ...ord,
            bookingDetails: ord.bookingDetails && {
              ...ord.bookingDetails,
              surveyPreference: action.preference,
              surveyDate: appointment ? action.date : '',
              surveyContactSlot: appointment ? action.slot : '',
            },
          },
          audit('CUSTOMER', 'Cập nhật lịch khảo sát', appointment ? `${label} — ${action.date.split('-').reverse().join('/')} (${action.slot}).` : `Đổi sang ${label}.`),
        ),
      );
      return toast(next, 'success', 'Đã cập nhật hình thức / lịch khảo sát.');
    }

    case 'SUBMIT_SURVEY': {
      const o = findOrder(state, action.orderId);
      if (!o || o.status !== 'NEW_PENDING_SURVEY') return toast(state, 'warning', 'Đơn không còn ở trạng thái chờ khảo sát.');
      const kind = surveyStageOf(o);
      const report = o.surveyReport;
      if (kind !== 'TRUCK_CONFIRM' && !report) return toast(state, 'warning', 'Chưa có kết quả khảo sát — chờ người khảo sát gửi về rồi mới lập báo giá.');
      if (!(action.estimatedHours > 0)) return toast(state, 'warning', 'Ghi tổng thời gian dự kiến của chuyến để biết xe & người bận bao lâu.');
      const evidenceNote = action.evidence.note.trim();
      if (kind === 'VIDEO_CALL') {
        // Cuộc gọi không để lại dấu vết → cần ảnh (người khảo sát đính kèm, Điều phối bổ sung, hoặc khách đã gửi).
        const missing = missingVideoPhotos([...(report?.photos ?? []), ...action.evidence.photos]);
        if (missing && !isMediaSubmitted(o)) return toast(state, 'warning', `${missing} Hoặc yêu cầu khách gửi bổ sung ảnh.`);
      }
      if (action.removedRequested.some((r) => !r.reason.trim())) return toast(state, 'warning', 'Ghi lý do khi bỏ dịch vụ khách đã chọn lúc đặt đơn.');
      const offCatalog = action.items.filter((i) => i.offCatalog);
      if (offCatalog.some((i) => !i.note?.trim() || i.unitPrice <= 0 || !i.name.trim())) return toast(state, 'warning', 'Khoản ngoài bảng giá cần tên, đơn giá và lý do.');
      const method = SURVEY_METHOD_OF[kind];
      const adjustReason = action.adjustReason?.trim();
      const surveyRecord: SurveyRecord = {
        method,
        baseline: action.input,
        // Ghi chú & ảnh của người khảo sát + phần Điều phối bổ sung khi lập báo giá.
        note: [report?.note ?? '', evidenceNote].filter(Boolean).join(' — '),
        photos: [...(report?.photos ?? []), ...action.evidence.photos],
        recordedAt: Date.now(),
      };
      const extraLog = [
        ...action.removedRequested.map((r) => `Bỏ dịch vụ khách chọn "${r.name}": ${r.reason.trim()}`),
        ...offCatalog.map((i) => `Ngoài bảng giá "${i.name}" ${i.quantity} × ${formatVND(i.unitPrice)}: ${i.note}`),
      ];
      const methodLabel: Record<SurveyMethod, string> = {
        ON_SITE: `Khảo sát trực tiếp (${o.surveyReport?.photos.length ?? 0} ảnh hiện trường)`,
        VIDEO_CALL: `Khảo sát qua video call (${(o.surveyReport?.photos.length ?? 0) + action.evidence.photos.length} ảnh)`,
        TRUCK_CONFIRM: 'Xác nhận xe qua điện thoại (chỉ thuê xe)',
        PHOTO: `Khảo sát qua ${o.customerPhotos.length} ảnh / video khách gửi`,
      };
      const next = patchOrder(state, action.orderId, (ord) =>
        withLog(
          { ...ord, quoteItems: action.items, estimatedHours: action.estimatedHours, surveyMethod: method, surveyRecord, status: 'SURVEYED_PENDING_CONFIRM', autoCancelDeadline: Date.now() + AUTO_CANCEL_MS },
          audit(
            'COORDINATOR',
            'Gửi báo giá',
            `${methodLabel[method]} — ${action.items.length} hạng mục, tổng ${formatVND(action.items.reduce((s, i) => s + i.unitPrice * i.quantity, 0))}, dự kiến ${hoursLabel(action.estimatedHours)}.` +
              (surveyRecord.photos.length > 0 ? ` ${surveyRecord.photos.length} ảnh làm căn cứ.` : '') +
              (adjustReason ? ` Điều chỉnh so với đề xuất người khảo sát: ${adjustReason}.` : '') +
              (extraLog.length > 0 ? ` ${extraLog.join('; ')}.` : ''),
          ),
        ),
      );
      return toast(next, 'success', `Đã gửi báo giá cho ${action.orderId}. Bộ đếm tự hủy 24h bắt đầu chạy.`);
    }

    case 'CUSTOMER_REQUEST_NEGOTIATION': {
      const o = findOrder(state, action.orderId);
      const message = action.message.trim();
      if (!o || o.status !== 'SURVEYED_PENDING_CONFIRM' || !message) return state;
      // Tạm dừng đếm ngược 24h trong lúc Điều phối xem xét — khởi động lại khi gửi lại báo giá.
      const next = patchOrder(state, action.orderId, (ord) =>
        withLog({ ...ord, negotiation: { requestedAt: Date.now(), message }, autoCancelDeadline: null }, audit('CUSTOMER', 'Yêu cầu thương lượng báo giá', message)),
      );
      return toast(next, 'success', 'Đã gửi yêu cầu thương lượng. Điều phối sẽ gửi lại báo giá cho bạn.');
    }

    case 'RESEND_QUOTE': {
      const o = findOrder(state, action.orderId);
      if (!o || o.status !== 'SURVEYED_PENDING_CONFIRM') return state;
      const note = action.note.trim();
      const next = patchOrder(state, action.orderId, (ord) =>
        withLog(
          { ...ord, negotiation: null, autoCancelDeadline: Date.now() + AUTO_CANCEL_MS },
          audit('COORDINATOR', 'Gửi lại báo giá', `${o.negotiation ? 'Phản hồi yêu cầu thương lượng' : 'Cập nhật báo giá'} — tổng ${formatVND(computeTotals(ord).grandTotal)}.${note ? ` ${note}` : ''}`),
        ),
      );
      return toast(next, 'success', `Đã gửi lại báo giá ${o.id}. Bộ đếm 24h bắt đầu lại.`);
    }

    case 'PAY_DEPOSIT': {
      const o = findOrder(state, action.orderId);
      if (!o || o.status !== 'SURVEYED_PENDING_CONFIRM') return toast(state, 'warning', 'Đơn hiện không ở trạng thái chờ chốt báo giá.');
      if (o.negotiation) return toast(state, 'info', 'Yêu cầu thương lượng đang chờ Điều phối phản hồi — hãy đợi báo giá mới.');
      const next = patchOrder(state, action.orderId, (ord) =>
        withLog(
          { ...ord, depositPaid: ord.depositRequired, status: 'QUOTED_PENDING_SCHEDULE', autoCancelDeadline: null },
          audit('CUSTOMER', 'Đồng ý báo giá & đặt cọc', `Thanh toán cọc ${formatVND(ord.depositRequired)} qua VietQR.`),
        ),
      );
      return toast(next, 'success', `Đã nhận cọc ${formatVND(o.depositRequired)}. Đơn chuyển sang Điều phối.`);
    }

    case 'STAFF_PROPOSE_SURCHARGE': {
      const o = findOrder(state, action.orderId);
      if (!o || !SURCHARGE_OPEN_STATUSES.includes(o.status) || o.fieldStage === 'HANDED_OVER') return toast(state, 'warning', 'Chỉ báo sai lệch khi lệnh đang thực hiện và chưa bàn giao.');
      const { report } = action;
      if (report.lines.length === 0) return toast(state, 'warning', 'Thêm ít nhất 1 dòng sai lệch.');
      const reportId = uid('rp');
      const now = Date.now();
      const lines: Surcharge[] = [];
      for (const [i, d] of report.lines.entries()) {
        const label = `Dòng ${i + 1} (${SURCHARGE_TYPE_META[d.type].label})`;
        if (!d.actual.trim()) return toast(state, 'warning', `${label}: ghi rõ thực tế tại hiện trường.`);
        if (!d.description.trim()) return toast(state, 'warning', `${label}: mô tả sai lệch.`);
        // Mỗi dòng có thể bị bác / từ chối riêng nên phải có bằng chứng riêng.
        if (d.photos.length === 0) return toast(state, 'warning', `${label}: cần ít nhất 1 ảnh bằng chứng.`);
        if (d.quantity < 1) return toast(state, 'warning', `${label}: số lượng chênh lệch phải lớn hơn 0.`);
        // Loại hệ thống tự tính: không dùng đơn giá phía NV gửi lên.
        const unitPrice = SURCHARGE_TYPE_META[d.type].mode === 'SYSTEM' ? systemUnitPrice(d.type, o, state.resources.vehicles) : d.unitPrice;
        if (unitPrice <= 0) return toast(state, 'warning', `${label}: chưa có đơn giá.`);
        lines.push({
          ...d,
          id: uid('sc'),
          reportId,
          // Ghi chú chung chỉ có nghĩa khi báo nhiều dòng.
          reportNote: report.lines.length > 1 ? report.note.trim() : '',
          surveyed: d.surveyed.trim(),
          actual: d.actual.trim(),
          description: d.description.trim(),
          unitPrice,
          proposedQuantity: d.quantity,
          proposedUnitPrice: unitPrice,
          status: 'PROPOSED',
          proposedAt: now,
          reviewedAt: null,
          reviewNote: '',
          respondedAt: null,
          customerNote: '',
        });
      }
      const next = patchOrder(state, action.orderId, (ord) => ({ ...ord, surcharges: [...ord.surcharges, ...lines] }));
      return toast(next, 'warning', `Đã gửi ${lines.length} dòng sai lệch cho Điều phối duyệt. Tạm khóa bước bắt đầu làm / di chuyển.`);
    }

    case 'STAFF_CANCEL_SURCHARGE': {
      const s = findSurcharge(state, action.orderId, action.surchargeId);
      if (s?.status !== 'PROPOSED') return toast(state, 'warning', 'Chỉ rút được đề xuất chưa được Điều phối xử lý.');
      const next = patchOrder(state, action.orderId, (ord) => ({
        ...ord,
        surcharges: ord.surcharges.map((x) => (x.id === s.id ? { ...x, status: 'CANCELLED' as const } : x)),
      }));
      return toast(next, 'info', `Đã rút đề xuất "${s.name}".`);
    }

    case 'COORD_SEND_SURCHARGE': {
      const o = findOrder(state, action.orderId);
      if (!o || action.items.length === 0) return state;
      const note = action.note.trim();
      const now = Date.now();
      const sent: Surcharge[] = [];
      const cancelledIds = new Set<string>();
      const logLines: string[] = [];
      for (const it of action.items) {
        const s = o.surcharges.find((x) => x.id === it.surchargeId);
        if (!s || (s.status !== 'PROPOSED' && s.status !== 'REJECTED_BY_CUSTOMER')) return toast(state, 'warning', 'Có dòng phát sinh không còn chờ Điều phối xử lý.');
        if (it.quantity < 1) return toast(state, 'warning', `${s.name}: số lượng phải lớn hơn 0.`);
        // Chỉ loại nhập tay mới được sửa đơn giá; loại bảng giá chỉ được đổi sang dịch vụ khác cùng loại (giữ giá bảng giá).
        const mode = SURCHARGE_TYPE_META[s.type].mode;
        const swap = mode === 'CATALOG' && it.service && it.service.name !== s.name ? it.service : null;
        const unitPrice = mode === 'MANUAL' ? it.unitPrice : (swap?.unitPrice ?? s.unitPrice);
        if (unitPrice <= 0) return toast(state, 'warning', `${s.name}: đơn giá phải lớn hơn 0.`);
        const changed = it.quantity !== s.proposedQuantity || unitPrice !== s.proposedUnitPrice;
        const line: Surcharge = {
          ...s,
          name: swap?.name ?? s.name,
          unit: swap?.unit ?? s.unit,
          quantity: it.quantity,
          unitPrice,
          status: 'WAITING_CUSTOMER',
          reviewedAt: now,
          reviewNote: note,
        };
        if (s.status === 'REJECTED_BY_CUSTOMER') {
          // Gửi lại sau khi khách từ chối: dòng cũ hủy, tạo dòng mới để giữ lịch sử thương lượng.
          cancelledIds.add(s.id);
          sent.push({ ...line, id: uid('sc'), respondedAt: null, customerNote: '' });
        } else {
          sent.push(line);
        }
        logLines.push(
          `${line.name} (${s.surveyed || 'khảo sát'} → ${s.actual}): ${it.quantity} ${line.unit} × ${formatVND(unitPrice)}` +
            (swap ? ` [NV chọn "${s.name}" → Điều phối đổi dịch vụ]` : '') +
            (changed ? ` [NV đề xuất ${s.proposedQuantity} × ${formatVND(s.proposedUnitPrice)}]` : ''),
        );
      }
      const total = formatVND(sumSurcharges(sent));
      const resend = cancelledIds.size > 0;
      const next = patchOrder(state, action.orderId, (ord) =>
        withLog(
          {
            ...ord,
            surcharges: [
              ...ord.surcharges.map((x) => {
                if (cancelledIds.has(x.id)) return { ...x, status: 'CANCELLED' as const, reviewedAt: now };
                return sent.find((y) => y.id === x.id) ?? x;
              }),
              ...sent.filter((y) => !ord.surcharges.some((x) => x.id === y.id)),
            ],
          },
          audit(
            'COORDINATOR',
            resend ? 'Gửi lại phát sinh hiện trường' : 'Chốt phát sinh hiện trường',
            `${logLines.join('; ')}. Tổng ${total}.${note ? ` Ghi chú: ${note}.` : ''} Gửi khách xác nhận.`,
          ),
        ),
      );
      return toast(next, 'success', `Đã gửi khách xác nhận ${sent.length} dòng phát sinh, tổng ${total}.`);
    }

    case 'COORD_REJECT_SURCHARGE': {
      const s = findSurcharge(state, action.orderId, action.surchargeId);
      if (s?.status !== 'PROPOSED') return toast(state, 'warning', 'Chỉ bác được đề xuất đang chờ duyệt.');
      const note = action.note.trim();
      if (!note) return toast(state, 'warning', 'Bắt buộc ghi lý do bác đề xuất để nhân viên nắm.');
      const next = patchOrder(state, action.orderId, (ord) =>
        withLog(
          { ...ord, surcharges: ord.surcharges.map((x) => (x.id === s.id ? { ...x, status: 'REJECTED' as const, reviewedAt: Date.now(), reviewNote: note } : x)) },
          audit('COORDINATOR', 'Bác đề xuất phát sinh hiện trường', `${s.name} (${s.surveyed || 'khảo sát'} → ${s.actual}). Lý do: ${note}`),
        ),
      );
      return toast(next, 'info', `Đã bác dòng "${s.name}".`);
    }

    case 'COORD_WITHDRAW_SURCHARGE': {
      const s = findSurcharge(state, action.orderId, action.surchargeId);
      if (s?.status !== 'WAITING_CUSTOMER' && s?.status !== 'REJECTED_BY_CUSTOMER') return toast(state, 'warning', 'Chỉ rút được khoản đang chờ khách hoặc khách đã từ chối.');
      const note = action.note.trim();
      const next = patchOrder(state, action.orderId, (ord) =>
        withLog(
          { ...ord, surcharges: ord.surcharges.map((x) => (x.id === s.id ? { ...x, status: 'CANCELLED' as const, reviewedAt: Date.now(), reviewNote: note || x.reviewNote } : x)) },
          audit('COORDINATOR', 'Rút phát sinh hiện trường', `${s.name}: ${formatVND(surchargeAmount(s))}.${note ? ` Lý do: ${note}` : ''}`),
        ),
      );
      return toast(next, 'info', `Đã rút khoản "${s.name}".`);
    }

    case 'CUSTOMER_ACCEPT_SURCHARGE': {
      const o = findOrder(state, action.orderId);
      const lines = o?.surcharges.filter((x) => action.surchargeIds.includes(x.id)) ?? [];
      if (!o || lines.length === 0 || lines.some((x) => x.status !== 'WAITING_CUSTOMER')) return toast(state, 'warning', 'Khoản phát sinh không còn chờ bạn xác nhận.');
      const items: QuoteItem[] = lines.map((s) => ({
        id: uid('qi'),
        name: `Phát sinh: ${s.name}`,
        category: 'FIELD_ADJUSTMENT',
        unit: s.unit,
        unitPrice: s.unitPrice,
        quantity: s.quantity,
        note: `${s.surveyed || 'Khảo sát'} → ${s.actual}`,
      }));
      const now = Date.now();
      const next = patchOrder(state, action.orderId, (ord) =>
        withLog(
          {
            ...ord,
            quoteItems: [...ord.quoteItems, ...items],
            surcharges: ord.surcharges.map((x) => (action.surchargeIds.includes(x.id) ? { ...x, status: 'ACCEPTED' as const, respondedAt: now } : x)),
          },
          audit('CUSTOMER', 'Xác nhận phát sinh hiện trường', `${lines.map((s) => `${s.name} +${formatVND(surchargeAmount(s))}`).join('; ')}. Tổng ${formatVND(sumSurcharges(lines))}.`),
        ),
      );
      const updated = findOrder(next, action.orderId);
      return toast(next, 'success', updated && isSurchargeBlocking(updated) ? 'Đã xác nhận. Còn khoản phát sinh khác đang chờ xử lý.' : 'Đã xác nhận. Đội xe được phép tiếp tục.');
    }

    case 'CUSTOMER_REJECT_SURCHARGE': {
      const s = findSurcharge(state, action.orderId, action.surchargeId);
      if (s?.status !== 'WAITING_CUSTOMER') return toast(state, 'warning', 'Khoản phát sinh không còn chờ bạn xác nhận.');
      const note = action.note.trim();
      if (!note) return toast(state, 'warning', 'Vui lòng cho biết lý do từ chối để chúng tôi liên hệ lại.');
      const next = patchOrder(state, action.orderId, (ord) =>
        withLog(
          { ...ord, surcharges: ord.surcharges.map((x) => (x.id === s.id ? { ...x, status: 'REJECTED_BY_CUSTOMER' as const, respondedAt: Date.now(), customerNote: note } : x)) },
          audit('CUSTOMER', 'Từ chối phát sinh hiện trường', `${s.name}: ${formatVND(surchargeAmount(s))}. Lý do: ${note}`),
        ),
      );
      return toast(next, 'info', 'Đã gửi phản hồi. Điều phối viên sẽ liên hệ bạn ngay.');
    }

    case 'DEMO_SURCHARGE': {
      const o = findOrder(state, action.orderId);
      if (!o) return state;
      if (!action.on) {
        return patchOrder(state, action.orderId, (ord) => ({
          ...ord,
          surcharges: ord.surcharges.map((x) => (SURCHARGE_BLOCKING.includes(x.status) ? { ...x, status: 'CANCELLED' as const } : x)),
        }));
      }
      const reportId = uid('rp');
      const now = Date.now();
      const base = {
        reportId,
        reportNote: 'Đầu hẻm có cổng chắn, xe tải phải đỗ ngoài đường lớn; nhà thực tế cao hơn khảo sát 1 lầu.',
        description: '',
        photos: [],
        status: 'PROPOSED' as const,
        proposedAt: now,
        reviewedAt: null,
        reviewNote: '',
        respondedAt: null,
        customerNote: '',
      };
      const line = (type: SurchargeType, surveyed: string, actual: string, quantity: number, description: string): Surcharge => {
        const unitPrice = systemUnitPrice(type, o, state.resources.vehicles);
        const meta = SURCHARGE_TYPE_META[type];
        return { ...base, id: uid('sc'), type, name: meta.label, unit: meta.unit, surveyed, actual, description, quantity, unitPrice, proposedQuantity: quantity, proposedUnitPrice: unitPrice };
      };
      const sample = [line('LONG_CARRY', 'Hẻm 50m', 'Hẻm 200m, xe tải không vào được', 3, 'Đầu hẻm có cổng chắn, phải trung chuyển thủ công thêm 150m.'), line('EXTRA_FLOOR', 'Lầu 2', 'Lầu 3', 1, 'Nhà có thêm tầng lửng, đồ đạc để ở lầu 3.')];
      return patchOrder(state, action.orderId, (ord) => ({ ...ord, surcharges: [...ord.surcharges, ...sample] }));
    }

    case 'ADVANCE_FIELD_STAGE': {
      const o = findOrder(state, action.orderId);
      if (!o) return state;
      if (o.status !== 'ASSIGNED' && o.status !== 'IN_TRANSIT') return toast(state, 'warning', 'Lệnh công tác chưa sẵn sàng hoặc đang tạm dừng.');
      if (nextFieldStage(o)?.stage !== action.stage) return state;
      // Bước nhận vật tư chỉ hoàn thành qua CONFIRM_MATERIALS_RECEIVED (kiểm từng dòng phiếu xuất).
      if (action.stage === 'MATERIALS_PICKED' && o.materials.lines.length > 0) {
        return toast(state, 'warning', o.materials.status === 'DRAFT' ? 'Kho chưa xuất phiếu vật tư cho lệnh này.' : 'Hãy kiểm và xác nhận số vật tư thực nhận trên phiếu.');
      }
      if ((action.stage === 'WORKING' || action.stage === 'MOVING') && isSurchargeBlocking(o)) {
        return toast(state, 'danger', 'Chưa thể bắt đầu: chờ khách xác nhận báo giá điều chỉnh.');
      }
      if (action.stage === 'MOVING' && o.flags.isVehicleIncident) {
        return toast(state, 'danger', 'Xe đang báo sự cố — chờ điều phối chuyển giao phương tiện.');
      }
      let status: OrderStatus = o.status;
      if (action.stage === 'WORKING') status = 'IN_TRANSIT';
      if (action.stage === 'HANDED_OVER') status = 'PENDING_PAYMENT_OR_CLAIM';
      let next = patchOrder(state, action.orderId, (ord) =>
        withLog({ ...ord, fieldStage: action.stage, status }, audit('STAFF', FIELD_STAGE_LOG_ACTION, FIELD_STAGE_LOG_LABEL[action.stage])),
      );
      if (action.stage === 'HANDED_OVER') {
        next = { ...next, resources: { ...next.resources, vehicles: releaseVehicle(next.resources.vehicles, o.vehicleIds) } };
        // Đã trả đủ từ trước & không dùng vật tư kho → đóng hồ sơ ngay khi bàn giao.
        const closed = autoCloseIfReady(next, action.orderId);
        next = toast(closed.state, 'success', closed.closed ? 'Đã lập biên bản bàn giao. Đơn đã hoàn tất.' : 'Đã lập biên bản bàn giao. Đơn chuyển sang chờ quyết toán.');
      }
      return next;
    }

    // Tồn kho (CatalogStore) được trừ / cộng ở giao diện admin, sau khi kiểm bằng cùng các hàm validate*.
    case 'ISSUE_MATERIALS': {
      const o = findOrder(state, action.orderId);
      if (!o) return state;
      if (o.status !== 'ASSIGNED') return toast(state, 'warning', 'Chỉ xuất vật tư cho lệnh đã gán đội và chưa bắt đầu.');
      const draftSlip = withAddedLines(o.materials, action.addedLines);
      const err = validateIssue(draftSlip, action.issued);
      if (err) return toast(state, 'danger', err);
      // Dòng xuất 0 thì bỏ khỏi phiếu — không có gì để nhận / trả.
      const lines = draftSlip.lines.map((l) => ({ ...l, issued: action.issued[l.id] ?? 0 })).filter((l) => l.issued > 0);
      const next = patchOrder(state, action.orderId, (ord) =>
        withLog(
          { ...ord, materials: { ...ord.materials, status: 'ISSUED', lines, issuedAt: Date.now() } },
          audit('ADMIN', 'Xuất kho vật tư', describeLines(lines, (l) => l.issued)),
        ),
      );
      return toast(next, 'success', `${o.id}: đã xuất kho, chờ đội xe xác nhận nhận.`);
    }

    case 'CONFIRM_MATERIALS_RECEIVED': {
      const o = findOrder(state, action.orderId);
      if (!o) return state;
      if (o.status !== 'ASSIGNED' || o.fieldStage !== 'NOT_STARTED') return toast(state, 'warning', 'Chỉ xác nhận nhận vật tư trước khi bắt đầu lệnh.');
      const err = validateReceive(o.materials, action.received, action.note);
      if (err) return toast(state, 'danger', err);
      const lines = o.materials.lines.map((l) => ({ ...l, received: action.received[l.id] ?? 0 }));
      const short = lines.filter((l) => (l.received ?? 0) < l.issued);
      const note = action.note.trim();
      const next = patchOrder(state, action.orderId, (ord) =>
        withLog(
          { ...ord, fieldStage: 'MATERIALS_PICKED', materials: { ...ord.materials, status: 'RECEIVED', lines, receivedAt: Date.now(), receiveNote: note } },
          audit(
            'STAFF',
            'Nhận vật tư tại kho',
            short.length
              ? `Nhận thiếu: ${describeLines(short, (l) => l.issued - (l.received ?? 0))}. Ghi chú: ${note}.`
              : `Nhận đủ theo phiếu: ${describeLines(lines, (l) => l.received ?? 0)}.`,
          ),
        ),
      );
      return toast(next, short.length ? 'warning' : 'success', short.length ? 'Đã ghi nhận nhận thiếu — kho sẽ đối chiếu khi quyết toán.' : 'Đã nhận đủ vật tư theo phiếu.');
    }

    case 'REPORT_MATERIAL_USAGE': {
      const o = findOrder(state, action.orderId);
      if (!o) return state;
      if (o.fieldStage !== 'HANDED_OVER') return toast(state, 'warning', 'Khai vật tư đã dùng sau khi lập biên bản bàn giao.');
      const err = validateUsage(o.materials, action.used);
      if (err) return toast(state, 'danger', err);
      const lines = o.materials.lines.map((l) => ({ ...l, used: l.kind === 'TOOL' ? 0 : (action.used[l.id] ?? 0) }));
      const next = patchOrder(state, action.orderId, (ord) =>
        withLog(
          { ...ord, materials: { ...ord.materials, status: 'USAGE_REPORTED', lines, usageReportedAt: Date.now() } },
          audit('STAFF', 'Khai vật tư đã dùng', `Đã dùng: ${describeLines(lines, usedOf)}. Mang về kho: ${describeLines(lines, (l) => (l.received ?? 0) - usedOf(l))}.`),
        ),
      );
      return toast(next, 'success', 'Đã gửi báo cáo vật tư. Hãy mang vật tư dư & dụng cụ về kho.');
    }

    case 'RETURN_MATERIALS': {
      const o = findOrder(state, action.orderId);
      if (!o) return state;
      const err = validateReturn(o, action.returned, action.note);
      if (err) return toast(state, 'danger', err);
      const cancelled = o.status === 'CANCELLED_EXPIRED';
      const lines = o.materials.lines.map((l) => {
        const returned = action.returned[l.id] ?? 0;
        return { ...l, used: usedOf(l), returned, lost: Math.max(0, discrepancyOf(l, returned)) };
      });
      const lost = lines.filter((l) => l.lost > 0);
      const note = action.note.trim();
      const next = patchOrder(state, action.orderId, (ord) =>
        withLog(
          { ...ord, materials: { ...ord.materials, status: 'RETURNED', lines, returnedAt: Date.now(), resolutionNote: note } },
          audit(
            'ADMIN',
            cancelled ? 'Thu hồi vật tư do hủy đơn' : 'Kiểm nhận vật tư trả về',
            `Nhập lại kho: ${describeLines(lines, (l) => l.returned ?? 0)}.${lost.length ? ` Chênh lệch: ${describeLines(lost, (l) => l.lost)} — ${note}.` : ' Không chênh lệch.'}`,
          ),
        ),
      );
      const closed = autoCloseIfReady(next, action.orderId);
      const base = lost.length ? `${o.id}: đã quyết toán vật tư, ghi nhận chênh lệch vào biên bản.` : `${o.id}: vật tư đã về kho đủ.`;
      return toast(closed.state, lost.length ? 'warning' : 'success', closed.closed ? `${base} Hồ sơ đã tự động đóng.` : base);
    }

    case 'START_WAIT_TIMER': {
      const o = findOrder(state, action.orderId);
      if (!o || (o.status !== 'IN_TRANSIT' && o.status !== 'ASSIGNED')) return toast(state, 'warning', 'Chỉ bật đồng hồ chờ khi đơn đang thực hiện.');
      const next = patchOrder(state, action.orderId, (ord) =>
        withLog({ ...ord, status: 'PAUSED_WAITING_CUSTOMER' }, audit('STAFF', 'Bật đồng hồ chờ khách (Mã 1.1.4)', '30 phút đầu miễn phí, sau đó +150.000đ/giờ.')),
      );
      return toast(next, 'warning', 'Đã bật đồng hồ chờ khách.');
    }

    case 'STOP_WAIT_TIMER': {
      return patchOrder(state, action.orderId, (o) => {
        if (o.status !== 'PAUSED_WAITING_CUSTOMER') return o;
        const resume: OrderStatus = FIELD_STAGE_ORDER.indexOf(o.fieldStage) >= FIELD_STAGE_ORDER.indexOf('WORKING') ? 'IN_TRANSIT' : 'ASSIGNED';
        return withLog({ ...o, status: resume }, audit('STAFF', 'Dừng đồng hồ chờ', `Tổng thời gian chờ ${o.flags.waitingMinutes} phút.`));
      });
    }

    case 'TICK_WAIT_TIMERS':
      return {
        ...state,
        orders: state.orders.map((o) =>
          o.status === 'PAUSED_WAITING_CUSTOMER' ? { ...o, flags: { ...o.flags, waitingMinutes: Math.min(600, o.flags.waitingMinutes + 1) } } : o,
        ),
      };

    case 'REPORT_VEHICLE_INCIDENT': {
      const note = action.note.trim();
      if (!note) return toast(state, 'warning', 'Mô tả xe bị gì để Điều phối xử lý.');
      const kindLabel = VEHICLE_INCIDENT_KINDS[action.kind];
      const next = patchOrder(state, action.orderId, (o) =>
        withLog(
          { ...o, flags: { ...o.flags, isVehicleIncident: true }, vehicleIncident: { kind: action.kind, note, reportedAt: Date.now() } },
          audit('STAFF', 'Báo sự cố xe (Mã 1.1.2)', `${kindLabel}: ${note}`),
        ),
      );
      return toast(next, 'danger', 'Đã gửi báo động sự cố xe tới Trung tâm điều phối.');
    }

    case 'TRANSFER_VEHICLE': {
      const o = findOrder(state, action.orderId);
      if (!o) return state;
      const existing = state.resources.vehicles.find((v) => v.plateNumber === action.plateNumber);
      const replacement: Vehicle = existing ?? {
        id: uid('veh'),
        plateNumber: action.plateNumber,
        capacityTons: 2,
        status: 'BUSY',
        currentShiftTime: orderShift(o),
        busyOrderId: shortId(o.id),
      };
      const today = todayLocalISO();
      if (!action.repairUntil || action.repairUntil < today) return toast(state, 'warning', 'Ghi ngày dự kiến sửa xong xe hỏng (từ hôm nay trở đi).');
      const replacementOff = existing ? downtimeOn(state.resources.downtimes, 'VEHICLE', existing.id, today) : null;
      if (existing && replacementOff) return toast(state, 'danger', `Xe ${existing.plateNumber} đang ${downtimeLabel(replacementOff).toLowerCase()}.`);
      let vehicles = releaseVehicle(state.resources.vehicles, o.vehicleId ? [o.vehicleId] : []);
      // Xe hỏng nghỉ sửa từ hôm nay tới ngày Điều phối ước tính; lịch nghỉ cũ chồng lên khoảng này được thay thế.
      const downtimes = o.vehicleId
        ? [
            ...state.resources.downtimes.filter((d) => !(d.target === 'VEHICLE' && d.resourceId === o.vehicleId && d.from <= action.repairUntil && d.to >= today)),
            { id: uid('dt'), target: 'VEHICLE' as const, resourceId: o.vehicleId, reason: 'REPAIR' as const, from: today, to: action.repairUntil, note: o.vehicleIncident ? `${VEHICLE_INCIDENT_KINDS[o.vehicleIncident.kind]}: ${o.vehicleIncident.note} — đơn ${o.id}` : `Sự cố giữa đường — đơn ${o.id}`, createdAt: Date.now() },
          ]
        : state.resources.downtimes;
      vehicles = existing
        ? vehicles.map((v) => (v.id === existing.id ? { ...v, status: 'BUSY', busyOrderId: shortId(o.id), currentShiftTime: orderShift(o) } : v))
        : [...vehicles, replacement];
      const driver = state.resources.staff.find((s) => s.name === action.driverName);
      // Tài xế mới phải lái được xe thay thế (đúng hạng bằng, còn hạn vào ngày chuyển).
      const driveError = driver ? driveBlockReason(driver, replacement.capacityTons, o.scheduledDate) : 'Không tìm thấy tài xế.';
      if (driveError) return toast(state, 'danger', `Không thể tăng bo: ${driver?.name ?? action.driverName} — ${driveError}.`);
      const oldPlate = state.resources.vehicles.find((v) => v.id === o.vehicleId)?.plateNumber ?? '—';
      const next = patchOrder({ ...state, resources: { ...state.resources, vehicles, downtimes } }, action.orderId, (ord) => {
        // Tài xế mới thay đúng vị trí tài xế của xe hỏng; nếu người này đang bốc xếp trong đội thì chuyển sang lái.
        const others = ord.crew.filter((c) => c.vehicleId !== o.vehicleId && c.staffId !== driver!.id);
        const crew = [...others, { staffId: driver!.id, role: 'DRIVER' as const, vehicleId: replacement.id }];
        return withLog(
          {
            ...ord,
            vehicleId: replacement.id,
            vehicleIds: ord.vehicleIds.map((id) => (id === o.vehicleId ? replacement.id : id)),
            crew,
            crewIds: crewIdsOf(crew),
            // Trưởng nhóm là tài xế bị thay thì quyền trưởng nhóm chuyển cho tài xế mới.
            leaderId: crew.some((c) => c.staffId === ord.leaderId) ? ord.leaderId : driver!.id,
            flags: { ...ord.flags, isVehicleIncident: false },
            vehicleIncident: null,
          },
          audit('COORDINATOR', 'Chuyển giao phương tiện (Tăng bo)', `${oldPlate} → ${action.plateNumber}, tài xế mới: ${action.driverName}.`),
        );
      });
      return toast(next, 'success', `Tăng bo thành công: xe ${action.plateNumber} — tài xế ${action.driverName}.`);
    }

    case 'UPDATE_QUOTE_ITEMS': {
      const o = findOrder(state, action.orderId);
      if (!o) return state;
      // Chỉ cho sửa giá khi báo giá đang chờ khách xác nhận — sau khi cọc/vận chuyển/kết thúc thì giá đã chốt.
      if (o.status !== 'SURVEYED_PENDING_CONFIRM') return toast(state, 'warning', 'Chỉ chỉnh sửa báo giá khi đơn đang chờ khách xác nhận.');
      const note = action.note.trim();
      if (!note) return toast(state, 'warning', 'Ghi lý do chỉnh sửa báo giá (khách sẽ thấy diễn giải trên từng dòng).');
      // Đơn giá theo bảng giá: chỉ khoản ngoài bảng giá được sửa đơn giá — thương lượng giá dùng giảm giá toàn đơn.
      const priceLocked = action.patches.find((p) => {
        const it = o.quoteItems.find((x) => x.id === p.id);
        return it && !it.offCatalog && p.unitPrice !== it.unitPrice;
      });
      if (priceLocked) return toast(state, 'warning', 'Đơn giá theo bảng giá không sửa được — dùng giảm giá toàn đơn để thương lượng.');
      const remaining = o.quoteItems.filter((it) => !action.removedIds.includes(it.id));
      if (!remaining.some((it) => it.category === 'VEHICLE') && o.quoteItems.some((it) => it.category === 'VEHICLE')) {
        return toast(state, 'warning', 'Báo giá phải còn ít nhất 1 dòng xe.');
      }
      if (action.added.some((i) => i.offCatalog && (!i.name.trim() || i.unitPrice <= 0 || !i.note?.trim()))) {
        return toast(state, 'warning', 'Khoản ngoài bảng giá cần tên, đơn giá và lý do.');
      }
      const entries: AuditLogEntry[] = [];
      o.quoteItems
        .filter((it) => action.removedIds.includes(it.id))
        .forEach((it) => entries.push(audit('COORDINATOR', 'Chỉnh sửa báo giá', `Bỏ "${it.name}" (${it.quantity} × ${formatVND(it.unitPrice)}) — ${note}.`)));
      action.added.forEach((it) =>
        entries.push(
          audit(
            'COORDINATOR',
            'Chỉnh sửa báo giá',
            `Thêm ${it.offCatalog ? 'khoản ngoài bảng giá' : 'dịch vụ'} "${it.name}" ${it.quantity} × ${formatVND(it.unitPrice)}${it.offCatalog ? ` (${it.note})` : ''} — ${note}.`,
          ),
        ),
      );
      const items = remaining.map((it) => {
        const p = action.patches.find((x) => x.id === it.id);
        if (!p) return it;
        const changes: string[] = [];
        if (p.quantity !== it.quantity) changes.push(`SL ${it.quantity} → ${p.quantity}`);
        if (p.unitPrice !== it.unitPrice) changes.push(`đơn giá ${formatVND(it.unitPrice)} → ${formatVND(p.unitPrice)}`);
        if (!changes.length) return it;
        entries.push(audit('COORDINATOR', 'Chỉnh sửa báo giá', `${it.name}: ${changes.join(', ')} — ${note}.`));
        // Cách tính tự động không còn đúng sau khi sửa tay — thay bằng diễn giải chỉnh sửa (khoản ngoài bảng giá giữ lý do gốc).
        return { ...it, quantity: p.quantity, unitPrice: p.unitPrice, note: it.offCatalog ? it.note : `Điều phối chỉnh ${changes.join(', ')} — ${note}` };
      }).concat(action.added);
      const discountPercent = Math.min(100, Math.max(0, action.discountPercent));
      if (discountPercent !== o.discountPercent) entries.push(audit('COORDINATOR', 'Cập nhật giảm giá toàn đơn', `Giảm giá ${o.discountPercent}% → ${discountPercent}% trên toàn đơn — ${note}.`));
      if (!entries.length) return toast(state, 'info', 'Không có thay đổi nào để lưu.');
      const next = patchOrder(state, action.orderId, (ord) => ({ ...ord, quoteItems: items, discountPercent, auditLog: [...ord.auditLog, ...entries] }));
      return toast(next, 'success', `Đã lưu ${entries.length} thay đổi vào nhật ký báo giá.`);
    }

    case 'ASSIGN_RESOURCES': {
      const o = findOrder(state, action.orderId);
      if (!o) return state;
      const chosen = action.vehicleIds.map((id) => state.resources.vehicles.find((x) => x.id === id));
      if (chosen.length === 0 || chosen.some((v) => !v)) return toast(state, 'danger', 'Chưa chọn phương tiện hợp lệ.');
      const picked = chosen as Vehicle[];
      const unavailable = picked.find((v) => v.status !== 'AVAILABLE' && !o.vehicleIds.includes(v.id));
      if (unavailable) return toast(state, 'danger', `Hệ thống chặn thao tác gán: xe ${unavailable.plateNumber} không khả dụng.`);
      if (o.status !== 'QUOTED_PENDING_SCHEDULE' && o.status !== 'ASSIGNED') return toast(state, 'warning', 'Chỉ gán nguồn lực cho đơn đã cọc và chờ điều phối.');
      const req = getAssignmentRequirement(o);
      const mismatch = validateAssignment(req, picked.map((v) => v.capacityTons));
      // Xe / người có lịch nghỉ (bảo dưỡng, nghỉ phép…) đúng ngày chuyển thì không gán được.
      const offOf = (target: 'VEHICLE' | 'STAFF', id: string) => downtimeOn(state.resources.downtimes, target, id, o.scheduledDate);
      const offVehicle = picked.find((v) => offOf('VEHICLE', v.id));
      if (offVehicle) return toast(state, 'danger', `Xe ${offVehicle.plateNumber} không chạy ngày chuyển: ${downtimeLabel(offOf('VEHICLE', offVehicle.id)!).toLowerCase()}.`);
      const offStaff = state.resources.staff.find((m) => crewIdsOf(action.crew).includes(m.id) && offOf('STAFF', m.id));
      if (offStaff) return toast(state, 'danger', `${offStaff.name} nghỉ vào ngày chuyển: ${downtimeLabel(offOf('STAFF', offStaff.id)!).toLowerCase()}.`);
      if (mismatch) return toast(state, 'danger', `Không khớp báo giá: ${mismatch}`);
      const crewError = validateCrew(req, picked, action.crew, state.resources.staff, o.scheduledDate);
      if (crewError) return toast(state, 'danger', `Đội chưa hợp lệ: ${crewError}`);
      // Tài xế mặc định là trưởng nhóm (nhiều xe: tài xế đầu tiên — xe lớn nhất).
      const leaderId = leaderOf(action.crew);
      let vehicles = releaseVehicle(state.resources.vehicles, o.vehicleIds);
      vehicles = vehicles.map((x) => (action.vehicleIds.includes(x.id) ? { ...x, status: 'BUSY', busyOrderId: shortId(o.id), currentShiftTime: orderShift(o) } : x));
      const crewText = describeCrew(action.crew, leaderId, state.resources.staff, picked);
      const plates = picked.map((v) => v.plateNumber).join(', ');
      // Phiếu chưa xuất kho thì lập lại theo số xe mới; đã xuất thì giữ nguyên (kho đã trừ tồn).
      const replan = o.materials.status === 'NONE' || o.materials.status === 'DRAFT';
      const materials: MaterialSlip = replan
        ? { ...emptyMaterialSlip(), status: action.materialPlan.length ? 'DRAFT' : 'NONE', lines: action.materialPlan }
        : o.materials;
      const next = patchOrder({ ...state, resources: { ...state.resources, vehicles } }, action.orderId, (ord) =>
        withLog(
          {
            ...ord,
            vehicleId: picked[0]?.id ?? null,
            vehicleIds: action.vehicleIds,
            crew: action.crew,
            crewIds: crewIdsOf(action.crew),
            leaderId,
            status: 'ASSIGNED',
            materials,
          },
          audit(
            'COORDINATOR',
            'Gán nguồn lực',
            `${picked.length} xe (${plates}) · ${crewText}.${replan && action.materialPlan.length ? ` Lập phiếu vật tư nháp: ${describeLines(action.materialPlan, (l) => l.planned)}.` : ''}`,
          ),
        ),
      );
      return toast(
        next,
        'success',
        `Đã gán ${picked.length} xe (${plates}) cho ${o.id}. ${replan && action.materialPlan.length ? 'Phiếu vật tư đã chuyển cho kho.' : 'Lệnh công tác đã gửi tới đội xe.'}`,
      );
    }

    case 'RESCHEDULE': {
      const o = findOrder(state, action.orderId);
      if (!o) return state;
      if (o.flags.hoursUntilDeparture < RESCHEDULE_LOCK_HOURS) return toast(state, 'danger', 'Dưới 12h trước giờ khởi hành — vui lòng gọi Hotline.');
      const next = patchOrder(state, action.orderId, (ord) =>
        withLog(
          { ...ord, scheduledDate: action.newDate, timeSlot: action.newSlot },
          audit('CUSTOMER', 'Tự dời lịch (Mã 1.1.7)', `${ord.scheduledDate} ${ord.timeSlot} → ${action.newDate} ${action.newSlot}.`),
        ),
      );
      return toast(next, 'success', `Đã dời lịch sang ${action.newDate}, ${action.newSlot}.`);
    }

    case 'CANCEL_ORDER': {
      const o = findOrder(state, action.orderId);
      if (!o) return state;
      const lateCancel = o.flags.hoursUntilDeparture < RESCHEDULE_LOCK_HOURS;
      if (action.mode === 'REFUND_FULL' && lateCancel) return toast(state, 'danger', 'Không đủ điều kiện hoàn cọc 100% (< 12h).');
      let next = { ...state, resources: { ...state.resources, vehicles: releaseVehicle(state.resources.vehicles, o.vehicleIds) } };
      const reservedUntil = new Date(Date.now() + 90 * 24 * 60 * 60 * 1000).toLocaleDateString('vi-VN');
      const detail: Record<typeof action.mode, string> = {
        REFUND_FULL: `Hủy trước ${o.flags.hoursUntilDeparture}h — tạo phiếu chi hoàn cọc 100%.`,
        FORFEIT: `Hủy sát giờ (${o.flags.hoursUntilDeparture}h) — cọc ${formatVND(o.depositPaid)} ghi nhận phí phạt.`,
        RESERVE_3_MONTHS: `Bảo lưu cọc ${formatVND(o.depositPaid)} tới ${reservedUntil}.`,
      };
      const materialsOut = hasMaterialsOutside(o.materials);
      next = patchOrder(next, action.orderId, (ord) =>
        withLog(
          {
            ...ord,
            status: 'CANCELLED_EXPIRED',
            depositReservedUntil: action.mode === 'RESERVE_3_MONTHS' ? reservedUntil : null,
            // Phiếu nháp chưa xuất kho thì hủy theo đơn; đã xuất thì giữ để kho thu hồi.
            materials: ord.materials.status === 'DRAFT' ? emptyMaterialSlip() : ord.materials,
          },
          audit(
            'CUSTOMER',
            'Hủy đơn hàng (Mã 1.1.6)',
            `${detail[action.mode]} Lý do: ${action.reason.trim() || 'Không nêu'}.${materialsOut ? ' Vật tư đã xuất kho — chờ kho thu hồi.' : ''}`,
          ),
        ),
      );
      if (o.depositPaid > 0 && action.mode === 'REFUND_FULL') {
        next = {
          ...next,
          refunds: [
            { id: `PC-${String(next.refunds.length + 11).padStart(4, '0')}`, orderId: o.id, customerName: o.customerName, amount: o.depositPaid, hoursBeforeDeparture: o.flags.hoursUntilDeparture, status: 'PENDING', createdAt: Date.now() },
            ...next.refunds,
          ],
        };
      }
      if (o.depositPaid > 0 && action.mode === 'FORFEIT') {
        next = {
          ...next,
          ledger: [
            { id: uid('GL'), orderId: o.id, amount: o.depositPaid, category: 'Thu nhập khác / Phí phạt hủy đơn', note: `Khách hủy trước ${o.flags.hoursUntilDeparture}h — giữ cọc theo chính sách.`, createdAt: Date.now() },
            ...next.ledger,
          ],
        };
      }
      const msg: Record<typeof action.mode, string> = {
        REFUND_FULL: 'Đã hủy đơn. Phiếu yêu cầu chi hoàn cọc 100% đã gửi tới Admin Tài chính.',
        FORFEIT: 'Đã hủy đơn. Khoản cọc được ghi nhận vào Thu nhập khác.',
        RESERVE_3_MONTHS: `Đã hủy đơn và bảo lưu cọc tới ${reservedUntil}.`,
      };
      return toast(next, action.mode === 'FORFEIT' ? 'warning' : 'success', msg[action.mode]);
    }

    case 'RUN_AUTO_CANCEL_CRON': {
      const now = Date.now();
      const expired = state.orders.filter((o) => o.status === 'SURVEYED_PENDING_CONFIRM' && o.autoCancelDeadline !== null && o.autoCancelDeadline <= now);
      if (!expired.length) return toast(state, 'info', 'Cronjob 24h đã chạy: không có đơn nào quá hạn.');
      const ids = new Set(expired.map((o) => o.id));
      const next: AppState = {
        ...state,
        orders: state.orders.map((o) =>
          ids.has(o.id) ? withLog({ ...o, status: 'CANCELLED_EXPIRED' }, audit('SYSTEM', 'Tự động hủy (Mã 1.1.3)', 'Quá 24h không chốt báo giá.')) : o,
        ),
      };
      return toast(next, 'warning', `Cronjob 24h: đã tự động hủy ${expired.length} đơn quá hạn (${expired.map((o) => o.id).join(', ')}).`);
    }

    case 'PAY_BALANCE': {
      const o = findOrder(state, action.orderId);
      if (!o || o.status !== 'PENDING_PAYMENT_OR_CLAIM') return toast(state, 'warning', 'Chỉ thanh toán sau khi đã lập biên bản bàn giao.');
      const remaining = computeTotals(o).remaining;
      const next = patchOrder(state, action.orderId, (ord) =>
        withLog({ ...ord, balancePaid: ord.balancePaid + remaining }, audit('CUSTOMER', 'Thanh toán QR', `Thanh toán ${formatVND(remaining)} — công nợ = 0đ.`)),
      );
      // Với khách, đơn đã xong ngay khi trả đủ; hồ sơ nội bộ tự đóng nếu vật tư cũng đã quyết toán.
      const closed = autoCloseIfReady(next, action.orderId);
      return toast(closed.state, 'success', `Thanh toán thành công ${formatVND(remaining)}. Đơn đã hoàn tất — cảm ơn bạn!`);
    }

    case 'CLOSE_ORDER': {
      const o = findOrder(state, action.orderId);
      if (!o) return state;
      if (!canCloseOrder(selectSettlement(o))) return toast(state, 'danger', 'Chưa thể đóng hồ sơ: cần công nợ = 0đ và đã quyết toán phiếu vật tư.');
      const revenue = computeTotals(o).grandTotal;
      const next = patchOrder(state, action.orderId, (ord) =>
        withLog({ ...ord, status: 'COMPLETED', finalRevenue: revenue }, audit('ADMIN', 'Đóng hồ sơ (Hoàn tất)', `Doanh thu cuối cùng ${formatVND(revenue)} — đã khóa bất biến.`)),
      );
      return toast(next, 'success', `${action.orderId} đã hoàn tất. Doanh thu ${formatVND(revenue)} được khóa.`);
    }

    case 'CREATE_CLAIM': {
      const o = findOrder(state, action.orderId);
      if (!o || !canFileClaim(o)) return toast(state, 'warning', 'Chỉ gửi khiếu nại sau khi đã bàn giao và thanh toán đủ.');
      const claim: ClaimTicket = {
        id: `CLM-${String(state.claims.length + 1).padStart(2, '0')}`,
        orderId: o.id,
        customerName: o.customerName,
        title: action.title,
        description: action.description,
        status: 'OPEN',
        requestedAmount: action.requestedAmount,
        proposedAmount: null,
        approvedAmount: null,
        handlingNote: '',
        createdAt: Date.now(),
        // Gói chỉ thuê xe: khách tự đóng gói → đội xe không có ảnh / video đóng gói để đối chiếu.
        packingEvidence: isTruckOnly(o)
          ? []
          : [
              { id: uid('ev'), kind: 'PHOTO', caption: 'Đồ đạc bọc màng PE trước khi lên xe', takenAt: 'Lúc đóng gói', tone: 'neutral' },
              { id: uid('ev'), kind: 'VIDEO', caption: 'Video kiểm tra tình trạng trước vận chuyển', takenAt: 'Lúc đóng gói', tone: 'neutral' },
            ],
        customerEvidence: [{ id: uid('ev'), kind: 'PHOTO', caption: action.title, takenAt: 'Vừa gửi', tone: 'danger' }],
      };
      return toast({ ...state, claims: [claim, ...state.claims] }, 'success', `Đã tạo phiếu ${claim.id}. Doanh thu gốc của ${o.id} không bị thay đổi.`);
    }

    case 'CLAIM_INVESTIGATE': {
      const c = state.claims.find((x) => x.id === action.claimId);
      if (!c || c.status !== 'OPEN') return state;
      return patchClaim(state, c.id, { status: 'INVESTIGATING' }, `${c.id}: Điều phối bắt đầu đối chiếu.`, 'info');
    }

    case 'CLAIM_PROPOSE_PAYOUT': {
      const c = state.claims.find((x) => x.id === action.claimId);
      if (!c || (c.status !== 'OPEN' && c.status !== 'INVESTIGATING')) return state;
      if (action.amount <= 0) return toast(state, 'warning', 'Mức đề xuất phải lớn hơn 0đ — không bồi thường thì chọn Từ chối.');
      if (!action.note.trim()) return toast(state, 'warning', 'Ghi căn cứ đề xuất để Admin duyệt.');
      return patchClaim(
        state,
        c.id,
        { status: 'PENDING_APPROVAL', proposedAmount: action.amount, handlingNote: action.note.trim() },
        `${c.id}: đã gửi Admin duyệt chi ${formatVND(action.amount)}.`,
      );
    }

    case 'CLAIM_REJECT': {
      const c = state.claims.find((x) => x.id === action.claimId);
      if (!c || (c.status !== 'OPEN' && c.status !== 'INVESTIGATING')) return state;
      if (!action.note.trim()) return toast(state, 'warning', 'Ghi lý do từ chối — khách sẽ thấy lý do này.');
      return patchClaim(state, c.id, { status: 'REJECTED', handlingNote: action.note.trim() }, `${c.id}: đã từ chối khiếu nại.`, 'info');
    }

    case 'CLAIM_APPROVE_PAYOUT': {
      const c = state.claims.find((x) => x.id === action.claimId);
      if (!c || c.status !== 'PENDING_APPROVAL') return state;
      if (action.amount <= 0) return toast(state, 'warning', 'Số tiền duyệt phải lớn hơn 0đ.');
      const next = patchClaim(state, c.id, { status: 'APPROVED', approvedAmount: action.amount });
      return toast(
        {
          ...next,
          ledger: [
            { id: uid('GL'), orderId: c.orderId, amount: action.amount, category: 'Chi phí bồi thường khiếu nại', note: `${c.id} — ${c.title}`, createdAt: Date.now() },
            ...next.ledger,
          ],
        },
        'success',
        `Duyệt chi ${formatVND(action.amount)} cho ${c.id} — ghi nhận chi phí riêng, doanh thu gốc giữ nguyên.`,
      );
    }

    case 'CLAIM_SEND_BACK': {
      const c = state.claims.find((x) => x.id === action.claimId);
      if (!c || c.status !== 'PENDING_APPROVAL') return state;
      if (!action.note.trim()) return toast(state, 'warning', 'Ghi lý do trả lại để Điều phối bổ sung.');
      return patchClaim(state, c.id, { status: 'INVESTIGATING', handlingNote: `Admin trả lại: ${action.note.trim()}` }, `${c.id}: đã trả lại Điều phối.`, 'info');
    }

    case 'APPROVE_REFUND': {
      const next = { ...state, refunds: state.refunds.map((r) => (r.id === action.refundId ? { ...r, status: 'APPROVED' as const } : r)) };
      return toast(next, 'success', `Đã duyệt phiếu chi ${action.refundId}.`);
    }

    case 'PUSH_TOAST':
      return toast(state, action.tone, action.message);

    case 'DISMISS_TOAST':
      return { ...state, toasts: state.toasts.filter((t) => t.id !== action.id) };

    case 'ADD_VEHICLE': {
      const plate = action.plateNumber.trim().toUpperCase();
      if (!PLATE_REGEX.test(plate)) return toast(state, 'danger', 'Biển số sai định dạng — ví dụ: 51C-456.78.');
      if (state.resources.vehicles.some((v) => v.plateNumber === plate)) return toast(state, 'danger', `Biển số ${plate} đã tồn tại.`);
      const vehicle: Vehicle = { id: uid('veh'), plateNumber: plate, capacityTons: action.capacityTons, status: 'AVAILABLE', currentShiftTime: null, busyOrderId: null };
      return toast({ ...state, resources: { ...state.resources, vehicles: [...state.resources.vehicles, vehicle] } }, 'success', `Đã thêm xe ${plate}.`);
    }

    case 'UPDATE_VEHICLE': {
      const v = state.resources.vehicles.find((x) => x.id === action.id);
      if (!v) return state;
      if (v.status === 'BUSY') return toast(state, 'danger', 'Xe đang được gán cho đơn hàng — không thể chỉnh sửa.');
      const plate = action.plateNumber.trim().toUpperCase();
      if (!PLATE_REGEX.test(plate)) return toast(state, 'danger', 'Biển số sai định dạng — ví dụ: 51C-456.78.');
      if (state.resources.vehicles.some((x) => x.id !== v.id && x.plateNumber === plate)) return toast(state, 'danger', `Biển số ${plate} đã tồn tại.`);
      const vehicles = state.resources.vehicles.map((x) => (x.id === v.id ? { ...x, plateNumber: plate, capacityTons: action.capacityTons } : x));
      return toast({ ...state, resources: { ...state.resources, vehicles } }, 'success', `Đã cập nhật xe ${plate}.`);
    }

    case 'ADD_DOWNTIME': {
      const { target, resourceId, from, to } = action;
      const today = todayLocalISO();
      const name =
        target === 'VEHICLE'
          ? state.resources.vehicles.find((v) => v.id === resourceId)?.plateNumber
          : state.resources.staff.find((m) => m.id === resourceId)?.name;
      if (!name) return state;
      if (!from || !to) return toast(state, 'warning', 'Chọn đủ ngày bắt đầu và ngày kết thúc.');
      if (to < from) return toast(state, 'warning', 'Ngày kết thúc phải từ ngày bắt đầu trở đi.');
      if (to < today) return toast(state, 'warning', 'Khoảng nghỉ đã qua — chỉ ghi lịch từ hôm nay trở đi.');
      const overlap = state.resources.downtimes.find((d) => d.target === target && d.resourceId === resourceId && d.from <= to && d.to >= from);
      if (overlap) return toast(state, 'warning', `${name} đã có lịch ${downtimeLabel(overlap).toLowerCase()} trùng khoảng này.`);
      // Đã nhận việc trong khoảng đó thì không cho nghỉ — Điều phối đổi người / xe cho đơn trước.
      const inRange = (d?: string | null) => !!d && from <= d && d <= to;
      const running = ['ASSIGNED', 'IN_TRANSIT', 'PAUSED_WAITING_CUSTOMER'];
      const clashes = state.orders.filter((o) => {
        if (!running.includes(o.status) && o.status !== 'NEW_PENDING_SURVEY') return false;
        if (target === 'VEHICLE') return running.includes(o.status) && o.vehicleIds.includes(resourceId) && inRange(o.scheduledDate);
        const moving = running.includes(o.status) && o.crewIds.includes(resourceId) && inRange(o.scheduledDate);
        const surveying = o.status === 'NEW_PENDING_SURVEY' && !o.surveyReport && o.surveyAssignment?.surveyorId === resourceId && inRange(o.bookingDetails?.surveyDate);
        return moving || surveying;
      });
      if (clashes.length) return toast(state, 'danger', `${name} đã có lịch ở đơn ${clashes.map((o) => o.id).join(', ')} trong khoảng này — đổi người / xe cho đơn trước.`);
      const downtime = { id: uid('dt'), target, resourceId, reason: action.reason, from, to, note: action.note.trim(), createdAt: Date.now() };
      return toast(
        { ...state, resources: { ...state.resources, downtimes: [...state.resources.downtimes, downtime] } },
        'success',
        `${name}: ${DOWNTIME_REASON_META[action.reason].label.toLowerCase()} ${downtimeRangeLabel(downtime)}.`,
      );
    }

    case 'END_DOWNTIME': {
      const d = state.resources.downtimes.find((x) => x.id === action.id);
      if (!d) return state;
      const today = todayLocalISO();
      // Chưa bắt đầu → hủy hẳn; đang diễn ra → kết thúc trước hôm nay để từ hôm nay nhận việc lại.
      const upcoming = d.from >= today;
      const downtimes = upcoming
        ? state.resources.downtimes.filter((x) => x.id !== d.id)
        : state.resources.downtimes.map((x) => (x.id === d.id ? { ...x, to: addDaysISO(today, -1) } : x));
      return toast({ ...state, resources: { ...state.resources, downtimes } }, 'success', upcoming ? 'Đã hủy lịch nghỉ.' : 'Đã kết thúc sớm — từ hôm nay nhận việc lại.');
    }

    case 'REMOVE_VEHICLE': {
      const v = state.resources.vehicles.find((x) => x.id === action.id);
      if (!v) return state;
      const inUse = state.orders.some((o) => o.vehicleIds.includes(v.id) && o.status !== 'COMPLETED' && o.status !== 'CANCELLED_EXPIRED');
      if (v.status === 'BUSY' || inUse) return toast(state, 'danger', 'Xe đang gắn với đơn hàng chưa hoàn tất — không thể xóa.');
      return toast({ ...state, resources: { ...state.resources, vehicles: state.resources.vehicles.filter((x) => x.id !== v.id) } }, 'success', `Đã xóa xe ${v.plateNumber}.`);
    }

    case 'UPSERT_STAFF': {
      const s = { ...action.staff, name: action.staff.name.trim(), code: action.staff.code.trim().toUpperCase() };
      const err = validateStaffProfile(s, state.resources.staff);
      if (err) return toast(state, 'danger', err);
      // Không để hồ sơ mâu thuẫn với lệnh đang chạy: đổi đội ở Điều phối trước.
      const blocker = staffChangeBlocker(s, state.orders);
      if (blocker) return toast(state, 'danger', blocker);
      const exists = state.resources.staff.some((x) => x.id === s.id);
      const staff = exists ? state.resources.staff.map((x) => (x.id === s.id ? s : x)) : [...state.resources.staff, s];
      return toast({ ...state, resources: { ...state.resources, staff } }, 'success', `${exists ? 'Đã cập nhật' : 'Đã tạo'} hồ sơ ${s.code} · ${s.name}.`);
    }

    case 'RESET_DEMO':
      return { ...createInitialState(), toasts: [{ id: uid('t'), tone: 'info', message: 'Đã khôi phục dữ liệu demo.' }] };
  }
};
