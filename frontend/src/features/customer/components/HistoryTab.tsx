import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ArrowRight, ChevronRight, History, Package, Plus } from 'lucide-react';
import { Badge, Button, Card, cx, EmptyState } from '@/components/ui';
import { customerStatusOf, routeLabelOf } from '@/features/customer/utils';
import { ROUTES } from '@/routes/paths';
import { formatDateVN, formatVND } from '@/services/format';
import { computeTotals } from '@/services/pricing';
import type { Order } from '@/types';

type Filter = 'ALL' | 'COMPLETED' | 'CANCELLED_EXPIRED';

const FILTERS: { key: Filter; label: string }[] = [
  { key: 'ALL', label: 'Tất cả' },
  { key: 'COMPLETED', label: 'Hoàn tất' },
  { key: 'CANCELLED_EXPIRED', label: 'Đã hủy' },
];

/** Lịch sử: chỉ các đơn đã đóng (Hoàn tất / Đã hủy). Đơn đang mở nằm ở tab Đơn hàng. */
export const HistoryTab = ({ orders, openCount }: { orders: Order[]; openCount: number }) => {
  const navigate = useNavigate();
  const [filter, setFilter] = useState<Filter>('ALL');
  const shown = filter === 'ALL' ? orders : orders.filter((o) => o.status === filter);

  if (!orders.length) {
    return (
      <Card className="mx-auto max-w-xl">
        <EmptyState icon={History} title="Chưa có đơn nào đã đóng">
          Đơn hoàn tất hoặc đã hủy sẽ hiển thị tại đây.
        </EmptyState>
        <div className="mt-4 flex flex-col gap-2 sm:flex-row sm:justify-center">
          {openCount > 0 && (
            <Button variant="secondary" icon={Package} onClick={() => navigate(ROUTES.customer.orders)}>
              Xem {openCount} đơn đang mở
            </Button>
          )}
          <Button icon={Plus} onClick={() => navigate(ROUTES.customer.create)}>
            Đặt lịch chuyển nhà
          </Button>
        </div>
      </Card>
    );
  }

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3 px-1">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Lịch sử đặt đơn</h1>
          <p className="mt-1 text-sm text-slate-500">
            <span className="tabular-nums">{orders.length}</span> đơn đã đóng
          </p>
        </div>
        <div className="flex gap-1.5" role="group" aria-label="Lọc theo trạng thái">
          {FILTERS.map((f) => (
            <button
              key={f.key}
              type="button"
              aria-pressed={filter === f.key}
              onClick={() => setFilter(f.key)}
              className={cx(
                'h-8 rounded-full border px-3 text-xs font-semibold transition-colors',
                filter === f.key ? 'border-slate-900 bg-slate-900 text-white' : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300',
              )}
            >
              {f.label}
            </button>
          ))}
        </div>
      </div>

      <Card padded={false}>
        {shown.length === 0 ? (
          <p className="px-5 py-8 text-center text-sm text-slate-400">Không có đơn nào ở trạng thái này.</p>
        ) : (
          <ul className="divide-y divide-slate-100">
            {shown.map((o) => {
              const status = customerStatusOf(o);
              return (
                <li key={o.id}>
                  <Link to={ROUTES.customer.historyOrder(o.id)} className="flex w-full items-center gap-3 px-4 py-3.5 text-left hover:bg-slate-50 sm:px-5">
                    <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-slate-100 text-slate-500">
                      <Package className="size-5" />
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-semibold">{routeLabelOf(o)}</p>
                      <p className="mt-0.5 text-[11px] text-slate-400 tabular-nums">
                        #{o.id} · Chuyển {formatDateVN(o.scheduledDate)}
                      </p>
                    </div>
                    <div className="flex flex-col items-end gap-1">
                      <Badge tone={status.tone}>{status.label}</Badge>
                      {o.status === 'COMPLETED' && o.quoteItems.length > 0 && (
                        <span className="text-xs font-semibold text-slate-600 tabular-nums">{formatVND(computeTotals(o).grandTotal)}</span>
                      )}
                    </div>
                    <ChevronRight className="hidden size-4 shrink-0 text-slate-300 sm:block" />
                  </Link>
                </li>
              );
            })}
          </ul>
        )}
      </Card>

      {openCount > 0 && (
        <Link to={ROUTES.customer.orders} className="flex items-center justify-center gap-1 text-xs font-semibold text-slate-500 hover:text-slate-900">
          <span className="tabular-nums">{openCount}</span> đơn đang mở · Xem đơn hàng <ArrowRight className="size-3.5" />
        </Link>
      )}
    </div>
  );
};
