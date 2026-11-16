import { type Dispatch } from 'react';
import type { MaterialKind } from './catalog';

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
  /** Đơn có dịch vụ tháo lắp → đội phải có ít nhất một người có kỹ năng tháo lắp. */
  needsAssembly: boolean;
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

/** SERVICE: dịch vụ thêm từ bảng giá (tháo lắp…) — tách khỏi LABOR để không bị tính vào số người bốc xếp khi điều phối. */
export type QuoteItemCategory = 'VEHICLE' | 'LABOR' | 'MATERIAL' | 'SERVICE' | 'SURCHARGE' | 'FIELD_ADJUSTMENT' | 'WAIT_FEE';

export interface QuoteItem {
  id: string;
  name: string;
  category: QuoteItemCategory;
  unit: string;
  unitPrice: number;
  quantity: number;
  /** Diễn giải cách ra đơn giá / số lượng (VD: "Giá chuyến 900.000 đ (gồm 10 km) + 8 km vượt × 15.000 đ"). */
  note?: string;
  /** Khoản ngoài bảng giá do Điều phối nhập tay (bắt buộc có lý do trong note) — hiện rõ cho khách. */
  offCatalog?: boolean;
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

/**
 * Kỹ năng của nhân viên (một người có thể có nhiều): lái xe, bốc xếp, tháo lắp.
 * Kỹ năng thuộc hồ sơ; vai trò trong từng chuyến do Điều phối chọn khi gán đội ({@link CrewAssignment}).
 * Trưởng nhóm không phải kỹ năng: tài xế của chuyến mặc định là trưởng nhóm.
 */
export type StaffSkill = 'DRIVE' | 'LOAD' | 'ASSEMBLY';

/** Hạng GPLX: B2 lái xe tải ≤ 3,5 tấn; C lái mọi xe tải. */
export type LicenseClass = 'B2' | 'C';

export interface DriverLicense {
  licenseClass: LicenseClass;
  /** Ngày hết hạn (ISO yyyy-mm-dd). */
  expiresAt: string;
}

/** ACTIVE: đang làm; ON_LEAVE: nghỉ phép (tạm không gán); RESIGNED: nghỉ việc. */
export type StaffWorkStatus = 'ACTIVE' | 'ON_LEAVE' | 'RESIGNED';

/** Vai trò của một người trong MỘT chuyến. */
export type TripRole = 'DRIVER' | 'LOADER';

export interface CrewAssignment {
  staffId: string;
  role: TripRole;
  /** Xe người này lái (chỉ với DRIVER). */
  vehicleId: string | null;
}

export interface StaffMember {
  id: string;
  code: string;
  name: string;
  phone: string;
  /** Tài khoản đăng nhập gắn với hồ sơ (CatalogState.users). */
  userId: string | null;
  skills: StaffSkill[];
  /** null = không có bằng lái xe tải. */
  license: DriverLicense | null;
  status: StaffWorkStatus;
}

export interface ResourceAllocation {
  vehicles: Vehicle[];
  staff: StaffMember[];
}

export interface ExceptionFlags {
  /** Mã 1.1.2 — Xe hỏng giữa đường, cần chuyển giao phương tiện. */
  isVehicleIncident: boolean;
  /** Mã 1.1.4 — 30 phút đầu miễn phí, sau đó +150.000đ/giờ. */
  waitingMinutes: number;
  /** Mã 1.1.6 & 1.1.7 — < 12h: khóa dời lịch & mất cọc khi hủy. */
  hoursUntilDeparture: number;
}

/** Step 7 Hard-Lock: chỉ được COMPLETED khi remainingBalance === 0 && materialsSettled === true. */
export interface SettlementLockState {
  remainingBalance: number;
  /** Phiếu vật tư đã quyết toán (hoặc đơn không dùng vật tư). */
  materialsSettled: boolean;
}

export type FieldStage = 'NOT_STARTED' | 'MATERIALS_PICKED' | 'WORKING' | 'MOVING' | 'ARRIVED' | 'HANDED_OVER';

/**
 * Vòng đời phiếu vật tư của một đơn:
 * NONE → (gán đội) DRAFT → (kho xuất) ISSUED → (đội nhận) RECEIVED → (đội khai đã dùng) USAGE_REPORTED → (kho kiểm nhận trả) RETURNED.
 * Đơn bị hủy khi đã xuất kho: kho kiểm nhận trả thẳng từ ISSUED / RECEIVED.
 */
export type MaterialSlipStatus = 'NONE' | 'DRAFT' | 'ISSUED' | 'RECEIVED' | 'USAGE_REPORTED' | 'RETURNED';

/** Một dòng trên phiếu vật tư. Số lượng null = bước đó chưa diễn ra. */
export interface MaterialLine {
  id: string;
  inventoryItemId: string;
  name: string;
  unit: string;
  kind: MaterialKind;
  /** Theo báo giá (vật tư tiêu hao) hoặc bộ dụng cụ chuẩn theo số xe. */
  planned: number;
  /** Kho thực xuất — 0 khi chưa xuất. */
  issued: number;
  /** Đội xe xác nhận thực nhận. */
  received: number | null;
  /** Đội xe khai đã dùng — chỉ vật tư tiêu hao; dụng cụ luôn 0. */
  used: number | null;
  /** Kho kiểm nhận trả lại. */
  returned: number | null;
  /** Chênh lệch đã xử lý khi quyết toán (mất / hỏng / hao hụt) = issued − used − returned. */
  lost: number;
}

export interface MaterialSlip {
  status: MaterialSlipStatus;
  lines: MaterialLine[];
  issuedAt: number | null;
  receivedAt: number | null;
  usageReportedAt: number | null;
  returnedAt: number | null;
  /** Ghi chú khi đội xe nhận thiếu so với phiếu xuất. */
  receiveNote: string;
  /** Biên bản xử lý chênh lệch khi quyết toán (lý do, người chịu trách nhiệm). */
  resolutionNote: string;
}

/** Số lượng nhập theo từng dòng phiếu: lineId → số lượng. */
export type MaterialQuantities = Record<string, number>;

/**
 * PHOTO         — Điều phối viên lập báo giá dựa trên ảnh / video khách gửi (mặc định).
 * VIDEO_CALL    — Điều phối viên khảo sát qua cuộc gọi video theo lịch khách hẹn.
 * TRUCK_CONFIRM — Gói chỉ thuê xe: Điều phối gọi xác nhận xe & bốc vác, không khảo sát.
 * ON_SITE       — Nhân viên vận chuyển khảo sát trực tiếp, CHỈ khi Điều phối yêu cầu.
 */
export type SurveyMethod = 'PHOTO' | 'VIDEO_CALL' | 'TRUCK_CONFIRM' | 'ON_SITE';

/** Nhóm ảnh hiện trường bắt buộc khi khảo sát trực tiếp (khớp SURVEY_MEDIA_CATEGORIES). */
export type SitePhotoCategory = 'ROOMS' | 'BIG' | 'ACCESS' | 'PARKING';

/** Ảnh bằng chứng nhân viên chụp tại hiện trường. Bản demo giữ object URL trong phiên làm việc. */
export interface EvidencePhoto {
  id: string;
  name: string;
  url: string;
  takenAt: number;
}

/** Ảnh khảo sát trực tiếp, gắn nhóm trong checklist. */
export interface SitePhoto extends EvidencePhoto {
  category: SitePhotoCategory;
}

/**
 * Loại phụ phí phát sinh (khớp SurchargeType backend). Đơn giá:
 * hệ thống tự tính (lầu, hẻm, km) · theo dịch vụ trong bảng giá (thêm đồ, tháo lắp, vật tư) · nhập tay (khác).
 * Phí chờ khách đã có đồng hồ chờ riêng (mã 1.1.4) nên không báo qua đây.
 */
export type SurchargeType = 'EXTRA_FLOOR' | 'NO_ELEVATOR' | 'LONG_CARRY' | 'EXTRA_DISTANCE' | 'EXTRA_ITEMS' | 'DISASSEMBLY' | 'PACKING_MATERIAL' | 'OTHER';

/**
 * PROPOSED ─(Điều phối chốt)─► WAITING_CUSTOMER ─(khách đồng ý)─► ACCEPTED
 * PROPOSED ─(Điều phối bác)─► REJECTED · PROPOSED ─(NV rút)─► CANCELLED
 * WAITING_CUSTOMER ─(khách từ chối)─► REJECTED_BY_CUSTOMER ─(Điều phối gửi lại / rút)─► CANCELLED
 */
export type SurchargeStatus = 'PROPOSED' | 'WAITING_CUSTOMER' | 'ACCEPTED' | 'REJECTED' | 'REJECTED_BY_CUSTOMER' | 'CANCELLED';

export interface Surcharge {
  id: string;
  /** Các dòng NV báo trong cùng một lần dùng chung reportId — hiển thị & duyệt theo nhóm, trạng thái vẫn theo từng dòng. */
  reportId: string;
  /** Ghi chú chung của lần báo (chỉ khi báo từ 2 dòng; giống nhau trên mọi dòng cùng reportId). */
  reportNote: string;
  type: SurchargeType;
  /** Tên dòng phí (nhãn loại hoặc tên dịch vụ trong bảng giá). */
  name: string;
  /** Khảo sát / khách khai ghi gì — VD "Hẻm 50m". */
  surveyed: string;
  /** Thực tế tại hiện trường — VD "Hẻm 200m, xe không vào được". */
  actual: string;
  /** Mô tả sai lệch của dòng (bắt buộc). */
  description: string;
  /** Phần chênh lệch so với khảo sát. */
  quantity: number;
  unit: string;
  unitPrice: number;
  /** Số lượng / đơn giá lúc NV đề xuất — để đối chiếu khi Điều phối điều chỉnh. */
  proposedQuantity: number;
  proposedUnitPrice: number;
  photos: EvidencePhoto[];
  status: SurchargeStatus;
  proposedAt: number;
  /** Điều phối chốt / bác / rút. */
  reviewedAt: number | null;
  reviewNote: string;
  /** Khách đồng ý / từ chối. */
  respondedAt: number | null;
  customerNote: string;
}

/** Một dòng NV gửi khi báo sai lệch — đơn giá chỉ là ước tính, Điều phối chốt. */
export type SurchargeDraft = Pick<Surcharge, 'type' | 'name' | 'surveyed' | 'actual' | 'description' | 'quantity' | 'unit' | 'unitPrice' | 'photos'>;

/** Một lần báo sai lệch: các dòng (mỗi dòng có mô tả & ảnh riêng) + ghi chú chung tùy chọn khi từ 2 dòng. */
export interface SurchargeReportDraft {
  note: string;
  lines: SurchargeDraft[];
}

/** Số liệu Điều phối chốt cho một dòng. */
export interface SurchargeDecision {
  surchargeId: string;
  quantity: number;
  unitPrice: number;
  /** Đổi dịch vụ trong cùng loại (loại tính theo bảng giá) — VD NV chọn nhầm tháo lắp tủ thay vì máy lạnh. */
  service?: { name: string; unit: string; unitPrice: number };
}

export interface OnSiteSurveyRequest {
  requestedAt: number;
  /** Lý do / ghi chú của Điều phối gửi cho nhân viên đi khảo sát. */
  note: string;
  /** Nhân viên được Điều phối cử đi khảo sát (StaffMember.id). */
  surveyorId: string;
  /** Nhân viên bấm "Bắt đầu khảo sát" tại nhà khách. */
  startedAt: number | null;
  /** Nhân viên bấm "Hoàn tất khảo sát" — chờ Điều phối lập báo giá. */
  completedAt: number | null;
  sitePhotos: SitePhoto[];
  /** Đề xuất xe / người / vật tư của nhân viên — Điều phối dùng làm căn cứ báo giá. */
  proposal: SurveyInput | null;
  /** Nhận xét hiện trường (vd. cầu thang hẹp, phải tháo cửa tủ lạnh). */
  resultNote: string;
}

/**
 * Biên bản khảo sát lưu khi gửi báo giá: số liệu làm căn cứ, ghi chú & ảnh hiện trường.
 * Khách xem cạnh báo giá; phát sinh hiện trường lấy "khảo sát ghi" từ đây.
 */
export interface SurveyRecord {
  method: SurveyMethod;
  baseline: SurveyInput;
  note: string;
  photos: SitePhoto[];
  recordedAt: number;
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
  /** Quy mô nhà ở (VD: "2 phòng ngủ") — chỉ thu thập với gói trọn gói. */
  homeScale: string;
  /** null với gói chỉ thuê xe — không khảo sát. */
  surveyPreference: SurveyPreference | null;
  /** Gói chỉ thuê xe: số nhân viên bốc vác khách thuê thêm (0 = không thuê). */
  laborCount: number;
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
  /** Mã 1.1.1 — Phụ phí phát sinh tại hiện trường (NV đề xuất → Điều phối chốt → khách xác nhận). */
  surcharges: Surcharge[];
  /** Biên bản khảo sát — căn cứ của báo giá chính thức (null khi chưa báo giá). */
  surveyRecord: SurveyRecord | null;
  depositRequired: number;
  depositPaid: number;
  balancePaid: number;
  /** Phiếu vật tư & dụng cụ: xuất kho → đội nhận → khai đã dùng → kiểm nhận trả. */
  materials: MaterialSlip;
  /** Bất biến sau khi COMPLETED — khiếu nại KHÔNG được ghi đè giá trị này. */
  finalRevenue: number | null;
  flags: ExceptionFlags;
  fieldStage: FieldStage;
  /** Xe chính (= vehicleIds[0]). */
  vehicleId: string | null;
  /** Tất cả xe đã gán cho đơn (đúng loại & số lượng theo báo giá). */
  vehicleIds: string[];
  /** Toàn bộ nhân sự của đơn (suy ra từ crew) — dùng để tra lệnh việc của nhân viên. */
  crewIds: string[];
  /** Phân vai theo chuyến: ai lái xe nào, ai bốc xếp. */
  crew: CrewAssignment[];
  /**
   * Trưởng nhóm chuyến — mặc định là tài xế (nhiều xe: tài xế xe lớn nhất).
   * Người nhận vật tư, chịu trách nhiệm bàn giao.
   */
  leaderId: string | null;
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
  /** Xe khác loại đi kèm (VD: 1 xe 5 tấn + 1 xe 1,25 tấn chở đồ lặt vặt). */
  extraVehicles?: { capacity: number; qty: number }[];
  workers: number;
  cartons: number;
  tapeRolls: number;
  peFilmRolls: number;
  /** Số lầu phải khiêng thang bộ — tách điểm đi / điểm đến để khách đối chiếu từng đầu. */
  floorsFrom: number;
  floorsTo: number;
  /** Độ dài hẻm / quãng khiêng bộ (m) ở từng đầu. */
  alleyFrom: number;
  alleyTo: number;
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
  /** Thông tin chi tiết khách khai báo qua wizard đặt đơn 4 bước. */
  details?: BookingDetails;
  /** Gói chỉ thuê xe: dịch vụ bảng giá khách chọn (tháo lắp, đóng gói) — đưa thẳng vào báo giá tự động. */
  extraQuoteItems?: QuoteItem[];
}

export type QuoteItemPatch = Pick<QuoteItem, 'id' | 'quantity' | 'unitPrice'>;

export type AppAction =
  | { type: 'SET_ACTIVE_ORDER'; orderId: string }
  | { type: 'SET_FLAGS'; orderId: string; patch: Partial<ExceptionFlags> }
  | { type: 'JUMP_STATUS'; orderId: string; status: OrderStatus }
  | { type: 'CREATE_ORDER'; input: NewOrderInput }
  | { type: 'REQUEST_ONSITE_SURVEY'; orderId: string; note: string; surveyorId: string }
  | { type: 'CANCEL_ONSITE_SURVEY'; orderId: string }
  | { type: 'STAFF_START_SURVEY'; orderId: string }
  | { type: 'STAFF_ADD_SITE_PHOTOS'; orderId: string; photos: SitePhoto[] }
  | { type: 'STAFF_REMOVE_SITE_PHOTO'; orderId: string; photoId: string }
  | { type: 'STAFF_COMPLETE_SURVEY'; orderId: string; proposal: SurveyInput; resultNote: string }
  /** Chỉ Điều phối gửi báo giá; adjustReason bắt buộc khi lệch nhiều so với đề xuất của NV khảo sát. */
  | {
      type: 'SUBMIT_SURVEY';
      orderId: string;
      items: QuoteItem[];
      /** Số liệu khảo sát làm căn cứ báo giá. */
      input: SurveyInput;
      /** Ghi chú & ảnh chụp màn hình (video call) của Điều phối. */
      evidence: { note: string; photos: SitePhoto[] };
      /** Dịch vụ khách chọn khi đặt đơn nhưng Điều phối bỏ khỏi báo giá — bắt buộc lý do. */
      removedRequested: { name: string; reason: string }[];
      adjustReason?: string;
    }
  | { type: 'CUSTOMER_ADD_SURVEY_MEDIA'; orderId: string; names: string[] }
  | { type: 'CUSTOMER_SUBMIT_SURVEY_MEDIA'; orderId: string }
  | { type: 'CUSTOMER_UPDATE_SURVEY'; orderId: string; preference: SurveyPreference; date: string; slot: string }
  | { type: 'PAY_DEPOSIT'; orderId: string }
  | { type: 'STAFF_PROPOSE_SURCHARGE'; orderId: string; report: SurchargeReportDraft }
  | { type: 'STAFF_CANCEL_SURCHARGE'; orderId: string; surchargeId: string }
  /** Chốt & gửi khách (PROPOSED), hoặc gửi lại với số liệu mới khi khách đã từ chối (REJECTED_BY_CUSTOMER). */
  | { type: 'COORD_SEND_SURCHARGE'; orderId: string; items: SurchargeDecision[]; note: string }
  | { type: 'COORD_REJECT_SURCHARGE'; orderId: string; surchargeId: string; note: string }
  | { type: 'COORD_WITHDRAW_SURCHARGE'; orderId: string; surchargeId: string; note: string }
  /** Đồng ý một hoặc nhiều dòng cùng lúc ("Đồng ý tất cả"). */
  | { type: 'CUSTOMER_ACCEPT_SURCHARGE'; orderId: string; surchargeIds: string[] }
  | { type: 'CUSTOMER_REJECT_SURCHARGE'; orderId: string; surchargeId: string; note: string }
  /** Công cụ demo: bật = thêm một đề xuất mẫu; tắt = hủy mọi khoản đang chặn. */
  | { type: 'DEMO_SURCHARGE'; orderId: string; on: boolean }
  | { type: 'ADVANCE_FIELD_STAGE'; orderId: string; stage: FieldStage }
  /** addedLines: dòng thủ kho thêm tay lúc xuất (đồ ngoài kế hoạch) — kế hoạch 0, vẫn phải quyết toán như dòng khác. */
  | { type: 'ISSUE_MATERIALS'; orderId: string; issued: MaterialQuantities; addedLines: MaterialLine[] }
  | { type: 'CONFIRM_MATERIALS_RECEIVED'; orderId: string; received: MaterialQuantities; note: string }
  | { type: 'REPORT_MATERIAL_USAGE'; orderId: string; used: MaterialQuantities }
  | { type: 'RETURN_MATERIALS'; orderId: string; returned: MaterialQuantities; note: string }
  | { type: 'START_WAIT_TIMER'; orderId: string }
  | { type: 'STOP_WAIT_TIMER'; orderId: string }
  | { type: 'TICK_WAIT_TIMERS' }
  | { type: 'REPORT_VEHICLE_INCIDENT'; orderId: string }
  | { type: 'TRANSFER_VEHICLE'; orderId: string; plateNumber: string; driverName: string }
  /**
   * Sửa báo giá khi đang chờ khách chốt: số lượng mọi dòng, đơn giá chỉ khoản ngoài bảng giá,
   * bỏ dòng (removedIds), thêm dịch vụ bảng giá / khoản ngoài bảng giá (added). note bắt buộc.
   */
  | { type: 'UPDATE_QUOTE_ITEMS'; orderId: string; patches: QuoteItemPatch[]; removedIds: string[]; added: QuoteItem[]; discountPercent: number; note: string }
  /** Trưởng nhóm suy ra từ crew (tài xế đầu tiên) — không truyền riêng. */
  | { type: 'ASSIGN_RESOURCES'; orderId: string; vehicleIds: string[]; crew: CrewAssignment[]; materialPlan: MaterialLine[] }
  /** Admin tạo / sửa hồ sơ nhân viên (kỹ năng, bằng lái, tình trạng). */
  | { type: 'UPSERT_STAFF'; staff: StaffMember }
  | { type: 'RESCHEDULE'; orderId: string; newDate: string; newSlot: string }
  | { type: 'CANCEL_ORDER'; orderId: string; mode: CancelMode; reason: string }
  | { type: 'RUN_AUTO_CANCEL_CRON' }
  | { type: 'PAY_BALANCE'; orderId: string }
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
