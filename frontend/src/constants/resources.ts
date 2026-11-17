import { addDaysISO, todayLocalISO } from "@/services/downtime";
import type {
  ResourceDowntime,
  StaffMember,
  StaffRole,
  ToastTone,
  Vehicle,
  VehicleIncidentKind,
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
export const shiftFromStart = (start: string, hours: number = ESTIMATED_JOB_HOURS): string => {
  const m = start.match(/^(\d{2}):(\d{2})$/);
  if (!m) return start;
  const end = (Number(m[1]) * 60 + Number(m[2]) + Math.round(hours * 60)) % (24 * 60);
  return `${start} - ${pad2(Math.floor(end / 60))}:${pad2(end % 60)}`;
};

/** Ca của một đơn theo thời gian dự kiến khảo sát chốt (chưa có thì mặc định ESTIMATED_JOB_HOURS). */
export const orderShift = (o: { timeSlot: string; estimatedHours: number | null }) => shiftFromStart(o.timeSlot, o.estimatedHours ?? ESTIMATED_JOB_HOURS);

/** "4,5 giờ" */
export const hoursLabel = (h: number) => `${String(h).replace('.', ',')} giờ`;

export const VEHICLE_INCIDENT_KINDS: Record<VehicleIncidentKind, string> = {
  ENGINE: 'Chết máy / không nổ máy',
  TIRE: 'Thủng / nổ lốp',
  ELECTRIC: 'Hỏng điện / ắc quy',
  BRAKE: 'Hỏng phanh / hộp số',
  ACCIDENT: 'Va chạm / tai nạn',
  OTHER: 'Khác',
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
    status: "AVAILABLE",
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
  },
  {
    id: "st-2",
    code: "NV-021",
    name: "Lê Hoàng Phúc",
    role: "DRIVER",
  },
  {
    id: "st-3",
    code: "NV-033",
    name: "Nguyễn Đức Thịnh",
    role: "MOVER",
  },
  {
    id: "st-4",
    code: "NV-037",
    name: "Phan Minh Khôi",
    role: "MOVER",
  },
  {
    id: "st-5",
    code: "NV-042",
    name: "Đỗ Quang Vinh",
    role: "TECHNICIAN",
  },
  {
    id: "st-6",
    code: "NV-045",
    name: "Huỳnh Tấn Lộc",
    role: "MOVER",
  },
  {
    id: "st-7",
    code: "NV-051",
    name: "Vũ Thành Đạt",
    role: "DRIVER",
  },
  {
    id: "st-8",
    code: "NV-058",
    name: "Cao Nhật Minh",
    role: "MOVER",
  },
];

/** Lịch nghỉ mẫu, tính theo ngày mở app để demo luôn có xe bảo dưỡng / người nghỉ. */
export const seedDowntimes = (): ResourceDowntime[] => {
  const today = todayLocalISO();
  const mk = (id: string, target: ResourceDowntime["target"], resourceId: string, reason: ResourceDowntime["reason"], from: number, to: number, note: string): ResourceDowntime => ({
    id,
    target,
    resourceId,
    reason,
    from: addDaysISO(today, from),
    to: addDaysISO(today, to),
    note,
    createdAt: Date.now(),
  });
  return [
    mk("dt-1", "VEHICLE", "veh-4", "MAINTENANCE", -1, 2, "Bảo dưỡng định kỳ 20.000 km"),
    mk("dt-2", "STAFF", "st-6", "LEAVE", 0, 1, "Về quê"),
    mk("dt-3", "STAFF", "st-8", "SICK", 0, 0, ""),
  ];
};

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
};

export const PLATE_REGEX = /^\d{2}[A-Z]{1,2}-\d{3}\.\d{2}$/;
