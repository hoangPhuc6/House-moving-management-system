import { type Dispatch } from 'react';

export interface OrderTotals {
  baseQuote: number;
  fieldAdjustments: number;
  /** Giảm giá toàn đơn (đã quy ra tiền) trên báo giá + phát sinh hiện trường. */
  discount: number;
  waitFee: number;
  grandTotal: number;
  deposit: number;
  paid: number;
  remaining: number;
}

/** Một nhóm xe mà báo giá yêu cầu. */
export interface VehicleRequirement {
  /** Tải trọng (tấn) đọc từ tên hạng mục; null nếu không xác định được. */
  capacity: number | null;
  label: string;
  qty: number;
}

export interface AssignmentRequirement {
  vehicles: VehicleRequirement[];
  vehicleTotal: number;
  workers: number;
}

export interface ReferenceInput {
  fromAddress: string;
  toAddress: string;
  houseType: HouseType;
  items: BookingItem[];
  requestedServices: string[];
  /** Số nhân viên bốc vác (khách chọn hoặc ước lượng theo quy mô nhà); bỏ trống = ước lượng theo khối lượng. */
  laborQuantity?: number;
  /** Tải trọng xe ước lượng theo quy mô nhà (gói trọn gói); bỏ trống = theo xe khách chọn / khối lượng. */
  vehicleTons?: number;
  /** Gói chỉ thuê xe: các xe khách chọn — có thì tính giá theo đúng các xe này. */
  vehicleLines?: VehicleLine[];
}

export interface AppContextValue {
  state: AppState;
  dispatch: Dispatch<AppAction>;
}

export type RoleType = 'LANDING' | 'AUTH' | 'CUSTOMER' | 'STAFF' | 'COORDINATOR' | 'ADMIN';

/** State machine của một đơn hàng chuyển nhà. */
export type OrderStatus =
  | 'NEW_PENDING_SURVEY'
  | 'SURVEYED_PENDING_CONFIRM'
  | 'QUOTED_PENDING_SCHEDULE'
  | 'ASSIGNED'
  | 'IN_TRANSIT'
  | 'PAUSED_WAITING_CUSTOMER'
  | 'PENDING_PAYMENT_OR_CLAIM'
  | 'COMPLETED'
  | 'CANCELLED_EXPIRED';

export type HouseType = 'GROUND_FLOOR' | 'APARTMENT' | 'DEEP_ALLEY';

export type QuoteItemCategory = 'VEHICLE' | 'LABOR' | 'MATERIAL' | 'SURCHARGE' | 'FIELD_ADJUSTMENT' | 'WAIT_FEE';

export interface QuoteItem {
  id: string;
  name: string;
  category: QuoteItemCategory;
  unit: string;
  unitPrice: number;
  quantity: number;
  /** Diễn giải cách ra đơn giá / số lượng (VD: "Giá chuyến 900.000 đ (gồm 10 km) + 8 km vượt × 15.000 đ"). */
  note?: string;
}

export type ActorRole = Exclude<RoleType, 'LANDING' | 'AUTH'> | 'SYSTEM';

export interface AuditLogEntry {
  id: string;
  timestamp: number;
  actor: string;
  actorRole: ActorRole;
  action: string;
  detail: string;
}

/** Trạng thái chạy của xe. Bảo dưỡng / sửa chữa theo khoảng ngày nằm ở ResourceDowntime. */
export type VehicleStatus = 'AVAILABLE' | 'BUSY';

export interface Vehicle {
  id: string;
  plateNumber: string;
  capacityTons: number;
  status: VehicleStatus;
  currentShiftTime: string | null;
  busyOrderId: string | null;
}

export type StaffRole = 'TEAM_LEADER' | 'DRIVER' | 'MOVER' | 'TECHNICIAN';

export interface StaffMember {
  id: string;
  code: string;
  name: string;
  role: StaffRole;
}

export type DowntimeTarget = 'VEHICLE' | 'STAFF';
export type VehicleDowntimeReason = 'MAINTENANCE' | 'REPAIR' | 'INSPECTION';
export type StaffDowntimeReason = 'LEAVE' | 'SICK' | 'OTHER';
export type DowntimeReason = VehicleDowntimeReason | StaffDowntimeReason;

/**
 * Khoảng ngày một xe / nhân sự không nhận việc (bảo dưỡng, nghỉ phép…) — Điều phối ghi lại.
 * Tính cả hai đầu: from = to là nghỉ đúng một ngày.
 */
export interface ResourceDowntime {
  id: string;
  target: DowntimeTarget;
  resourceId: string;
  reason: DowntimeReason;
  /** YYYY-MM-DD */
  from: string;
  /** YYYY-MM-DD */
  to: string;
  note: string;
  createdAt: number;
}

export interface ResourceAllocation {
  vehicles: Vehicle[];
  staff: StaffMember[];
  downtimes: ResourceDowntime[];
}

export interface ExceptionFlags {
  /** Mã 1.1.1 — Khóa chuyển sang IN_TRANSIT cho tới khi khách xác nhận báo giá điều chỉnh. */
  hasFieldDiscrepancyLock: boolean;
  /** Mã 1.1.2 — Xe hỏng giữa đường, cần chuyển giao phương tiện. */
  isVehicleIncident: boolean;
  /** Mã 1.1.4 — 30 phút đầu miễn phí, sau đó +150.000đ/giờ. */
  waitingMinutes: number;
  /** Mã 1.1.6 & 1.1.7 — < 12h: khóa dời lịch & mất cọc khi hủy. */
  hoursUntilDeparture: number;
}

/** Step 7 Hard-Lock: chỉ được COMPLETED khi remainingBalance === 0 && isWarehouseReturned === true. */
export interface SettlementLockState {
  remainingBalance: number;
  isWarehouseReturned: boolean;
}

export type FieldStage = 'NOT_STARTED' | 'MATERIALS_PICKED' | 'WORKING' | 'MOVING' | 'ARRIVED' | 'HANDED_OVER';

export interface EquipmentItem {
  id: string;
  name: string;
  quantity: number;
  checked: boolean;
}

/**
 * Hình thức khảo sát đã dùng. Với PHOTO / VIDEO_CALL / ON_SITE: Điều phối phân người khảo sát →
 * người khảo sát gửi kết quả (đồ đạc, ghi chú, đề xuất xe & nhân sự) → Điều phối lập báo giá.
 * PHOTO         — Người khảo sát kiểm tra ảnh / video khách gửi.
 * VIDEO_CALL    — Người khảo sát gọi video theo lịch khách hẹn.
 * TRUCK_CONFIRM — Gói chỉ thuê xe: Điều phối gọi xác nhận xe & bốc vác, không khảo sát.
 * ON_SITE       — Người khảo sát đến tận nơi.
 */
export type SurveyMethod = 'PHOTO' | 'VIDEO_CALL' | 'TRUCK_CONFIRM' | 'ON_SITE';

/** Điều phối phân người khảo sát — áp dụng cho cả video call, tận nơi và khách gửi ảnh. */
export interface SurveyAssignment {
  assignedAt: number;
  /** Ghi chú của Điều phối gửi cho người khảo sát. */
  note: string;
  /** Nhân viên được phân khảo sát (StaffMember.id). */
  surveyorId: string;
}

/** Một món đồ ghi nhận khi khảo sát — khách, Điều phối và đội chuyển cùng đối chiếu theo danh sách này. */
export interface SurveyItem {
  /** Tên kèm kích thước nếu cần, VD "Giường 1m8", "Tủ lạnh 2 cánh". */
  name: string;
  qty: number;
  fragile: boolean;
  /** Cần tháo / lắp khi chuyển. */
  disassemble: boolean;
}

/** Kết quả người khảo sát gửi về — Điều phối dựa vào đây để lập báo giá (người khảo sát không định giá). */
export interface SurveyReport {
  submittedAt: number;
  surveyorId: string;
  /** Danh sách đồ đạc ghi nhận khi khảo sát. */
  items: SurveyItem[];
  /** Ghi chú hiện trường: lối đi, đồ đặc biệt, lưu ý cho đội chuyển… */
  note: string;
  /** Đề xuất nguồn lực: xe, số người, vật tư, lầu, hẻm. */
  proposal: SurveyInput;
}

/** Yêu cầu thương lượng của khách trên báo giá đang chờ xác nhận. */
export interface QuoteNegotiation {
  requestedAt: number;
  message: string;
}

/** Một dòng trong bảng phân rã báo giá tham khảo. */
export interface ReferenceBreakdownItem {
  serviceName: string;
  quantity: number;
  unitPrice: number;
  totalPrice: number;
  note: string | null;
  /** true: phụ phí hệ thống tự cộng (vượt km, thang bộ, hẻm sâu); false: dịch vụ khách chọn. */
  autoApplied: boolean;
}

export interface ReferenceQuote {
  currency: string;
  /** Quãng đường ước tính giữa hai địa chỉ (km). */
  distanceKm: number;
  /** Số km đã gồm trong giá xe. */
  includedKm: number;
  /** Số km vượt bị tính phụ phí (làm tròn lên). */
  chargeableExtraKm: number;
  disclaimer: string;
  financialSummary: {
    subtotal: number;
    discountAmount: number;
    totalEstimated: number;
    depositRequired: number;
  };
  categoryFees: {
    transportFee: number;
    serviceFee: number;
    surchargeFee: number;
  };
  breakdownItems: ReferenceBreakdownItem[];
  appliedVoucher: { code: string; description: string; discountAmount: number } | null;
}

/** Món đồ khách khai báo khi đặt đơn. */
export interface BookingItem {
  id: string;
  name: string;
  quantity: number;
  /** Thể tích ước tính của một món (m³), tùy chọn. */
  volumeM3: number | null;
  fragile: boolean;
}

/** Hình thức chuyển nhà khách chọn ở Bước 2. */
export type ServiceMode = 'FULL_PACKAGE' | 'TRUCK_ONLY';

/** Hình thức khảo sát khách mong muốn ở Bước 2 của wizard (Điều phối vẫn quyết định phương thức cuối cùng). */
export type SurveyPreference = 'VIDEO_CALL' | 'ON_SITE' | 'PHOTOS_ONLY';

/** Địa chỉ theo mô hình hành chính 2 cấp (từ 01/07/2025): Tỉnh / Thành phố → Phường / Xã / Đặc khu. */
export interface AddressDetail {
  province: string;
  ward: string;
  street: string;
  /** Loại bất động sản & lối đi (nhãn hiển thị). */
  propertyType: string;
  /** Tầng / lầu bốc dỡ (nhãn hiển thị). */
  floor: string;
  note: string;
}

export interface BookingDetails {
  from: AddressDetail;
  to: AddressDetail;
  serviceMode: ServiceMode;
  /** null với gói chỉ thuê xe — không khảo sát. */
  surveyPreference: SurveyPreference | null;
  /** Gói chỉ thuê xe: số nhân viên bốc vác khách thuê thêm (0 = không thuê). */
  laborCount: number;
  /** Gói chỉ thuê xe: các xe khách thuê (ghép được nhiều loại). */
  vehicles?: VehicleLine[];
  /** Ngày hẹn khảo sát (ISO) — rỗng khi khách chọn gửi ảnh / video. */
  surveyDate: string;
  /** Khung giờ hẹn khảo sát — rỗng khi khách chọn gửi ảnh / video. */
  surveyContactSlot: string;
  /** Khách chưa cố định giờ, cần tư vấn thêm. */
  flexibleTime: boolean;
  scheduleNote: string;
  email: string;
  customerNote: string;
}

export type VehicleIncidentKind = 'ENGINE' | 'TIRE' | 'ELECTRIC' | 'BRAKE' | 'ACCIDENT' | 'OTHER';

/** Đội xe báo xe gặp sự cố giữa chuyến — Điều phối dựa vào đây để quyết định tăng bo. */
export interface VehicleIncident {
  kind: VehicleIncidentKind;
  /** Mô tả: xe bị gì, đang ở đâu, đồ đạc có an toàn không. */
  note: string;
  reportedAt: number;
}

export interface Order {
  id: string;
  customerName: string;
  phone: string;
  fromAddress: string;
  toAddress: string;
  houseType: HouseType;
  scheduledDate: string;
  /** Giờ xe bắt đầu đi (HH:MM) — đơn cũ có thể là khung "07:00 - 09:00". */
  timeSlot: string;
  status: OrderStatus;
  /** Tên các ảnh đồ đạc khách tải lên khi đặt lịch — căn cứ khảo sát qua ảnh. */
  customerPhotos: string[];
  /** Danh sách đồ đạc khách khai báo. */
  items: BookingItem[];
  /** Tên các dịch vụ khách chọn khi đặt đơn. */
  requestedServices: string[];
  /** Báo giá tham khảo do hệ thống trả về ngay sau khi đặt đơn. */
  referenceQuote: ReferenceQuote | null;
  /** Người khảo sát được Điều phối phân công (null = chưa phân). */
  surveyAssignment: SurveyAssignment | null;
  /** Kết quả khảo sát người khảo sát gửi về (null = chưa khảo sát xong). */
  surveyReport: SurveyReport | null;
  /** Thời gian dự kiến của chuyến (giờ), chốt khi gửi báo giá; null = chưa có, dùng mặc định. */
  estimatedHours: number | null;
  /** Sự cố xe đội xe báo — null khi không có / đã tăng bo xong. */
  vehicleIncident: VehicleIncident | null;
  /** Khách yêu cầu thương lượng báo giá — Điều phối sửa rồi gửi lại (null = không có yêu cầu đang chờ). */
  negotiation: QuoteNegotiation | null;
  /** Phương thức đã dùng để lập báo giá khảo sát (null khi chưa khảo sát). */
  surveyMethod: SurveyMethod | null;
  quoteItems: QuoteItem[];
  /** Giảm giá trên toàn đơn theo phần trăm (0–100), áp dụng cho tổng báo giá. */
  discountPercent: number;
  pendingAdjustment: QuoteItem | null;
  depositRequired: number;
  depositPaid: number;
  balancePaid: number;
  isWarehouseReturned: boolean;
  /** Bất biến sau khi COMPLETED — khiếu nại KHÔNG được ghi đè giá trị này. */
  finalRevenue: number | null;
  flags: ExceptionFlags;
  fieldStage: FieldStage;
  /** Xe chính (= vehicleIds[0]). */
  vehicleId: string | null;
  /** Tất cả xe đã gán cho đơn (đúng loại & số lượng theo báo giá). */
  vehicleIds: string[];
  crewIds: string[];
  equipment: EquipmentItem[];
  auditLog: AuditLogEntry[];
  /** Epoch ms — hạn tự động hủy 24h (Mã 1.1.3). */
  autoCancelDeadline: number | null;
  depositReservedUntil: string | null;
  createdAt: number;
  /** Chi tiết khách khai báo khi đặt đơn (đơn tạo qua wizard). */
  bookingDetails?: BookingDetails | null;
  /** Epoch ms — lúc khách bấm gửi ảnh / video khảo sát cho chuyên viên (null khi chưa gửi). */
  surveyMediaSubmittedAt?: number | null;
}

/**
 * OPEN → (Điều phối) INVESTIGATING → đề xuất PENDING_APPROVAL hoặc từ chối REJECTED
 * → (Admin) duyệt chi APPROVED hoặc trả lại INVESTIGATING.
 */
export type ClaimStatus = 'OPEN' | 'INVESTIGATING' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED';

export interface EvidenceMedia {
  id: string;
  kind: 'PHOTO' | 'VIDEO';
  caption: string;
  takenAt: string;
  tone: 'neutral' | 'warning' | 'danger';
}

export interface ClaimTicket {
  id: string;
  orderId: string;
  customerName: string;
  title: string;
  description: string;
  status: ClaimStatus;
  requestedAmount: number;
  /** Mức bồi thường Điều phối đề xuất sau khi đối chiếu. */
  proposedAmount: number | null;
  approvedAmount: number | null;
  /** Ghi chú xử lý mới nhất: căn cứ đề xuất, lý do từ chối, hoặc lý do Admin trả lại. */
  handlingNote: string;
  createdAt: number;
  packingEvidence: EvidenceMedia[];
  customerEvidence: EvidenceMedia[];
}

export type RefundStatus = 'PENDING' | 'APPROVED';

export interface RefundRequest {
  id: string;
  orderId: string;
  customerName: string;
  amount: number;
  hoursBeforeDeparture: number;
  status: RefundStatus;
  createdAt: number;
}

export type LedgerCategory = 'Thu nhập khác / Phí phạt hủy đơn' | 'Chi phí bồi thường khiếu nại';

export interface LedgerEntry {
  id: string;
  orderId: string;
  amount: number;
  category: LedgerCategory;
  note: string;
  createdAt: number;
}


export interface AppState {
  activeOrderId: string;
  orders: Order[];
  resources: ResourceAllocation;
  claims: ClaimTicket[];
  refunds: RefundRequest[];
  ledger: LedgerEntry[];
  toasts: Toast[];
}

export type ToastTone = 'info' | 'success' | 'warning' | 'danger';

export interface Toast {
  id: string;
  tone: ToastTone;
  message: string;
}

export type CancelMode = 'REFUND_FULL' | 'FORFEIT' | 'RESERVE_3_MONTHS';

/** Một loại xe trong đề xuất / báo giá — đơn có thể ghép nhiều loại, VD 1 xe 5 tấn + 1 xe 1,25 tấn. */
export interface VehicleLine {
  /** Tải trọng (tấn) — khóa của VEHICLE_PRICING. */
  capacity: number;
  qty: number;
}

/** Thùng carton theo cỡ: nhỏ (sách, đồ nặng), vừa (đồ dùng chung), lớn (quần áo, chăn gối). */
export type CartonSize = 'S' | 'M' | 'L';

export interface SurveyInput {
  distanceKm: number;
  /** Các loại xe cần dùng; loại không dùng thì không có trong danh sách. */
  vehicles: VehicleLine[];
  workers: number;
  cartons: Record<CartonSize, number>;
  tapeRolls: number;
  peFilmRolls: number;
  floors: number;
  alleyMeters: number;
  /** Tổng thời gian dự kiến cho chuyến (bốc xếp + di chuyển + dỡ), giờ — quyết định xe & người bận bao lâu. */
  estimatedHours: number;
}

export interface NewOrderInput {
  customerName: string;
  phone: string;
  fromAddress: string;
  toAddress: string;
  houseType: HouseType;
  scheduledDate: string;
  /** Giờ xe bắt đầu đi (HH:MM) — đơn cũ có thể là khung "07:00 - 09:00". */
  timeSlot: string;
  photoNames: string[];
  items: BookingItem[];
  requestedServices: string[];
  referenceQuote: ReferenceQuote;
  /** Thông tin chi tiết khách khai báo qua wizard đặt đơn 4 bước. */
  details?: BookingDetails;
}

export type QuoteItemPatch = Pick<QuoteItem, 'id' | 'quantity' | 'unitPrice'>;

export type AppAction =
  | { type: 'SET_ACTIVE_ORDER'; orderId: string }
  | { type: 'SET_FLAGS'; orderId: string; patch: Partial<ExceptionFlags> }
  | { type: 'JUMP_STATUS'; orderId: string; status: OrderStatus }
  | { type: 'CREATE_ORDER'; input: NewOrderInput }
  | { type: 'ASSIGN_SURVEYOR'; orderId: string; note: string; surveyorId: string }
  | { type: 'UNASSIGN_SURVEYOR'; orderId: string }
  | { type: 'SUBMIT_SURVEY_REPORT'; orderId: string; items: SurveyItem[]; note: string; proposal: SurveyInput }
  | { type: 'SUBMIT_SURVEY'; orderId: string; items: QuoteItem[]; estimatedHours: number }
  | { type: 'CUSTOMER_REQUEST_NEGOTIATION'; orderId: string; message: string }
  | { type: 'RESEND_QUOTE'; orderId: string; note: string }
  | { type: 'CUSTOMER_ADD_SURVEY_MEDIA'; orderId: string; names: string[] }
  | { type: 'CUSTOMER_SUBMIT_SURVEY_MEDIA'; orderId: string }
  | { type: 'CUSTOMER_UPDATE_SURVEY'; orderId: string; preference: SurveyPreference; date: string; slot: string }
  | { type: 'PAY_DEPOSIT'; orderId: string }
  | { type: 'REPORT_FIELD_DISCREPANCY'; orderId: string }
  | { type: 'CONFIRM_FIELD_ADJUSTMENT'; orderId: string }
  | { type: 'ADVANCE_FIELD_STAGE'; orderId: string; stage: FieldStage }
  | { type: 'TOGGLE_EQUIPMENT'; orderId: string; itemId: string }
  | { type: 'START_WAIT_TIMER'; orderId: string }
  | { type: 'STOP_WAIT_TIMER'; orderId: string }
  | { type: 'TICK_WAIT_TIMERS' }
  | { type: 'REPORT_VEHICLE_INCIDENT'; orderId: string; kind: VehicleIncidentKind; note: string }
  | { type: 'TRANSFER_VEHICLE'; orderId: string; plateNumber: string; driverName: string; /** Xe cũ hỏng — sửa tới ngày nào (YYYY-MM-DD). */ repairUntil: string }
  | { type: 'UPDATE_QUOTE_ITEMS'; orderId: string; patches: QuoteItemPatch[]; discountPercent: number; note: string }
  | { type: 'ASSIGN_RESOURCES'; orderId: string; vehicleIds: string[]; crewIds: string[] }
  | { type: 'RESCHEDULE'; orderId: string; newDate: string; newSlot: string }
  | { type: 'CANCEL_ORDER'; orderId: string; mode: CancelMode; reason: string }
  | { type: 'RUN_AUTO_CANCEL_CRON' }
  | { type: 'PAY_BALANCE'; orderId: string }
  | { type: 'CONFIRM_WAREHOUSE_RETURN'; orderId: string }
  | { type: 'CREATE_CLAIM'; orderId: string; title: string; description: string; requestedAmount: number }
  | { type: 'CLAIM_INVESTIGATE'; claimId: string }
  | { type: 'CLAIM_PROPOSE_PAYOUT'; claimId: string; amount: number; note: string }
  | { type: 'CLAIM_REJECT'; claimId: string; note: string }
  | { type: 'CLAIM_APPROVE_PAYOUT'; claimId: string; amount: number }
  | { type: 'CLAIM_SEND_BACK'; claimId: string; note: string }
  | { type: 'APPROVE_REFUND'; refundId: string }
  | { type: 'PUSH_TOAST'; tone: ToastTone; message: string }
  | { type: 'DISMISS_TOAST'; id: string }
  | { type: 'ADD_VEHICLE'; plateNumber: string; capacityTons: number }
  | { type: 'UPDATE_VEHICLE'; id: string; plateNumber: string; capacityTons: number }
  | { type: 'ADD_DOWNTIME'; target: DowntimeTarget; resourceId: string; reason: DowntimeReason; from: string; to: string; note: string }
  | { type: 'END_DOWNTIME'; id: string }
  | { type: 'REMOVE_VEHICLE'; id: string }
  | { type: 'RESET_DEMO' };
