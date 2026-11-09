import { CUSTOMER_PROCESS_STEPS, PROCESS_STEPS } from '@/constants/order';
import type { OrderStatus } from '@/types';

export const stepIndexOf = (status: OrderStatus): number => PROCESS_STEPS.findIndex((s) => s.statuses.includes(status));

/** Vị trí trạng thái trên thanh tiến độ 5 bước của khách hàng (-1 nếu đơn đã hủy). */
export const customerStepIndexOf = (status: OrderStatus): number => CUSTOMER_PROCESS_STEPS.findIndex((s) => s.statuses.includes(status));
