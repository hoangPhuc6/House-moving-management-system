import { HistoryTab } from '@/features/customer/components/HistoryTab';
import { isOpenOrder, selectMyOrders } from '@/features/customer/utils';
import { useApp } from '@/services/store/AppStore';

/** /customer/history — các đơn đã đóng (hoàn tất / đã hủy). */
export const CustomerHistoryPage = () => {
  const { state } = useApp();
  const mine = selectMyOrders(state.orders, state.activeOrderId);
  const closed = mine.filter((o) => !isOpenOrder(o));
  return <HistoryTab orders={closed} openCount={mine.length - closed.length} />;
};
