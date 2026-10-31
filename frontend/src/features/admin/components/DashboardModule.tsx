import { useEffect, useMemo, useState } from 'react';
import {
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
  Select,
  Stat,
  Tabs,
} from '@/components/ui';
import { STATUS_META, STATUS_ORDER } from '@/constants/order';
import { DAY } from '@/features/admin/constants';
import type { Group, Range, RevenueRow } from '@/features/admin/types';
import { bucketKey, bucketLabel, isoDate } from '@/features/admin/utils';
import { formatVND } from '@/services/format';
import { computeTotals } from '@/services/pricing';
import { useApp } from '@/services/store/AppStore';
import { useCatalog } from '@/services/store/CatalogStore';

/** Dashboard Admin — số liệu tổng hợp từ store, không tính ở nơi hiển thị khác. */
export const DashboardModule = () => {
  const { state } = useApp();
  const { catalog } = useCatalog();
  const [range, setRange] = useState<Range>('30');
  const [group, setGroup] = useState<Group>('day');
  const [loading, setLoading] = useState(true);
  const [reloadKey, setReloadKey] = useState(0);

  // Mô phỏng thời gian gọi API thống kê để thể hiện trạng thái đang tải.
  useEffect(() => {
    setLoading(true);
    const t = window.setTimeout(() => setLoading(false), 450);
    return () => window.clearTimeout(t);
  }, [range, group, reloadKey]);

  const data = useMemo(() => {
    const since = range === 'all' ? 0 : Date.now() - Number(range) * DAY;
    const inRange = (t: number) => t >= since;

    const rows: RevenueRow[] = [
      ...catalog.history.filter((h) => inRange(new Date(`${h.date}T00:00:00Z`).getTime())).map((h) => ({ date: h.date, revenue: h.revenue, services: h.services })),
      ...state.orders
        // Chỉ đơn COMPLETED có doanh thu khóa — loại đơn hủy / thất bại / chờ xác nhận.
        .filter((o) => o.status === 'COMPLETED' && o.finalRevenue !== null && inRange(o.createdAt))
        .map((o) => ({ date: isoDate(o.createdAt), revenue: o.finalRevenue ?? 0, services: o.quoteItems.map((q) => q.name) })),
    ];

    const buckets = new Map<string, number>();
    rows.forEach((r) => {
      const k = bucketKey(r.date, group);
      buckets.set(k, (buckets.get(k) ?? 0) + r.revenue);
    });
    const series = [...buckets.entries()].sort(([a], [b]) => a.localeCompare(b));

    const orders = state.orders.filter((o) => inRange(o.createdAt));
    const statusCount = STATUS_ORDER.map((s) => ({ status: s, count: orders.filter((o) => o.status === s).length }));
    const active = orders.filter((o) => o.status !== 'COMPLETED' && o.status !== 'CANCELLED_EXPIRED');
    const collected = orders.reduce((s, o) => s + o.depositPaid + o.balancePaid, 0);
    const receivable = active.reduce((s, o) => s + computeTotals(o).remaining, 0);
    const pending = catalog.payments.filter((p) => p.status === 'PENDING' && inRange(p.createdAt));

    const svcCount = new Map<string, number>();
    rows.forEach((r) => r.services.forEach((n) => svcCount.set(n, (svcCount.get(n) ?? 0) + 1)));
    const topServices = [...svcCount.entries()].sort((a, b) => b[1] - a[1]).slice(0, 6);

    const fbs = catalog.feedbacks.filter((f) => inRange(f.createdAt));
    const avgRating = fbs.length ? fbs.reduce((s, f) => s + f.stars, 0) / fbs.length : null;
    const claims = state.claims.filter((c) => inRange(c.createdAt));

    return {
      series,
      revenue: rows.reduce((s, r) => s + r.revenue, 0),
      completedCount: rows.length,
      cancelled: orders.filter((o) => o.status === 'CANCELLED_EXPIRED').length,
      activeCount: active.length,
      statusCount,
      collected,
      receivable,
      pending,
      topServices,
      avgRating,
      ratingCount: fbs.length,
      claimCount: claims.length,
      openClaims: claims.filter((c) => c.status === 'OPEN' || c.status === 'INVESTIGATING').length,
      empty: rows.length === 0 && orders.length === 0,
    };
  }, [range, group, state.orders, state.claims, catalog.history, catalog.payments, catalog.feedbacks]);

  const maxRev = Math.max(1, ...data.series.map(([, v]) => v));
  const maxStatus = Math.max(1, ...data.statusCount.map((s) => s.count));
  const maxSvc = Math.max(1, ...data.topServices.map(([, c]) => c));

  return (
    <>
      <PageHeader
        eyebrow="Báo cáo"
        title="Dashboard & thống kê"
        subtitle="Doanh thu chỉ tính đơn đã hoàn tất — không cộng giao dịch thất bại hoặc chờ xác nhận."
        actions={
          <>
            <Select value={range} onChange={(e) => setRange(e.target.value as Range)} className="h-10 w-40" aria-label="Khoảng thời gian">
              <option value="7">7 ngày qua</option>
              <option value="30">30 ngày qua</option>
              <option value="90">90 ngày qua</option>
              <option value="all">Tất cả</option>
            </Select>
            <Button variant="secondary" icon={RefreshCw} onClick={() => setReloadKey((k) => k + 1)}>Làm mới</Button>
          </>
        }
      />

      {loading ? (
        <Card className="flex items-center justify-center gap-3 py-24 text-sm text-slate-500" aria-busy="true">
          <Loader2 className="size-5 animate-spin text-brand" /> Đang tải số liệu thống kê…
        </Card>
      ) : data.empty ? (
        <Card><EmptyState icon={BarChart3} title="Không có dữ liệu trong khoảng thời gian này">Hãy chọn khoảng thời gian rộng hơn.</EmptyState></Card>
      ) : (
        <div className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <Stat label="Doanh thu" value={formatVND(data.revenue)} icon={CircleDollarSign} tone="success" hint={`${data.completedCount} đơn hoàn tất`} />
            <Stat label="Đã thu" value={formatVND(data.collected)} icon={Wallet} tone="info" hint="Cọc + thanh toán thành công" />
            <Stat label="Còn phải thu" value={formatVND(data.receivable)} icon={Hourglass} tone="warning" hint={`${data.activeCount} đơn đang thực hiện`} />
            <Stat label="Giao dịch chờ xác nhận" value={data.pending.length} icon={Clock} tone="brand" hint={formatVND(data.pending.reduce((s, p) => s + p.amount, 0))} />
            <Stat label="Hoàn tất" value={data.completedCount} icon={CheckCircle2} tone="success" />
            <Stat label="Đã hủy / hết hạn" value={data.cancelled} icon={FileWarning} tone="danger" />
            <Stat label="Điểm đánh giá TB" value={data.avgRating === null ? '—' : `${data.avgRating.toFixed(1)} / 5`} icon={Star} tone="warning" hint={`${data.ratingCount} lượt đánh giá`} />
            <Stat label="Khiếu nại" value={data.claimCount} icon={FileWarning} tone="danger" hint={`${data.openClaims} đang xử lý`} />
          </div>

          <Card>
            <CardHeader
              title="Doanh thu theo thời gian"
              icon={BarChart3}
              action={
                <Tabs<Group>
                  value={group}
                  onChange={setGroup}
                  tabs={[
                    { id: 'day', label: 'Ngày' },
                    { id: 'week', label: 'Tuần' },
                    { id: 'month', label: 'Tháng' },
                  ]}
                />
              }
            />
            {data.series.length === 0 ? (
              <EmptyState icon={BarChart3} title="Chưa có doanh thu" />
            ) : (
              <div className="flex h-56 items-end gap-2 overflow-x-auto pb-1" role="img" aria-label="Biểu đồ cột doanh thu">
                {data.series.map(([k, v]) => (
                  <div key={k} className="flex h-full min-w-12 flex-1 flex-col items-center justify-end gap-1.5">
                    <span className="text-[10px] font-semibold text-slate-500 tabular-nums">{(v / 1_000_000).toFixed(1)}tr</span>
                    <div className="w-full max-w-14 rounded-t-lg bg-brand/85 transition-all" style={{ height: `${Math.max(4, (v / maxRev) * 100)}%` }} title={formatVND(v)} />
                    <span className="text-[10px] whitespace-nowrap text-slate-500">{bucketLabel(k, group)}</span>
                  </div>
                ))}
              </div>
            )}
          </Card>

          <div className="grid gap-4 lg:grid-cols-2">
            <Card>
              <CardHeader title="Booking theo trạng thái" icon={BarChart3} />
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
            <div className="p-6 pb-4"><CardHeader title="Đánh giá gần đây" icon={Star} subtitle="Mỗi booking chỉ có một đánh giá" /></div>
            <ul className="divide-y divide-slate-100 border-t border-slate-100">
              {catalog.feedbacks.length === 0 && <li className="p-6"><EmptyState icon={Star} title="Chưa có đánh giá" /></li>}
              {catalog.feedbacks.map((f) => (
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
