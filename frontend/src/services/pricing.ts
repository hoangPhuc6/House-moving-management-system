import { VEHICLE_PRICING, WAIT_FEE_PER_HOUR, WAIT_FREE_MINUTES } from '@/constants/pricing';
import { uid } from '@/services/format';
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

export const buildSurveyQuote = (s: SurveyInput): QuoteItem[] => {
  const v = VEHICLE_PRICING[s.vehicleCapacity] ?? VEHICLE_PRICING[2]!;
  const items: QuoteItem[] = [
    { id: uid('qi'), name: v.label, category: 'VEHICLE', unit: 'chuyến', unitPrice: v.perTrip + v.perKm * s.distanceKm, quantity: s.vehicleQty },
    { id: uid('qi'), name: 'Nhân viên bốc xếp', category: 'LABOR', unit: 'người', unitPrice: 350_000, quantity: s.workers },
    { id: uid('qi'), name: 'Thùng carton 60x40x40', category: 'MATERIAL', unit: 'thùng', unitPrice: 25_000, quantity: s.cartons },
    { id: uid('qi'), name: 'Băng keo đóng gói', category: 'MATERIAL', unit: 'cuộn', unitPrice: 15_000, quantity: s.tapeRolls },
    { id: uid('qi'), name: 'Màng PE bọc đồ', category: 'MATERIAL', unit: 'cuộn', unitPrice: 120_000, quantity: s.peFilmRolls },
  ];
  if (s.floors > 0) {
    items.push({ id: uid('qi'), name: 'Phụ phí lầu (không thang máy)', category: 'SURCHARGE', unit: 'lầu', unitPrice: 100_000, quantity: s.floors });
  }
  if (s.alleyMeters > 0) {
    items.push({ id: uid('qi'), name: 'Phụ phí hẻm (trung chuyển)', category: 'SURCHARGE', unit: 'đoạn 50m', unitPrice: 80_000, quantity: Math.ceil(s.alleyMeters / 50) });
  }
  return items.filter((i) => i.quantity > 0);
};
