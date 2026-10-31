import { cx } from '@/components/ui';
import { OrderOverview } from '@/features/customer/components/OrderOverview';
import { PaymentSection } from '@/features/customer/components/PaymentSection';
import { QuoteSection } from '@/features/customer/components/QuoteSection';
import { PAYMENT_STATUSES } from '@/features/customer/constants';
import { computeTotals } from '@/services/pricing';
import type { Order } from '@/types';

export const OrderDetail = ({ order }: { order: Order }) => {
  const stickyBar =
    order.status === "SURVEYED_PENDING_CONFIRM" ||
    (order.status === "PENDING_PAYMENT_OR_CLAIM" &&
      computeTotals(order).remaining > 0);
  return (
    <div className={cx("space-y-8", stickyBar && "pb-20 md:pb-0")}>
      <OrderOverview order={order} />
      {order.status !== "CANCELLED_EXPIRED" && <QuoteSection order={order} />}
      {PAYMENT_STATUSES.includes(order.status) && (
        <PaymentSection order={order} />
      )}
    </div>
  );
};
