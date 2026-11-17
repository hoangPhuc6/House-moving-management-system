import type { Tone } from '@/components/ui';
import { formatDateVN } from '@/services/format';
import type { Order } from '@/types';

/** Cách đơn đi qua bước khảo sát — dùng chung cho Khách hàng, Điều phối và Nhân viên. */
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
export const shortDate = (iso: string): string => formatDateVN(iso).slice(0, 5);

/**
 * Hình thức ở bước khảo sát: gói chỉ thuê xe → không khảo sát; còn lại theo lựa chọn của khách
 * trong wizard (mặc định: gửi ảnh / video).
 */
export const surveyStageOf = (order: Order): SurveyStageKind => {
  const d = order.bookingDetails;
  if (d?.serviceMode === 'TRUCK_ONLY') return 'TRUCK_CONFIRM';
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

/** "Thứ Ba 18/11 · 09:00 – 11:00" — rỗng khi khách chưa hẹn lịch. */
export const appointmentLabel = (order: Order): string => {
  const d = order.bookingDetails;
  if (!d || !hasAppointment(order)) return '';
  return `${weekdayOf(d.surveyDate)} ${shortDate(d.surveyDate)} · ${d.surveyContactSlot}`;
};

export const SURVEY_STAGE_META: Record<SurveyStageKind, { label: string; short: string }> = {
  TRUCK_CONFIRM: { label: 'Chỉ thuê xe — gọi xác nhận xe & bốc vác', short: 'Thuê xe' },
  VIDEO_CALL: { label: 'Khảo sát qua video call', short: 'Video call' },
  ON_SITE: { label: 'Khảo sát trực tiếp tận nơi', short: 'Tận nơi' },
  PHOTOS: { label: 'Khảo sát qua ảnh / video khách gửi', short: 'Qua ảnh' },
};

/**
 * Đơn đang chờ khảo sát nằm ở bước nào của flow:
 * ASSIGN (Điều phối phân người khảo sát) → SURVEYING (người khảo sát làm việc / chờ khách gửi ảnh)
 * → QUOTE (có kết quả khảo sát, Điều phối lập báo giá). Gói chỉ thuê xe bỏ qua khảo sát → QUOTE ngay.
 */
export type SurveyPhase = 'ASSIGN' | 'SURVEYING' | 'QUOTE';

export const surveyPhaseOf = (order: Order): SurveyPhase => {
  if (surveyStageOf(order) === 'TRUCK_CONFIRM' || order.surveyReport) return 'QUOTE';
  return order.surveyAssignment ? 'SURVEYING' : 'ASSIGN';
};

/** Nhãn ngắn cho thẻ đơn ở hàng đợi Điều phối: hình thức + việc đang chờ. */
export const coordinatorSurveyBadge = (order: Order): { label: string; tone: Tone } => {
  const kind = surveyStageOf(order);
  if (kind === 'TRUCK_CONFIRM') return { label: 'Thuê xe · gọi xác nhận & báo giá', tone: 'brand' };
  const short = SURVEY_STAGE_META[kind].short;
  const when = order.bookingDetails && hasAppointment(order) ? ` ${shortDate(order.bookingDetails.surveyDate)}` : '';
  switch (surveyPhaseOf(order)) {
    case 'ASSIGN':
      return { label: `${short}${when} · cần phân người`, tone: 'warning' };
    case 'SURVEYING':
      return kind === 'PHOTOS' && !isMediaSubmitted(order)
        ? { label: 'Qua ảnh · chờ khách gửi', tone: 'neutral' }
        : { label: `${short}${when} · đang khảo sát`, tone: 'info' };
    case 'QUOTE':
      return { label: `${short} · có kết quả khảo sát`, tone: 'success' };
  }
};
