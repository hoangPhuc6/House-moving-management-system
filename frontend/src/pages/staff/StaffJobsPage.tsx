import { JobList } from '@/features/staff/components/JobList';
import { selectStaffJobs } from '@/features/staff/utils';
import { useApp } from '@/services/store/AppStore';

/** /staff/jobs — danh sách lệnh việc được phân công. */
export const StaffJobsPage = () => {
  const { state } = useApp();
  return <JobList jobs={selectStaffJobs(state.orders)} />;
};
