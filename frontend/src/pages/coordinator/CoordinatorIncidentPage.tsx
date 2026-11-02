import { IncidentTab } from '@/features/coordinator/components/IncidentTab';
import { NoOrdersState } from '@/features/coordinator/components/NoOrdersState';
import { useCoordinatorContext } from '@/features/coordinator/hooks';

/** /coordinator/incident — xử lý sự cố phương tiện. */
export const CoordinatorIncidentPage = () => {
  const { selected, setSelectedId } = useCoordinatorContext();
  if (!selected) return <NoOrdersState />;
  return <IncidentTab order={selected} onSelect={setSelectedId} />;
};
