import { ClaimsModule } from '@/features/admin/components/ClaimsModule';
import { useAdminQuery } from '@/features/admin/hooks';

/** /admin/claims — quản lý khiếu nại. */
export const AdminClaimsPage = () => {
  const query = useAdminQuery();
  return <ClaimsModule query={query} />;
};
