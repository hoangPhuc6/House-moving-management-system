import {
  hasAppointment,
  isMediaSubmitted,
  surveyPhaseOf,
  shortDate,
  surveyStageOf,
} from '@/features/order/surveyStage';
import { FLOOR_OPTIONS, PROPERTY_TYPES } from '@/features/customer/booking/constants';
import { formatTime } from '@/services/format';
import type { Order, SurveyMediaKey } from '@/types';

export * from '@/features/order/surveyStage';

/**
 * Nhóm ảnh nào không bắt buộc với đơn này, kèm lý do để hiện cho khách; null = nên chụp.
 * Dựa trên nhà hiện tại (nơi đi) vì khách chụp ảnh ở đó.
 */
export const optionalMediaReason = (order: Order, key: SurveyMediaKey): string | null => {
  if (key === 'OTHER') return 'Không bắt buộc';
  const from = order.bookingDetails?.from;
  if (!from) return null;
  if (key === 'ACCESS' && from.floor === FLOOR_OPTIONS[0]) return 'Không bắt buộc với nhà tầng trệt';
  if (key === 'PARKING' && from.propertyType === PROPERTY_TYPES[0]?.label) return 'Không bắt buộc vì xe đỗ sát cửa';
  return null;
};

/** Lời nhắn ở phần đầu trang khi đơn đang ở bước 2 (chỉ áp dụng đơn tạo qua wizard). */
export const surveyHeroCopy = (order: Order): { title: string; desc: string } => {
  // Ngày giờ hẹn khảo sát chỉ hiện ở thẻ khảo sát bên dưới — tiêu đề không lặp lại.
  const kind = surveyStageOf(order);
  if (kind === 'TRUCK_CONFIRM') {
    return { title: 'Đang xác nhận xe', desc: 'Điều phối viên sẽ gọi xác nhận loại xe, nhân viên bốc vác và gửi báo giá cho bạn.' };
  }
  const phase = surveyPhaseOf(order);
  if (phase === 'QUOTE') return { title: 'Đang lập báo giá', desc: 'Đã khảo sát xong. Điều phối viên đang lập báo giá chi tiết cho bạn.' };
  if (kind === 'PHOTOS') {
    return isMediaSubmitted(order)
      ? { title: 'Chuyên viên đang kiểm tra ảnh', desc: 'Chuyên viên khảo sát xem ảnh / video bạn gửi, sau đó MoveFlow gửi báo giá.' }
      : { title: 'Chờ bạn gửi ảnh / video', desc: 'Tải ảnh / video đồ đạc và lối đi để chuyên viên khảo sát.' };
  }
  if (phase === 'ASSIGN') {
    return { title: 'Đang sắp xếp chuyên viên khảo sát', desc: 'MoveFlow đang phân chuyên viên khảo sát theo lịch bạn hẹn.' };
  }
  return kind === 'VIDEO_CALL'
    ? { title: 'Chờ khảo sát qua video call', desc: 'Chuyên viên sẽ gọi Zalo / Messenger đúng khung giờ bạn hẹn để xem đồ đạc.' }
    : { title: 'Chờ khảo sát tận nơi', desc: 'Chuyên viên sẽ đến đo đạc, kiểm tra lối đi và tư vấn phương án chuyển dọn.' };
};

/** Tiêu đề & nhãn phụ cho từng bước trên thanh tiến độ của khách (khớp CUSTOMER_PROCESS_STEPS). */
export const customerStepMeta = (order: Order): { title?: string; sub?: string | null }[] => {
  const d = order.bookingDetails;
  const kind = surveyStageOf(order);
  const surveySub = (() => {
    if (kind === 'TRUCK_CONFIRM') return 'Báo giá tự động';
    if (kind === 'PHOTOS') return isMediaSubmitted(order) ? 'Đã gửi ảnh / video' : 'Chờ bạn gửi ảnh';
    const label = kind === 'VIDEO_CALL' ? 'Video call' : 'Tận nơi';
    return d && hasAppointment(order) ? `${label} ${shortDate(d.surveyDate)}` : label;
  })();
  return [
    { sub: `Đã gửi ${formatTime(order.createdAt)}` },
    { title: kind === 'TRUCK_CONFIRM' ? 'Báo giá xe' : undefined, sub: surveySub },
    { sub: order.status === 'SURVEYED_PENDING_CONFIRM' ? 'Đang chờ bạn duyệt' : null },
    { sub: `Dự kiến ${shortDate(order.scheduledDate)}` },
    { sub: null },
  ];
};
