import {
  hasAppointment,
  isMediaSubmitted,
  shortDate,
  surveyStageOf,
  weekdayOf,
} from '@/features/order/surveyStage';
import { formatTime } from '@/services/format';
import type { Order } from '@/types';

export * from '@/features/order/surveyStage';

/** Lời nhắn ở phần đầu trang khi đơn đang ở bước 2 (chỉ áp dụng đơn tạo qua wizard). */
export const surveyHeroCopy = (order: Order): { title: string; desc: string } => {
  const d = order.bookingDetails;
  const when = d && hasAppointment(order) ? ` · ${weekdayOf(d.surveyDate)} ${shortDate(d.surveyDate)}` : '';
  switch (surveyStageOf(order)) {
    case 'TRUCK_CONFIRM':
      return { title: 'Đang xác nhận xe', desc: 'Điều phối viên sẽ gọi xác nhận loại xe, nhân viên bốc vác và gửi báo giá cho bạn.' };
    case 'VIDEO_CALL':
      return { title: `Chờ khảo sát qua video call${when}`, desc: 'Chuyên viên sẽ gọi Zalo / Messenger đúng khung giờ bạn hẹn để xem đồ đạc.' };
    case 'ON_SITE':
      return order.onSiteSurveyRequest && !when
        ? { title: 'Sắp xếp khảo sát trực tiếp', desc: 'Nhân viên vận chuyển sẽ liên hệ hẹn giờ đến khảo sát tại nhà bạn.' }
        : { title: `Chờ khảo sát tận nơi${when}`, desc: 'Chuyên viên sẽ đến đo đạc, kiểm tra lối đi và tư vấn phương án chuyển dọn.' };
    case 'PHOTOS':
      return isMediaSubmitted(order)
        ? { title: 'Chuyên viên đang lập báo giá', desc: 'Báo giá được lập dựa trên ảnh / video bạn đã gửi.' }
        : { title: 'Chờ bạn gửi ảnh / video', desc: 'Tải ảnh / video đồ đạc và lối đi để chuyên viên lập báo giá.' };
  }
};

/** Tiêu đề & nhãn phụ cho từng bước trên thanh tiến độ của khách (khớp CUSTOMER_PROCESS_STEPS). */
export const customerStepMeta = (order: Order): { title?: string; sub?: string | null }[] => {
  const d = order.bookingDetails;
  const kind = surveyStageOf(order);
  const surveySub = (() => {
    if (kind === 'TRUCK_CONFIRM') return 'Điều phối gọi xác nhận';
    if (kind === 'PHOTOS') return isMediaSubmitted(order) ? 'Đã gửi ảnh / video' : 'Chờ bạn gửi ảnh';
    const label = kind === 'VIDEO_CALL' ? 'Video call' : 'Tận nơi';
    return d && hasAppointment(order) ? `${label} ${shortDate(d.surveyDate)}` : label;
  })();
  return [
    { sub: `Đã gửi ${formatTime(order.createdAt)}` },
    { title: kind === 'TRUCK_CONFIRM' ? 'Xác nhận xe' : undefined, sub: surveySub },
    { sub: order.status === 'SURVEYED_PENDING_CONFIRM' ? 'Đang chờ bạn duyệt' : null },
    { sub: `Dự kiến ${shortDate(order.scheduledDate)}` },
    { sub: null },
  ];
};
