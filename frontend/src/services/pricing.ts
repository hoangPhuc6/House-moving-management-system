import { INCLUDED_KM, VEHICLE_PRICING, WAIT_FEE_PER_HOUR, WAIT_FREE_MINUTES } from '@/constants/pricing';
import { formatVND, uid } from '@/services/format';
import type { Order, OrderTotals, QuoteItem, SettlementLockState, SurveyInput } from '@/types';

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
  isWarehouseReturned: order.isWarehouseReturned,
});

/**
 * Khách được khiếu nại sau khi đã bàn giao VÀ thanh toán đủ (công nợ = 0đ)
 * — không cần chờ Admin thu hồi dụng cụ & đóng hồ sơ.
 */
export const canFileClaim = (order: Order): boolean =>
  (order.status === 'PENDING_PAYMENT_OR_CLAIM' || order.status === 'COMPLETED') && computeTotals(order).remaining === 0;

export const canCloseOrder =(s: SettlementLockState): boolean => s.remainingBalance === 0 && s.isWarehouseReturned;

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
  const vehicleNote =
    extraKm > 0
      ? `Giá chuyến ${formatVND(v.perTrip)} (gồm ${INCLUDED_KM} km) + ${extraKm} km vượt × ${formatVND(v.perKm)} — quãng đường ${s.distanceKm} km`
      : `Giá chuyến ${formatVND(v.perTrip)}, đã gồm ${INCLUDED_KM} km đầu (quãng đường ${s.distanceKm} km)`;
  const segments = Math.ceil(s.alleyMeters / p.alleySegmentMeters);
  const items: QuoteItem[] = [
    { id: uid('qi'), name: v.label, category: 'VEHICLE', unit: 'chuyến', unitPrice: v.perTrip + v.perKm * extraKm, quantity: s.vehicleQty, note: vehicleNote },
    { id: uid('qi'), name: 'Nhân viên bốc xếp', category: 'LABOR', unit: 'người', unitPrice: v.laborPerPerson, quantity: s.workers, note: `Đơn giá bốc xếp đi theo ${v.label}` },
    { id: uid('qi'), name: 'Thùng carton 60x40x40', category: 'MATERIAL', unit: 'thùng', unitPrice: p.carton, quantity: s.cartons, note: 'Vật tư — đơn giá cố định' },
    { id: uid('qi'), name: 'Băng keo đóng gói', category: 'MATERIAL', unit: 'cuộn', unitPrice: p.tape, quantity: s.tapeRolls, note: 'Vật tư — đơn giá cố định' },
    { id: uid('qi'), name: 'Màng PE bọc đồ', category: 'MATERIAL', unit: 'cuộn', unitPrice: p.peFilm, quantity: s.peFilmRolls, note: 'Vật tư — đơn giá cố định' },
  ];
  if (s.floors > 0) {
    items.push({ id: uid('qi'), name: 'Phụ phí lầu (không thang máy)', category: 'SURCHARGE', unit: 'lầu', unitPrice: p.perFloor, quantity: s.floors, note: `Khiêng thang bộ ${s.floors} lầu (tổng 2 đầu) × ${formatVND(p.perFloor)}/lầu` });
  }
  if (s.alleyMeters > 0) {
    items.push({ id: uid('qi'), name: 'Phụ phí hẻm (trung chuyển)', category: 'SURCHARGE', unit: 'đoạn 50m', unitPrice: p.perAlleySegment, quantity: segments, note: `Hẻm ${s.alleyMeters} m ÷ ${p.alleySegmentMeters} m = ${segments} đoạn (làm tròn lên) × ${formatVND(p.perAlleySegment)}` });
  }
  return items.filter((i) => i.quantity > 0);
};
