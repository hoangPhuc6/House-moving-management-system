import { JOB_STATUSES } from '@/features/staff/constants';
import type { Order } from '@/types';

/** Các lệnh việc được phân công cho nhân viên đang đăng nhập. */
export const selectStaffJobs = (orders: Order[], staffId: string): Order[] =>
  orders.filter((o) => o.crewIds.includes(staffId) && JOB_STATUSES.includes(o.status));
