import type {
  StaffMember,
  StaffRole,
  ToastTone,
  Vehicle,
  VehicleStatus,
} from "@/types";

/** Giờ hoạt động: xe có thể bắt đầu từ OPEN_TIME đến LAST_START_TIME (định dạng HH:MM, so sánh chuỗi được). */
export const OPEN_TIME = "06:00";
export const LAST_START_TIME = "20:00";
const pad2 = (n: number) => String(n).padStart(2, "0");

/** Các giờ bắt đầu hay được chọn — hiện sẵn dạng nút; giờ khác khách tự nhập. */
export const POPULAR_START_TIMES = ["07:00", "08:00", "09:00", "13:00", "15:00"];

export const DEFAULT_START_TIME = "08:00";

/** Giờ bắt đầu hợp lệ: đúng HH:MM và nằm trong giờ hoạt động. */
export const isValidStartTime = (value: string): boolean =>
  /^([01]\d|2[0-3]):[0-5]\d$/.test(value) && value >= OPEN_TIME && value <= LAST_START_TIME;

/** Thời lượng ước tính 1 ca chuyển nhà — dùng để giữ chỗ xe trên bảng điều phối. */
export const ESTIMATED_JOB_HOURS = 4;

/**
 * Ca làm của xe suy ra từ giờ bắt đầu: "08:30" → "08:30 - 12:30".
 * Giá trị cũ dạng khung "07:00 - 09:00" được giữ nguyên.
 */
export const shiftFromStart = (start: string): string => {
  const m = start.match(/^(\d{2}):(\d{2})$/);
  if (!m) return start;
  return `${start} - ${pad2((Number(m[1]) + ESTIMATED_JOB_HOURS) % 24)}:${m[2]}`;
};

/** Chuẩn hoá giờ bắt đầu (VD nháp cũ lưu khung "07:00 - 09:00" → "07:00"). */
export const toStartTime = (value: string): string => {
  const first = value.match(/\d{2}:\d{2}/)?.[0];
  return first && isValidStartTime(first) ? first : DEFAULT_START_TIME;
};

export const seedVehicles = (): Vehicle[] => [
  {
    id: "veh-1",
    plateNumber: "51C-889.92",
    capacityTons: 2,
    status: "AVAILABLE",
    currentShiftTime: null,
    busyOrderId: null,
  },
  {
    id: "veh-2",
    plateNumber: "51C-123.45",
    capacityTons: 2,
    status: "AVAILABLE",
    currentShiftTime: null,
    busyOrderId: null,
  },
  {
    id: "veh-3",
    plateNumber: "51D-456.78",
    capacityTons: 5,
    status: "AVAILABLE",
    currentShiftTime: null,
    busyOrderId: null,
  },
  {
    id: "veh-4",
    plateNumber: "51C-777.01",
    capacityTons: 1.25,
    status: "MAINTENANCE",
    currentShiftTime: null,
    busyOrderId: null,
  },
  {
    id: "veh-5",
    plateNumber: "51D-212.36",
    capacityTons: 2,
    status: "AVAILABLE",
    currentShiftTime: null,
    busyOrderId: null,
  },
];

export const seedStaff = (): StaffMember[] => [
  {
    id: "st-1",
    code: "NV-014",
    name: "Trần Văn Hùng",
    role: "DRIVER",
    isAvailable: true,
  },
  {
    id: "st-2",
    code: "NV-021",
    name: "Lê Hoàng Phúc",
    role: "DRIVER",
    isAvailable: true,
  },
  {
    id: "st-3",
    code: "NV-033",
    name: "Nguyễn Đức Thịnh",
    role: "MOVER",
    isAvailable: true,
  },
  {
    id: "st-4",
    code: "NV-037",
    name: "Phan Minh Khôi",
    role: "MOVER",
    isAvailable: true,
  },
  {
    id: "st-5",
    code: "NV-042",
    name: "Đỗ Quang Vinh",
    role: "TECHNICIAN",
    isAvailable: true,
  },
  {
    id: "st-6",
    code: "NV-045",
    name: "Huỳnh Tấn Lộc",
    role: "MOVER",
    isAvailable: false,
  },
  {
    id: "st-7",
    code: "NV-051",
    name: "Vũ Thành Đạt",
    role: "DRIVER",
    isAvailable: true,
  },
  {
    id: "st-8",
    code: "NV-058",
    name: "Cao Nhật Minh",
    role: "MOVER",
    isAvailable: false,
  },
];

export const STAFF_ROLE_LABEL: Record<StaffRole, string> = {
  TEAM_LEADER: "Trưởng nhóm",
  DRIVER: "Tài xế",
  MOVER: "Bốc xếp",
  TECHNICIAN: "Kỹ thuật tháo lắp",
};

export const VEHICLE_STATUS_META: Record<
  VehicleStatus,
  { label: string; tone: ToastTone }
> = {
  AVAILABLE: { label: "Sẵn sàng", tone: "success" },
  BUSY: { label: "Đang kẹt ca", tone: "danger" },
  MAINTENANCE: { label: "Bảo dưỡng", tone: "warning" },
};

export const PLATE_REGEX = /^\d{2}[A-Z]{1,2}-\d{3}\.\d{2}$/;
