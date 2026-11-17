import { type ReactNode, useMemo, useState } from 'react';
import { AlertTriangle, CalendarOff, CalendarRange, ChevronLeft, ChevronRight, Footprints, Truck, Users } from 'lucide-react';
import { Badge, Button, Card, CardHeader, cx, Input, PageHeader, Select } from '@/components/ui';
import { orderShift, STAFF_ROLE_LABEL } from '@/constants/resources';
import { DowntimeModal, type DowntimeSubject } from '@/features/coordinator/components/DowntimeModal';
import type { OrderSection } from '@/features/coordinator/types';
import { shortDate, weekdayOf } from '@/features/order/surveyStage';
import { addDaysISO, DOWNTIME_REASON_META, downtimeLabel, downtimeOn, downtimeRangeLabel, localISO } from '@/services/downtime';
import { useApp, useNow } from '@/services/store/AppStore';
import type { Order, ResourceDowntime } from '@/types';

/** Trục giờ của bảng: 06:00 → 24:00. */
const START_H = 6;
const END_H = 24;
const SPAN = END_H - START_H;
const HOUR_MARKS = Array.from({ length: SPAN / 2 + 1 }, (_, i) => START_H + i * 2);

/** Trạng thái đơn đã có xe / người được gán (lịch thật sự chiếm chỗ). */
const SCHEDULED = ['ASSIGNED', 'IN_TRANSIT', 'PAUSED_WAITING_CUSTOMER', 'PENDING_PAYMENT_OR_CLAIM', 'COMPLETED'];

const addDays = addDaysISO;

/** "08:30 - 12:30" (hoặc "08:30 - 11:30 (Sáng)") → [8.5, 12.5]; 00:00 ở đầu cuối hiểu là 24:00. */
const parseRange = (text: string): [number, number] | null => {
  const m = text.match(/(\d{2}):(\d{2})\s*-\s*(\d{2}):(\d{2})/);
  if (!m) return null;
  const a = Number(m[1]) + Number(m[2]) / 60;
  let b = Number(m[3]) + Number(m[4]) / 60;
  if (b <= a) b += 24;
  return [a, Math.min(b, END_H)];
};

interface Block {
  order: Order;
  range: [number, number];
  kind: 'move' | 'survey';
  /** Ngày của khối (YYYY-MM-DD) — chế độ nhiều ngày xếp theo cột này. */
  date: string;
}

/** Các khối giờ trong một hàng có chồng lên nhau không. */
const overlapping = (blocks: Block[]): Set<string> => {
  const bad = new Set<string>();
  const sorted = [...blocks].sort((a, b) => a.range[0] - b.range[0]);
  for (let i = 1; i < sorted.length; i++) {
    for (let j = 0; j < i; j++) {
      if (sorted[j]!.range[1] > sorted[i]!.range[0]) {
        bad.add(`${sorted[j]!.kind}-${sorted[j]!.order.id}`);
        bad.add(`${sorted[i]!.kind}-${sorted[i]!.order.id}`);
      }
    }
  }
  return bad;
};

const Timeline = ({ blocks, onOpen, hatched }: { blocks: Block[]; onOpen: (b: Block) => void; hatched?: string }) => {
  const clash = overlapping(blocks);
  return (
    <div className="relative h-9 rounded-lg bg-slate-50">
      {/* vạch 2 giờ */}
      {HOUR_MARKS.slice(1, -1).map((h) => (
        <span key={h} className="absolute top-0 bottom-0 w-px bg-slate-200/70" style={{ left: `${((h - START_H) / SPAN) * 100}%` }} />
      ))}
      {hatched && (
        <div className="absolute inset-0 rounded-lg bg-[repeating-linear-gradient(45deg,#fef3c7,#fef3c7_6px,#fffbeb_6px,#fffbeb_12px)] px-2 py-2 text-[11px] font-semibold text-amber-700">
          {hatched}
        </div>
      )}
      {blocks.map((b) => {
        const key = `${b.kind}-${b.order.id}`;
        const left = Math.max(0, (b.range[0] - START_H) / SPAN) * 100;
        const width = Math.max(4, ((Math.min(b.range[1], END_H) - Math.max(b.range[0], START_H)) / SPAN) * 100);
        return (
          <button
            key={key}
            type="button"
            onClick={() => onOpen(b)}
            title={`#${b.order.id} · ${b.order.customerName}`}
            className={cx(
              'absolute top-1 bottom-1 flex items-center gap-1 overflow-hidden rounded-md px-2 text-left text-[11px] font-semibold text-white hover:brightness-110',
              b.kind === 'survey' ? 'bg-blue-600' : 'bg-slate-800',
              clash.has(key) && 'ring-2 ring-rose-500 ring-offset-1',
            )}
            style={{ left: `${left}%`, width: `${width}%` }}
          >
            {b.kind === 'survey' && <Footprints className="size-3 shrink-0" />}
            <span className="truncate">#{b.order.id.replace('ORD-2026-', '')}</span>
          </button>
        );
      })}
    </div>
  );
};

/** Cột tên của một hàng — bấm để ghi / xem lịch nghỉ. */
const RowLabel = ({ title, sub, off, onClick }: { title: string; sub: string; off: ResourceDowntime | null; onClick: () => void }) => (
  <button type="button" onClick={onClick} title="Ghi lịch nghỉ" className="group min-w-0 rounded-lg py-0.5 pr-3 text-left">
    <p className="flex items-center gap-1.5 truncate text-sm font-semibold tabular-nums group-hover:text-brand">
      {title}
      <CalendarOff className="size-3.5 shrink-0 text-slate-300 opacity-0 transition-opacity group-hover:opacity-100" />
    </p>
    <p className="flex items-center gap-1.5 truncate text-[11px] text-slate-400">
      {sub}
      {off && (
        <Badge tone={DOWNTIME_REASON_META[off.reason].tone} className="!px-1.5 !py-0 !text-[10px]">
          {DOWNTIME_REASON_META[off.reason].label}
        </Badge>
      )}
    </p>
  </button>
);

const HourAxis = () => (
  <div className="relative ml-[160px] h-5 text-[11px] font-semibold text-slate-400 tabular-nums">
    {HOUR_MARKS.map((h) => (
      <span key={h} className="absolute -translate-x-1/2" style={{ left: `${((h - START_H) / SPAN) * 100}%` }}>
        {String(h).padStart(2, '0')}h
      </span>
    ))}
  </div>
);

/** Số ngày hiển thị trên bảng. 1 ngày: trục giờ trong ngày; nhiều ngày: mỗi cột là một ngày. */
const SPANS = [1, 3, 7, 15, 30] as const;
type Span = (typeof SPANS)[number];

const isWeekend = (iso: string) => {
  const [y, m, d] = iso.split('-').map(Number);
  const w = new Date(y!, m! - 1, d!).getDay();
  return w === 0 || w === 6;
};
const WEEKDAY_SHORT = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];
const weekdayShort = (iso: string) => {
  const [y, m, d] = iso.split('-').map(Number);
  return WEEKDAY_SHORT[new Date(y!, m! - 1, d!).getDay()]!;
};

/** Khối trùng giờ — chỉ so các khối trong cùng một ngày. */
const clashKeys = (blocks: Block[]): Set<string> => {
  const out = new Set<string>();
  new Set(blocks.map((b) => b.date)).forEach((day) => overlapping(blocks.filter((b) => b.date === day)).forEach((k) => out.add(k)));
  return out;
};

const DayAxis = ({ days, today }: { days: string[]; today: string }) => (
  <div className="ml-[160px] grid gap-1 pb-1 text-center text-[11px] font-semibold tabular-nums" style={{ gridTemplateColumns: `repeat(${days.length}, minmax(0, 1fr))` }}>
    {days.map((d) => (
      <span key={d} className={cx('rounded-md py-0.5', d === today ? 'bg-brand text-white' : isWeekend(d) ? 'text-rose-400' : 'text-slate-400')}>
        {days.length <= 7 ? `${weekdayShort(d)} ${shortDate(d)}` : (
          <>
            <span className="block text-[9px] font-medium opacity-70">{weekdayShort(d)}</span>
            {d.slice(8)}
          </>
        )}
      </span>
    ))}
  </div>
);

/**
 * Một hàng xe / nhân sự theo nhiều ngày. Ngày nghỉ tô sọc; ≤ 7 ngày hiện mã đơn, dài hơn hiện số việc.
 * Bấm vào ô trống / số việc → mở ngày đó ở chế độ 1 ngày.
 */
const DayGrid = ({
  days,
  today,
  blocks,
  offOf,
  onOpen,
  onPickDay,
}: {
  days: string[];
  today: string;
  blocks: Block[];
  offOf: (day: string) => ResourceDowntime | null;
  onOpen: (b: Block) => void;
  onPickDay: (day: string) => void;
}) => {
  const clash = clashKeys(blocks);
  const roomy = days.length <= 7;
  return (
    <div className="grid gap-1" style={{ gridTemplateColumns: `repeat(${days.length}, minmax(0, 1fr))` }}>
      {days.map((day) => {
        const off = offOf(day);
        const items = blocks.filter((b) => b.date === day).sort((a, b) => a.range[0] - b.range[0]);
        const bad = items.some((b) => clash.has(`${b.kind}-${b.order.id}`));
        return (
          <div
            key={day}
            role="button"
            tabIndex={0}
            onClick={() => onPickDay(day)}
            onKeyDown={(e) => e.key === 'Enter' && onPickDay(day)}
            title={off ? downtimeLabel(off) : `Xem ${shortDate(day)} theo giờ`}
            className={cx(
              'flex h-9 cursor-pointer items-center gap-0.5 overflow-hidden rounded-md px-0.5 hover:ring-1 hover:ring-slate-300',
              off
                ? 'bg-[repeating-linear-gradient(45deg,#fef3c7,#fef3c7_6px,#fffbeb_6px,#fffbeb_12px)]'
                : isWeekend(day)
                  ? 'bg-slate-100/70'
                  : 'bg-slate-50',
              day === today && 'ring-1 ring-brand/40',
              bad && 'ring-2 ring-rose-500',
            )}
          >
            {off && items.length === 0 && roomy && (
              <span className="truncate px-1 text-[10px] font-semibold text-amber-700">{DOWNTIME_REASON_META[off.reason].label}</span>
            )}
            {roomy
              ? items.slice(0, 2).map((b) => (
                  <button
                    key={`${b.kind}-${b.order.id}`}
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      onOpen(b);
                    }}
                    title={`#${b.order.id} · ${b.order.customerName}`}
                    className={cx(
                      'flex h-7 min-w-0 flex-1 items-center justify-center gap-0.5 rounded px-1 text-[10px] font-semibold text-white hover:brightness-110',
                      b.kind === 'survey' ? 'bg-blue-600' : 'bg-slate-800',
                    )}
                  >
                    {b.kind === 'survey' && <Footprints className="size-2.5 shrink-0" />}
                    <span className="truncate">{b.order.id.replace('ORD-2026-', '')}</span>
                  </button>
                ))
              : items.length > 0 && (
                  <span
                    className={cx(
                      'mx-auto grid size-6 place-items-center rounded-full text-[10px] font-bold text-white tabular-nums',
                      items.every((b) => b.kind === 'survey') ? 'bg-blue-600' : 'bg-slate-800',
                    )}
                  >
                    {items.length}
                  </span>
                )}
            {roomy && items.length > 2 && <span className="shrink-0 px-0.5 text-[10px] font-semibold text-slate-500">+{items.length - 2}</span>}
          </div>
        );
      })}
    </div>
  );
};

/** Lịch xe & nhân sự — xem 1 ngày theo giờ, hoặc 3 / 7 / 15 / 30 ngày theo ngày. */
export const ScheduleBoard = ({ onOpen }: { onOpen: (id: string, s?: OrderSection) => void }) => {
  const { state } = useApp();
  const now = useNow(60_000);
  const today = localISO(new Date(now));
  const [date, setDate] = useState(today);
  // Mỗi phần (Xe / Nhân sự) chọn số ngày hiển thị riêng, bắt đầu từ ngày đang chọn ở đầu trang.
  const [vehicleSpan, setVehicleSpan] = useState<Span>(1);
  const [staffSpan, setStaffSpan] = useState<Span>(1);
  const [subject, setSubject] = useState<DowntimeSubject | null>(null);
  const daysOf = (span: Span) => Array.from({ length: span }, (_, i) => addDays(date, i));
  // Lấy dữ liệu theo khoảng rộng nhất của hai phần; từng phần tự lọc theo số ngày của mình.
  const last = addDays(date, Math.max(vehicleSpan, staffSpan) - 1);

  const offOf = (target: 'VEHICLE' | 'STAFF', id: string) => (day: string) => downtimeOn(state.resources.downtimes, target, id, day);
  /** Lịch nghỉ đầu tiên chạm khoảng đang xem — hiện nhãn cạnh tên. */
  const offInView = (target: 'VEHICLE' | 'STAFF', id: string, span: Span) => {
    const end = addDays(date, span - 1);
    return state.resources.downtimes.find((d) => d.target === target && d.resourceId === id && d.from <= end && d.to >= date) ?? null;
  };
  // Lịch nghỉ chưa kết thúc — để Điều phối thấy trước ai / xe nào sắp vắng.
  const upcoming = state.resources.downtimes
    .filter((d) => d.to >= today)
    .sort((a, b) => a.from.localeCompare(b.from))
    .map((d) => ({
      d,
      name:
        d.target === 'VEHICLE'
          ? (state.resources.vehicles.find((v) => v.id === d.resourceId)?.plateNumber ?? '—')
          : (state.resources.staff.find((m) => m.id === d.resourceId)?.name ?? '—'),
    }));

  const moves = useMemo(
    () =>
      state.orders
        .filter((o) => o.scheduledDate >= date && o.scheduledDate <= last && SCHEDULED.includes(o.status))
        .map((o) => ({ o, day: o.scheduledDate, range: parseRange(orderShift(o)) }))
        .filter((x): x is { o: Order; day: string; range: [number, number] } => x.range !== null),
    [state.orders, date, last],
  );
  const surveys = useMemo(
    () =>
      state.orders
        .filter((o) => o.status === 'NEW_PENDING_SURVEY' && o.surveyAssignment && !o.surveyReport && !!o.bookingDetails?.surveyDate)
        .filter((o) => o.bookingDetails!.surveyDate >= date && o.bookingDetails!.surveyDate <= last)
        .map((o) => ({ o, day: o.bookingDetails!.surveyDate, range: parseRange(o.bookingDetails?.surveyContactSlot ?? '') }))
        .filter((x): x is { o: Order; day: string; range: [number, number] } => x.range !== null),
    [state.orders, date, last],
  );
  const unscheduled = state.orders.filter((o) => o.status === 'QUOTED_PENDING_SCHEDULE' && o.scheduledDate === date);

  const vehicleRows = state.resources.vehicles.map((v) => ({
    v,
    blocks: moves.filter((m) => m.o.vehicleIds.includes(v.id)).map<Block>((m) => ({ order: m.o, range: m.range, kind: 'move', date: m.day })),
  }));
  const staffRows = state.resources.staff.map((s) => ({
    s,
    blocks: [
      ...moves.filter((m) => m.o.crewIds.includes(s.id)).map<Block>((m) => ({ order: m.o, range: m.range, kind: 'move', date: m.day })),
      ...surveys.filter((x) => x.o.surveyAssignment?.surveyorId === s.id).map<Block>((x) => ({ order: x.o, range: x.range, kind: 'survey', date: x.day })),
    ],
  }));
  // Số liệu đầu trang tính cho ngày đang chọn.
  const onDate = (blocks: Block[]) => blocks.filter((b) => b.date === date);
  const clashes = [...vehicleRows, ...staffRows].filter((r) => clashKeys(onDate(r.blocks)).size > 0).length;
  const busyStaff = staffRows.filter((r) => onDate(r.blocks).length > 0).length;
  const busyVehicles = vehicleRows.filter((r) => onDate(r.blocks).length > 0).length;
  const movesToday = moves.filter((m) => m.day === date).length;

  const openBlock = (b: Block) => onOpen(b.order.id, b.kind === 'survey' ? 'quote' : 'dispatch');
  const dayLabel = date === today ? 'Hôm nay' : date === addDays(today, 1) ? 'Ngày mai' : `${weekdayOf(date)} ${shortDate(date)}`;

  /** Bảng của một phần: 1 ngày → trục giờ; nhiều ngày → mỗi cột một ngày (bấm ô để về xem ngày đó theo giờ). */
  const board = (
    span: Span,
    setSpan: (s: Span) => void,
    rows: { key: string; label: ReactNode; blocks: Block[]; off: (day: string) => ResourceDowntime | null }[],
  ) => {
    const single = span === 1;
    const days = daysOf(span);
    // 1 ngày giữ bề rộng cũ; nhiều ngày thì mỗi cột tối thiểu đủ đọc, quá rộng thì cuộn ngang.
    const minWidth = span <= 7 ? 760 : 160 + span * (span <= 15 ? 44 : 34);
    const pickDay = (day: string) => {
      setDate(day);
      setSpan(1);
    };
    return (
      <div className="overflow-x-auto px-5 pb-5">
        <div style={{ minWidth }}>
          {single ? <HourAxis /> : <DayAxis days={days} today={today} />}
          {rows.map((r) => {
            const offToday = r.off(date);
            return (
              <div key={r.key} className="grid grid-cols-[160px_1fr] items-center border-b border-slate-50 py-1.5">
                {r.label}
                {single ? (
                  <Timeline blocks={r.blocks.filter((b) => b.date === date)} onOpen={openBlock} hatched={offToday ? downtimeLabel(offToday) : undefined} />
                ) : (
                  <DayGrid days={days} today={today} blocks={r.blocks.filter((b) => b.date <= days[days.length - 1]!)} offOf={r.off} onOpen={openBlock} onPickDay={pickDay} />
                )}
              </div>
            );
          })}
        </div>
      </div>
    );
  };

  const spanSelect = (span: Span, setSpan: (s: Span) => void, label: string) => (
    <Select value={span} onChange={(e) => setSpan(Number(e.target.value) as Span)} aria-label={label} className="!h-8 !w-auto shrink-0 !px-2.5 !pr-7 text-xs font-semibold">
      {SPANS.map((n) => (
        <option key={n} value={n}>
          {n} ngày
        </option>
      ))}
    </Select>
  );

  return (
    <>
      <PageHeader
        eyebrow="Lịch xe & nhân sự"
        title={dayLabel}
        subtitle="Lịch vận chuyển và khảo sát. Bấm vào một khối để mở đơn."
        actions={
          <div className="flex items-center gap-2">
            <Button size="sm" variant="secondary" icon={ChevronLeft} onClick={() => setDate((d) => addDays(d, -1))} aria-label="Ngày trước" />
            <Input type="date" value={date} onChange={(e) => e.target.value && setDate(e.target.value)} className="h-9 w-40 tabular-nums" />
            <Button size="sm" variant="secondary" icon={ChevronRight} onClick={() => setDate((d) => addDays(d, 1))} aria-label="Ngày sau" />
            {date !== today && (
              <Button size="sm" variant="dark" onClick={() => setDate(today)}>
                Hôm nay
              </Button>
            )}
          </div>
        }
      />

      <div className="mb-4 grid grid-cols-2 gap-2 lg:grid-cols-4">
        {[
          { label: 'Chuyến vận chuyển', value: movesToday, tone: 'text-slate-900' },
          { label: 'Xe có lịch', value: `${busyVehicles}/${state.resources.vehicles.length}`, tone: 'text-slate-900' },
          { label: 'Nhân sự có lịch', value: `${busyStaff}/${state.resources.staff.length}`, tone: 'text-slate-900' },
          { label: 'Trùng lịch', value: clashes, tone: clashes > 0 ? 'text-rose-600' : 'text-emerald-600' },
        ].map((x) => (
          <Card key={x.label} className="!py-3">
            <p className={cx('text-2xl font-bold tabular-nums', x.tone)}>{x.value}</p>
            <p className="text-xs font-medium text-slate-500">{x.label}</p>
          </Card>
        ))}
      </div>

      {upcoming.length > 0 && (
        <Card className="mb-4">
          <CardHeader title={`Lịch nghỉ (${upcoming.length})`} icon={CalendarOff} subtitle="Đang và sắp nghỉ — bấm để sửa. Ghi lịch mới bằng cách bấm tên xe / nhân sự bên dưới." />
          <div className="flex flex-wrap gap-2">
            {upcoming.map(({ d, name }) => (
              <button
                key={d.id}
                type="button"
                onClick={() => setSubject({ target: d.target, id: d.resourceId, name })}
                className={cx(
                  'flex items-center gap-2 rounded-xl border px-3 py-2 text-left text-sm hover:border-slate-400',
                  d.from <= date && date <= d.to ? 'border-amber-300 bg-amber-50/50' : 'border-slate-200',
                )}
              >
                <Badge tone={DOWNTIME_REASON_META[d.reason].tone}>{DOWNTIME_REASON_META[d.reason].label}</Badge>
                <span className="font-semibold">{name}</span>
                <span className="text-slate-500 tabular-nums">{downtimeRangeLabel(d)}</span>
              </button>
            ))}
          </div>
        </Card>
      )}

      {unscheduled.length > 0 && (
        <Card className="mb-4 border-amber-200">
          <CardHeader title={`Đã cọc nhưng chưa xếp xe (${unscheduled.length})`} icon={AlertTriangle} subtitle={`Đơn chuyển vào ${dayLabel.toLowerCase()}`} />
          <ul className="divide-y divide-slate-100">
            {unscheduled.map((o) => (
              <li key={o.id} className="flex flex-wrap items-center gap-3 py-2.5">
                <span className="text-sm font-semibold tabular-nums">#{o.id.replace('ORD-2026-', 'ORD-')}</span>
                <span className="min-w-0 flex-1 truncate text-sm text-slate-600">
                  {o.customerName} · bắt đầu {o.timeSlot}
                </span>
                <Button size="sm" onClick={() => onOpen(o.id, 'dispatch')}>
                  Xếp xe
                </Button>
              </li>
            ))}
          </ul>
        </Card>
      )}

      <Card className="mb-4" padded={false}>
        <div className="p-5 pb-3">
          <CardHeader
            title="Xe"
            icon={Truck}
            subtitle="Bấm biển số để ghi bảo dưỡng / sửa chữa"
            action={spanSelect(vehicleSpan, setVehicleSpan, 'Số ngày hiển thị lịch xe')}
          />
        </div>
        {board(
          vehicleSpan,
          setVehicleSpan,
          vehicleRows.map(({ v, blocks }) => ({
                key: v.id,
                blocks,
                off: offOf('VEHICLE', v.id),
                label: (
                  <RowLabel
                    title={v.plateNumber}
                    sub={`${String(v.capacityTons).replace('.', ',')} tấn`}
                    off={offInView('VEHICLE', v.id, vehicleSpan)}
                    onClick={() => setSubject({ target: 'VEHICLE', id: v.id, name: `Xe ${v.plateNumber}` })}
                  />
                ),
              })),
        )}
      </Card>

      <Card padded={false}>
        <div className="flex flex-wrap items-center justify-between gap-2 p-5 pb-3">
          <CardHeader title="Nhân sự" icon={Users} subtitle="Vận chuyển và khảo sát đã phân · Bấm tên để ghi nghỉ phép" />
          <span className="flex flex-wrap items-center gap-3 text-xs text-slate-500">
            <span className="flex items-center gap-1.5">
              <span className="size-2.5 rounded-sm bg-slate-800" /> Vận chuyển
            </span>
            <span className="flex items-center gap-1.5">
              <span className="size-2.5 rounded-sm bg-blue-600" /> Khảo sát
            </span>
            <span className="flex items-center gap-1.5">
              <span className="size-2.5 rounded-sm ring-2 ring-rose-500" /> Trùng lịch
            </span>
            {spanSelect(staffSpan, setStaffSpan, 'Số ngày hiển thị lịch nhân sự')}
          </span>
        </div>
        {board(
          staffSpan,
          setStaffSpan,
          staffRows.map(({ s, blocks }) => ({
                key: s.id,
                blocks,
                off: offOf('STAFF', s.id),
                label: (
                  <RowLabel title={s.name} sub={STAFF_ROLE_LABEL[s.role]} off={offInView('STAFF', s.id, staffSpan)} onClick={() => setSubject({ target: 'STAFF', id: s.id, name: s.name })} />
                ),
              })),
        )}
      </Card>

      <p className="mt-3 flex items-center gap-1.5 text-xs text-slate-400">
        <CalendarRange className="size-3.5" /> Mỗi chuyến giữ chỗ theo thời gian dự kiến lúc khảo sát (chưa có thì mặc định 4 giờ); lịch khảo sát theo khung giờ khách hẹn.
      </p>

      <DowntimeModal key={subject ? `${subject.target}-${subject.id}` : 'none'} subject={subject} defaultFrom={date} onClose={() => setSubject(null)} />
    </>
  );
};
