import type { Tone } from '@/components/ui';
import { formatDateVN } from '@/services/format';
import type { OnSiteSurveyRequest, Order, SitePhotoCategory } from '@/types';

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

/** Checklist ảnh hiện trường NV phải chụp khi khảo sát trực tiếp — mỗi nhóm tối thiểu 1 ảnh. */
export const SITE_PHOTO_CHECKLIST: { key: SitePhotoCategory; label: string; hint: string }[] = [
  { key: 'ROOMS', label: 'Toàn cảnh từng phòng', hint: 'Phòng khách, phòng ngủ, bếp — mỗi phòng 1 ảnh' },
  { key: 'BIG', label: 'Đồ lớn / dễ vỡ / giá trị cao', hint: 'Chụp rõ tình trạng trước khi chuyển' },
  { key: 'ACCESS', label: 'Lối đi, cầu thang, thang máy', hint: 'Độ rộng cửa, chiếu nghỉ, thang máy' },
  { key: 'PARKING', label: 'Cổng, hẻm, chỗ đỗ xe', hint: 'Căn cứ chọn loại xe & phí trung chuyển' },
];

/** Tổng số ảnh hiện trường tối thiểu để hoàn tất khảo sát. */
export const MIN_SITE_PHOTOS = 5;

/** Lý do chưa hoàn tất được khảo sát vì thiếu ảnh; null khi đã đủ. */
export const missingSitePhotos = (req: OnSiteSurveyRequest): string | null => {
  const missing = SITE_PHOTO_CHECKLIST.filter((c) => !req.sitePhotos.some((p) => p.category === c.key));
  if (missing.length > 0) return `Còn thiếu ảnh: ${missing.map((c) => c.label.toLowerCase()).join(', ')}.`;
  if (req.sitePhotos.length < MIN_SITE_PHOTOS) return `Cần tối thiểu ${MIN_SITE_PHOTOS} ảnh hiện trường (mới có ${req.sitePhotos.length}).`;
  return null;
};

/** Video call: tối thiểu 3 ảnh chụp màn hình, bắt buộc có lối đi và chỗ đỗ xe (nguồn phát sinh phổ biến nhất). */
export const MIN_VIDEO_PHOTOS = 3;
export const VIDEO_REQUIRED_CATEGORIES: SitePhotoCategory[] = ['ACCESS', 'PARKING'];

/** Lý do chưa đủ ảnh chụp màn hình video call; null khi đã đủ. */
export const missingVideoPhotos = (photos: { category: SitePhotoCategory }[]): string | null => {
  const missing = SITE_PHOTO_CHECKLIST.filter((c) => VIDEO_REQUIRED_CATEGORIES.includes(c.key) && !photos.some((p) => p.category === c.key));
  if (missing.length > 0) return `Còn thiếu ảnh: ${missing.map((c) => c.label.toLowerCase()).join(', ')}.`;
  if (photos.length < MIN_VIDEO_PHOTOS) return `Cần tối thiểu ${MIN_VIDEO_PHOTOS} ảnh chụp màn hình (mới có ${photos.length}).`;
  return null;
};

export type OnSiteProgress ='ASSIGNED' | 'IN_PROGRESS' | 'COMPLETED';

export const onSiteProgressOf = (req: OnSiteSurveyRequest): OnSiteProgress =>
  req.completedAt ? 'COMPLETED' : req.startedAt ? 'IN_PROGRESS' : 'ASSIGNED';

/** NV đã khảo sát xong, đang chờ Điều phối lập báo giá. */
export const awaitingOnSiteQuote = (order: Order): boolean =>
  order.status === 'NEW_PENDING_SURVEY' && Boolean(order.onSiteSurveyRequest?.completedAt);

export const mediaPrefix =(c: SurveyMediaCategory): string => `[${c.label}] `;

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
 * Hình thức ở bước khảo sát. Ưu tiên: gói chỉ thuê xe → không khảo sát; Điều phối đã cử khảo sát trực tiếp → tận nơi;
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

/** Nhãn ngắn cho thẻ đơn ở hàng đợi Điều phối: hình thức + việc cần làm tiếp theo. */
export const coordinatorSurveyBadge = (order: Order): { label: string; tone: Tone } => {
  const kind = surveyStageOf(order);
  const when = order.bookingDetails && hasAppointment(order) ? ` ${shortDate(order.bookingDetails.surveyDate)}` : '';
  switch (kind) {
    case 'TRUCK_CONFIRM':
      return { label: 'Thuê xe · cần gọi xác nhận', tone: 'brand' };
    case 'VIDEO_CALL':
      return { label: `Video call${when || ' · chưa hẹn'}`, tone: 'info' };
    case 'ON_SITE':
      if (!order.onSiteSurveyRequest) return { label: `Tận nơi${when} · cần cử NV`, tone: 'warning' };
      switch (onSiteProgressOf(order.onSiteSurveyRequest)) {
        case 'COMPLETED':
          return { label: 'Tận nơi · đã khảo sát, cần báo giá', tone: 'brand' };
        case 'IN_PROGRESS':
          return { label: 'Tận nơi · NV đang khảo sát', tone: 'info' };
        case 'ASSIGNED':
          return { label: `Tận nơi · đã cử NV${when}`, tone: 'neutral' };
      }
    case 'PHOTOS':
      return isMediaSubmitted(order)
        ? { label: `Qua ảnh · đã gửi ${order.customerPhotos.length}`, tone: 'success' }
        : { label: 'Qua ảnh · chờ khách gửi', tone: 'neutral' };
  }
};
