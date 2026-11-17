import type { CartonSize, SurveyInput, VehicleLine } from '@/types';

export const WAIT_FREE_MINUTES = 30;

export const WAIT_FEE_PER_HOUR = 150_000;

export const RESCHEDULE_LOCK_HOURS = 12;

export const DEPOSIT_AMOUNT = 500_000;

export const AUTO_CANCEL_MS = 24 * 60 * 60 * 1000;

export const TELESALES_THRESHOLD_MS = 2 * 60 * 60 * 1000;

/** perTrip: giá chuyến đã gồm INCLUDED_KM đầu; perKm: cước mỗi km vượt; maxLabor: số người tối đa xe chở được. */
export const VEHICLE_PRICING: Record<number, { label: string; perTrip: number; perKm: number; maxLabor: number }> = {
  1.25: { label: 'Xe tải 1.25 tấn', perTrip: 650_000, perKm: 12_000, maxLabor: 2 },
  2: { label: 'Xe tải 2 tấn', perTrip: 900_000, perKm: 15_000, maxLabor: 4 },
  5: { label: 'Xe tải 5 tấn', perTrip: 1_600_000, perKm: 22_000, maxLabor: 6 },
};

/** Đơn giá 1 nhân viên bốc vác / bốc xếp — không phụ thuộc loại xe. */
export const LABOR_PER_PERSON = 350_000;

/** Giá trị khởi tạo thấp nhất cho biểu mẫu báo giá — người lập báo giá tự tăng theo thực tế. */
export const MIN_SURVEY_INPUT: SurveyInput = {
  distanceKm: 1,
  vehicles: [{ capacity: Math.min(...Object.keys(VEHICLE_PRICING).map(Number)), qty: 1 }],
  workers: 0,
  cartons: { S: 0, M: 0, L: 0 },
  tapeRolls: 0,
  peFilmRolls: 0,
  floors: 0,
  alleyMeters: 0,
  estimatedHours: 0,
};

/** Các tải trọng xe có trong bảng giá, nhỏ → lớn. */
export const VEHICLE_CAPACITIES = Object.keys(VEHICLE_PRICING)
  .map(Number)
  .sort((a, b) => a - b);

/** "xe 1,25 tấn" */
export const capacityLabel = (cap: number) => `xe ${String(cap).replace('.', ',')} tấn`;

/** "1 × xe 5 tấn + 1 × xe 1,25 tấn" — bỏ loại có số lượng 0, lớn trước. */
export const vehicleLinesLabel = (lines: VehicleLine[]) =>
  lines
    .filter((l) => l.qty > 0)
    .sort((a, b) => b.capacity - a.capacity)
    .map((l) => `${l.qty} × ${capacityLabel(l.capacity)}`)
    .join(' + ') || 'chưa chọn xe';

export const totalVehicles = (lines: VehicleLine[]) => lines.reduce((n, l) => n + l.qty, 0);

/** Đổi số lượng một loại xe; về 0 thì bỏ khỏi danh sách. */
export const setVehicleQty = (lines: VehicleLine[], capacity: number, qty: number): VehicleLine[] => {
  const rest = lines.filter((l) => l.capacity !== capacity);
  return (qty > 0 ? [...rest, { capacity, qty }] : rest).sort((a, b) => a.capacity - b.capacity);
};

export const qtyOf = (lines: VehicleLine[], capacity: number) => lines.find((l) => l.capacity === capacity)?.qty ?? 0;

/** Số người tối đa các xe đã chọn chở được (gói chỉ thuê xe — bốc vác đi cùng xe). */
export const maxLaborOf = (lines: VehicleLine[]) => lines.reduce((n, l) => n + (VEHICLE_PRICING[l.capacity]?.maxLabor ?? 0) * l.qty, 0);

/**
 * Cỡ thùng carton hay dùng khi chuyển nhà: thùng nhỏ cho đồ nặng (sách, chén bát) để không quá tải,
 * thùng lớn cho đồ nhẹ cồng kềnh (quần áo, chăn gối).
 */
export const CARTON_SIZES: Record<CartonSize, { label: string; dims: string; use: string; price: number }> = {
  S: { label: 'Thùng nhỏ', dims: '40x30x30', use: 'sách, chén bát, đồ nặng', price: 18_000 },
  M: { label: 'Thùng vừa', dims: '60x40x40', use: 'đồ dùng chung', price: 25_000 },
  L: { label: 'Thùng lớn', dims: '60x50x50', use: 'quần áo, chăn gối, đồ nhẹ', price: 32_000 },
};
export const CARTON_ORDER: CartonSize[] = ['S', 'M', 'L'];
export const totalCartons = (c: Record<CartonSize, number>) => c.S + c.M + c.L;

/** Số km đã gồm trong giá xe. */
export const INCLUDED_KM = 10;

/** Giá 1 chuyến của xe cho quãng đường `km`: giá chuyến (gồm INCLUDED_KM đầu) + km vượt × cước km của chính xe đó. */
export const vehicleTripPrice = (v: { perTrip: number; perKm: number }, km: number) => {
  const extraKm = Math.max(0, Math.ceil(km - INCLUDED_KM));
  return { extraKm, total: v.perTrip + extraKm * v.perKm };
};

export const REFERENCE_DISCLAIMER = 'Bảng giá mang tính chất tham khảo dự kiến. Phụ phí (lầu, hẻm, lối đi…) và báo giá chính thức sẽ được xác nhận sau khi hoàn tất khảo sát.';
