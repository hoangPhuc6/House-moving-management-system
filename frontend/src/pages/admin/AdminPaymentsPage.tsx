import { PaymentsModule } from '@/features/admin/components/PaymentsModule';
import { useAdminQuery } from '@/features/admin/hooks';

/** /admin/payments — thanh toán & công nợ. */
export const AdminPaymentsPage = () => {
  const query = useAdminQuery();
  return <PaymentsModule query={query} />;
};
