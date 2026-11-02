import { QueueTab } from '@/features/coordinator/components/QueueTab';
import { useCoordinatorContext } from '@/features/coordinator/hooks';

/** /coordinator/queue — hàng đợi & Kanban. */
export const CoordinatorQueuePage = () => {
  const { open, query } = useCoordinatorContext();
  return <QueueTab onOpen={open} query={query} />;
};
