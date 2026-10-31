import { SettlementModule } from '@/features/admin/components/SettlementModule';
import { useAdminQuery } from '@/features/admin/hooks';

/** /admin/settlement — thu hồi vật tư & đóng hồ sơ. */
export const AdminSettlementPage = () => {
  const query = useAdminQuery();
  return <SettlementModule query={query} />;
};
