import { DispatchTab } from '@/features/coordinator/components/DispatchTab';
import { NoOrdersState } from '@/features/coordinator/components/NoOrdersState';
import { useCoordinatorContext } from '@/features/coordinator/hooks';

/** /coordinator/dispatch — điều phối xe & nhân sự. */
export const CoordinatorDispatchPage = () => {
  const { selected, setSelectedId } = useCoordinatorContext();
  if (!selected) return <NoOrdersState />;
  return <DispatchTab key={selected.id} order={selected} onSelect={setSelectedId} />;
};
