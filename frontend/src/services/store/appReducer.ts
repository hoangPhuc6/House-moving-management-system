import { FIELD_STAGE_LOG_ACTION, FIELD_STAGE_LOG_LABEL, FIELD_STAGE_ORDER, isTruckOnly, nextFieldStage, STATUS_META } from '@/constants/order';
import { AUTO_CANCEL_MS, DEPOSIT_AMOUNT, RESCHEDULE_LOCK_HOURS, totalVehicles, vehicleLinesLabel } from '@/constants/pricing';
import { hoursLabel, orderShift, PLATE_REGEX, VEHICLE_INCIDENT_KINDS } from '@/constants/resources';
import { getAssignmentRequirement, isDriverRole, validateAssignment } from '@/services/dispatch';
import { formatVND, uid } from '@/services/format';
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
import { appointmentLabel, isMediaSubmitted, SURVEY_STAGE_META, type SurveyStageKind, surveyStageOf } from '@/features/order/surveyStage';
import { cleanSurveyItems } from '@/features/order/surveyItems';
import { addDaysISO, DOWNTIME_REASON_META, downtimeLabel, downtimeOn, downtimeRangeLabel, todayLocalISO } from '@/services/downtime';
import type {
  AppAction,
  AppState,
  AuditLogEntry,
  ClaimTicket,
  FieldStage,
  Order,
  OrderStatus,
  SurveyMethod,
  ToastTone,
  Vehicle,
} from '@/types';

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

/**
 * Step 7 Hard-Lock: đủ 2 điều kiện (công nợ = 0đ, đã trả dụng cụ về kho) thì tự đóng hồ sơ và khóa doanh thu —
 * không cần ai bấm. Gọi sau mỗi thao tác có thể làm thỏa điều kiện cuối cùng.
 */
const closeIfSettled = (state: AppState, orderId: string): AppState => {
  const o = findOrder(state, orderId);
  if (!o || o.status !== 'PENDING_PAYMENT_OR_CLAIM' || !canCloseOrder(selectSettlement(o))) return state;
  const revenue = computeTotals(o).grandTotal;
  const next = patchOrder(state, orderId, (ord) =>
    withLog({ ...ord, status: 'COMPLETED', finalRevenue: revenue }, audit('SYSTEM', 'Tự động đóng hồ sơ', `Đã thu đủ & trả dụng cụ về kho. Doanh thu ${formatVND(revenue)} được khóa.`)),
  );
  return toast(next, 'success', `${orderId} đã hoàn tất — doanh thu ${formatVND(revenue)} được khóa.`);
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
          updated.crewIds = ['st-1', 'st-2', 'st-3', 'st-4'];
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
        if (action.status === 'COMPLETED') {
          updated.isWarehouseReturned = true;
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
        customerPhotos: action.input.photoNames,
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
        pendingAdjustment: null,
        depositRequired: DEPOSIT_AMOUNT,
        depositPaid: 0,
        balancePaid: 0,
        // Gói chỉ thuê xe không xuất dụng cụ / vật tư → không có gì phải trả kho.
        isWarehouseReturned: action.input.details?.serviceMode === 'TRUCK_ONLY',
        finalRevenue: null,
        flags: { hasFieldDiscrepancyLock: false, isVehicleIncident: false, waitingMinutes: 0, hoursUntilDeparture: 72 },
        fieldStage: 'NOT_STARTED',
        vehicleId: null,
        vehicleIds: [],
        crewIds: [],
        equipment: [],
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
              proposal: p,
            },
          },
          audit(
            'STAFF',
            'Gửi kết quả khảo sát',
            `${surveyor?.name ?? 'Người khảo sát'} đề xuất ${vehicleLinesLabel(p.vehicles)}, ${p.workers} người, ${p.distanceKm} km.`,
          ),
        ),
      );
      return toast(next, 'success', `Đã gửi kết quả khảo sát ${o.id} cho Điều phối lập báo giá.`);
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
      const kind = surveyStageOf(o);
      if (kind !== 'TRUCK_CONFIRM' && !o.surveyReport) return toast(state, 'warning', 'Chưa có kết quả khảo sát — chờ người khảo sát gửi về rồi mới lập báo giá.');
      if (!(action.estimatedHours > 0)) return toast(state, 'warning', 'Ghi tổng thời gian dự kiến của chuyến để biết xe & người bận bao lâu.');
      const method = SURVEY_METHOD_OF[kind];
      const methodLabel: Record<SurveyMethod, string> = {
        ON_SITE: 'Khảo sát trực tiếp',
        VIDEO_CALL: 'Khảo sát qua video call',
        TRUCK_CONFIRM: 'Xác nhận xe qua điện thoại (chỉ thuê xe)',
        PHOTO: `Khảo sát qua ${o.customerPhotos.length} ảnh / video khách gửi`,
      };
      const next = patchOrder(state, action.orderId, (ord) =>
        withLog(
          { ...ord, quoteItems: action.items, estimatedHours: action.estimatedHours, surveyMethod: method, status: 'SURVEYED_PENDING_CONFIRM', autoCancelDeadline: Date.now() + AUTO_CANCEL_MS },
          audit('COORDINATOR', 'Gửi báo giá', `${methodLabel[method]} — ${action.items.length} hạng mục, tổng ${formatVND(action.items.reduce((s, i) => s + i.unitPrice * i.quantity, 0))}, dự kiến ${hoursLabel(action.estimatedHours)}.`),
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
      if (nextFieldStage(o)?.stage !== action.stage) return state;
      if ((action.stage === 'WORKING' || action.stage === 'MOVING') && o.flags.hasFieldDiscrepancyLock) {
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
        next = toast(next, 'success', 'Đã lập biên bản bàn giao. Đơn chuyển sang chờ quyết toán.');
      }
      return next;
    }

    case 'TOGGLE_EQUIPMENT':
      return patchOrder(state, action.orderId, (o) => ({
        ...o,
        equipment: o.equipment.map((e) => (e.id === action.itemId ? { ...e, checked: !e.checked } : e)),
      }));

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
      const oldPlate = state.resources.vehicles.find((v) => v.id === o.vehicleId)?.plateNumber ?? '—';
      const next = patchOrder({ ...state, resources: { ...state.resources, vehicles, downtimes } }, action.orderId, (ord) =>
        withLog(
          {
            ...ord,
            vehicleId: replacement.id,
            vehicleIds: ord.vehicleIds.map((id) => (id === o.vehicleId ? replacement.id : id)),
            crewIds: driver && !ord.crewIds.includes(driver.id) ? [...ord.crewIds, driver.id] : ord.crewIds,
            flags: { ...ord.flags, isVehicleIncident: false },
            vehicleIncident: null,
          },
          audit('COORDINATOR', 'Chuyển giao phương tiện (Tăng bo)', `${oldPlate} → ${action.plateNumber}, tài xế mới: ${action.driverName}.`),
        ),
      );
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
      const crewMembers = state.resources.staff.filter((m) => action.crewIds.includes(m.id));
      const offOf = (target: 'VEHICLE' | 'STAFF', id: string) => downtimeOn(state.resources.downtimes, target, id, o.scheduledDate);
      const offVehicle = picked.find((v) => offOf('VEHICLE', v.id));
      if (offVehicle) return toast(state, 'danger', `Xe ${offVehicle.plateNumber} không chạy ngày chuyển: ${downtimeLabel(offOf('VEHICLE', offVehicle.id)!).toLowerCase()}.`);
      const offStaff = crewMembers.find((m) => offOf('STAFF', m.id));
      if (offStaff) return toast(state, 'danger', `${offStaff.name} nghỉ vào ngày chuyển: ${downtimeLabel(offOf('STAFF', offStaff.id)!).toLowerCase()}.`);
      const mismatch = validateAssignment(
        getAssignmentRequirement(o),
        picked.map((v) => v.capacityTons),
        crewMembers.filter((m) => isDriverRole(m.role)).length,
        crewMembers.filter((m) => !isDriverRole(m.role)).length,
      );
      if (mismatch) return toast(state, 'danger', `Không khớp báo giá: ${mismatch}`);
      let vehicles = releaseVehicle(state.resources.vehicles, o.vehicleIds);
      vehicles = vehicles.map((x) => (action.vehicleIds.includes(x.id) ? { ...x, status: 'BUSY', busyOrderId: shortId(o.id), currentShiftTime: orderShift(o) } : x));
      const crewNames = state.resources.staff.filter((s) => action.crewIds.includes(s.id)).map((s) => s.name);
      const plates = picked.map((v) => v.plateNumber).join(', ');
      const next = patchOrder({ ...state, resources: { ...state.resources, vehicles } }, action.orderId, (ord) =>
        withLog(
          { ...ord, vehicleId: picked[0]?.id ?? null, vehicleIds: action.vehicleIds, crewIds: action.crewIds, status: 'ASSIGNED' },
          audit('COORDINATOR', 'Gán nguồn lực', `${picked.length} xe (${plates}) + ${crewNames.length} nhân sự: ${crewNames.join(', ')}.`),
        ),
      );
      return toast(next, 'success', `Đã gán ${picked.length} xe (${plates}) cho ${o.id}. Lệnh công tác đã gửi tới đội xe.`);
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
      next = patchOrder(next, action.orderId, (ord) =>
        withLog(
          { ...ord, status: 'CANCELLED_EXPIRED', depositReservedUntil: action.mode === 'RESERVE_3_MONTHS' ? reservedUntil : null },
          audit('CUSTOMER', 'Hủy đơn hàng (Mã 1.1.6)', `${detail[action.mode]} Lý do: ${action.reason.trim() || 'Không nêu'}.`),
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
      return closeIfSettled(toast(next, 'success', `Thanh toán thành công ${formatVND(remaining)}. Công nợ khách hàng = 0đ.`), action.orderId);
    }

    case 'CONFIRM_WAREHOUSE_RETURN': {
      const o = findOrder(state, action.orderId);
      if (!o || o.status !== 'PENDING_PAYMENT_OR_CLAIM' || o.isWarehouseReturned) return state;
      const next = patchOrder(state, action.orderId, (ord) =>
        withLog({ ...ord, isWarehouseReturned: true }, audit('STAFF', 'Trả dụng cụ về kho', 'Đội xe xác nhận đã trả đủ dụng cụ & nhập lại vật tư dư.')),
      );
      return closeIfSettled(toast(next, 'success', `${action.orderId}: đã trả dụng cụ về kho.`), action.orderId);
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

    case 'RESET_DEMO':
      return { ...createInitialState(), toasts: [{ id: uid('t'), tone: 'info', message: 'Đã khôi phục dữ liệu demo.' }] };
  }
};
