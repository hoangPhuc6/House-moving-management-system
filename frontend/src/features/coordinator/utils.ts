import { MIN_SURVEY_INPUT, VEHICLE_PRICING } from '@/constants/pricing';
import { FLOOR_OPTIONS, HOME_SCALES, houseTypeOf } from '@/features/customer/booking/constants';
import { parseCapacityTons } from '@/services/dispatch';
import type { AddressDetail, Order, SurveyInput } from '@/types';

export const parseShift = (shift: string | null): [number, number] | null => {
  if (!shift) return null;
  const m = shift.match(/(\d{2}):\d{2}\s*-\s*(\d{2}):\d{2}/);
  return m ? [Number(m[1]), Number(m[2])] : null;
};

/** Số lầu phải khiêng thang bộ theo lựa chọn "Tầng" của khách (thang máy / tầng trệt = 0). */
const stairFloors = (a: AddressDetail): number => {
  const idx = FLOOR_OPTIONS.indexOf(a.floor);
  return idx >= 1 && idx <= 3 ? idx : 0;
};

/** Hẻm sâu > 100m: gợi ý 100m mỗi đầu, Điều phối chỉnh lại theo thực tế. */
const alleyMeters = (a: AddressDetail): number => (houseTypeOf(a.propertyType) === 'DEEP_ALLEY' ? 100 : 0);

/**
 * Điền sẵn biểu mẫu báo giá từ thông tin khách khai báo khi đặt đơn
 * (quãng đường, xe, bốc vác, lầu, hẻm, thùng carton) — Điều phối chỉ cần chỉnh lại.
 */
export const surveyInputFromOrder = (order: Order): SurveyInput => {
  const d = order.bookingDetails;
  const ref = order.referenceQuote;
  const input: SurveyInput = { ...MIN_SURVEY_INPUT };
  if (ref) input.distanceKm = Math.max(1, Math.round(ref.distanceKm));

  const refCapacity = ref?.breakdownItems.map((b) => parseCapacityTons(b.serviceName)).find((c) => c !== null && VEHICLE_PRICING[c]);
  if (refCapacity) input.vehicleCapacity = refCapacity;

  const cartons = ref?.breakdownItems.find((b) => /carton/i.test(b.serviceName));
  if (cartons) input.cartons = cartons.quantity;

  if (!d) return input;
  if (d.serviceMode === 'TRUCK_ONLY') {
    input.workers = d.laborCount;
  } else {
    const scale = HOME_SCALES.find((s) => s.label === d.homeScale);
    if (scale) {
      input.vehicleCapacity = scale.vehicleTons;
      input.workers = scale.labor;
    }
  }
  input.floors = stairFloors(d.from) + stairFloors(d.to);
  input.alleyMeters = alleyMeters(d.from) + alleyMeters(d.to);
  return input;
};
