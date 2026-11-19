import { JobList } from '@/features/staff/components/JobList';
import { useCurrentStaffId } from '@/features/staff/session';
import { selectStaffJobs } from '@/features/staff/utils';
import { useApp } from '@/services/store/AppStore';

/** /staff/jobs — danh sách lệnh việc được phân công. */
export const StaffJobsPage = () => {
  const { state } = useApp();
  const staffId = useCurrentStaffId();
  return <JobList jobs={selectStaffJobs(state.orders, staffId)} />;
};
