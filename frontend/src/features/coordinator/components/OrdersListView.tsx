import { useMemo, useState } from 'react';
import { ChevronRight, KanbanSquare, List } from 'lucide-react';
import { Badge, Card, cx, EmptyState, PageHeader, StatusBadge, Tabs } from '@/components/ui';
import { KanbanBoard } from '@/features/coordinator/components/KanbanBoard';
import type { OrderSection } from '@/features/coordinator/types';
import { coordinatorSurveyBadge, shortDate, weekdayOf } from '@/features/order/surveyStage';
import { routeSummaryOf } from '@/features/order/route';
import { settlementBadges } from '@/features/order/settlementStage';
import type { Order, OrderStatus } from '@/types';
import { useApp } from '@/services/store/AppStore';

type View = 'list' | 'kanban';

/** Nhóm trạng thái để lọc nhanh danh sách đơn. */
const GROUPS: { id: string; label: string; statuses: OrderStatus[] }[] = [
  { id: 'open', label: 'Đang mở', statuses: ['NEW_PENDING_SURVEY', 'SURVEYED_PENDING_CONFIRM', 'QUOTED_PENDING_SCHEDULE', 'ASSIGNED', 'IN_TRANSIT', 'PAUSED_WAITING_CUSTOMER', 'PENDING_PAYMENT_OR_CLAIM'] },
  { id: 'survey', label: 'Khảo sát', statuses: ['NEW_PENDING_SURVEY'] },
  { id: 'confirm', label: 'Chờ khách chốt', statuses: ['SURVEYED_PENDING_CONFIRM'] },
  { id: 'schedule', label: 'Chờ xếp xe', statuses: ['QUOTED_PENDING_SCHEDULE'] },
  { id: 'running', label: 'Đang thực hiện', statuses: ['ASSIGNED', 'IN_TRANSIT', 'PAUSED_WAITING_CUSTOMER'] },
  { id: 'settle', label: 'Quyết toán', statuses: ['PENDING_PAYMENT_OR_CLAIM'] },
  { id: 'closed', label: 'Đã đóng / hủy', statuses: ['COMPLETED', 'CANCELLED_EXPIRED'] },
];

const PAGE_SIZE = 20;

const shortId = (id: string) => id.replace('ORD-2026-', 'ORD-');

/** Danh sách tất cả đơn — tìm, lọc theo nhóm trạng thái, mở chi tiết; có dạng Kanban để nhìn tổng thể. */
export const OrdersListView = ({ onOpen, query }: { onOpen: (id: string, s?: OrderSection) => void; query: string }) => {
  const { state } = useApp();
  const [view, setView] = useState<View>('list');
  const [groupId, setGroupId] = useState('open');
  const [limit, setLimit] = useState(PAGE_SIZE);

  const searched = useMemo(() => {
    const q = query.trim().toLowerCase();
    const list = q ? state.orders.filter((o) => `${o.id} ${o.customerName} ${o.phone}`.toLowerCase().includes(q)) : state.orders;
    return [...list].sort((a, b) => b.createdAt - a.createdAt);
  }, [state.orders, query]);

  const group = GROUPS.find((g) => g.id === groupId) ?? GROUPS[0]!;
  const filtered = searched.filter((o) => group.statuses.includes(o.status));
  const visible = filtered.slice(0, limit);

  return (
    <>
      <PageHeader
        eyebrow="Đơn hàng"
        title="Tất cả đơn hàng"
        subtitle={query.trim() ? `Kết quả tìm “${query.trim()}”` : 'Bấm vào một đơn để xem chi tiết và xử lý.'}
        actions={
          <Tabs<View>
            value={view}
            onChange={setView}
            tabs={[
              { id: 'list', label: 'Danh sách', icon: List },
              { id: 'kanban', label: 'Kanban', icon: KanbanSquare },
            ]}
          />
        }
      />

      {view === 'kanban' ? (
        <KanbanBoard onOpen={onOpen} query={query} />
      ) : (
        <div className="space-y-3">
          <div className="scrollbar-none -mx-1 flex gap-1.5 overflow-x-auto px-1" role="tablist" aria-label="Nhóm trạng thái">
            {GROUPS.map((g) => {
              const count = searched.filter((o) => g.statuses.includes(o.status)).length;
              const sel = g.id === groupId;
              return (
                <button
                  key={g.id}
                  type="button"
                  role="tab"
                  aria-selected={sel}
                  onClick={() => {
                    setGroupId(g.id);
                    setLimit(PAGE_SIZE);
                  }}
                  className={cx(
                    'inline-flex h-9 shrink-0 items-center gap-1.5 rounded-full border px-3.5 text-sm font-semibold transition-colors',
                    sel ? 'border-slate-900 bg-slate-900 text-white' : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300',
                  )}
                >
                  {g.label} <span className="text-xs tabular-nums opacity-70">{count}</span>
                </button>
              );
            })}
          </div>

          <Card padded={false}>
            {filtered.length === 0 ? (
              <EmptyState icon={List} title={query.trim() ? 'Không tìm thấy đơn phù hợp' : 'Chưa có đơn nào ở nhóm này'} />
            ) : (
              <ul className="divide-y divide-slate-100">
                <li className="hidden grid-cols-[110px_1.2fr_2fr_130px_minmax(180px,1fr)_20px] gap-4 px-4 py-2.5 text-[11px] font-semibold tracking-wider text-slate-400 uppercase lg:grid">
                  <span>Mã đơn</span>
                  <span>Khách hàng</span>
                  <span>Tuyến</span>
                  <span>Ngày chuyển</span>
                  <span>Trạng thái</span>
                  <span />
                </li>
                {visible.map((o) => (
                  <li key={o.id}>
                    <button
                      type="button"
                      onClick={() => onOpen(o.id)}
                      className="grid w-full gap-x-4 gap-y-1 px-4 py-3 text-left hover:bg-slate-50 lg:grid-cols-[110px_1.2fr_2fr_130px_minmax(180px,1fr)_20px] lg:items-center"
                    >
                      <span className="text-sm font-bold text-slate-900 tabular-nums">#{shortId(o.id)}</span>
                      <span className="min-w-0">
                        <span className="block truncate text-sm font-semibold text-slate-800">{o.customerName}</span>
                        <span className="block text-xs text-slate-500 tabular-nums">{o.phone}</span>
                      </span>
                      <span className="min-w-0 truncate text-sm text-slate-600" title={`${o.fromAddress} → ${o.toAddress}`}>
                        {routeSummaryOf(o)}
                      </span>
                      <span className="text-sm text-slate-700 tabular-nums">
                        {weekdayOf(o.scheduledDate)} {shortDate(o.scheduledDate)} · {o.timeSlot}
                      </span>
                      <StatusCell order={o} />
                      <ChevronRight className="hidden size-4 text-slate-300 lg:block" />
                    </button>
                  </li>
                ))}
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
      )}
    </>
  );
};

/** Trạng thái + việc đang chờ (khảo sát / thương lượng / sự cố). */
const StatusCell = ({ order: o }: { order: Order }) => {
  const sub = o.flags.isVehicleIncident
    ? { label: 'Sự cố xe', tone: 'danger' as const }
    : o.negotiation
      ? { label: 'Khách thương lượng', tone: 'warning' as const }
      : o.status === 'NEW_PENDING_SURVEY'
        ? coordinatorSurveyBadge(o)
        : null;
  return (
    <span className="flex flex-wrap items-center gap-1.5">
      <StatusBadge status={o.status} />
      {sub && <Badge tone={sub.tone}>{sub.label}</Badge>}
      {settlementBadges(o)?.map((b) => (
        <Badge key={b.label} tone={b.tone}>
          {b.label}
        </Badge>
      ))}
    </span>
  );
};
