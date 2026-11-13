import { Link, Navigate, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { cx } from '@/components/ui';
import { OrderDetail } from '@/features/customer/components/OrderDetail';
import { OrdersList } from '@/features/customer/components/OrdersList';
import { isOpenOrder, selectMyOrders, sortOpenOrders } from '@/features/customer/utils';
import { ROUTES } from '@/routes/paths';
import { useApp } from '@/services/store/AppStore';

const BackLink = ({ to, label, className }: { to: string; label: string; className?: string }) => (
  <Link to={to} className={cx('-ml-1 inline-flex h-10 items-center gap-1.5 rounded-xl px-2 text-sm font-semibold text-slate-600 hover:bg-slate-100', className)}>
    <ArrowLeft className="size-4" /> {label}
  </Link>
);

/**
 * /customer/orders/:orderId và /customer/history/:orderId — chi tiết một đơn.
 * Khi khách có từ 2 đơn mở, desktop (≥ lg) hiện thêm cột danh sách bên trái; mobile/tablet có nút quay về danh sách.
 */
export const CustomerOrderDetailPage = () => {
  const { orderId = '' } = useParams();
  const { state } = useApp();
  const mine = selectMyOrders(state.orders, state.activeOrderId);
  const order = mine.find((o) => o.id === orderId);

  if (!order) return <Navigate to={ROUTES.customer.orders} replace />;

  const open = sortOpenOrders(mine.filter(isOpenOrder));
  const isOpen = isOpenOrder(order);
  const showList = isOpen && open.length > 1;

  return (
    <div className={cx(showList && 'lg:grid lg:grid-cols-[260px_minmax(0,1fr)] lg:items-start lg:gap-6')}>
      {showList && (
        <aside className="hidden lg:sticky lg:top-20 lg:block lg:max-h-[calc(100vh-6rem)] lg:overflow-y-auto lg:pb-2" aria-label="Danh sách đơn đang mở">
          <OrdersList variant="sidebar" orders={open} closedCount={mine.length - open.length} selectedId={order.id} />
        </aside>
      )}
      <div className="min-w-0 space-y-3">
        {!isOpen ? (
          <BackLink to={ROUTES.customer.history} label="Lịch sử đặt đơn" />
        ) : (
          showList && <BackLink to={ROUTES.customer.orders} label="Đơn hàng" className="lg:hidden" />
        )}
        <OrderDetail key={order.id} order={order} />
      </div>
    </div>
  );
};
