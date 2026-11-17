import { seedDowntimes, seedStaff, seedVehicles } from '@/constants/resources';
import type { AppState } from '@/types';

/** Trạng thái khởi tạo sạch: chưa có đơn hàng, khiếu nại hay giao dịch nào. */
export const createInitialState = (): AppState => ({
  activeOrderId: '',
  orders: [],
  resources: { vehicles: seedVehicles(), staff: seedStaff(), downtimes: seedDowntimes() },
  claims: [],
  refunds: [],
  ledger: [],
  toasts: [],
});
