import { formatDateVN, formatTime } from '@/services/format';
import type { Order } from '@/types';

/** Cách đơn đi qua bước 2 trên góc nhìn khách hàng. */
export type SurveyStageKind = 'TRUCK_CONFIRM' | 'VIDEO_CALL' | 'ON_SITE' | 'PHOTOS';

/** Danh mục ảnh / video gợi ý khách chụp — tên tệp được lưu kèm tiền tố "[nhãn] ". */
export const SURVEY_MEDIA_CATEGORIES = [
  { key: 'ROOMS', label: 'Phòng khách / phòng ngủ', hint: 'Mỗi phòng 1–2 ảnh toàn cảnh' },
  { key: 'BIG', label: 'Đồ lớn', hint: 'Tủ, giường, tủ lạnh, sofa, máy giặt…' },
  { key: 'ACCESS', label: 'Lối đi, hành lang, thang', hint: 'Thang máy / thang bộ, cửa ra vào' },
  { key: 'PARKING', label: 'Cổng / hẻm chỗ xe đậu', hint: 'Giúp chọn xe & tính trung chuyển' },
] as const;

export type SurveyMediaCategory = (typeof SURVEY_MEDIA_CATEGORIES)[number];

export const mediaPrefix = (c: SurveyMediaCategory): string => `[${c.label}] `;

export const mediaOfCategory = (order: Order, c: SurveyMediaCategory): string[] =>
  order.customerPhotos.filter((n) => n.startsWith(mediaPrefix(c))).map((n) => n.slice(mediaPrefix(c).length));

const WEEKDAYS = ['Chủ nhật', 'Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy'];

export const weekdayOf = (iso: string): string => {
  const [y, m, d] = iso.split('-').map(Number);
  if (!y || !m || !d) return '';
  return WEEKDAYS[new Date(y, m - 1, d).getDay()] ?? '';
};

/** dd/mm — dùng cho nhãn phụ ngắn. */
const shortDate = (iso: string): string => formatDateVN(iso).slice(0, 5);

/**
 * Hình thức ở bước 2. Ưu tiên: gói chỉ thuê xe → không khảo sát; Điều phối đã cử khảo sát trực tiếp → tận nơi;
 * còn lại theo lựa chọn của khách trong wizard (mặc định: gửi ảnh / video).
 */
export const surveyStageOf = (order: Order): SurveyStageKind => {
  const d = order.bookingDetails;
  if (d?.serviceMode === 'TRUCK_ONLY') return 'TRUCK_CONFIRM';
  if (order.onSiteSurveyRequest) return 'ON_SITE';
  if (d?.surveyPreference === 'VIDEO_CALL') return 'VIDEO_CALL';
  if (d?.surveyPreference === 'ON_SITE') return 'ON_SITE';
  return 'PHOTOS';
};

/** Đơn cũ (không qua wizard) đính kèm ảnh ngay khi đặt nên coi như đã gửi. */
export const isMediaSubmitted = (order: Order): boolean => Boolean(order.surveyMediaSubmittedAt) || (!order.bookingDetails && order.customerPhotos.length > 0);

/** Ảnh / video không thuộc danh mục nào (gửi kèm lúc đặt đơn). */
export const uncategorizedMedia = (order: Order): string[] =>
  order.customerPhotos.filter((n) => !SURVEY_MEDIA_CATEGORIES.some((c) => n.startsWith(mediaPrefix(c))));

export const hasAppointment = (order: Order): boolean => Boolean(order.bookingDetails?.surveyDate && order.bookingDetails.surveyContactSlot);

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
