import { OrdersTab } from '@/features/customer/components/OrdersTab';
import { isOpenOrder, selectMyOrders } from '@/features/customer/utils';
import { useApp } from '@/services/store/AppStore';

/** /customer/orders — các đơn đang thực hiện. */
export const CustomerOrdersPage = () => {
  const { state } = useApp();
  const orders = selectMyOrders(state.orders, state.activeOrderId).filter(isOpenOrder);
  return <OrdersTab orders={orders} />;
};
