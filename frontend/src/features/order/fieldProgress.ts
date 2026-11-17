import { FIELD_STAGE_LOG_ACTION, FIELD_STAGE_LOG_LABEL, FIELD_STAGE_ORDER } from '@/constants/order';
import type { FieldStage, Order } from '@/types';

/** Đơn đang ở hiện trường (đã gán lệnh → chờ quyết toán) — có tiến độ do đội xe cập nhật. */
export const hasFieldProgress = (o: Order): boolean =>
  ['ASSIGNED', 'IN_TRANSIT', 'PAUSED_WAITING_CUSTOMER', 'PENDING_PAYMENT_OR_CLAIM'].includes(o.status);

/** Giờ đội xe bấm cập nhật từng bước (đọc từ nhật ký; lấy lần cập nhật gần nhất của mỗi bước). */
export const fieldStageTimes = (o: Order): Partial<Record<FieldStage, number>> => {
  const out: Partial<Record<FieldStage, number>> = {};
  for (const l of o.auditLog) {
    if (l.action !== FIELD_STAGE_LOG_ACTION) continue;
    const stage = FIELD_STAGE_ORDER.find((s) => FIELD_STAGE_LOG_LABEL[s] === l.detail);
    if (stage) out[stage] = l.timestamp;
  }
  return out;
};

/** Lần cập nhật hiện trường gần nhất (null = đội xe chưa cập nhật bước nào). */
export const lastFieldUpdate = (o: Order): number | null => fieldStageTimes(o)[o.fieldStage] ?? null;

/** "vừa xong" · "12 phút trước" · "2 giờ trước". */
export const agoText = (epoch: number, now: number): string => {
  const min = Math.max(0, Math.round((now - epoch) / 60_000));
  if (min < 1) return 'vừa xong';
  if (min < 60) return `${min} phút trước`;
  const h = Math.floor(min / 60);
  return `${h} giờ${min % 60 ? ` ${min % 60} phút` : ''} trước`;
};
