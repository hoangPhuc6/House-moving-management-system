import { OrdersModule } from '@/features/admin/components/OrdersModule';
import { useAdminQuery } from '@/features/admin/hooks';

/** /admin/orders — tra cứu toàn bộ đơn hàng (mọi trạng thái), xem chi tiết & nhật ký. */
export const AdminOrdersPage = () => {
  const query = useAdminQuery();
  return <OrdersModule query={query} />;
};
