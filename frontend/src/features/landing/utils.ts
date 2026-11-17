import { LABOR_PER_PERSON, VEHICLE_PRICING, vehicleTripPrice } from '@/constants/pricing';

/** Gói tham chiếu cho báo giá nhanh: xe 2 tấn, 4 nhân công, 20 thùng carton. */
const QUICK_VEHICLE = VEHICLE_PRICING[2]!;
const QUICK_LABOR = 4;
const QUICK_CARTONS = 20;
const CARTON_PRICE = 25_000;

/**
 * Tạm tính chỉ dựa trên quãng đường — phụ phí tầng lầu, hẻm, lối đi
 * được chốt sau khảo sát nên không đưa vào đây.
 */
export const estimate = (km: number) => {
  const base =
    vehicleTripPrice(QUICK_VEHICLE, km).total +
    QUICK_LABOR * LABOR_PER_PERSON +
    QUICK_CARTONS * CARTON_PRICE;
  return {
    km,
    vehicleLabel: QUICK_VEHICLE.label,
    labor: QUICK_LABOR,
    cartons: QUICK_CARTONS,
    min: Math.round((base * 0.92) / 10_000) * 10_000,
    max: Math.round((base * 1.12) / 10_000) * 10_000,
  };
};
