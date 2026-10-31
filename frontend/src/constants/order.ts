import type { FieldStage, HouseType, OrderStatus, SurveyMethod, ToastTone } from '@/types';

export const STATUS_META: Record<OrderStatus, { label: string; tone: ToastTone | 'neutral' }> = {
  NEW_PENDING_SURVEY: { label: 'Mới - Chờ khảo sát', tone: 'info' },
  SURVEYED_PENDING_CONFIRM: { label: 'Đã khảo sát - Chờ chốt', tone: 'warning' },
  QUOTED_PENDING_SCHEDULE: { label: 'Đã cọc - Chờ điều phối', tone: 'info' },
  ASSIGNED: { label: 'Đã gán lệnh', tone: 'info' },
  IN_TRANSIT: { label: 'Đang vận chuyển', tone: 'info' },
  PAUSED_WAITING_CUSTOMER: { label: 'Tạm dừng - Chờ khách', tone: 'warning' },
  PENDING_PAYMENT_OR_CLAIM: { label: 'Chờ quyết toán', tone: 'warning' },
  COMPLETED: { label: 'Hoàn tất', tone: 'success' },
  CANCELLED_EXPIRED: { label: 'Đã hủy / Hết hạn', tone: 'danger' },
};

export const STATUS_ORDER: OrderStatus[] = [
  'NEW_PENDING_SURVEY',
  'SURVEYED_PENDING_CONFIRM',
  'QUOTED_PENDING_SCHEDULE',
  'ASSIGNED',
  'IN_TRANSIT',
  'PAUSED_WAITING_CUSTOMER',
  'PENDING_PAYMENT_OR_CLAIM',
  'COMPLETED',
  'CANCELLED_EXPIRED',
];

export const PROCESS_STEPS: { title: string; short: string; detail: string; statuses: OrderStatus[] }[] = [
  { title: 'Đặt lịch', short: 'Booking', detail: 'Khách gửi yêu cầu, chọn khung giờ còn trống công suất và tải ảnh đồ đạc.', statuses: ['NEW_PENDING_SURVEY'] },
  { title: 'Khảo sát', short: 'Survey', detail: 'Điều phối viên lập báo giá dựa trên ảnh khách gửi. Khi cần, Điều phối cử nhân viên vận chuyển đến khảo sát trực tiếp.', statuses: ['SURVEYED_PENDING_CONFIRM'] },
  { title: 'Báo giá & Cọc', short: 'Quote', detail: 'Báo giá minh bạch từng dòng, có nhật ký chỉnh sửa. Khách đồng ý và đặt cọc qua QR.', statuses: ['QUOTED_PENDING_SCHEDULE'] },
  { title: 'Điều phối', short: 'Dispatch', detail: 'Điều phối viên gán xe & nhân sự, hệ thống chặn trùng lịch tự động.', statuses: ['ASSIGNED'] },
  { title: 'Vận chuyển', short: 'Transit', detail: 'Theo dõi tiến độ thời gian thực, xử lý phát sinh, sự cố xe, đồng hồ chờ.', statuses: ['IN_TRANSIT', 'PAUSED_WAITING_CUSTOMER'] },
  { title: 'Nghiệm thu', short: 'Settle', detail: 'Lập biên bản bàn giao, khách thanh toán phần còn lại qua QR.', statuses: ['PENDING_PAYMENT_OR_CLAIM'] },
  { title: 'Bàn giao & Đóng', short: 'Close', detail: 'Admin xác nhận thu hồi dụng cụ, công nợ = 0đ rồi mới đóng hồ sơ.', statuses: ['COMPLETED'] },
];

export const HOUSE_TYPE_META: Record<HouseType, { label: string; hint: string }> = {
  GROUND_FLOOR: { label: 'Nhà mặt tiền', hint: 'Xe tải đỗ sát cửa' },
  APARTMENT: { label: 'Chung cư', hint: 'Có thang máy / thang bộ' },
  DEEP_ALLEY: { label: 'Hẻm sâu >100m', hint: 'Cần trung chuyển xe đẩy' },
};

export const FIELD_STAGES: { stage: FieldStage; label: string }[] = [
  { stage: 'MATERIALS_PICKED', label: 'Nhận vật tư tại kho' },
  { stage: 'WORKING', label: 'Bắt đầu làm (Bắt đầu vận chuyển)' },
  { stage: 'MOVING', label: 'Đang di chuyển' },
  { stage: 'ARRIVED', label: 'Đã tới nơi' },
  { stage: 'HANDED_OVER', label: 'Lập biên bản bàn giao' },
];

export const FIELD_STAGE_ORDER: FieldStage[] = ['NOT_STARTED', 'MATERIALS_PICKED', 'WORKING', 'MOVING', 'ARRIVED', 'HANDED_OVER'];

export const FIELD_STAGE_LABEL: Record<FieldStage, string> = {
  NOT_STARTED: 'Chưa bắt đầu',
  MATERIALS_PICKED: 'Đã nhận vật tư',
  WORKING: 'Đang bốc xếp',
  MOVING: 'Đang di chuyển',
  ARRIVED: 'Đã tới nơi',
  HANDED_OVER: 'Đã bàn giao',
};

export const CUSTOMER_STATUS_COPY: Record<OrderStatus, { title: string; desc: string }> = {
  NEW_PENDING_SURVEY: { title: 'Đang khảo sát qua ảnh', desc: 'Điều phối viên đang lập báo giá dựa trên ảnh bạn cung cấp.' },
  SURVEYED_PENDING_CONFIRM: { title: 'Báo giá đã sẵn sàng', desc: 'Xem chi tiết và đặt cọc để giữ lịch xe.' },
  QUOTED_PENDING_SCHEDULE: { title: 'Đã nhận cọc', desc: 'Chúng tôi đang sắp xếp xe và đội ngũ cho bạn.' },
  ASSIGNED: { title: 'Đội xe đã sẵn sàng', desc: 'Xe và nhân sự đã được phân công cho đơn của bạn.' },
  IN_TRANSIT: { title: 'Đang vận chuyển', desc: 'Đội ngũ đang thực hiện chuyển nhà cho bạn.' },
  PAUSED_WAITING_CUSTOMER: { title: 'Đội xe đang chờ bạn', desc: '30 phút đầu miễn phí, sau đó tính phí chờ theo giờ.' },
  PENDING_PAYMENT_OR_CLAIM: { title: 'Chờ thanh toán', desc: 'Kiểm tra hóa đơn và thanh toán phần còn lại.' },
  COMPLETED: { title: 'Hoàn tất', desc: 'Cảm ơn bạn đã tin chọn MoveFlow!' },
  CANCELLED_EXPIRED: { title: 'Đơn đã hủy', desc: 'Đơn hàng không còn hiệu lực.' },
};

/** Lời nhắn cho khách khi Điều phối đã chuyển đơn sang khảo sát trực tiếp. */
export const ONSITE_SURVEY_CUSTOMER_COPY = { title: 'Sắp xếp khảo sát trực tiếp', desc: 'Nhân viên vận chuyển sẽ liên hệ hẹn giờ đến khảo sát tại nhà bạn.' };

export const SURVEY_METHOD_META: Record<SurveyMethod, { label: string; short: string }> = {
  PHOTO: { label: 'Khảo sát qua ảnh (Điều phối thực hiện)', short: 'Qua ảnh' },
  ON_SITE: { label: 'Khảo sát trực tiếp (nhân viên vận chuyển)', short: 'Trực tiếp' },
};
