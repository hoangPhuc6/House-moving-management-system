import {
  DEPOSIT_AMOUNT,
  INCLUDED_KM,
  REFERENCE_DISCLAIMER,
  VEHICLE_PRICING,
} from '@/constants/pricing';
import { parseCapacityTons } from '@/services/dispatch';
import type {
  BookingItem,
  ReferenceBreakdownItem,
  ReferenceInput,
  ReferenceQuote,
  ServiceItem,
} from '@/types';

/** Giả lập quãng đường từ hai địa chỉ (4–28 km) — thay bằng dịch vụ bản đồ khi có backend. */
export const estimateDistanceKm = (from: string, to: string): number => {
  let h = 0;
  for (const c of `${from.trim().toLowerCase()}|${to.trim().toLowerCase()}`) h = (h * 31 + c.charCodeAt(0)) >>> 0;
  return 4 + (h % 25);
};

export const pickCapacity = (items: BookingItem[], requested: ServiceItem[]): number => {
  const chosen = requested.find((s) => s.group === 'TRANSPORT');
  const fromService = chosen ? parseCapacityTons(chosen.name) : null;
  if (fromService !== null && VEHICLE_PRICING[fromService]) return fromService;
  const volume = items.reduce((s, i) => s + (i.volumeM3 ?? 0.5) * i.quantity, 0);
  return volume <= 4 ? 1.25 : volume <= 10 ? 2 : 5;
};

/** Mô phỏng báo giá tham khảo mà backend trả về sau khi tạo đơn. */
export const buildReferenceQuote = (input: ReferenceInput, catalog: ServiceItem[]): ReferenceQuote => {
  const requested = catalog.filter((s) => input.requestedServices.includes(s.name));
  const distanceKm = estimateDistanceKm(input.fromAddress, input.toAddress);
  const capacity = input.vehicleTons && VEHICLE_PRICING[input.vehicleTons] ? input.vehicleTons : pickCapacity(input.items, requested);
  const vehicle = VEHICLE_PRICING[capacity] ?? VEHICLE_PRICING[2]!;
  const breakdown: ReferenceBreakdownItem[] = [];
  const line = (serviceName: string, quantity: number, unitPrice: number, note: string | null, autoApplied: boolean) =>
    breakdown.push({ serviceName, quantity, unitPrice, totalPrice: unitPrice * quantity, note, autoApplied });

  // Vận chuyển: giá xe đã gồm INCLUDED_KM đầu tiên, phần vượt tính theo cước km của xe.
  line(vehicle.label, 1, vehicle.perTrip, `Đã gồm ${INCLUDED_KM} km đầu tiên`, false);
  const chargeableExtraKm = Math.max(0, Math.ceil(distanceKm - INCLUDED_KM));
  if (chargeableExtraKm > 0) line('Cước km vượt', chargeableExtraKm, vehicle.perKm, `Quãng đường ước tính ${distanceKm} km, vượt ${chargeableExtraKm} km so với ${INCLUDED_KM} km đã gồm`, true);
  const transportFee = vehicle.perTrip + chargeableExtraKm * vehicle.perKm;

  // Dịch vụ khách chọn (trừ xe — đã tính ở trên).
  const totalItems = input.items.reduce((s, i) => s + i.quantity, 0);
  let serviceFee = 0;
  requested
    .filter((s) => s.group !== 'TRANSPORT')
    .forEach((s) => {
      let qty = 1;
      let unitPrice = s.basePrice;
      let note: string | null = null;
      if (s.group === 'LABOR' && input.laborQuantity !== undefined) {
        qty = input.laborQuantity;
        unitPrice = vehicle.laborPerPerson;
        note = input.vehicleTons ? 'Ước lượng theo quy mô nhà, chốt sau khảo sát' : `Số người bạn chọn, đơn giá theo ${vehicle.label}`;
      } else if (s.group === 'LABOR') {
        qty = Math.max(2, Math.ceil(totalItems / 4));
        unitPrice = vehicle.laborPerPerson;
        note = 'Số lượng ước tính theo khối lượng khai báo';
      } else if (/carton/i.test(s.name)) {
        qty = Math.max(1, input.items.filter((i) => /carton|thùng/i.test(i.name)).reduce((sum, i) => sum + i.quantity, 0) || 10);
        note = 'Số lượng theo đồ đạc đã khai báo';
      }
      line(s.name, qty, unitPrice, note, false);
      serviceFee += unitPrice * qty;
    });

  // Phụ phí (lầu, hẻm…) phụ thuộc thực địa — chỉ tính trong báo giá chính thức sau khảo sát.
  const surchargeFee = 0;

  const subtotal = transportFee + serviceFee;
  const discountAmount = 0;
  return {
    currency: 'VND',
    distanceKm,
    includedKm: INCLUDED_KM,
    chargeableExtraKm,
    disclaimer: REFERENCE_DISCLAIMER,
    financialSummary: { subtotal, discountAmount, totalEstimated: subtotal - discountAmount, depositRequired: DEPOSIT_AMOUNT },
    categoryFees: { transportFee, serviceFee, surchargeFee },
    breakdownItems: breakdown,
    appliedVoucher: null,
  };
};
