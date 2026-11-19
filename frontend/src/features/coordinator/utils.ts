import { MIN_SURVEY_INPUT, VEHICLE_PRICING } from '@/constants/pricing';
import { FLOOR_OPTIONS, houseTypeOf } from '@/features/customer/booking/constants';
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
 * Gợi ý thời gian cho gói chỉ thuê xe: ~1,5 giờ bốc + dỡ, cộng thời gian chạy (≈25 km/h trong phố),
 * làm tròn lên 0,5 giờ, tối thiểu 2 giờ.
 */
export const suggestTruckHours = (km: number) => Math.max(2, Math.ceil((1.5 + km / 25) * 2) / 2);

/**
 * Điền sẵn biểu mẫu báo giá từ thông tin khách khai báo khi đặt đơn
 * (quãng đường, xe, bốc vác, lầu, hẻm, thùng carton) — Điều phối chỉ cần chỉnh lại.
 */
export const surveyInputFromOrder = (order: Order): SurveyInput => {
  // Đã có kết quả khảo sát → dùng đúng đề xuất của người khảo sát.
  if (order.surveyReport) return structuredClone(order.surveyReport.proposal);
  const d = order.bookingDetails;
  const ref = order.referenceQuote;
  const input: SurveyInput = { ...MIN_SURVEY_INPUT };
  if (ref) input.distanceKm = Math.max(1, Math.round(ref.distanceKm));

  const refCapacity = ref?.breakdownItems
    .filter((b) => !/^Cước km/i.test(b.serviceName))
    .map((b) => parseCapacityTons(b.serviceName))
    .find((c) => c !== null && VEHICLE_PRICING[c]);
  if (refCapacity) input.vehicles = [{ capacity: refCapacity, qty: 1 }];
  // Gói chỉ thuê xe: đúng các xe khách đã chọn (có thể nhiều xe / nhiều loại).
  if (d?.vehicles?.length) input.vehicles = d.vehicles.map((l) => ({ ...l }));

  // Báo giá tham khảo chỉ có một loại thùng (cỡ vừa) — chuyên viên chia lại cỡ khi khảo sát.
  const cartons = ref?.breakdownItems.find((b) => /carton/i.test(b.serviceName));
  if (cartons) input.cartons = { ...input.cartons, M: cartons.quantity };

  if (!d) return input;
  // Gói trọn gói: xe & nhân sự do chuyên viên chốt khi khảo sát — để mức thấp nhất cho điều phối tự tăng.
  if (d.serviceMode === 'TRUCK_ONLY') {
    input.workers = d.laborCount;
    // Không qua khảo sát → gợi ý thời gian theo quãng đường, Điều phối xác nhận lại khi gọi khách.
    input.estimatedHours = suggestTruckHours(input.distanceKm);
  }
  input.floorsFrom = stairFloors(d.from);
  input.floorsTo = stairFloors(d.to);
  input.alleyFrom = alleyMeters(d.from);
  input.alleyTo = alleyMeters(d.to);
  return input;
};
