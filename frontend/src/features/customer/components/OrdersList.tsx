import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, Plus } from 'lucide-react';
import { cx } from '@/components/ui';
import { OrderCard } from '@/features/customer/components/OrderCard';
import { needsAction } from '@/features/customer/utils';
import { ROUTES } from '@/routes/paths';
import type { Order } from '@/types';

const Group = ({ title, count, children }: { title: string; count: number; children: ReactNode }) => (
  <section className="space-y-2">
    <h2 className="px-1 text-[11px] font-semibold tracking-wider text-slate-500 uppercase">
      {title} · <span className="tabular-nums">{count}</span>
    </h2>
    {children}
  </section>
);

/**
 * Danh sách đơn đang mở, chia nhóm "Cần bạn xử lý" / "Đang tiến hành".
 * - `page`: trang /customer/orders khi khách có từ 2 đơn mở.
 * - `sidebar`: cột trái trang chi tiết trên desktop, đánh dấu đơn đang xem.
 * `orders` đã được sắp xếp sẵn (sortOpenOrders).
 */
export const OrdersList = ({
  orders,
  closedCount,
  variant,
  selectedId,
}: {
  orders: Order[];
  closedCount: number;
  variant: 'page' | 'sidebar';
  selectedId?: string;
}) => {
  const compact = variant === 'sidebar';
  const urgent = orders.filter(needsAction);
  const rest = orders.filter((o) => !needsAction(o));
  const listClass = compact ? 'space-y-2' : 'grid gap-3 sm:grid-cols-2';

  return (
    <div className={compact ? 'space-y-4' : 'space-y-6'}>
      {compact ? (
        <div className="flex items-baseline justify-between px-1">
          <p className="text-sm font-semibold text-slate-900">Đơn đang mở</p>
          <span className="text-xs text-slate-500 tabular-nums">{orders.length}</span>
        </div>
      ) : (
        <div className="px-1">
          <h1 className="text-2xl font-bold tracking-tight">Đơn hàng</h1>
          <p className="mt-1 text-sm text-slate-500">
            <span className="tabular-nums">{orders.length}</span> đơn đang mở
            {urgent.length > 0 && (
              <>
                {' · '}
                <span className="font-semibold text-brand tabular-nums">{urgent.length} cần bạn xử lý</span>
              </>
            )}
          </p>
        </div>
      )}

      {urgent.length > 0 && (
        <Group title="Cần bạn xử lý" count={urgent.length}>
          <div className={listClass}>
            {urgent.map((o) => (
              <OrderCard key={o.id} order={o} to={ROUTES.customer.order(o.id)} selected={o.id === selectedId} compact={compact} />
            ))}
          </div>
        </Group>
      )}
      {rest.length > 0 && (
        <Group title="Đang tiến hành" count={rest.length}>
          <div className={listClass}>
            {rest.map((o) => (
              <OrderCard key={o.id} order={o} to={ROUTES.customer.order(o.id)} selected={o.id === selectedId} compact={compact} />
            ))}
          </div>
        </Group>
      )}

      <div className={cx('flex gap-2', compact ? 'flex-col' : 'flex-col sm:flex-row sm:items-center sm:justify-between')}>
        <Link
          to={ROUTES.customer.create}
          className="inline-flex h-10 items-center justify-center gap-1.5 rounded-xl border border-slate-200 bg-white px-4 text-sm font-semibold text-slate-700 hover:border-slate-300"
        >
          <Plus className="size-4" /> Đặt lịch mới
        </Link>
        {closedCount > 0 && (
          <Link
            to={ROUTES.customer.history}
            className={cx(
              'inline-flex items-center gap-1 text-xs font-semibold text-slate-500 hover:text-slate-900',
              compact ? 'justify-between border-t border-dashed border-slate-200 px-1 pt-3' : 'justify-center',
            )}
          >
            <span>
              <span className="tabular-nums">{closedCount}</span> đơn đã đóng
            </span>
            <span className="inline-flex items-center gap-1 text-slate-900">
              Xem lịch sử <ArrowRight className="size-3.5" />
            </span>
          </Link>
        )}
      </div>
    </div>
  );
};
