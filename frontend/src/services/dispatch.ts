import type { AssignmentRequirement, Order } from '@/types';

/** Dịch vụ tháo lắp (máy lạnh, tủ, giường…) trong yêu cầu của khách hoặc báo giá. */
const ASSEMBLY_PATTERN = /tháo lắp/i;

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
    needsAssembly: [...order.requestedServices, ...order.quoteItems.map((i) => i.name)].some((n) => ASSEMBLY_PATTERN.test(n)),
  };
};

/** Kiểm tra bộ XE đã chọn có khớp báo giá; trả lỗi đầu tiên hoặc null. Đội người kiểm ở validateCrew (services/staff). */
export const validateAssignment = (req: AssignmentRequirement, vehicleCaps: number[]): string | null => {
  if (req.vehicleTotal > 0) {
    if (vehicleCaps.length !== req.vehicleTotal) return `Cần chọn đúng ${req.vehicleTotal} xe theo báo giá (đang chọn ${vehicleCaps.length}).`;
    for (const r of req.vehicles) {
      if (r.capacity === null) continue;
      const got = vehicleCaps.filter((c) => c === r.capacity).length;
      if (got !== r.qty) return `Báo giá yêu cầu ${r.qty} × ${r.label} (đang chọn ${got}).`;
    }
  }
  return null;
};
