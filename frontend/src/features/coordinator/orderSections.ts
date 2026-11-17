import type { OrderSection } from '@/features/coordinator/types';
import type { Order } from '@/types';

/** Đơn đang thực hiện (đã gán xe hoặc đang chạy) — có thể phát sinh sự cố. */
export const isRunning = (o: Order): boolean => ['ASSIGNED', 'IN_TRANSIT', 'PAUSED_WAITING_CUSTOMER'].includes(o.status);

/** Mục mở mặc định khi vào chi tiết đơn — đúng việc của bước hiện tại. */
export const defaultSectionOf = (o: Order): OrderSection => {
  if (o.flags.isVehicleIncident) return 'incident';
  if (o.status === 'NEW_PENDING_SURVEY' || o.status === 'SURVEYED_PENDING_CONFIRM') return 'quote';
  // Đã gán xe nhưng đội xe chưa bắt đầu → vẫn ở bước điều phối; bắt đầu rồi → xem tiến độ ở Tổng quan.
  if (o.status === 'QUOTED_PENDING_SCHEDULE' || (o.status === 'ASSIGNED' && o.fieldStage === 'NOT_STARTED')) return 'dispatch';
  return 'overview';
};

/** Các mục hiển thị cho đơn: Sự cố chỉ có khi đơn đang thực hiện. */
export const sectionsOf = (o: Order): { id: OrderSection; label: string }[] => [
  { id: 'overview', label: 'Tổng quan' },
  { id: 'quote', label: 'Khảo sát & Báo giá' },
  { id: 'dispatch', label: 'Điều phối xe' },
  ...(isRunning(o) || o.flags.isVehicleIncident ? [{ id: 'incident' as const, label: 'Sự cố' }] : []),
  { id: 'log', label: 'Nhật ký' },
];
