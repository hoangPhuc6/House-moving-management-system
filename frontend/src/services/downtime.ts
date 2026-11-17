import type { DowntimeReason, DowntimeTarget, ResourceDowntime, StaffDowntimeReason, ToastTone, VehicleDowntimeReason } from '@/types';

/** YYYY-MM-DD theo giờ máy. */
export const localISO = (d: Date): string => {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
};

export const todayLocalISO = (now = Date.now()) => localISO(new Date(now));

export const addDaysISO = (iso: string, n: number): string => {
  const [y, m, d] = iso.split('-').map(Number);
  return localISO(new Date(y!, m! - 1, d! + n));
};

/** "2026-11-17" → "17/11". */
export const dayMonth = (iso: string) => {
  const [, m, d] = iso.split('-');
  return `${d}/${m}`;
};

export const DOWNTIME_REASON_META: Record<DowntimeReason, { label: string; tone: ToastTone }> = {
  MAINTENANCE: { label: 'Bảo dưỡng', tone: 'warning' },
  REPAIR: { label: 'Sửa chữa', tone: 'danger' },
  INSPECTION: { label: 'Đăng kiểm', tone: 'info' },
  LEAVE: { label: 'Nghỉ phép', tone: 'warning' },
  SICK: { label: 'Nghỉ ốm', tone: 'danger' },
  OTHER: { label: 'Bận việc khác', tone: 'info' },
};

export const VEHICLE_DOWNTIME_REASONS: VehicleDowntimeReason[] = ['MAINTENANCE', 'REPAIR', 'INSPECTION'];
export const STAFF_DOWNTIME_REASONS: StaffDowntimeReason[] = ['LEAVE', 'SICK', 'OTHER'];

/** "17/11 → 20/11" hoặc "17/11" nếu một ngày. */
export const downtimeRangeLabel = (d: Pick<ResourceDowntime, 'from' | 'to'>) =>
  d.from === d.to ? dayMonth(d.from) : `${dayMonth(d.from)} → ${dayMonth(d.to)}`;

/** Lần nghỉ phủ lên ngày `date` của một xe / nhân sự (null = làm việc bình thường). */
export const downtimeOn = (downtimes: ResourceDowntime[], target: DowntimeTarget, resourceId: string, date: string) =>
  downtimes.find((d) => d.target === target && d.resourceId === resourceId && d.from <= date && date <= d.to) ?? null;

/** Các lần nghỉ chưa kết thúc (đang diễn ra hoặc sắp tới), sớm nhất trước. */
export const upcomingDowntimes = (downtimes: ResourceDowntime[], target: DowntimeTarget, resourceId: string, today: string) =>
  downtimes.filter((d) => d.target === target && d.resourceId === resourceId && d.to >= today).sort((a, b) => a.from.localeCompare(b.from));

/** Nhãn ngắn để hiện cạnh xe / nhân sự, VD "Bảo dưỡng 17/11 → 20/11". */
export const downtimeLabel = (d: ResourceDowntime) => `${DOWNTIME_REASON_META[d.reason].label} ${downtimeRangeLabel(d)}`;
