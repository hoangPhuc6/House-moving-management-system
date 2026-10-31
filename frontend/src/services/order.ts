import { PROCESS_STEPS } from '@/constants/order';
import type { OrderStatus } from '@/types';

export const stepIndexOf = (status: OrderStatus): number => PROCESS_STEPS.findIndex((s) => s.statuses.includes(status));
