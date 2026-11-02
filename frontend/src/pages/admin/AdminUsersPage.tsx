import { UsersModule } from '@/features/admin/components/UsersModule';
import { useAdminQuery } from '@/features/admin/hooks';

/** /admin/users — tài khoản & phân quyền. */
export const AdminUsersPage = () => {
  const query = useAdminQuery();
  return <UsersModule query={query} />;
};
