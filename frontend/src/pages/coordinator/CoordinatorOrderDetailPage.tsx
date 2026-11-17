import { useParams } from 'react-router-dom';
import { CoordinatorOrderDetail } from '@/features/coordinator/components/CoordinatorOrderDetail';
import { NoOrdersState } from '@/features/coordinator/components/NoOrdersState';
import { useApp } from '@/services/store/AppStore';

/** /coordinator/orders/:orderId?tab=… — chi tiết & xử lý một đơn. */
export const CoordinatorOrderDetailPage = () => {
  const { orderId } = useParams();
  const { state } = useApp();
  const order = state.orders.find((o) => o.id === orderId);
  if (!order) return <NoOrdersState />;
  return <CoordinatorOrderDetail order={order} />;
};
