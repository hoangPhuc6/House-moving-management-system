/**
 * Kỳ báo cáo của Dashboard Admin: theo THÁNG hoặc NĂM dương lịch (khớp sổ sách),
 * kèm kỳ so sánh. Mọi ngày là chuỗi ISO yyyy-mm-dd theo UTC (cùng quy ước với dữ liệu đơn).
 */

export type PeriodKind = 'month' | 'year';

export interface Period {
  kind: PeriodKind;
  year: number;
  /** 1–12, chỉ dùng khi kind = 'month'. */
  month: number;
}

/** Khoảng ngày đóng [start, end] (ISO). */
export interface DateWindow {
  start: string;
  end: string;
}

export interface PeriodWindows {
  current: DateWindow;
  /** Kỳ trước để so sánh — cùng số ngày nếu kỳ hiện tại đang chạy. */
  previous: DateWindow;
  /** Kỳ hiện tại chứa hôm nay (chưa kết thúc). */
  running: boolean;
  label: string;
  compareLabel: string;
}

export interface ChartBucket {
  key: string;
  label: string;
  /** Nhãn đầy đủ cho tooltip. */
  title: string;
}

const pad = (n: number) => String(n).padStart(2, '0');
export const isoOf = (y: number, m: number, d: number) => `${y}-${pad(m)}-${pad(d)}`;
export const todayISO = (now = Date.now()) => new Date(now).toISOString().slice(0, 10);
const daysInMonth = (y: number, m: number) => new Date(Date.UTC(y, m, 0)).getUTCDate();
const toTime = (iso: string) => Date.UTC(Number(iso.slice(0, 4)), Number(iso.slice(5, 7)) - 1, Number(iso.slice(8, 10)));
const addDays = (iso: string, n: number) => new Date(toTime(iso) + n * 86_400_000).toISOString().slice(0, 10);
const dayMonth = (iso: string) => `${iso.slice(8, 10)}/${iso.slice(5, 7)}`;

export const inWindow = (iso: string, w: DateWindow) => iso >= w.start && iso <= w.end;

export const currentPeriod = (kind: PeriodKind, now = Date.now()): Period => {
  const d = new Date(now);
  return { kind, year: d.getUTCFullYear(), month: d.getUTCMonth() + 1 };
};

export const periodLabel = (p: Period) => (p.kind === 'month' ? `Tháng ${pad(p.month)}/${p.year}` : `Năm ${p.year}`);

const fullWindow = (p: Period): DateWindow =>
  p.kind === 'month'
    ? { start: isoOf(p.year, p.month, 1), end: isoOf(p.year, p.month, daysInMonth(p.year, p.month)) }
    : { start: isoOf(p.year, 1, 1), end: isoOf(p.year, 12, 31) };

const previousPeriod = (p: Period): Period =>
  p.kind === 'year' ? { ...p, year: p.year - 1 } : p.month === 1 ? { ...p, year: p.year - 1, month: 12 } : { ...p, month: p.month - 1 };

/**
 * Kỳ hiện tại + kỳ so sánh. Kỳ đang chạy chỉ tính tới hôm nay và so với CÙNG SỐ NGÀY của kỳ trước
 * (VD 01–15/11 so với 01–15/10), tránh so một nửa tháng với trọn tháng.
 */
export const periodWindows = (p: Period, today = todayISO()): PeriodWindows => {
  const full = fullWindow(p);
  const running = today >= full.start && today <= full.end;
  const current = { start: full.start, end: running ? today : full.end };
  const prevP = previousPeriod(p);
  const prevFull = fullWindow(prevP);
  const elapsed = Math.round((toTime(current.end) - toTime(current.start)) / 86_400_000);
  const previous = running
    ? { start: prevFull.start, end: [addDays(prevFull.start, elapsed), prevFull.end].sort()[0]! }
    : prevFull;
  const label = running ? `${periodLabel(p)} (đến ${dayMonth(today)})` : periodLabel(p);
  const compareLabel = running
    ? `${p.kind === 'year' ? 'cùng kỳ' : 'so với'} ${dayMonth(previous.start)}–${dayMonth(previous.end)}/${prevP.year}`
    : `so với ${periodLabel(prevP).toLowerCase()}`;
  return { current, previous, running, label, compareLabel };
};

/** Cột biểu đồ tự gom theo kỳ: tháng → từng ngày, năm → từng tháng; kỳ đang chạy chỉ tới hiện tại. */
export const chartBuckets = (p: Period, w: DateWindow): ChartBucket[] => {
  if (p.kind === 'month') {
    const out: ChartBucket[] = [];
    for (let d = w.start; d <= w.end; d = addDays(d, 1)) out.push({ key: d, label: String(Number(d.slice(8, 10))), title: dayMonth(d) + `/${p.year}` });
    return out;
  }
  const lastMonth = Number(w.end.slice(5, 7));
  return Array.from({ length: lastMonth }, (_, i) => ({ key: `${p.year}-${pad(i + 1)}`, label: `T${i + 1}`, title: `Tháng ${pad(i + 1)}/${p.year}` }));
};

/** Khóa cột của một ngày theo kiểu kỳ. */
export const bucketOf = (iso: string, kind: PeriodKind) => (kind === 'month' ? iso : iso.slice(0, 7));

/** Các tháng / năm chọn được, mới nhất trước, từ ngày dữ liệu sớm nhất tới hiện tại. */
export const periodOptions = (kind: PeriodKind, earliest: string, today = todayISO()): Period[] => {
  const [ty, tm] = [Number(today.slice(0, 4)), Number(today.slice(5, 7))];
  const [ey, em] = [Number(earliest.slice(0, 4)), Number(earliest.slice(5, 7))];
  if (kind === 'year') return Array.from({ length: ty - ey + 1 }, (_, i) => ({ kind, year: ty - i, month: 1 }));
  const out: Period[] = [];
  for (let y = ty, m = tm; y > ey || (y === ey && m >= em); m === 1 ? ((y -= 1), (m = 12)) : (m -= 1)) out.push({ kind, year: y, month: m });
  return out;
};

export const periodKey = (p: Period) => (p.kind === 'month' ? `${p.year}-${pad(p.month)}` : String(p.year));
