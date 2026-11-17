import { OrdersListView } from '@/features/coordinator/components/OrdersListView';
import { useCoordinatorContext } from '@/features/coordinator/hooks';

/** /coordinator/orders — danh sách tất cả đơn (dạng danh sách / Kanban). */
export const CoordinatorOrdersPage = () => {
  const { open, query } = useCoordinatorContext();
  return <OrdersListView onOpen={open} query={query} />;
};
