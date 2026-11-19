import { type ReactNode, useEffect, useMemo, useState } from 'react';
import {
  ArrowDownRight,
  ArrowUpRight,
  BarChart3,
  CheckCircle2,
  CircleDollarSign,
  Clock,
  FileWarning,
  Hourglass,
  Loader2,
  RefreshCw,
  Star,
  Wallet,
} from 'lucide-react';
import {
  Alert,
  Button,
  Card,
  CardHeader,
  cx,
  EmptyState,
  PageHeader,
  Stat,
} from '@/components/ui';
import { STATUS_META, STATUS_ORDER } from '@/constants/order';
import { AdminPeriodPicker } from '@/features/admin/components/PeriodPicker';
import { useAdminPeriod } from '@/features/admin/hooks';
import { bucketOf, chartBuckets, type DateWindow, inWindow, periodKey } from '@/features/admin/period';
import { isoDate } from '@/features/admin/utils';
import { formatVND } from '@/services/format';
import { computeTotals } from '@/services/pricing';
import { useApp } from '@/services/store/AppStore';
import { useCatalog } from '@/services/store/CatalogStore';

const RECENT_FEEDBACKS = 8;

/** Chỉ số phát sinh trong một khoảng ngày — tính giống nhau cho kỳ hiện tại và kỳ so sánh. */
interface FlowMetrics {
  revenue: number;
  completed: number;
  cancelled: number;
  collected: number;
  avgRating: number | null;
  ratingCount: number;
  claims: number;
  openClaims: number;
}

/**
 * Mức thay đổi so với kỳ trước. invert: tăng là xấu (hủy, khiếu nại).
 * points: so theo hiệu số điểm (điểm đánh giá) thay vì phần trăm.
 */
const Delta = ({ cur, prev, invert = false, points = false }: { cur: number | null; prev: number | null; invert?: boolean; points?: boolean }) => {
  if (cur === null || prev === null) return <span className="text-slate-400">Kỳ trước chưa có dữ liệu</span>;
  if (!points && prev === 0) return <span className="text-slate-400">{cur === 0 ? 'Không đổi' : 'Kỳ trước chưa có'}</span>;
  const value = points ? cur - prev : ((cur - prev) / prev) * 100;
  if (Math.abs(value) < (points ? 0.05 : 0.5)) return <span className="text-slate-400">Không đổi</span>;
  const up = value > 0;
  const Icon = up ? ArrowUpRight : ArrowDownRight;
  return (
    <span className={cx('inline-flex items-center gap-0.5 font-semibold', up !== invert ? 'text-emerald-600' : 'text-rose-600')}>
      <Icon className="size-3.5" />
      {up ? '+' : ''}
      {points ? `${value.toFixed(1)} điểm` : `${value.toFixed(0)}%`}
    </span>
  );
};

const withNote = (delta: ReactNode, note?: string) => (
  <span className="inline-flex flex-wrap items-center gap-x-1.5">
    {delta}
    {note && <span className="text-slate-400">· {note}</span>}
  </span>
);

/** Dashboard Admin — số liệu theo tháng / năm dương lịch, kèm so sánh với kỳ trước. Kỳ chọn chung với các tab Admin. */
export const DashboardModule = () => {
  const { state } = useApp();
  const { catalog } = useCatalog();
  const { period, windows } = useAdminPeriod();
  const [loading, setLoading] = useState(true);
  const [reloadKey, setReloadKey] = useState(0);

  // Mô phỏng thời gian gọi API thống kê để thể hiện trạng thái đang tải.
  const loadKey = `${periodKey(period)}-${period.kind}-${reloadKey}`;
  useEffect(() => {
    setLoading(true);
    const t = window.setTimeout(() => setLoading(false), 450);
    return () => window.clearTimeout(t);
  }, [loadKey]);

  const data = useMemo(() => {
    const { current, previous } = windows;
    const createdIn = (t: number, w: DateWindow) => inWindow(isoDate(t), w);
    // Chỉ đơn COMPLETED có doanh thu khóa — loại đơn hủy / thất bại / chờ xác nhận. Đơn lịch sử hoàn tất coi như thu đủ.
    const revenueRows = (w: DateWindow) => [
      ...catalog.history.filter((h) => h.status === 'COMPLETED' && inWindow(h.date, w)).map((h) => ({ date: h.date, revenue: h.revenue, services: h.services })),
      ...state.orders
        .filter((o) => o.status === 'COMPLETED' && o.finalRevenue !== null && createdIn(o.createdAt, w))
        .map((o) => ({ date: isoDate(o.createdAt), revenue: o.finalRevenue ?? 0, services: o.quoteItems.map((q) => q.name) })),
    ];

    const metrics = (w: DateWindow): FlowMetrics => {
      const rows = revenueRows(w);
      const orders = state.orders.filter((o) => createdIn(o.createdAt, w));
      const archivedCancelled = catalog.history.filter((h) => h.status === 'CANCELLED' && inWindow(h.date, w)).length;
      const fbs = catalog.feedbacks.filter((f) => createdIn(f.createdAt, w));
      const claims = state.claims.filter((c) => createdIn(c.createdAt, w));
      const revenue = rows.reduce((s, r) => s + r.revenue, 0);
      return {
        revenue,
        completed: rows.length,
        cancelled: archivedCancelled + orders.filter((o) => o.status === 'CANCELLED_EXPIRED').length,
        collected:
          catalog.history.filter((h) => h.status === 'COMPLETED' && inWindow(h.date, w)).reduce((s, h) => s + h.revenue, 0) +
          orders.reduce((s, o) => s + o.depositPaid + o.balancePaid, 0),
        avgRating: fbs.length ? fbs.reduce((s, f) => s + f.stars, 0) / fbs.length : null,
        ratingCount: fbs.length,
        claims: claims.length,
        openClaims: claims.filter((c) => c.status === 'OPEN' || c.status === 'INVESTIGATING' || c.status === 'PENDING_APPROVAL').length,
      };
    };

    const cur = metrics(current);
    const prev = metrics(previous);
    const rows = revenueRows(current);

    // Biểu đồ: cột theo ngày (tháng) hoặc theo tháng (năm), đủ mọi mốc kể cả mốc không có doanh thu.
    const sums = new Map<string, number>();
    rows.forEach((r) => {
      const k = bucketOf(r.date, period.kind);
      sums.set(k, (sums.get(k) ?? 0) + r.revenue);
    });
    const series = chartBuckets(period, current).map((b) => ({ ...b, value: sums.get(b.key) ?? 0 }));

    const ordersInPeriod = state.orders.filter((o) => createdIn(o.createdAt, current));
    const statusCount = STATUS_ORDER.map((s) => ({
      status: s,
      count:
        ordersInPeriod.filter((o) => o.status === s).length +
        (s === 'COMPLETED' ? catalog.history.filter((h) => h.status === 'COMPLETED' && inWindow(h.date, current)).length : 0) +
        (s === 'CANCELLED_EXPIRED' ? catalog.history.filter((h) => h.status === 'CANCELLED' && inWindow(h.date, current)).length : 0),
    }));

    const svcCount = new Map<string, number>();
    rows.forEach((r) => r.services.forEach((n) => svcCount.set(n, (svcCount.get(n) ?? 0) + 1)));
    const topServices = [...svcCount.entries()].sort((a, b) => b[1] - a[1]).slice(0, 6);

    const recentFeedbacks = catalog.feedbacks
      .filter((f) => createdIn(f.createdAt, current))
      .sort((a, b) => b.createdAt - a.createdAt)
      .slice(0, RECENT_FEEDBACKS);

    // Số liệu tại thời điểm hiện tại (không theo kỳ): công nợ của đơn đang chạy, giao dịch chờ xác nhận.
    const active = state.orders.filter((o) => o.status !== 'COMPLETED' && o.status !== 'CANCELLED_EXPIRED');
    const receivable = active.reduce((s, o) => s + computeTotals(o).remaining, 0);
    const pending = catalog.payments.filter((p) => p.status === 'PENDING');

    return {
      cur,
      prev,
      series,
      statusCount,
      topServices,
      recentFeedbacks,
      activeCount: active.length,
      receivable,
      pending,
      empty: cur.completed === 0 && cur.cancelled === 0 && ordersInPeriod.length === 0,
    };
  }, [windows, period, state.orders, state.claims, catalog.history, catalog.payments, catalog.feedbacks]);

  const maxRev = Math.max(1, ...data.series.map((b) => b.value));
  const maxStatus = Math.max(1, ...data.statusCount.map((s) => s.count));
  const maxSvc = Math.max(1, ...data.topServices.map(([, c]) => c));

  return (
    <>
      <PageHeader
        eyebrow="Báo cáo"
        title="Dashboard & thống kê"
        actions={
          <>
            <AdminPeriodPicker />
            <Button variant="secondary" icon={RefreshCw} onClick={() => setReloadKey((k) => k + 1)} aria-label="Làm mới số liệu">
              <span className="hidden sm:inline">Làm mới</span>
            </Button>
          </>
        }
      />

      {loading ? (
        <Card className="flex items-center justify-center gap-3 py-24 text-sm text-slate-500" aria-busy="true">
          <Loader2 className="size-5 animate-spin text-brand" /> Đang tải số liệu thống kê…
        </Card>
      ) : data.empty ? (
        <Card><EmptyState icon={BarChart3} title="Không có dữ liệu trong kỳ này">Hãy chọn tháng hoặc năm khác.</EmptyState></Card>
      ) : (
        <div className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <Stat label="Doanh thu" value={formatVND(data.cur.revenue)} icon={CircleDollarSign} tone="success" hint={withNote(<Delta cur={data.cur.revenue} prev={data.prev.revenue} />, `${data.cur.completed} đơn hoàn tất`)} />
            <Stat label="Đã thu" value={formatVND(data.cur.collected)} icon={Wallet} tone="info" hint={withNote(<Delta cur={data.cur.collected} prev={data.prev.collected} />, 'cọc + thanh toán thành công')} />
            <Stat label="Còn phải thu" value={formatVND(data.receivable)} icon={Hourglass} tone="warning" hint={`Hiện tại · ${data.activeCount} đơn đang thực hiện`} />
            <Stat label="Giao dịch chờ xác nhận" value={data.pending.length} icon={Clock} tone="brand" hint={`Hiện tại · ${formatVND(data.pending.reduce((s, p) => s + p.amount, 0))}`} />
            <Stat label="Hoàn tất" value={data.cur.completed} icon={CheckCircle2} tone="success" hint={withNote(<Delta cur={data.cur.completed} prev={data.prev.completed} />)} />
            <Stat label="Đã hủy / hết hạn" value={data.cur.cancelled} icon={FileWarning} tone="danger" hint={withNote(<Delta cur={data.cur.cancelled} prev={data.prev.cancelled} invert />)} />
            <Stat
              label="Điểm đánh giá TB"
              value={data.cur.avgRating === null ? '—' : `${data.cur.avgRating.toFixed(1)} / 5`}
              icon={Star}
              tone="warning"
              hint={withNote(<Delta cur={data.cur.avgRating} prev={data.prev.avgRating} points />, `${data.cur.ratingCount} lượt`)}
            />
            <Stat label="Khiếu nại" value={data.cur.claims} icon={FileWarning} tone="danger" hint={withNote(<Delta cur={data.cur.claims} prev={data.prev.claims} invert />, `${data.cur.openClaims} đang xử lý`)} />
          </div>

          <Card>
            <CardHeader
              title="Doanh thu theo thời gian"
              icon={BarChart3}
              subtitle={`${period.kind === 'month' ? 'Theo ngày' : 'Theo tháng'} · ${windows.label} · tổng ${formatVND(data.cur.revenue)}`}
            />
            {data.cur.revenue === 0 ? (
              <EmptyState icon={BarChart3} title="Chưa có doanh thu trong kỳ" />
            ) : (
              <div className="flex h-56 items-end gap-0.5 sm:gap-1.5" role="img" aria-label={`Biểu đồ cột doanh thu ${windows.label}`}>
                {data.series.map((b) => {
                  // Tháng ~30 cột: chỉ ghi nhãn ngày 1, 5, 10… để không chồng chữ; năm 12 cột: ghi đủ kèm giá trị.
                  const day = Number(b.label);
                  const showLabel = period.kind === 'year' || day === 1 || day % 5 === 0;
                  return (
                    <div key={b.key} className="flex h-full min-w-0 flex-1 flex-col items-center justify-end gap-1.5" title={`${b.title}: ${formatVND(b.value)}`}>
                      {period.kind === 'year' && <span className="text-[10px] font-semibold text-slate-500 tabular-nums">{(b.value / 1_000_000).toFixed(0)}tr</span>}
                      <div
                        className={cx('w-full max-w-14 rounded-t-md transition-all', b.value > 0 ? 'bg-brand/85 hover:bg-brand' : 'bg-slate-200')}
                        style={{ height: b.value > 0 ? `${Math.max(3, (b.value / maxRev) * 100)}%` : '2px' }}
                      />
                      <span className={cx('text-[10px] whitespace-nowrap text-slate-500 tabular-nums', !showLabel && 'invisible')}>{b.label}</span>
                    </div>
                  );
                })}
              </div>
            )}
          </Card>

          <div className="grid gap-4 lg:grid-cols-2">
            <Card>
              <CardHeader title="Đơn hàng theo trạng thái" icon={BarChart3} />
              <ul className="space-y-2.5">
                {data.statusCount.map(({ status, count }) => (
                  <li key={status} className="flex items-center gap-3 text-sm">
                    <span className="w-44 shrink-0 truncate text-slate-600">{STATUS_META[status].label}</span>
                    <div className="h-2.5 flex-1 overflow-hidden rounded-full bg-slate-100">
                      <div className={cx('h-full rounded-full', status === 'CANCELLED_EXPIRED' ? 'bg-rose-400' : status === 'COMPLETED' ? 'bg-emerald-500' : 'bg-slate-800')} style={{ width: `${(count / maxStatus) * 100}%` }} />
                    </div>
                    <span className="w-6 text-right font-semibold tabular-nums">{count}</span>
                  </li>
                ))}
              </ul>
            </Card>
            <Card>
              <CardHeader title="Dịch vụ được dùng nhiều" icon={Star} />
              {data.topServices.length === 0 ? (
                <EmptyState icon={Star} title="Chưa có dữ liệu dịch vụ" />
              ) : (
                <ul className="space-y-2.5">
                  {data.topServices.map(([name, count]) => (
                    <li key={name} className="flex items-center gap-3 text-sm">
                      <span className="w-44 shrink-0 truncate text-slate-600">{name}</span>
                      <div className="h-2.5 flex-1 overflow-hidden rounded-full bg-slate-100">
                        <div className="h-full rounded-full bg-brand" style={{ width: `${(count / maxSvc) * 100}%` }} />
                      </div>
                      <span className="w-6 text-right font-semibold tabular-nums">{count}</span>
                    </li>
                  ))}
                </ul>
              )}
            </Card>
          </div>

          <Card padded={false}>
            <div className="p-6 pb-4">
              <CardHeader
                title="Đánh giá gần đây"
                icon={Star}
                subtitle={`${RECENT_FEEDBACKS} đánh giá mới nhất trong khoảng đã chọn · mỗi booking chỉ có một đánh giá`}
              />
            </div>
            <ul className="divide-y divide-slate-100 border-t border-slate-100">
              {data.recentFeedbacks.length === 0 && <li className="p-6"><EmptyState icon={Star} title="Chưa có đánh giá" /></li>}
              {data.recentFeedbacks.map((f) => (
                <li key={f.id} className="flex flex-wrap items-start gap-3 px-6 py-3.5 text-sm">
                  <div className="flex gap-0.5">{[1, 2, 3, 4, 5].map((n) => <Star key={n} className={cx('size-4', n <= f.stars ? 'fill-amber-400 text-amber-400' : 'text-slate-300')} />)}</div>
                  <div className="min-w-0 flex-1">
                    <p className="font-medium text-slate-800">{f.customerName} <span className="font-normal text-slate-400 tabular-nums">· #{f.orderId}</span></p>
                    <p className="text-slate-500">{f.comment || 'Không có nhận xét'}</p>
                  </div>
                </li>
              ))}
            </ul>
          </Card>
        </div>
      )}
      {!loading && !data.empty && data.pending.length > 0 && (
        <Alert tone="warning" className="mt-4" title={`${data.pending.length} giao dịch đang chờ xác nhận`}>Các giao dịch này chưa được tính vào số tiền đã thu hay doanh thu.</Alert>
      )}
    </>
  );
};
