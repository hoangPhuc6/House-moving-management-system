import { CUSTOMER } from '@/features/customer/constants';
import { computeTotals } from '@/services/pricing';
import type { Order } from '@/types';

export const isOpenOrder = (o: Order): boolean =>
  o.status !== "COMPLETED" && o.status !== "CANCELLED_EXPIRED";

/** Đơn đang chờ khách thao tác: chốt báo giá, xác nhận phát sinh hoặc thanh toán. */
export const needsAction = (o: Order): boolean =>
  o.status === "SURVEYED_PENDING_CONFIRM" ||
  o.flags.hasFieldDiscrepancyLock ||
  (o.status === "PENDING_PAYMENT_OR_CLAIM" && computeTotals(o).remaining > 0);

export const scrollToSection = (id: string) =>
  document
    .getElementById(id)
    ?.scrollIntoView({ behavior: "smooth", block: "start" });

/** Đơn của khách đang đăng nhập — kèm đơn đang chọn trên DevToolbar để demo các trạng thái. */
export const selectMyOrders = (orders: Order[], activeOrderId: string): Order[] =>
  orders
    .filter((o) => o.phone === CUSTOMER.phone || o.id === activeOrderId)
    .sort((a, b) => b.createdAt - a.createdAt);
