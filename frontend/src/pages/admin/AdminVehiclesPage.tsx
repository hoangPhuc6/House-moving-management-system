import { VehiclesModule } from '@/features/admin/components/VehiclesModule';
import { useAdminQuery } from '@/features/admin/hooks';

/** /admin/vehicles — quản lý xe. */
export const AdminVehiclesPage = () => {
  const query = useAdminQuery();
  return <VehiclesModule query={query} />;
};
