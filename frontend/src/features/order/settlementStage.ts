import type { Tone } from '@/components/ui';
import { computeTotals } from '@/services/pricing';
import type { Order } from '@/types';

export interface StageBadge {
  label: string;
  tone: Tone;
}

/**
 * Đơn ở "Chờ quyết toán" (đã bàn giao) đang chờ điều gì — cho nhân viên nội bộ (điều phối / admin).
 * Tách rõ: khách chưa trả xong, hay khách đã xong và chỉ còn việc nội bộ (thu hồi vật tư) trước khi hồ sơ tự đóng.
 * Trả về null nếu đơn không ở giai đoạn quyết toán.
 */
export const settlementBadges = (o: Order): StageBadge[] | null => {
  if (o.status !== 'PENDING_PAYMENT_OR_CLAIM') return null;
  const badges: StageBadge[] = [];
  const remaining = computeTotals(o).remaining;
  badges.push(remaining > 0 ? { label: 'Chờ khách thanh toán', tone: 'warning' } : { label: 'Khách đã trả đủ', tone: 'success' });
  switch (o.materials.status) {
    case 'ISSUED':
    case 'RECEIVED':
      badges.push({ label: 'Chờ đội khai vật tư', tone: 'info' });
      break;
    case 'USAGE_REPORTED':
      badges.push({ label: 'Chờ kho kiểm nhận', tone: 'info' });
      break;
    default:
      break;
  }
  return badges;
};
