import { InventoryModule } from '@/features/admin/components/InventoryModule';
import { useAdminQuery } from '@/features/admin/hooks';

/** /admin/inventory — kho & dụng cụ. */
export const AdminInventoryPage = () => {
  const query = useAdminQuery();
  return <InventoryModule query={query} />;
};
