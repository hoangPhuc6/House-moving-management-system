import { AlertTriangle, type LucideIcon, QrCode, ReceiptText, Star } from 'lucide-react';
import type { Tone } from '@/components/ui';
import { CUSTOMER } from '@/features/customer/constants';
import { isMediaSubmitted, surveyStageOf } from '@/features/order/surveyStage';
import { formatVND } from '@/services/format';
import { computeTotals } from '@/services/pricing';
import type { AddressDetail, Order } from '@/types';

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

/** Đơn đang mở theo thứ tự hiển thị: cần khách xử lý lên trước, sau đó theo ngày chuyển gần nhất. */
export const sortOpenOrders = (orders: Order[]): Order[] =>
  [...orders].sort((a, b) => Number(needsAction(b)) - Number(needsAction(a)) || a.scheduledDate.localeCompare(b.scheduledDate));

export interface CustomerAction {
  label: string;
  icon: LucideIcon;
  /** id khối cần cuộn tới trên trang chi tiết; null khi hành động nằm ngay ở banner đầu trang. */
  section: string | null;
}

/** Việc khách nên làm tiếp với đơn — dùng chung cho CTA ở hero chi tiết và dòng "việc cần làm" trên thẻ đơn. */
export const customerActionOf = (o: Order): CustomerAction | null => {
  const { remaining } = computeTotals(o);
  if (o.status === 'SURVEYED_PENDING_CONFIRM') return { label: 'Xem báo giá & đặt cọc', icon: ReceiptText, section: 'quote-section' };
  if (o.status === 'PENDING_PAYMENT_OR_CLAIM' && remaining > 0) return { label: `Thanh toán ${formatVND(remaining)}`, icon: QrCode, section: 'payment-section' };
  if (o.flags.hasFieldDiscrepancyLock) return { label: 'Xác nhận phát sinh hiện trường', icon: AlertTriangle, section: null };
  if (o.status === 'COMPLETED') return { label: 'Đánh giá & hỗ trợ sau chuyển', icon: Star, section: 'payment-section' };
  return null;
};

/** Nhãn trạng thái ngắn theo góc nhìn khách (khác nhãn nội bộ STATUS_META). */
export const customerStatusOf = (o: Order): { label: string; tone: Tone } => {
  switch (o.status) {
    case 'NEW_PENDING_SURVEY':
      switch (surveyStageOf(o)) {
        case 'TRUCK_CONFIRM':
          return { label: 'Đang xác nhận xe', tone: 'info' };
        case 'VIDEO_CALL':
          return { label: 'Chờ khảo sát video', tone: 'info' };
        case 'ON_SITE':
          return { label: 'Chờ khảo sát tận nơi', tone: 'info' };
        case 'PHOTOS':
          return isMediaSubmitted(o) ? { label: 'Đang lập báo giá', tone: 'info' } : { label: 'Chờ bạn gửi ảnh', tone: 'warning' };
      }
      break;
    case 'SURVEYED_PENDING_CONFIRM':
      return { label: 'Chờ duyệt báo giá', tone: 'brand' };
    case 'QUOTED_PENDING_SCHEDULE':
      return { label: 'Đã nhận cọc', tone: 'info' };
    case 'ASSIGNED':
      return { label: 'Đã xếp đội xe', tone: 'info' };
    case 'IN_TRANSIT':
      return { label: 'Đang vận chuyển', tone: 'info' };
    case 'PAUSED_WAITING_CUSTOMER':
      return { label: 'Đội xe đang chờ bạn', tone: 'warning' };
    case 'PENDING_PAYMENT_OR_CLAIM':
      return computeTotals(o).remaining > 0 ? { label: 'Chờ thanh toán', tone: 'brand' } : { label: 'Đang hoàn tất', tone: 'info' };
    case 'COMPLETED':
      return { label: 'Hoàn tất', tone: 'success' };
    case 'CANCELLED_EXPIRED':
      return { label: 'Đã hủy', tone: 'neutral' };
  }
  return { label: o.status, tone: 'neutral' };
};

const shortPlace = (detail: AddressDetail | undefined, full: string): string => {
  if (detail?.ward) return detail.ward.replace(/^(Phường|Xã|Đặc khu)\s+/u, '');
  const parts = full.split(',').map((s) => s.trim()).filter(Boolean);
  return parts.length >= 2 ? parts[parts.length - 2]! : full;
};

/** Lộ trình rút gọn để khách nhận ra đơn, VD: "Thạnh Mỹ Tây → Long Bình". */
export const routeLabelOf = (o: Order): string =>
  `${shortPlace(o.bookingDetails?.from, o.fromAddress)} → ${shortPlace(o.bookingDetails?.to, o.toAddress)}`;
