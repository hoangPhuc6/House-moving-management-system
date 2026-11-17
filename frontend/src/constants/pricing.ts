import type { SurveyInput } from '@/types';

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
  vehicleCapacity: Math.min(...Object.keys(VEHICLE_PRICING).map(Number)),
  vehicleQty: 1,
  workers: 0,
  cartons: 0,
  tapeRolls: 0,
  peFilmRolls: 0,
  floors: 0,
  alleyMeters: 0,
};

/** Số km đã gồm trong giá xe. */
export const INCLUDED_KM = 10;

/** Giá 1 chuyến của xe cho quãng đường `km`: giá chuyến (gồm INCLUDED_KM đầu) + km vượt × cước km của chính xe đó. */
export const vehicleTripPrice = (v: { perTrip: number; perKm: number }, km: number) => {
  const extraKm = Math.max(0, Math.ceil(km - INCLUDED_KM));
  return { extraKm, total: v.perTrip + extraKm * v.perKm };
};

export const REFERENCE_DISCLAIMER = 'Bảng giá mang tính chất tham khảo dự kiến. Phụ phí (lầu, hẻm, lối đi…) và báo giá chính thức sẽ được xác nhận sau khi hoàn tất khảo sát.';
