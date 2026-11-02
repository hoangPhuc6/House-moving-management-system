import type { AssignmentRequirement, Order, StaffRole } from '@/types';

export const parseCapacityTons = (name: string): number | null => {
  const m = /(\d+(?:[.,]\d+)?)\s*tấn/i.exec(name);
  return m?.[1] ? Number(m[1].replace(',', '.')) : null;
};

/** Số xe & nhân viên bốc xếp mà báo giá đã chốt — căn cứ bắt buộc khi điều phối. */
export const getAssignmentRequirement = (order: Order): AssignmentRequirement => {
  const vehicles = order.quoteItems
    .filter((i) => i.category === 'VEHICLE' && i.quantity > 0)
    .map((i) => ({ capacity: parseCapacityTons(i.name), label: i.name, qty: i.quantity }));
  return {
    vehicles,
    vehicleTotal: vehicles.reduce((s, v) => s + v.qty, 0),
    workers: order.quoteItems.filter((i) => i.category === 'LABOR').reduce((s, i) => s + i.quantity, 0),
  };
};

/** Kiểm tra bộ xe/nhân sự đã chọn có khớp báo giá; trả lỗi đầu tiên hoặc null. */
/** Tài xế = vai trò DRIVER; mọi vai trò còn lại thuộc nhóm bốc xếp. */
export const isDriverRole = (role: StaffRole): boolean => role === 'DRIVER';

export const validateAssignment = (req: AssignmentRequirement, vehicleCaps: number[], driverCount: number, moverCount: number): string | null => {
  if (req.vehicleTotal > 0) {
    if (vehicleCaps.length !== req.vehicleTotal) return `Cần chọn đúng ${req.vehicleTotal} xe theo báo giá (đang chọn ${vehicleCaps.length}).`;
    for (const r of req.vehicles) {
      if (r.capacity === null) continue;
      const got = vehicleCaps.filter((c) => c === r.capacity).length;
      if (got !== r.qty) return `Báo giá yêu cầu ${r.qty} × ${r.label} (đang chọn ${got}).`;
    }
  }
  if (req.vehicleTotal > 0 && driverCount !== req.vehicleTotal) return `Cần chọn đúng ${req.vehicleTotal} tài xế — mỗi xe một tài xế (đang chọn ${driverCount}).`;
  if (req.workers > 0 && moverCount !== req.workers) return `Cần chọn đúng ${req.workers} nhân viên bốc xếp theo báo giá (đang chọn ${moverCount}).`;
  return null;
};
