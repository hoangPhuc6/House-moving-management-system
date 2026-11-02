import { HistoryTab } from '@/features/customer/components/HistoryTab';
import { selectMyOrders } from '@/features/customer/utils';
import { useApp } from '@/services/store/AppStore';

/** /customer/history — lịch sử đặt đơn. */
export const CustomerHistoryPage = () => {
  const { state } = useApp();
  return <HistoryTab orders={selectMyOrders(state.orders, state.activeOrderId)} />;
};
