import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, ChevronRight, History, Package, Plus } from 'lucide-react';
import { Button, Card, cx, EmptyState, StatusBadge } from '@/components/ui';
import { OrderDetail } from '@/features/customer/components/OrderDetail';
import { isOpenOrder } from '@/features/customer/utils';
import { ROUTES } from '@/routes/paths';
import { formatDateVN, formatTime, formatVND } from '@/services/format';
import { computeTotals } from '@/services/pricing';
import { useApp } from '@/services/store/AppStore';
import type { Order } from '@/types';

export const HistoryTab = ({ orders }: { orders: Order[] }) => {
  const { dispatch } = useApp();
  const navigate = useNavigate();
  const onOpenActive = (id: string) => {
    dispatch({ type: 'SET_ACTIVE_ORDER', orderId: id });
    navigate(ROUTES.customer.orders);
  };
  const [viewId, setViewId] = useState<string | null>(null);
  const viewing = orders.find((o) => o.id === viewId);

  if (viewing) {
    return (
      <div className="space-y-4">
        <button
          type="button"
          onClick={() => setViewId(null)}
          className="-ml-1 inline-flex h-10 items-center gap-1.5 rounded-xl px-2 text-sm font-semibold text-slate-600 hover:bg-slate-100"
        >
          <ArrowLeft className="size-4" /> Lịch sử đặt đơn
        </button>
        <OrderDetail order={viewing} />
      </div>
    );
  }

  if (!orders.length) {
    return (
      <Card className="mx-auto max-w-xl">
        <EmptyState icon={History} title="Chưa có lịch sử đặt đơn">
          Các đơn bạn đã đặt sẽ hiển thị tại đây.
        </EmptyState>
        <div className="mt-4 flex justify-center">
          <Button icon={Plus} onClick={() => navigate(ROUTES.customer.create)}>
            Đặt lịch chuyển nhà
          </Button>
        </div>
      </Card>
    );
  }

  return (
    <div className="mx-auto max-w-3xl">
      <div className="mb-4 px-1">
        <h1 className="text-2xl font-bold tracking-tight">Lịch sử đặt đơn</h1>
        <p className="mt-1 text-sm text-slate-500">
          <span className="tabular-nums">{orders.length}</span> đơn đã đặt
        </p>
      </div>
      <Card padded={false}>
        <ul className="divide-y divide-slate-100">
          {orders.map((o) => (
            <li key={o.id}>
              <button
                type="button"
                onClick={() =>
                  isOpenOrder(o) ? onOpenActive(o.id) : setViewId(o.id)
                }
                className="flex w-full items-center gap-3 px-4 py-3.5 text-left hover:bg-slate-50 sm:px-5"
              >
                <span
                  className={cx(
                    "grid size-10 shrink-0 place-items-center rounded-xl",
                    isOpenOrder(o)
                      ? "bg-orange-50 text-brand"
                      : "bg-slate-100 text-slate-500",
                  )}
                >
                  <Package className="size-5" />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-semibold tabular-nums">#{o.id}</p>
                  <p className="truncate text-xs text-slate-500">
                    {o.fromAddress} → {o.toAddress}
                  </p>
                  <p className="mt-0.5 text-[11px] text-slate-400 tabular-nums">
                    Đặt lúc {formatTime(o.createdAt)} · Chuyển{" "}
                    {formatDateVN(o.scheduledDate)}
                  </p>
                </div>
                <div className="flex flex-col items-end gap-1">
                  <StatusBadge status={o.status} />
                  {o.quoteItems.length > 0 && (
                    <span className="text-xs font-semibold text-slate-600 tabular-nums">
                      {formatVND(computeTotals(o).grandTotal)}
                    </span>
                  )}
                </div>
                <ChevronRight className="hidden size-4 shrink-0 text-slate-300 sm:block" />
              </button>
            </li>
          ))}
        </ul>
      </Card>
    </div>
  );
};
