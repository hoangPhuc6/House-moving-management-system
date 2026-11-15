import { createInitialCatalog } from '@/constants/catalog';
import { FIELD_STAGE_ORDER, STATUS_META } from '@/constants/order';
import { AUTO_CANCEL_MS, DEPOSIT_AMOUNT, RESCHEDULE_LOCK_HOURS } from '@/constants/pricing';
import { PLATE_REGEX } from '@/constants/resources';
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
  fieldAdjustmentItem,
  findOrder,
  nextOrderId,
  patchOrder,
  releaseVehicle,
  shortId,
  statusIndex,
  toast,
  withLog,
} from '@/services/store/helpers';
import { createInitialState } from '@/services/store/initialState';
import { appointmentLabel, isMediaSubmitted, type SurveyStageKind, surveyStageOf } from '@/features/order/surveyStage';
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
  SurveyMethod,
  Vehicle,
} from '@/types';

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

export const appReducer = (state: AppState, action: AppAction): AppState => {
  switch (action.type) {
    case 'SET_ACTIVE_ORDER':
      return { ...state, activeOrderId: action.orderId };

    case 'SET_FLAGS': {
      return patchOrder(state, action.orderId, (o) => {
        const flags = { ...o.flags, ...action.patch };
        let pendingAdjustment = o.pendingAdjustment;
        if (action.patch.hasFieldDiscrepancyLock === true && !pendingAdjustment) pendingAdjustment = fieldAdjustmentItem();
        if (action.patch.hasFieldDiscrepancyLock === false) pendingAdjustment = null;
        return { ...o, flags, pendingAdjustment };
      });
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
              order.vehicleIds.includes(v.id) ? { ...v, status: 'BUSY', busyOrderId: shortId(order.id), currentShiftTime: order.timeSlot } : v,
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
        customerPhotos: action.input.photoNames,
        items: action.input.items,
        requestedServices: action.input.requestedServices,
        referenceQuote: action.input.referenceQuote,
        onSiteSurveyRequest: null,
        surveyMethod: null,
        quoteItems: [],
        discountPercent: 0,
        pendingAdjustment: null,
        depositRequired: DEPOSIT_AMOUNT,
        depositPaid: 0,
        balancePaid: 0,
        materials: emptyMaterialSlip(),
        finalRevenue: null,
        flags: { hasFieldDiscrepancyLock: false, isVehicleIncident: false, waitingMinutes: 0, hoursUntilDeparture: 72 },
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

    case 'REQUEST_ONSITE_SURVEY': {
      const o = findOrder(state, action.orderId);
      if (!o || o.status !== 'NEW_PENDING_SURVEY') return toast(state, 'warning', 'Chỉ yêu cầu khảo sát trực tiếp cho đơn đang chờ khảo sát.');
      if (o.onSiteSurveyRequest) return toast(state, 'info', `${o.id} đã có yêu cầu khảo sát trực tiếp.`);
      if (o.bookingDetails?.serviceMode === 'TRUCK_ONLY') return toast(state, 'warning', 'Gói chỉ thuê xe không cần khảo sát — gọi khách xác nhận xe rồi gửi báo giá.');
      const appointment = appointmentLabel(o);
      const surveyor = state.resources.staff.find((s) => s.id === action.surveyorId);
      if (!surveyor) return toast(state, 'warning', 'Cần chọn nhân viên khảo sát trước khi gửi yêu cầu.');
      if (!isActiveStaff(surveyor)) return toast(state, 'warning', `${surveyor.name} đang ${WORK_STATUS_META[surveyor.status].label.toLowerCase()}, hãy chọn nhân viên khác.`);
      const note = action.note.trim();
      const next = patchOrder(state, action.orderId, (ord) =>
        withLog(
          { ...ord, onSiteSurveyRequest: { requestedAt: Date.now(), note, surveyorId: surveyor.id } },
          audit('COORDINATOR', 'Yêu cầu khảo sát trực tiếp', `Cử ${surveyor.name} (${surveyor.code}) khảo sát${appointment ? ` theo lịch khách hẹn ${appointment}` : ''}. ${note ? `Lý do: ${note}.` : 'Chuyển đơn sang khảo sát trực tiếp tại nhà khách.'}`),
        ),
      );
      return toast(next, 'success', `Đã cử ${surveyor.name} khảo sát trực tiếp ${o.id}.`);
    }

    case 'CANCEL_ONSITE_SURVEY': {
      const o = findOrder(state, action.orderId);
      if (!o?.onSiteSurveyRequest || o.status !== 'NEW_PENDING_SURVEY') return state;
      const next = patchOrder(state, action.orderId, (ord) =>
        withLog({ ...ord, onSiteSurveyRequest: null }, audit('COORDINATOR', 'Hủy yêu cầu khảo sát trực tiếp', 'Đơn quay lại khảo sát qua ảnh khách cung cấp.')),
      );
      return toast(next, 'info', `${o.id}: đã hủy yêu cầu khảo sát trực tiếp.`);
    }

    case 'CUSTOMER_ADD_SURVEY_MEDIA': {
      const o = findOrder(state, action.orderId);
      if (!o || o.status !== 'NEW_PENDING_SURVEY' || action.names.length === 0) return state;
      const next = patchOrder(state, action.orderId, (ord) =>
        withLog({ ...ord, customerPhotos: [...ord.customerPhotos, ...action.names] }, audit('CUSTOMER', 'Tải ảnh / video khảo sát', `Khách tải thêm ${action.names.length} tệp.`)),
      );
      return toast(next, 'success', `Đã tải lên ${action.names.length} tệp.`);
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
      // Ảnh / video call / thuê xe: Điều phối lập báo giá. Trực tiếp: chỉ nhân viên vận chuyển được cử đi khảo sát mới gửi.
      const kind = surveyStageOf(o);
      if (kind === 'ON_SITE' && !o.onSiteSurveyRequest) return toast(state, 'warning', 'Khách chọn khảo sát tận nơi — hãy cử nhân viên đi khảo sát trước.');
      if (kind === 'PHOTOS' && !isMediaSubmitted(o)) return toast(state, 'warning', 'Khách chưa gửi ảnh / video khảo sát.');
      const method = SURVEY_METHOD_OF[kind];
      if (method !== 'ON_SITE' && action.by !== 'COORDINATOR') return toast(state, 'warning', 'Đơn này do Điều phối lập báo giá.');
      if (method === 'ON_SITE' && action.by !== 'STAFF') return toast(state, 'warning', 'Đơn này đang chờ nhân viên vận chuyển khảo sát trực tiếp.');
      const methodLabel: Record<SurveyMethod, string> = {
        ON_SITE: 'Khảo sát trực tiếp',
        VIDEO_CALL: 'Khảo sát qua video call',
        TRUCK_CONFIRM: 'Xác nhận xe qua điện thoại (chỉ thuê xe)',
        PHOTO: `Khảo sát qua ${o.customerPhotos.length} ảnh / video khách gửi`,
      };
      const next = patchOrder(state, action.orderId, (ord) =>
        withLog(
          { ...ord, quoteItems: action.items, surveyMethod: method, status: 'SURVEYED_PENDING_CONFIRM', autoCancelDeadline: Date.now() + AUTO_CANCEL_MS },
          audit(action.by, 'Tạo báo giá khảo sát', `${methodLabel[method]} —${action.items.length} hạng mục, tổng ${formatVND(action.items.reduce((s, i) => s + i.unitPrice * i.quantity, 0))}.`),
        ),
      );
      return toast(next, 'success', `Đã gửi báo giá khảo sát cho ${action.orderId}. Bộ đếm tự hủy 24h bắt đầu chạy.`);
    }

    case 'PAY_DEPOSIT': {
      const o = findOrder(state, action.orderId);
      if (!o || o.status !== 'SURVEYED_PENDING_CONFIRM') return toast(state, 'warning', 'Đơn hiện không ở trạng thái chờ chốt báo giá.');
      const next = patchOrder(state, action.orderId, (ord) =>
        withLog(
          { ...ord, depositPaid: ord.depositRequired, status: 'QUOTED_PENDING_SCHEDULE', autoCancelDeadline: null },
          audit('CUSTOMER', 'Đồng ý báo giá & đặt cọc', `Thanh toán cọc ${formatVND(ord.depositRequired)} qua VietQR.`),
        ),
      );
      return toast(next, 'success', `Đã nhận cọc ${formatVND(o.depositRequired)}. Đơn chuyển sang Điều phối.`);
    }

    case 'REPORT_FIELD_DISCREPANCY': {
      const next = patchOrder(state, action.orderId, (o) =>
        withLog(
          { ...o, flags: { ...o.flags, hasFieldDiscrepancyLock: true }, pendingAdjustment: o.pendingAdjustment ?? fieldAdjustmentItem() },
          audit('STAFF', 'Báo cáo sai lệch hiện trường (Mã 1.1.1)', 'Hẻm sâu 200m (khảo sát ghi 100m) + phát sinh tháo lắp tủ gỗ. Đề xuất +350.000đ.'),
        ),
      );
      return toast(next, 'warning', 'Đã khóa luồng vận chuyển. Chờ khách xác nhận báo giá điều chỉnh.');
    }

    case 'CONFIRM_FIELD_ADJUSTMENT': {
      const o = findOrder(state, action.orderId);
      if (!o?.pendingAdjustment) return state;
      const adj = o.pendingAdjustment;
      const next = patchOrder(state, action.orderId, (ord) =>
        withLog(
          { ...ord, quoteItems: [...ord.quoteItems, adj], pendingAdjustment: null, flags: { ...ord.flags, hasFieldDiscrepancyLock: false } },
          audit('CUSTOMER', 'Xác nhận báo giá điều chỉnh', `${adj.name}: +${formatVND(adj.unitPrice)}. Mở khóa luồng vận chuyển.`),
        ),
      );
      return toast(next, 'success', 'Khách đã xác nhận. Đội xe được phép bắt đầu vận chuyển.');
    }

    case 'ADVANCE_FIELD_STAGE': {
      const o = findOrder(state, action.orderId);
      if (!o) return state;
      if (o.status !== 'ASSIGNED' && o.status !== 'IN_TRANSIT') return toast(state, 'warning', 'Lệnh công tác chưa sẵn sàng hoặc đang tạm dừng.');
      const cur = FIELD_STAGE_ORDER.indexOf(o.fieldStage);
      const target = FIELD_STAGE_ORDER.indexOf(action.stage);
      if (target !== cur + 1) return state;
      // Bước nhận vật tư chỉ hoàn thành qua CONFIRM_MATERIALS_RECEIVED (kiểm từng dòng phiếu xuất).
      if (action.stage === 'MATERIALS_PICKED' && o.materials.lines.length > 0) {
        return toast(state, 'warning', o.materials.status === 'DRAFT' ? 'Kho chưa xuất phiếu vật tư cho lệnh này.' : 'Hãy kiểm và xác nhận số vật tư thực nhận trên phiếu.');
      }
      if ((action.stage === 'WORKING' || action.stage === 'MOVING') && o.flags.hasFieldDiscrepancyLock) {
        return toast(state, 'danger', 'Chưa thể bắt đầu: chờ khách xác nhận báo giá điều chỉnh.');
      }
      if (action.stage === 'MOVING' && o.flags.isVehicleIncident) {
        return toast(state, 'danger', 'Xe đang báo sự cố — chờ điều phối chuyển giao phương tiện.');
      }
      let status: OrderStatus = o.status;
      if (action.stage === 'WORKING') status = 'IN_TRANSIT';
      if (action.stage === 'HANDED_OVER') status = 'PENDING_PAYMENT_OR_CLAIM';
      const label: Record<FieldStage, string> = {
        NOT_STARTED: 'Chưa bắt đầu',
        MATERIALS_PICKED: 'Đã nhận vật tư tại kho',
        WORKING: 'Bắt đầu làm / vận chuyển',
        MOVING: 'Đang di chuyển',
        ARRIVED: 'Đã tới nơi',
        HANDED_OVER: 'Đã lập biên bản bàn giao',
      };
      let next = patchOrder(state, action.orderId, (ord) =>
        withLog({ ...ord, fieldStage: action.stage, status }, audit('STAFF', 'Cập nhật tiến độ hiện trường', label[action.stage])),
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
      const next = patchOrder(state, action.orderId, (o) =>
        withLog({ ...o, flags: { ...o.flags, isVehicleIncident: true } }, audit('STAFF', 'Báo sự cố xe hỏng (Mã 1.1.2)', 'Xe chết máy giữa đường, đồ đạc vẫn an toàn trên thùng xe.')),
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
        currentShiftTime: o.timeSlot,
        busyOrderId: shortId(o.id),
      };
      let vehicles = releaseVehicle(state.resources.vehicles, o.vehicleId ? [o.vehicleId] : [], 'MAINTENANCE');
      vehicles = existing
        ? vehicles.map((v) => (v.id === existing.id ? { ...v, status: 'BUSY', busyOrderId: shortId(o.id), currentShiftTime: o.timeSlot } : v))
        : [...vehicles, replacement];
      const driver = state.resources.staff.find((s) => s.name === action.driverName);
      // Tài xế mới phải lái được xe thay thế (đúng hạng bằng, còn hạn vào ngày chuyển).
      const driveError = driver ? driveBlockReason(driver, replacement.capacityTons, o.scheduledDate) : 'Không tìm thấy tài xế.';
      if (driveError) return toast(state, 'danger', `Không thể tăng bo: ${driver?.name ?? action.driverName} — ${driveError}.`);
      const oldPlate = state.resources.vehicles.find((v) => v.id === o.vehicleId)?.plateNumber ?? '—';
      const next = patchOrder({ ...state, resources: { ...state.resources, vehicles } }, action.orderId, (ord) => {
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
      const entries: AuditLogEntry[] = [];
      const items = o.quoteItems.map((it) => {
        const p = action.patches.find((x) => x.id === it.id);
        if (!p) return it;
        const changes: string[] = [];
        if (p.quantity !== it.quantity) changes.push(`SL ${it.quantity} → ${p.quantity}`);
        if (p.unitPrice !== it.unitPrice) changes.push(`đơn giá ${formatVND(it.unitPrice)} → ${formatVND(p.unitPrice)}`);
        if (!changes.length) return it;
        entries.push(audit('COORDINATOR', 'Chỉnh sửa báo giá', `${it.name}: ${changes.join(', ')}${action.note ? ` — ${action.note}` : ''}.`));
        // Cách tính tự động không còn đúng sau khi sửa tay — thay bằng diễn giải chỉnh sửa.
        return { ...it, quantity: p.quantity, unitPrice: p.unitPrice, note: `Điều phối chỉnh ${changes.join(', ')}${action.note ? ` — ${action.note}` : ''}` };
      });
      const discountPercent = Math.min(100, Math.max(0, action.discountPercent));
      if (discountPercent !== o.discountPercent) entries.push(audit('COORDINATOR', 'Cập nhật giảm giá toàn đơn', `Giảm giá ${o.discountPercent}% → ${discountPercent}% trên toàn đơn${action.note ? ` — ${action.note}` : ''}.`));
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
      if (mismatch) return toast(state, 'danger', `Không khớp báo giá: ${mismatch}`);
      const crewError = validateCrew(req, picked, action.crew, state.resources.staff, o.scheduledDate);
      if (crewError) return toast(state, 'danger', `Đội chưa hợp lệ: ${crewError}`);
      // Tài xế mặc định là trưởng nhóm (nhiều xe: tài xế đầu tiên — xe lớn nhất).
      const leaderId = leaderOf(action.crew);
      let vehicles = releaseVehicle(state.resources.vehicles, o.vehicleIds);
      vehicles = vehicles.map((x) => (action.vehicleIds.includes(x.id) ? { ...x, status: 'BUSY', busyOrderId: shortId(o.id), currentShiftTime: o.timeSlot } : x));
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
        approvedAmount: null,
        createdAt: Date.now(),
        packingEvidence: [
          { id: uid('ev'), kind: 'PHOTO', caption: 'Đồ đạc bọc màng PE trước khi lên xe', takenAt: 'Lúc đóng gói', tone: 'neutral' },
          { id: uid('ev'), kind: 'VIDEO', caption: 'Video kiểm tra tình trạng trước vận chuyển', takenAt: 'Lúc đóng gói', tone: 'neutral' },
        ],
        customerEvidence: [{ id: uid('ev'), kind: 'PHOTO', caption: action.title, takenAt: 'Vừa gửi', tone: 'danger' }],
      };
      return toast({ ...state, claims: [claim, ...state.claims] }, 'success', `Đã tạo phiếu ${claim.id}. Doanh thu gốc của ${o.id} không bị thay đổi.`);
    }

    case 'SET_CLAIM_STATUS': {
      const claim = state.claims.find((c) => c.id === action.claimId);
      if (!claim) return state;
      let next: AppState = {
        ...state,
        claims: state.claims.map((c) => (c.id === action.claimId ? { ...c, status: action.status, approvedAmount: action.approvedAmount } : c)),
      };
      if (action.status === 'APPROVED' && action.approvedAmount) {
        next = {
          ...next,
          ledger: [
            { id: uid('GL'), orderId: claim.orderId, amount: action.approvedAmount, category: 'Chi phí bồi thường khiếu nại', note: `${claim.id} — ${claim.title}`, createdAt: Date.now() },
            ...next.ledger,
          ],
        };
        next = toast(next, 'success', `Duyệt bồi thường ${formatVND(action.approvedAmount)} cho ${claim.id} — ghi nhận chi phí riêng, doanh thu gốc giữ nguyên.`);
      }
      return next;
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

    case 'SET_VEHICLE_STATUS': {
      const v = state.resources.vehicles.find((x) => x.id === action.id);
      if (!v) return state;
      if (v.status === 'BUSY') return toast(state, 'danger', 'Xe đang được gán cho đơn hàng — không thể đổi trạng thái.');
      const vehicles = state.resources.vehicles.map((x) => (x.id === v.id ? { ...x, status: action.status } : x));
      return toast({ ...state, resources: { ...state.resources, vehicles } }, 'success', action.status === 'MAINTENANCE' ? `Xe ${v.plateNumber} chuyển sang bảo dưỡng.` : `Xe ${v.plateNumber} sẵn sàng hoạt động.`);
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
