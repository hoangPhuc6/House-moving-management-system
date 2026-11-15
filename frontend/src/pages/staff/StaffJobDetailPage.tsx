import { Navigate, useParams } from 'react-router-dom';
import { JobDetail } from '@/features/staff/components/JobDetail';
import { selectStaffJobs } from '@/features/staff/utils';
import { ROUTES } from '@/routes/paths';
import { useApp } from '@/services/store/AppStore';

/** /staff/jobs/:orderId — chi tiết một lệnh việc. */
export const StaffJobDetailPage = () => {
  const { orderId } = useParams();
  const { state } = useApp();
  const job = selectStaffJobs(state.orders).find((o) => o.id === orderId);
  if (!job) return <Navigate to={ROUTES.staff.jobs} replace />;
  // key theo đơn: chuyển lệnh khác thì form nhận vật tư (số lượng, tick đã kiểm tra) khởi tạo lại.
  return <JobDetail key={job.id} order={job} />;
};
