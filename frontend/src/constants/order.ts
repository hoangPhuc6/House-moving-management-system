import type { BookingDetails, FieldStage, HouseType, OrderStatus, SurveyMethod, ToastTone } from '@/types';

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
  { title: 'Đặt lịch', short: 'Booking', detail: 'Khách gửi yêu cầu, chọn ngày Khách gửi yêu cầu, chọn khung giờ còn trống công suất và tải ảnh đồ đạc. giờ bắt đầu vận chuyển và tải ảnh đồ đạc.', statuses: ['NEW_PENDING_SURVEY'] },
  { title: 'Khảo sát', short: 'Survey', detail: 'Điều phối viên lập báo giá dựa trên ảnh khách gửi. Khi cần, Điều phối cử nhân viên vận chuyển đến khảo sát trực tiếp.', statuses: ['SURVEYED_PENDING_CONFIRM'] },
  { title: 'Báo giá & Cọc', short: 'Quote', detail: 'Báo giá minh bạch từng dòng, có nhật ký chỉnh sửa. Khách đồng ý và đặt cọc qua QR.', statuses: ['QUOTED_PENDING_SCHEDULE'] },
  { title: 'Điều phối', short: 'Dispatch', detail: 'Điều phối viên gán xe & nhân sự, hệ thống chặn trùng lịch tự động.', statuses: ['ASSIGNED'] },
  { title: 'Vận chuyển', short: 'Transit', detail: 'Theo dõi tiến độ thời gian thực, xử lý phát sinh, sự cố xe, đồng hồ chờ.', statuses: ['IN_TRANSIT', 'PAUSED_WAITING_CUSTOMER'] },
  { title: 'Nghiệm thu', short: 'Settle', detail: 'Lập biên bản bàn giao, khách thanh toán phần còn lại qua QR.', statuses: ['PENDING_PAYMENT_OR_CLAIM'] },
  { title: 'Bàn giao & Đóng', short: 'Close', detail: 'Admin xác nhận thu hồi dụng cụ, công nợ = 0đ rồi mới đóng hồ sơ.', statuses: ['COMPLETED'] },
];

/**
 * Thanh tiến độ rút gọn 5 bước hiển thị cho khách hàng.
 * Bước đang diễn ra = bước chứa trạng thái hiện tại; "Đặt lịch" luôn hoàn tất khi đơn đã tạo.
 */
export const CUSTOMER_PROCESS_STEPS: { title: string; statuses: OrderStatus[] }[] = [
  { title: 'Đặt lịch', statuses: [] },
  { title: 'Khảo sát', statuses: ['NEW_PENDING_SURVEY'] },
  { title: 'Báo giá & Cọc', statuses: ['SURVEYED_PENDING_CONFIRM'] },
  { title: 'Vận chuyển', statuses: ['QUOTED_PENDING_SCHEDULE', 'ASSIGNED', 'IN_TRANSIT', 'PAUSED_WAITING_CUSTOMER'] },
  { title: 'Bàn giao', statuses: ['PENDING_PAYMENT_OR_CLAIM', 'COMPLETED'] },
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

type StageOrder = { fieldStage: FieldStage; bookingDetails?: BookingDetails | null };

export const isTruckOnly = (o: { bookingDetails?: BookingDetails | null }) => o.bookingDetails?.serviceMode === 'TRUCK_ONLY';

/** Phạm vi khiếu nại gói chỉ thuê xe — khách tự đóng gói nên chỉ nhận hư hỏng phát sinh do phía MoveFlow. */
export const TRUCK_ONLY_CLAIM_SCOPE =
  'Gói chỉ thuê xe: bạn tự đóng gói, nên MoveFlow chỉ nhận khiếu nại hư hỏng do va chạm / sự cố trong lúc xe chạy, hoặc do nhân viên bốc vác MoveFlow (nếu có thuê). Đồ vỡ do đóng gói không kỹ không thuộc phạm vi bồi thường.';

/** Các bước hiện trường của đơn — gói chỉ thuê xe không xuất vật tư nên bỏ bước "Nhận vật tư tại kho". */
export const fieldStagesOf = (o: StageOrder) => (isTruckOnly(o) ? FIELD_STAGES.filter((s) => s.stage !== 'MATERIALS_PICKED') : FIELD_STAGES);

const rank = (s: FieldStage) => FIELD_STAGE_ORDER.indexOf(s);

/** Bước đội xe bấm tiếp theo (null = đã bàn giao). */
export const nextFieldStage = (o: StageOrder) => fieldStagesOf(o).find((s) => rank(s.stage) > rank(o.fieldStage)) ?? null;

export const isFieldStageDone = (o: StageOrder, stage: FieldStage) => rank(stage) <= rank(o.fieldStage);

export const FIELD_STAGE_LABEL: Record<FieldStage, string> = {
  NOT_STARTED: 'Chưa bắt đầu',
  MATERIALS_PICKED: 'Đã nhận vật tư',
  WORKING: 'Đang bốc xếp',
  MOVING: 'Đang di chuyển',
  ARRIVED: 'Đã tới nơi',
  HANDED_OVER: 'Đã bàn giao',
};

/** Nội dung ghi nhật ký khi đội xe cập nhật từng bước — cũng dùng để đọc lại giờ cập nhật. */
export const FIELD_STAGE_LOG_ACTION = 'Cập nhật tiến độ hiện trường';
export const FIELD_STAGE_LOG_LABEL: Record<FieldStage, string> = {
  NOT_STARTED: 'Chưa bắt đầu',
  MATERIALS_PICKED: 'Đã nhận vật tư tại kho',
  WORKING: 'Bắt đầu làm / vận chuyển',
  MOVING: 'Đang di chuyển',
  ARRIVED: 'Đã tới nơi',
  HANDED_OVER: 'Đã lập biên bản bàn giao',
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


export const SURVEY_METHOD_META: Record<SurveyMethod, { label: string; short: string }> = {
  PHOTO: { label: 'Khảo sát qua ảnh / video khách gửi', short: 'Qua ảnh' },
  VIDEO_CALL: { label: 'Khảo sát qua video call', short: 'Video call' },
  TRUCK_CONFIRM: { label: 'Xác nhận xe qua điện thoại (gói chỉ thuê xe)', short: 'Xác nhận xe' },
  ON_SITE: { label: 'Khảo sát trực tiếp tận nơi', short: 'Trực tiếp' },
};
