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

export type VehicleStatus = 'AVAILABLE' | 'BUSY' | 'MAINTENANCE';

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
  isAvailable: boolean;
}

export interface ResourceAllocation {
  vehicles: Vehicle[];
  staff: StaffMember[];
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
 * PHOTO   — Điều phối viên tự lập báo giá dựa trên ảnh khách cung cấp (mặc định).
 * ON_SITE — Nhân viên vận chuyển khảo sát trực tiếp, CHỈ khi Điều phối yêu cầu.
 */
export type SurveyMethod = 'PHOTO' | 'ON_SITE';

export interface OnSiteSurveyRequest {
  requestedAt: number;
  /** Lý do / ghi chú của Điều phối gửi cho nhân viên đi khảo sát. */
  note: string;
  /** Nhân viên được Điều phối cử đi khảo sát (StaffMember.id). */
  surveyorId: string;
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

export interface Order {
  id: string;
  customerName: string;
  phone: string;
  fromAddress: string;
  toAddress: string;
  houseType: HouseType;
  scheduledDate: string;
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
  /** Khác null khi Điều phối yêu cầu khảo sát trực tiếp. */
  onSiteSurveyRequest: OnSiteSurveyRequest | null;
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
}

export type ClaimStatus = 'OPEN' | 'INVESTIGATING' | 'APPROVED' | 'REJECTED';

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
  approvedAmount: number | null;
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

export interface TimeSlot {
  id: string;
  label: string;
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

export interface SurveyInput {
  distanceKm: number;
  vehicleCapacity: number;
  vehicleQty: number;
  workers: number;
  cartons: number;
  tapeRolls: number;
  peFilmRolls: number;
  floors: number;
  alleyMeters: number;
}

export interface NewOrderInput {
  customerName: string;
  phone: string;
  fromAddress: string;
  toAddress: string;
  houseType: HouseType;
  scheduledDate: string;
  timeSlot: string;
  photoNames: string[];
  items: BookingItem[];
  requestedServices: string[];
  referenceQuote: ReferenceQuote;
}

export type QuoteItemPatch = Pick<QuoteItem, 'id' | 'quantity' | 'unitPrice'>;

export type AppAction =
  | { type: 'SET_ACTIVE_ORDER'; orderId: string }
  | { type: 'SET_FLAGS'; orderId: string; patch: Partial<ExceptionFlags> }
  | { type: 'JUMP_STATUS'; orderId: string; status: OrderStatus }
  | { type: 'CREATE_ORDER'; input: NewOrderInput }
  | { type: 'REQUEST_ONSITE_SURVEY'; orderId: string; note: string; surveyorId: string }
  | { type: 'CANCEL_ONSITE_SURVEY'; orderId: string }
  | { type: 'SUBMIT_SURVEY'; orderId: string; items: QuoteItem[]; by: 'COORDINATOR' | 'STAFF' }
  | { type: 'PAY_DEPOSIT'; orderId: string }
  | { type: 'REPORT_FIELD_DISCREPANCY'; orderId: string }
  | { type: 'CONFIRM_FIELD_ADJUSTMENT'; orderId: string }
  | { type: 'ADVANCE_FIELD_STAGE'; orderId: string; stage: FieldStage }
  | { type: 'TOGGLE_EQUIPMENT'; orderId: string; itemId: string }
  | { type: 'START_WAIT_TIMER'; orderId: string }
  | { type: 'STOP_WAIT_TIMER'; orderId: string }
  | { type: 'TICK_WAIT_TIMERS' }
  | { type: 'REPORT_VEHICLE_INCIDENT'; orderId: string }
  | { type: 'TRANSFER_VEHICLE'; orderId: string; plateNumber: string; driverName: string }
  | { type: 'UPDATE_QUOTE_ITEMS'; orderId: string; patches: QuoteItemPatch[]; discountPercent: number; note: string }
  | { type: 'ASSIGN_RESOURCES'; orderId: string; vehicleIds: string[]; crewIds: string[] }
  | { type: 'RESCHEDULE'; orderId: string; newDate: string; newSlot: string }
  | { type: 'CANCEL_ORDER'; orderId: string; mode: CancelMode; reason: string }
  | { type: 'RUN_AUTO_CANCEL_CRON' }
  | { type: 'PAY_BALANCE'; orderId: string }
  | { type: 'CONFIRM_WAREHOUSE_RETURN'; orderId: string }
  | { type: 'CLOSE_ORDER'; orderId: string }
  | { type: 'CREATE_CLAIM'; orderId: string; title: string; description: string; requestedAmount: number }
  | { type: 'SET_CLAIM_STATUS'; claimId: string; status: ClaimStatus; approvedAmount: number | null }
  | { type: 'APPROVE_REFUND'; refundId: string }
  | { type: 'PUSH_TOAST'; tone: ToastTone; message: string }
  | { type: 'DISMISS_TOAST'; id: string }
  | { type: 'ADD_VEHICLE'; plateNumber: string; capacityTons: number }
  | { type: 'UPDATE_VEHICLE'; id: string; plateNumber: string; capacityTons: number }
  | { type: 'SET_VEHICLE_STATUS'; id: string; status: Exclude<VehicleStatus, 'BUSY'> }
  | { type: 'REMOVE_VEHICLE'; id: string }
  | { type: 'RESET_DEMO' };
