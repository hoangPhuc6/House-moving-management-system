import type { SurveyInput } from '@/types';

export const WAIT_FREE_MINUTES = 30;

export const WAIT_FEE_PER_HOUR = 150_000;

export const RESCHEDULE_LOCK_HOURS = 12;

export const DEPOSIT_AMOUNT = 500_000;

export const AUTO_CANCEL_MS = 24 * 60 * 60 * 1000;

export const TELESALES_THRESHOLD_MS = 2 * 60 * 60 * 1000;

/** laborPerPerson: đơn giá 1 nhân viên bốc vác đi theo xe; maxLabor: số người tối đa xe chở được. */
export const VEHICLE_PRICING: Record<number, { label: string; perTrip: number; perKm: number; laborPerPerson: number; maxLabor: number }> = {
  1.25: { label: 'Xe tải 1.25 tấn', perTrip: 650_000, perKm: 12_000, laborPerPerson: 300_000, maxLabor: 2 },
  2: { label: 'Xe tải 2 tấn', perTrip: 900_000, perKm: 15_000, laborPerPerson: 350_000, maxLabor: 4 },
  5: { label: 'Xe tải 5 tấn', perTrip: 1_600_000, perKm: 22_000, laborPerPerson: 400_000, maxLabor: 6 },
};

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

export const REFERENCE_DISCLAIMER = 'Bảng giá mang tính chất tham khảo dự kiến. Phụ phí (lầu, hẻm, lối đi…) và báo giá chính thức sẽ được xác nhận sau khi hoàn tất khảo sát.';
