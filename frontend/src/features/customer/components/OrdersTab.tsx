import { useNavigate } from 'react-router-dom';
import { History, Package, Plus } from 'lucide-react';
import { Button, Card, cx, EmptyState } from '@/components/ui';
import { OrderDetail } from '@/features/customer/components/OrderDetail';
import { needsAction } from '@/features/customer/utils';
import { ROUTES } from '@/routes/paths';
import { formatDateVN } from '@/services/format';
import { useApp } from '@/services/store/AppStore';
import type { Order } from '@/types';

export const OrdersTab = ({ orders }: { orders: Order[] }) => {
  const { state, dispatch } = useApp();
  const navigate = useNavigate();
  const order = orders.find((o) => o.id === state.activeOrderId) ?? orders[0];

  if (!order) {
    return (
      <Card className="mx-auto max-w-xl">
        <EmptyState
          icon={Package}
          title="Bạn chưa có đơn hàng nào đang thực hiện"
        >
          Đặt lịch chuyển nhà và gửi ảnh đồ đạc để nhận báo giá nhanh.
        </EmptyState>
        <div className="mt-4 flex flex-col gap-2 sm:flex-row sm:justify-center">
          <Button size="lg" icon={Plus} onClick={() => navigate(ROUTES.customer.create)}>
            Đặt lịch chuyển nhà
          </Button>
          <Button
            size="lg"
            variant="secondary"
            icon={History}
            onClick={() => navigate(ROUTES.customer.history)}
          >
            Xem lịch sử
          </Button>
        </div>
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      {orders.length > 1 && (
        <div
          className="scrollbar-none -mx-4 flex gap-2 overflow-x-auto px-4 pt-1"
          role="tablist"
          aria-label="Chọn đơn hàng"
        >
          {orders.map((o) => {
            const sel = o.id === order.id;
            return (
              <button
                key={o.id}
                type="button"
                role="tab"
                aria-selected={sel}
                onClick={() =>
                  dispatch({ type: "SET_ACTIVE_ORDER", orderId: o.id })
                }
                className={cx(
                  "relative flex shrink-0 flex-col items-start rounded-xl border px-3.5 py-2 text-left transition-colors",
                  sel
                    ? "border-slate-900 bg-slate-900 text-white"
                    : "border-slate-200 bg-white text-slate-700 hover:border-slate-300",
                )}
              >
                <span className="text-sm font-semibold tabular-nums">
                  #{o.id.replace("ORD-2026-", "ORD-")}
                </span>
                <span
                  className={cx(
                    "text-[11px] tabular-nums",
                    sel ? "text-white/60" : "text-slate-400",
                  )}
                >
                  {formatDateVN(o.scheduledDate)}
                </span>
                {needsAction(o) && (
                  <span className="absolute -top-1 -right-1 size-2.5 rounded-full bg-rose-500 ring-2 ring-white" />
                )}
              </button>
            );
          })}
        </div>
      )}
      <OrderDetail key={order.id} order={order} />
    </div>
  );
};
