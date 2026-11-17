import { ClaimsModule } from '@/features/admin/components/ClaimsModule';
import { useCoordinatorContext } from '@/features/coordinator/hooks';

/** /coordinator/claims — tiếp nhận, đối chiếu và đề xuất xử lý khiếu nại. */
export const CoordinatorClaimsPage = () => {
  const { query } = useCoordinatorContext();
  return <ClaimsModule query={query} role="COORDINATOR" />;
};
