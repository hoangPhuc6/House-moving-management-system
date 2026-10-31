import { JOB_STATUSES, STAFF_ID } from '@/features/staff/constants';
import type { Order } from '@/types';

/** Các lệnh việc được phân công cho nhân viên đang đăng nhập. */
export const selectStaffJobs = (orders: Order[]): Order[] =>
  orders.filter((o) => o.crewIds.includes(STAFF_ID) && JOB_STATUSES.includes(o.status));
