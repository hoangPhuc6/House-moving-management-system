import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { History, Package, Plus } from 'lucide-react';
import { Button, Card, EmptyState } from '@/components/ui';
import { OrdersList } from '@/features/customer/components/OrdersList';
import { isOpenOrder, selectMyOrders, sortOpenOrders } from '@/features/customer/utils';
import { ROUTES } from '@/routes/paths';
import { useApp } from '@/services/store/AppStore';

/** State điều hướng: vừa tạo đơn xong → mở thẳng chi tiết đơn mới (id do store gán vào activeOrderId). */
export interface OrdersPageNavState {
  focusActive?: boolean;
}

/**
 * /customer/orders — điểm vào khu vực đơn hàng.
 * 0 đơn mở → trang trống; 1 đơn → chuyển thẳng tới chi tiết; từ 2 đơn → danh sách thẻ.
 */
export const CustomerOrdersPage = () => {
  const { state } = useApp();
  const navigate = useNavigate();
  const navState = useLocation().state as OrdersPageNavState | null;
  const mine = selectMyOrders(state.orders, state.activeOrderId);
  const open = sortOpenOrders(mine.filter(isOpenOrder));

  if (navState?.focusActive && open.some((o) => o.id === state.activeOrderId)) {
    return <Navigate to={ROUTES.customer.order(state.activeOrderId)} replace />;
  }

  if (open.length === 1) return <Navigate to={ROUTES.customer.order(open[0]!.id)} replace />;

  if (open.length === 0) {
    return (
      <Card className="mx-auto max-w-xl">
        <EmptyState icon={Package} title="Bạn chưa có đơn hàng nào đang thực hiện">
          Đặt lịch chuyển nhà để nhận báo giá tham khảo ngay và báo giá chi tiết sau khảo sát.
        </EmptyState>
        <div className="mt-4 flex flex-col gap-2 sm:flex-row sm:justify-center">
          <Button size="lg" icon={Plus} onClick={() => navigate(ROUTES.customer.create)}>
            Đặt lịch chuyển nhà
          </Button>
          {mine.length > 0 && (
            <Button size="lg" variant="secondary" icon={History} onClick={() => navigate(ROUTES.customer.history)}>
              Xem lịch sử
            </Button>
          )}
        </div>
      </Card>
    );
  }

  return <OrdersList variant="page" orders={open} closedCount={mine.length - open.length} />;
};
