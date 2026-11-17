import { useMemo, useState } from 'react';
import {
  AlertTriangle,
  ArrowRight,
  CalendarRange,
  CheckCircle2,
  ClipboardCheck,
  Clock,
  FileText,
  type LucideIcon,
  MessageSquare,
  Phone,
  Siren,
  Truck,
  UserPlus,
  Wallet,
} from 'lucide-react';
import { Badge, Button, Card, cx, StatusBadge } from '@/components/ui';
import { AUTO_CANCEL_MS, TELESALES_THRESHOLD_MS } from '@/constants/pricing';
import type { CoordTab } from '@/features/coordinator/types';
import { AutoCancelCountdown } from '@/features/order/components/AutoCancelCountdown';
import { coordinatorSurveyBadge, shortDate, SURVEY_STAGE_META, surveyPhaseOf, surveyStageOf, weekdayOf } from '@/features/order/surveyStage';
import { formatVND } from '@/services/format';
import { computeTotals } from '@/services/pricing';
import { useApp, useNow } from '@/services/store/AppStore';
import type { Order } from '@/types';

type BucketId = 'ASSIGN' | 'SURVEYING' | 'QUOTE' | 'NEGOTIATE' | 'CONFIRM' | 'SCHEDULE' | 'RUNNING' | 'SETTLE';

/**
 * Các nhóm việc theo đúng flow: phân người khảo sát → đang khảo sát → lập báo giá → (thương lượng) →
 * khách xác nhận & cọc → xếp xe → thực hiện → quyết toán. `mine`: việc nằm ở Điều phối; còn lại đang chờ người khác.
 */
const BUCKETS: { id: BucketId; label: string; mine: boolean; match: (o: Order) => boolean; action: string; tab: CoordTab; icon: LucideIcon }[] = [
  { id: 'ASSIGN', label: 'Cần phân người khảo sát', mine: true, match: (o) => o.status === 'NEW_PENDING_SURVEY' && surveyPhaseOf(o) === 'ASSIGN', action: 'Phân người', tab: 'editor', icon: UserPlus },
  { id: 'SURVEYING', label: 'Đang khảo sát', mine: false, match: (o) => o.status === 'NEW_PENDING_SURVEY' && surveyPhaseOf(o) === 'SURVEYING', action: 'Xem', tab: 'editor', icon: ClipboardCheck },
  { id: 'QUOTE', label: 'Cần lập báo giá', mine: true, match: (o) => o.status === 'NEW_PENDING_SURVEY' && surveyPhaseOf(o) === 'QUOTE', action: 'Lập báo giá', tab: 'editor', icon: FileText },
  { id: 'NEGOTIATE', label: 'Khách thương lượng', mine: true, match: (o) => o.status === 'SURVEYED_PENDING_CONFIRM' && Boolean(o.negotiation), action: 'Phản hồi', tab: 'editor', icon: MessageSquare },
  { id: 'CONFIRM', label: 'Chờ khách xác nhận & cọc', mine: false, match: (o) => o.status === 'SURVEYED_PENDING_CONFIRM' && !o.negotiation, action: 'Xem báo giá', tab: 'editor', icon: Clock },
  { id: 'SCHEDULE', label: 'Đã cọc · cần xếp xe', mine: true, match: (o) => o.status === 'QUOTED_PENDING_SCHEDULE', action: 'Xếp xe', tab: 'dispatch', icon: CalendarRange },
  { id: 'RUNNING', label: 'Đang thực hiện', mine: false, match: (o) => ['ASSIGNED', 'IN_TRANSIT', 'PAUSED_WAITING_CUSTOMER'].includes(o.status), action: 'Theo dõi', tab: 'editor', icon: Truck },
  { id: 'SETTLE', label: 'Chờ quyết toán', mine: true, match: (o) => o.status === 'PENDING_PAYMENT_OR_CLAIM', action: 'Quyết toán', tab: 'editor', icon: Wallet },
];

interface UrgentItem {
  order: Order;
  level: 'danger' | 'warning';
  reason: string;
  action: string;
  tab: CoordTab;
  rank: number;
}

const shortId = (id: string) => id.replace('ORD-2026-', 'ORD-');

const localISO = (epoch: number) => {
  const d = new Date(epoch);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
};

const hoursText = (ms: number) => {
  const h = Math.floor(ms / 3_600_000);
  const m = Math.floor((ms % 3_600_000) / 60_000);
  return h > 0 ? `${h}h${m > 0 ? `${String(m).padStart(2, '0')}` : ''}` : `${m} phút`;
};

/** Một đơn chỉ lấy lý do gấp nhất — tránh lặp cùng một đơn nhiều dòng. */
const urgencyOf = (o: Order, now: number): UrgentItem | null => {
  if (o.flags.isVehicleIncident) {
    return { order: o, level: 'danger', reason: 'Sự cố xe giữa đường — cần điều xe thay thế', action: 'Xử lý sự cố', tab: 'incident', rank: 0 };
  }
  if (o.status === 'PAUSED_WAITING_CUSTOMER' || (o.flags.waitingMinutes > 30 && ['ASSIGNED', 'IN_TRANSIT'].includes(o.status))) {
    return { order: o, level: 'danger', reason: `Đội xe đang chờ khách${o.flags.waitingMinutes > 0 ? ` · ${o.flags.waitingMinutes} phút` : ''}`, action: 'Xem', tab: 'editor', rank: 1 };
  }
  if (o.flags.hasFieldDiscrepancyLock) {
    return { order: o, level: 'warning', reason: 'Phát sinh hiện trường — chờ khách duyệt báo giá điều chỉnh', action: 'Xem', tab: 'editor', rank: 2 };
  }
  if (o.status === 'SURVEYED_PENDING_CONFIRM' && o.autoCancelDeadline) {
    const left = o.autoCancelDeadline - now;
    if (left <= 0) return { order: o, level: 'danger', reason: 'Quá hạn 24h chưa chốt — sẽ bị tự hủy', action: 'Gọi khách', tab: 'editor', rank: 3 };
    if (AUTO_CANCEL_MS - left > TELESALES_THRESHOLD_MS) {
      return { order: o, level: 'warning', reason: `Chưa chốt báo giá · còn ${hoursText(left)} trước khi tự hủy`, action: 'Gọi khách', tab: 'editor', rank: 4 };
    }
  }
  return null;
};

const scheduleKey = (o: Order) => `${o.scheduledDate} ${o.timeSlot}`;

const URGENT_PREVIEW = 4;
const PAGE_SIZE = 20;

const addDays = (iso: string, n: number) => {
  const [y, m, d] = iso.split('-').map(Number);
  return localISO(new Date(y!, m! - 1, d! + n).getTime());
};

type FilterDef = { id: string; label: string; test: (o: Order, today: string) => boolean };

const DATE_FILTERS: FilterDef[] = [
  { id: 'late', label: 'Đã qua ngày', test: (o, t) => o.scheduledDate < t },
  { id: 'today', label: 'Hôm nay', test: (o, t) => o.scheduledDate === t },
  { id: 'tomorrow', label: 'Ngày mai', test: (o, t) => o.scheduledDate === addDays(t, 1) },
  { id: 'week', label: '7 ngày tới', test: (o, t) => o.scheduledDate >= t && o.scheduledDate <= addDays(t, 7) },
];

const KIND_FILTERS: FilterDef[] = (['VIDEO_CALL', 'ON_SITE', 'PHOTOS'] as const).map((k) => ({
  id: k,
  label: SURVEY_STAGE_META[k].short,
  test: (o) => surveyStageOf(o) === k,
}));

/** Bộ lọc nhanh theo từng nhóm việc — giúp thu hẹp khi có nhiều đơn. */
const FILTERS: Partial<Record<BucketId, FilterDef[]>> = {
  ASSIGN: KIND_FILTERS,
  SURVEYING: KIND_FILTERS,
  SCHEDULE: DATE_FILTERS,
  RUNNING: DATE_FILTERS,
  SETTLE: DATE_FILTERS,
};

/** Nhãn nhóm theo ngày chuyển cho danh sách dài. */
const dayGroup = (iso: string, today: string): { key: string; label: string; late: boolean } => {
  if (iso < today) return { key: 'late', label: 'Đã qua ngày chuyển', late: true };
  if (iso === today) return { key: iso, label: 'Hôm nay', late: false };
  if (iso === addDays(today, 1)) return { key: iso, label: 'Ngày mai', late: false };
  return { key: iso, label: `${weekdayOf(iso)} ${shortDate(iso)}`, late: false };
};

const DATE_BUCKETS: BucketId[] = ['SCHEDULE', 'RUNNING', 'SETTLE'];

const surveyDateKey = (o: Order) => o.bookingDetails?.surveyDate || '9999-12-31';

const sortBucket = (id: BucketId, list: Order[]): Order[] => {
  const copy = [...list];
  switch (id) {
    case 'ASSIGN':
    case 'SURVEYING':
      // Lịch hẹn khảo sát gần nhất lên trước; khách gửi ảnh (không hẹn) theo thứ tự tạo đơn.
      return copy.sort((a, b) => surveyDateKey(a).localeCompare(surveyDateKey(b)) || a.createdAt - b.createdAt);
    case 'QUOTE':
      return copy.sort((a, b) => (a.surveyReport?.submittedAt ?? a.createdAt) - (b.surveyReport?.submittedAt ?? b.createdAt));
    case 'NEGOTIATE':
      return copy.sort((a, b) => (a.negotiation?.requestedAt ?? 0) - (b.negotiation?.requestedAt ?? 0));
    case 'CONFIRM':
      return copy.sort((a, b) => (a.autoCancelDeadline ?? Infinity) - (b.autoCancelDeadline ?? Infinity));
    default:
      return copy.sort((a, b) => scheduleKey(a).localeCompare(scheduleKey(b)));
  }
};

const SORT_HINT: Record<BucketId, string> = {
  ASSIGN: 'Lịch hẹn khảo sát gần nhất lên trước',
  SURVEYING: 'Lịch hẹn khảo sát gần nhất lên trước',
  QUOTE: 'Chờ lâu nhất lên trước',
  NEGOTIATE: 'Chờ lâu nhất lên trước',
  CONFIRM: 'Sắp hết hạn 24h lên trước',
  SCHEDULE: 'Ngày chuyển gần nhất lên trước',
  RUNNING: 'Ngày chuyển gần nhất lên trước',
  SETTLE: 'Ngày chuyển gần nhất lên trước',
};

const BucketDetail = ({ id, order }: { id: BucketId; order: Order }) => {
  if (id === 'ASSIGN' || id === 'SURVEYING' || id === 'QUOTE') {
    const b = coordinatorSurveyBadge(order);
    return <Badge tone={b.tone}>{b.label}</Badge>;
  }
  if (id === 'NEGOTIATE' && order.negotiation) {
    return (
      <span className="max-w-64 truncate rounded-full bg-amber-50 px-2.5 py-0.5 text-xs font-medium text-amber-800 ring-1 ring-amber-600/20 ring-inset" title={order.negotiation.message}>
        “{order.negotiation.message}”
      </span>
    );
  }
  if (id === 'CONFIRM' && order.autoCancelDeadline) return <AutoCancelCountdown deadline={order.autoCancelDeadline} compact />;
  if (id === 'SCHEDULE' && order.quoteItems.length > 0) return <Badge tone="neutral">{formatVND(computeTotals(order).grandTotal)}</Badge>;
  if (id === 'SETTLE') return <Badge tone="warning">Còn thu {formatVND(computeTotals(order).remaining)}</Badge>;
  return <StatusBadge status={order.status} />;
};

/** Trang đầu của Điều phối: việc gấp trước, rồi việc theo từng bước quy trình. */
export const TodoView = ({ onOpen, query }: { onOpen: (id: string, t: CoordTab) => void; query: string }) => {
  const { state } = useApp();
  const now = useNow(30_000);
  const today = localISO(now);

  const orders = useMemo(() => {
    const q = query.trim().toLowerCase();
    return q ? state.orders.filter((o) => `${o.id} ${o.customerName} ${o.phone}`.toLowerCase().includes(q)) : state.orders;
  }, [state.orders, query]);

  const urgent = useMemo(
    () =>
      orders
        .map((o) => urgencyOf(o, now))
        .filter((x): x is UrgentItem => x !== null)
        .sort((a, b) => a.rank - b.rank),
    [orders, now],
  );
  const urgentIds = new Set(urgent.map((u) => u.order.id));

  const buckets = BUCKETS.map((b) => ({ ...b, orders: sortBucket(b.id, orders.filter(b.match)) }));
  const [picked, setPicked] = useState<BucketId | null>(null);
  // Mặc định mở nhóm việc đầu tiên đang chờ chính Điều phối.
  const active =
    buckets.find((b) => b.id === picked) ?? buckets.find((b) => b.mine && b.orders.length > 0) ?? buckets.find((b) => b.orders.length > 0) ?? buckets[0]!;
  const [showAllUrgent, setShowAllUrgent] = useState(false);
  const [filterId, setFilterId] = useState<string | null>(null);
  const [limit, setLimit] = useState(PAGE_SIZE);

  const filters = FILTERS[active.id] ?? [];
  const filter = filters.find((f) => f.id === filterId) ?? null;
  const filtered = filter ? active.orders.filter((o) => filter.test(o, today)) : active.orders;
  const visible = filtered.slice(0, limit);
  const pickBucket = (id: BucketId) => {
    setPicked(id);
    setFilterId(null);
    setLimit(PAGE_SIZE);
  };
  const shownUrgent = showAllUrgent ? urgent : urgent.slice(0, URGENT_PREVIEW);

  return (
    <div className="space-y-4">
      {urgent.length === 0 ? (
        <div className="flex items-center gap-2.5 rounded-2xl border border-emerald-200 bg-emerald-50/70 px-4 py-3 text-sm font-medium text-emerald-800">
          <CheckCircle2 className="size-4 shrink-0" /> Không có việc khẩn cấp — các đơn đang chạy đúng tiến độ.
        </div>
      ) : (
        <Card padded={false} className="overflow-hidden border-rose-200">
          <div className="flex items-center gap-2 border-b border-rose-100 bg-rose-50/70 px-4 py-3">
            <AlertTriangle className="size-4 text-rose-600" />
            <p className="text-sm font-semibold text-rose-900">Cần xử lý ngay</p>
            <span className="rounded-full bg-rose-600 px-2 text-xs font-semibold text-white tabular-nums">{urgent.length}</span>
          </div>
          <ul className="divide-y divide-slate-100">
            {shownUrgent.map((u) => (
              <li key={u.order.id} className="flex flex-wrap items-center gap-3 px-4 py-3">
                <span className={cx('grid size-8 shrink-0 place-items-center rounded-lg', u.level === 'danger' ? 'bg-rose-100 text-rose-600' : 'bg-amber-100 text-amber-600')}>
                  {u.tab === 'incident' ? <Siren className="size-4" /> : <AlertTriangle className="size-4" />}
                </span>
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-semibold text-slate-900">
                    <span className="tabular-nums">#{shortId(u.order.id)}</span> · {u.order.customerName}
                  </p>
                  <p className={cx('text-xs font-medium', u.level === 'danger' ? 'text-rose-700' : 'text-amber-700')}>{u.reason}</p>
                </div>
                {u.action === 'Gọi khách' && (
                  <a
                    href={`tel:${u.order.phone.replace(/[^\d+]/g, '')}`}
                    className="inline-flex h-9 items-center gap-1.5 rounded-xl border border-slate-200 px-3 text-sm font-semibold text-slate-700 hover:border-slate-300"
                  >
                    <Phone className="size-4" /> {u.order.phone}
                  </a>
                )}
                <Button size="sm" variant={u.level === 'danger' ? 'primary' : 'dark'} onClick={() => onOpen(u.order.id, u.tab)}>
                  {u.action === 'Gọi khách' ? 'Mở đơn' : u.action} <ArrowRight className="size-4" />
                </Button>
              </li>
            ))}
          </ul>
          {urgent.length > URGENT_PREVIEW && (
            <button
              type="button"
              onClick={() => setShowAllUrgent((v) => !v)}
              className="w-full border-t border-slate-100 px-4 py-2.5 text-sm font-semibold text-rose-700 hover:bg-rose-50/60"
            >
              {showAllUrgent ? 'Thu gọn' : `Xem thêm ${urgent.length - URGENT_PREVIEW} việc khẩn cấp`}
            </button>
          )}
        </Card>
      )}

      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4" role="tablist" aria-label="Nhóm việc">
        {buckets.map((b) => {
          const sel = b.id === active.id;
          return (
            <button
              key={b.id}
              type="button"
              role="tab"
              aria-selected={sel}
              onClick={() => pickBucket(b.id)}
              className={cx(
                'flex items-center gap-3 rounded-2xl border px-4 py-3 text-left transition-colors',
                sel ? 'border-slate-900 bg-slate-900 text-white' : 'border-slate-200 bg-white text-slate-700 hover:border-slate-300',
              )}
            >
              <b.icon className={cx('size-5 shrink-0', sel ? 'text-brand' : 'text-slate-400')} />
              <span className="min-w-0 flex-1">
                <span className="block text-2xl leading-none font-bold tabular-nums">{b.orders.length}</span>
                <span className={cx('mt-1 block truncate text-xs font-medium', sel ? 'text-white/70' : 'text-slate-500')}>{b.label}</span>
                <span className={cx('mt-0.5 block text-[10px] font-semibold tracking-wide uppercase', b.mine ? (sel ? 'text-brand' : 'text-brand/80') : sel ? 'text-white/40' : 'text-slate-400')}>
                  {b.mine ? 'Việc của bạn' : 'Đang chờ'}
                </span>
              </span>
            </button>
          );
        })}
      </div>

      <Card padded={false}>
        {/* Dính khi cuộn danh sách dài — luôn thấy đang xem nhóm nào, lọc gì. */}
        <div className="sticky top-16 z-10 space-y-2.5 rounded-t-2xl border-b border-slate-100 bg-white/95 px-4 py-3 backdrop-blur lg:top-0">
          <div className="flex items-center justify-between gap-2">
            <p className="text-sm font-semibold text-slate-900">
              {active.label} <span className="font-medium text-slate-400 tabular-nums">· {filtered.length}</span>
            </p>
            <span className="hidden text-xs text-slate-500 sm:inline">
              {SORT_HINT[active.id]}
            </span>
          </div>
          {filters.length > 0 && (
            <div className="scrollbar-none -mx-1 flex gap-1.5 overflow-x-auto px-1">
              {[
                { id: null as string | null, label: 'Tất cả', count: active.orders.length },
                ...filters.map((f) => ({ id: f.id as string | null, label: f.label, count: active.orders.filter((o) => f.test(o, today)).length })),
              ].map((f) => {
                const sel = filterId === f.id;
                return (
                  <button
                    key={f.label}
                    type="button"
                    aria-pressed={sel}
                    disabled={f.count === 0 && !sel}
                    onClick={() => {
                      setFilterId(f.id);
                      setLimit(PAGE_SIZE);
                    }}
                    className={cx(
                      'inline-flex h-8 shrink-0 items-center gap-1.5 rounded-full border px-3 text-xs font-semibold transition-colors disabled:opacity-40',
                      sel ? 'border-slate-900 bg-slate-900 text-white' : 'border-slate-200 text-slate-600 hover:border-slate-300',
                      f.id === 'late' && f.count > 0 && !sel && 'border-rose-200 text-rose-700',
                    )}
                  >
                    {f.label} <span className="tabular-nums opacity-70">{f.count}</span>
                  </button>
                );
              })}
            </div>
          )}
        </div>
        {filtered.length === 0 ? (
          <p className="px-4 py-10 text-center text-sm text-slate-400">Không có đơn nào{filter ? ' khớp bộ lọc' : ' ở bước này'}.</p>
        ) : (
          <ul className="divide-y divide-slate-100">
            {visible.map((o, i) => {
              // Nhóm theo ngày chuyển (chỉ với các bước sắp theo ngày) — chèn tiêu đề khi sang ngày mới.
              const group = DATE_BUCKETS.includes(active.id) ? dayGroup(o.scheduledDate, today) : null;
              const prev = i > 0 && group ? dayGroup(visible[i - 1]!.scheduledDate, today) : null;
              const showGroup = group !== null && (!prev || prev.key !== group.key);
              const groupCount = group ? filtered.filter((x) => dayGroup(x.scheduledDate, today).key === group.key).length : 0;
              return (
                <li key={o.id}>
                  {showGroup && group && (
                    <p
                      className={cx(
                        'flex items-center justify-between bg-slate-50 px-4 py-1.5 text-[11px] font-semibold tracking-wider uppercase',
                        group.late ? 'text-rose-600' : 'text-slate-500',
                      )}
                    >
                      {group.label} <span className="tabular-nums">{groupCount} đơn</span>
                    </p>
                  )}
                  <button
                    type="button"
                    onClick={() => onOpen(o.id, o.flags.isVehicleIncident ? 'incident' : active.tab)}
                    className="group flex w-full flex-wrap items-center gap-x-4 gap-y-2 px-4 py-3 text-left hover:bg-slate-50"
                  >
                    <div className="min-w-0 flex-1">
                      <p className="flex items-center gap-2 text-sm font-semibold text-slate-900">
                        <span className="tabular-nums">#{shortId(o.id)}</span>
                        <span className="truncate">· {o.customerName}</span>
                        {urgentIds.has(o.id) && <span className="size-2 shrink-0 rounded-full bg-rose-500" title="Đang trong danh sách cần xử lý ngay" />}
                      </p>
                      <p className="truncate text-xs text-slate-500">
                        {!group && (
                          <>
                            <span className={cx('font-medium tabular-nums', o.scheduledDate === today ? 'text-brand' : 'text-slate-700')}>
                              {o.scheduledDate === today ? 'Hôm nay' : `${weekdayOf(o.scheduledDate)} ${shortDate(o.scheduledDate)}`}
                            </span>{' '}
                            ·{' '}
                          </>
                        )}
                        <span className="font-medium text-slate-700 tabular-nums">{o.timeSlot}</span> · {o.toAddress}
                      </p>
                    </div>
                    <BucketDetail id={active.id} order={o} />
                    <span className="inline-flex items-center gap-1 text-sm font-semibold text-brand group-hover:underline">
                      {active.action} <ArrowRight className="size-4" />
                    </span>
                  </button>
                </li>
              );
            })}
          </ul>
        )}
        {filtered.length > visible.length && (
          <button
            type="button"
            onClick={() => setLimit((n) => n + PAGE_SIZE)}
            className="w-full border-t border-slate-100 px-4 py-3 text-sm font-semibold text-slate-700 hover:bg-slate-50"
          >
            Xem thêm {Math.min(PAGE_SIZE, filtered.length - visible.length)} đơn{' '}
            <span className="font-medium text-slate-400">(còn {filtered.length - visible.length})</span>
          </button>
        )}
      </Card>
    </div>
  );
};
