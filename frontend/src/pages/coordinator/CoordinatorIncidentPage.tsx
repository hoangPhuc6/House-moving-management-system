import { IncidentList } from '@/features/coordinator/components/IncidentList';
import { useCoordinatorContext } from '@/features/coordinator/hooks';

/** /coordinator/incident — sự cố & đơn đang chạy. */
export const CoordinatorIncidentPage = () => {
  const { open } = useCoordinatorContext();
  return <IncidentList onOpen={open} />;
};
