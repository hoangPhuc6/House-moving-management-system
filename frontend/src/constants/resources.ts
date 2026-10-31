import type { StaffMember, StaffRole, TimeSlot, ToastTone, Vehicle, VehicleStatus } from '@/types';

export const TIME_SLOTS: TimeSlot[] = [
  { id: 's1', label: '07:00 - 09:00' },
  { id: 's2', label: '09:00 - 11:00' },
  { id: 's3', label: '13:00 - 15:00' },
  { id: 's4', label: '15:00 - 17:00' },
  { id: 's5', label: '18:00 - 20:00' },
];

export const seedVehicles = (): Vehicle[] => [
  { id: 'veh-1', plateNumber: '51C-889.92', capacityTons: 2, status: 'AVAILABLE', currentShiftTime: null, busyOrderId: null },
  { id: 'veh-2', plateNumber: '51C-123.45', capacityTons: 2, status: 'AVAILABLE', currentShiftTime: null, busyOrderId: null },
  { id: 'veh-3', plateNumber: '51D-456.78', capacityTons: 5, status: 'AVAILABLE', currentShiftTime: null, busyOrderId: null },
  { id: 'veh-4', plateNumber: '51C-777.01', capacityTons: 1.25, status: 'MAINTENANCE', currentShiftTime: null, busyOrderId: null },
  { id: 'veh-5', plateNumber: '51D-212.36', capacityTons: 2, status: 'AVAILABLE', currentShiftTime: null, busyOrderId: null },
];

export const seedStaff = (): StaffMember[] => [
  { id: 'st-1', code: 'NV-014', name: 'Trần Văn Hùng', role: 'TEAM_LEADER', isAvailable: true },
  { id: 'st-2', code: 'NV-021', name: 'Lê Hoàng Phúc', role: 'DRIVER', isAvailable: true },
  { id: 'st-3', code: 'NV-033', name: 'Nguyễn Đức Thịnh', role: 'MOVER', isAvailable: true },
  { id: 'st-4', code: 'NV-037', name: 'Phan Minh Khôi', role: 'MOVER', isAvailable: true },
  { id: 'st-5', code: 'NV-042', name: 'Đỗ Quang Vinh', role: 'TECHNICIAN', isAvailable: true },
  { id: 'st-6', code: 'NV-045', name: 'Huỳnh Tấn Lộc', role: 'MOVER', isAvailable: false },
  { id: 'st-7', code: 'NV-051', name: 'Vũ Thành Đạt', role: 'DRIVER', isAvailable: true },
  { id: 'st-8', code: 'NV-058', name: 'Cao Nhật Minh', role: 'MOVER', isAvailable: false },
];

export const STAFF_ROLE_LABEL: Record<StaffRole, string> = {
  TEAM_LEADER: 'Trưởng nhóm',
  DRIVER: 'Tài xế',
  MOVER: 'Bốc xếp',
  TECHNICIAN: 'Kỹ thuật tháo lắp',
};

export const VEHICLE_STATUS_META: Record<VehicleStatus, { label: string; tone: ToastTone }> = {
  AVAILABLE: { label: 'Sẵn sàng', tone: 'success' },
  BUSY: { label: 'Đang kẹt ca', tone: 'danger' },
  MAINTENANCE: { label: 'Bảo dưỡng', tone: 'warning' },
};

export const PLATE_REGEX = /^\d{2}[A-Z]{1,2}-\d{3}\.\d{2}$/;
