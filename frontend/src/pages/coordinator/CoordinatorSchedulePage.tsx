import { ScheduleBoard } from '@/features/coordinator/components/ScheduleBoard';
import { useCoordinatorContext } from '@/features/coordinator/hooks';

/** /coordinator/schedule — lịch xe & nhân sự theo ngày. */
export const CoordinatorSchedulePage = () => {
  const { open } = useCoordinatorContext();
  return <ScheduleBoard onOpen={open} />;
};
