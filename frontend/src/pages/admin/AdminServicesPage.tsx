import { ServicesModule } from '@/features/admin/components/ServicesModule';
import { useAdminQuery } from '@/features/admin/hooks';

/** /admin/services — dịch vụ & bảng giá. */
export const AdminServicesPage = () => {
  const query = useAdminQuery();
  return <ServicesModule query={query} />;
};
