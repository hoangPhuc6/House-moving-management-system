import { DAY } from '@/features/admin/constants';
import type { Group } from '@/features/admin/types';

export const isoDate = (t: number): string => new Date(t).toISOString().slice(0, 10);

export const bucketKey = (date: string, group: Group): string => {
  if (group === 'day') return date;
  if (group === 'month') return date.slice(0, 7);
  const d = new Date(`${date}T00:00:00Z`);
  const dow = (d.getUTCDay() + 6) % 7; // Thứ Hai = 0
  return isoDate(d.getTime() - dow * DAY);
};

export const bucketLabel = (key: string, group: Group): string => {
  if (group === 'month') return `${key.slice(5)}/${key.slice(0, 4)}`;
  const [, m, d] = key.split('-');
  return group === 'week' ? `Tuần ${d}/${m}` : `${d}/${m}`;
};

export const capLabel = (t: number): string => `${String(t).replace('.', ',')} tấn`;

export const matches = (q: string, ...fields: string[]) =>
  !q.trim() || fields.join(" ").toLowerCase().includes(q.trim().toLowerCase());
