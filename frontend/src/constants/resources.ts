import type {
  StaffMember,
  TimeSlot,
  ToastTone,
  Vehicle,
  VehicleStatus,
} from "@/types";

export const TIME_SLOTS: TimeSlot[] = [
  { id: "s1", label: "07:00 - 09:00" },
  { id: "s2", label: "09:00 - 11:00" },
  { id: "s3", label: "13:00 - 15:00" },
  { id: "s4", label: "15:00 - 17:00" },
  { id: "s5", label: "18:00 - 20:00" },
];

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

/** Ngày ISO cách hôm nay `days` ngày — hạn bằng lái mẫu luôn tương đối với ngày chạy demo. */
const inDays = (days: number): string => new Date(Date.now() + days * 86_400_000).toISOString().slice(0, 10);

/**
 * Hồ sơ nhân viên mẫu (gắn tài khoản u-2, u-6…u-13 trong constants/catalog.ts).
 * Tình huống demo: st-7 bằng sắp hết hạn, st-2 chỉ có B2 (không lái xe 5 tấn), st-6 / st-8 nghỉ phép, st-9 nghỉ việc.
 */
export const seedStaff = (): StaffMember[] => [
  { id: 'st-1', code: 'NV-014', name: 'Trần Văn Hùng', phone: '0903 111 222', userId: 'u-2', skills: ['DRIVE', 'LOAD'], license: { licenseClass: 'C', expiresAt: inDays(900) }, status: 'ACTIVE' },
  { id: 'st-2', code: 'NV-021', name: 'Lê Hoàng Phúc', phone: '0909 210 021', userId: 'u-7', skills: ['DRIVE', 'LOAD'], license: { licenseClass: 'B2', expiresAt: inDays(420) }, status: 'ACTIVE' },
  { id: 'st-3', code: 'NV-033', name: 'Nguyễn Đức Thịnh', phone: '0909 330 033', userId: 'u-8', skills: ['LOAD'], license: null, status: 'ACTIVE' },
  { id: 'st-4', code: 'NV-037', name: 'Phan Minh Khôi', phone: '0909 370 037', userId: 'u-9', skills: ['LOAD'], license: null, status: 'ACTIVE' },
  { id: 'st-5', code: 'NV-042', name: 'Đỗ Quang Vinh', phone: '0909 420 042', userId: 'u-10', skills: ['ASSEMBLY', 'LOAD'], license: null, status: 'ACTIVE' },
  { id: 'st-6', code: 'NV-045', name: 'Huỳnh Tấn Lộc', phone: '0909 450 045', userId: 'u-11', skills: ['LOAD'], license: null, status: 'ON_LEAVE' },
  { id: 'st-7', code: 'NV-051', name: 'Vũ Thành Đạt', phone: '0909 510 051', userId: 'u-12', skills: ['DRIVE'], license: { licenseClass: 'C', expiresAt: inDays(20) }, status: 'ACTIVE' },
  { id: 'st-8', code: 'NV-058', name: 'Cao Nhật Minh', phone: '0909 580 058', userId: 'u-13', skills: ['LOAD', 'ASSEMBLY'], license: null, status: 'ON_LEAVE' },
  { id: 'st-9', code: 'NV-009', name: 'Đặng Hoài Nam', phone: '0977 555 444', userId: 'u-6', skills: ['DRIVE', 'LOAD'], license: { licenseClass: 'B2', expiresAt: inDays(-40) }, status: 'RESIGNED' },
];

export const VEHICLE_STATUS_META: Record<
  VehicleStatus,
  { label: string; tone: ToastTone }
> = {
  AVAILABLE: { label: "Sẵn sàng", tone: "success" },
  BUSY: { label: "Đang kẹt ca", tone: "danger" },
  MAINTENANCE: { label: "Bảo dưỡng", tone: "warning" },
};

export const PLATE_REGEX = /^\d{2}[A-Z]{1,2}-\d{3}\.\d{2}$/;
