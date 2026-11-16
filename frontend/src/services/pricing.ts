import { INCLUDED_KM, VEHICLE_PRICING, WAIT_FEE_PER_HOUR, WAIT_FREE_MINUTES } from '@/constants/pricing';
import { formatVND, uid } from '@/services/format';
import { isMaterialsSettled } from '@/services/materials';
import type { Order, OrderTotals, QuoteItem, ServiceItem, SettlementLockState, SurveyInput } from '@/types';

export const lineTotal = (item: QuoteItem): number => item.unitPrice * item.quantity;

export const quoteSubtotal = (items: QuoteItem[]): number => items.reduce((sum, i) => sum + lineTotal(i), 0);

/** Mã 1.1.4 — 30 phút đầu 0đ, sau đó tính tròn theo giờ bắt đầu (+150.000đ/giờ). */
export const waitFee = (waitingMinutes: number): number => {
  if (waitingMinutes <= WAIT_FREE_MINUTES) return 0;
  return Math.ceil((waitingMinutes - WAIT_FREE_MINUTES) / 60) * WAIT_FEE_PER_HOUR;
};

export const billableWaitHours = (waitingMinutes: number): number =>
  waitingMinutes <= WAIT_FREE_MINUTES ? 0 : Math.ceil((waitingMinutes - WAIT_FREE_MINUTES) / 60);

export const computeTotals = (order: Order): OrderTotals => {
  const baseItems = order.quoteItems.filter((i) => i.category !== 'FIELD_ADJUSTMENT');
  const adjustments = order.quoteItems.filter((i) => i.category === 'FIELD_ADJUSTMENT');
  const baseQuote = quoteSubtotal(baseItems);
  const fieldAdjustments = quoteSubtotal(adjustments);
  const fee = waitFee(order.flags.waitingMinutes);
  const discount = Math.round(((baseQuote + fieldAdjustments) * order.discountPercent) / 100);
  const grandTotal = baseQuote + fieldAdjustments - discount + fee;
  const paid = order.depositPaid + order.balancePaid;
  return {
    baseQuote,
    fieldAdjustments,
    discount,
    waitFee: fee,
    grandTotal,
    deposit: order.depositPaid,
    paid,
    remaining: Math.max(0, grandTotal - paid),
  };
};

export const selectSettlement = (order: Order): SettlementLockState => ({
  remainingBalance: computeTotals(order).remaining,
  materialsSettled: isMaterialsSettled(order.materials),
});

/**
 * Khách được khiếu nại sau khi đã bàn giao VÀ thanh toán đủ (công nợ = 0đ)
 * — không cần chờ Admin thu hồi dụng cụ & đóng hồ sơ.
 */
export const canFileClaim = (order: Order): boolean =>
  (order.status === 'PENDING_PAYMENT_OR_CLAIM' || order.status === 'COMPLETED') && computeTotals(order).remaining === 0;

export const canCloseOrder = (s: SettlementLockState): boolean => s.remainingBalance === 0 && s.materialsSettled;

/** Đơn giá vật tư & phụ phí trong báo giá chính thức. */
export const SURVEY_UNIT_PRICES = {
  carton: 25_000,
  tape: 15_000,
  peFilm: 120_000,
  perFloor: 100_000,
  perAlleySegment: 80_000,
  alleySegmentMeters: 50,
} as const;

/**
 * Báo giá chính thức từ số liệu khảo sát. Giá xe tính giống báo giá tham khảo:
 * giá chuyến đã gồm INCLUDED_KM đầu, phần vượt tính theo cước km của xe.
 */
export const buildSurveyQuote = (s: SurveyInput): QuoteItem[] => {
  const v = VEHICLE_PRICING[s.vehicleCapacity] ?? VEHICLE_PRICING[2]!;
  const p = SURVEY_UNIT_PRICES;
  const extraKm = Math.max(0, Math.ceil(s.distanceKm - INCLUDED_KM));
  // Gộp xe cùng tải trọng thành một dòng — Điều phối gán xe đối chiếu số xe theo từng tải trọng.
  const fleet = new Map<number, number>();
  [{ capacity: s.vehicleCapacity, qty: s.vehicleQty }, ...(s.extraVehicles ?? [])].forEach((x) => {
    if (VEHICLE_PRICING[x.capacity] && x.qty > 0) fleet.set(x.capacity, (fleet.get(x.capacity) ?? 0) + x.qty);
  });
  const vehicleItems: QuoteItem[] = [...fleet.entries()].map(([cap, qty]) => {
    const vp = VEHICLE_PRICING[cap]!;
    const note =
      extraKm > 0
        ? `Giá chuyến ${formatVND(vp.perTrip)} (gồm ${INCLUDED_KM} km) + ${extraKm} km vượt × ${formatVND(vp.perKm)} — quãng đường ${s.distanceKm} km`
        : `Giá chuyến ${formatVND(vp.perTrip)}, đã gồm ${INCLUDED_KM} km đầu (quãng đường ${s.distanceKm} km)`;
    return { id: uid('qi'), name: vp.label, category: 'VEHICLE', unit: 'chuyến', unitPrice: vp.perTrip + vp.perKm * extraKm, quantity: qty, note };
  });
  const items: QuoteItem[] = [
    ...vehicleItems,
    { id: uid('qi'), name: 'Nhân viên bốc xếp', category: 'LABOR', unit: 'người', unitPrice: v.laborPerPerson, quantity: s.workers, note: `Đơn giá bốc xếp đi theo ${v.label}` },
    { id: uid('qi'), name: 'Thùng carton 60x40x40', category: 'MATERIAL', unit: 'thùng', unitPrice: p.carton, quantity: s.cartons, note: 'Vật tư — đơn giá cố định' },
    { id: uid('qi'), name: 'Băng keo đóng gói', category: 'MATERIAL', unit: 'cuộn', unitPrice: p.tape, quantity: s.tapeRolls, note: 'Vật tư — đơn giá cố định' },
    { id: uid('qi'), name: 'Màng PE bọc đồ', category: 'MATERIAL', unit: 'cuộn', unitPrice: p.peFilm, quantity: s.peFilmRolls, note: 'Vật tư — đơn giá cố định' },
  ];
  // Lầu & hẻm tính riêng từng đầu (điểm đi / điểm đến) — hẻm làm tròn đoạn 50m theo từng đầu.
  const ends = [
    { label: 'điểm đi', floors: s.floorsFrom, alley: s.alleyFrom },
    { label: 'điểm đến', floors: s.floorsTo, alley: s.alleyTo },
  ];
  for (const e of ends) {
    if (e.floors > 0) {
      items.push({ id: uid('qi'), name: `Phụ phí lầu — ${e.label}`, category: 'SURCHARGE', unit: 'lầu', unitPrice: p.perFloor, quantity: e.floors, note: `Khiêng thang bộ ${e.floors} lầu ở ${e.label} × ${formatVND(p.perFloor)}/lầu` });
    }
  }
  for (const e of ends) {
    if (e.alley > 0) {
      const seg = Math.ceil(e.alley / p.alleySegmentMeters);
      items.push({ id: uid('qi'), name: `Phụ phí hẻm — ${e.label}`, category: 'SURCHARGE', unit: 'đoạn 50m', unitPrice: p.perAlleySegment, quantity: seg, note: `Hẻm ${e.alley} m ở ${e.label} ÷ ${p.alleySegmentMeters} m = ${seg} đoạn (làm tròn lên) × ${formatVND(p.perAlleySegment)}` });
    }
  }
  return items.filter((i) => i.quantity > 0);
};

/** Tên các dòng báo giá tự tính từ số liệu khảo sát — dịch vụ bảng giá trùng tên không cho thêm tay (tránh tính 2 lần). */
const AUTO_LINE_NAMES = ['Thùng carton 60x40x40', 'Băng keo đóng gói', 'Màng PE bọc đồ'];

/** Nhóm dịch vụ được thêm tay vào báo giá: tháo lắp & đóng gói. Xe / bốc xếp / lầu / hẻm đã có ô số liệu riêng. */
export const QUOTE_EXTRA_GROUPS: ServiceItem['group'][] = ['ASSEMBLY', 'PACKING'];

export const isQuoteExtraService = (s: ServiceItem): boolean =>
  s.active && QUOTE_EXTRA_GROUPS.includes(s.group) && !AUTO_LINE_NAMES.includes(s.name);

/** Dòng báo giá từ dịch vụ trong bảng giá — đơn giá theo bảng giá, không sửa tay. */
export const quoteItemFromService = (s: ServiceItem, quantity: number, note?: string): QuoteItem => ({
  id: uid('qi'),
  name: s.name,
  category: s.group === 'PACKING' ? 'MATERIAL' : 'SERVICE',
  unit: s.unit,
  unitPrice: s.basePrice,
  quantity,
  note: note ?? 'Theo bảng giá dịch vụ',
});

/** Đội xe theo số liệu khảo sát: "2 × 2 tấn + 1 × 1,25 tấn". */
export const fleetText = (s: SurveyInput): string =>
  [{ capacity: s.vehicleCapacity, qty: s.vehicleQty }, ...(s.extraVehicles ?? [])]
    .filter((v) => v.qty > 0)
    .map((v) => `${v.qty} × ${String(v.capacity).replace('.', ',')} tấn`)
    .join(' + ');
