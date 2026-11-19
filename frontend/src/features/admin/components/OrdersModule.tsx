import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Archive, ChevronLeft, ChevronRight, CircleDollarSign, FileSearch, FileWarning, Hourglass, RotateCcw } from 'lucide-react';
import { Badge, Button, Card, cx, EmptyState, Field, Input, PageHeader, Select, Stat, StatusBadge } from '@/components/ui';
import { STATUS_META, STATUS_ORDER } from '@/constants/order';
import { OrderLookupDrawer, SERVICE_MODE_LABEL } from '@/features/admin/components/OrderLookupDrawer';
import { matches } from '@/features/admin/utils';
import { formatDateVN, formatVND } from '@/services/format';
import { computeTotals } from '@/services/pricing';
import { useApp } from '@/services/store/AppStore';
import { useCatalog } from '@/services/store/CatalogStore';
import type { Order, OrderStatus, ServiceMode } from '@/types';

const PAGE_SIZE = 20;

type StatusFilter = 'ALL' | 'ACTIVE' | OrderStatus;
type ModeFilter = 'ALL' | ServiceMode;

/** Một dòng tra cứu: đơn đang có trong hệ thống (xem được chi tiết) hoặc đơn lưu trữ (chỉ số liệu tổng). */
interface LookupRow {
  id: string;
  archived: boolean;
  customerName: string;
  phone: string;
  /** Ngày chuyển (đơn thật) / ngày đóng đơn (lưu trữ), ISO yyyy-mm-dd. */
  date: string;
  status: OrderStatus;
  mode: ServiceMode;
  total: number;
  remaining: number;
  claims: number;
  order: Order | null;
}

const PACKING = 'Thùng carton 60x40x40';
const isClosed = (s: OrderStatus) => s === 'COMPLETED' || s === 'CANCELLED_EXPIRED';

const readStatus = (raw: string | null): StatusFilter =>
  raw === 'ACTIVE' || STATUS_ORDER.includes(raw as OrderStatus) ? (raw as StatusFilter) : 'ALL';

/** Tra cứu đơn hàng cho Admin: mọi trạng thái, lọc nhiều tiêu chí, mở chi tiết + nhật ký. Chỉ xem. */
export const OrdersModule = ({ query }: { query: string }) => {
  const { state } = useApp();
  const { catalog } = useCatalog();
  const [params, setParams] = useSearchParams();
  const [status, setStatus] = useState<StatusFilter>(() => readStatus(params.get('status')));
  const [mode, setMode] = useState<ModeFilter>('ALL');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [debtOnly, setDebtOnly] = useState(false);
  const [claimOnly, setClaimOnly] = useState(false);
  const [page, setPage] = useState(0);
  const [openId, setOpenId] = useState<string | null>(null);

  const allRows = useMemo<LookupRow[]>(() => {
    const live = state.orders.map((o): LookupRow => {
      const t = computeTotals(o);
      return {
        id: o.id,
        archived: false,
        customerName: o.customerName,
        phone: o.phone,
        date: o.scheduledDate,
        status: o.status,
        mode: o.bookingDetails?.serviceMode ?? 'FULL_PACKAGE',
        total: o.finalRevenue ?? t.grandTotal,
        remaining: o.status === 'CANCELLED_EXPIRED' ? 0 : t.remaining,
        claims: state.claims.filter((c) => c.orderId === o.id).length,
        order: o,
      };
    });
    const archived = catalog.history.map((h): LookupRow => ({
      id: h.id,
      archived: true,
      customerName: h.customerName,
      phone: '',
      date: h.date,
      status: h.status === 'COMPLETED' ? 'COMPLETED' : 'CANCELLED_EXPIRED',
      // Đơn lưu trữ không lưu gói — suy ra: có vật tư đóng gói là trọn gói.
      mode: h.services.includes(PACKING) ? 'FULL_PACKAGE' : 'TRUCK_ONLY',
      total: h.revenue,
      remaining: 0,
      claims: 0,
      order: null,
    }));
    return [...live, ...archived].sort((a, b) => b.date.localeCompare(a.date) || Number(a.archived) - Number(b.archived));
  }, [state.orders, state.claims, catalog.history]);

  const rows = useMemo(
    () =>
      allRows.filter(
        (r) =>
          (status === 'ALL' || (status === 'ACTIVE' ? !isClosed(r.status) : r.status === status)) &&
          (mode === 'ALL' || r.mode === mode) &&
          (!from || r.date >= from) &&
          (!to || r.date <= to) &&
          (!debtOnly || r.remaining > 0) &&
          (!claimOnly || r.claims > 0) &&
          matches(query, r.id, r.customerName, r.phone, r.phone.replace(/\s/g, '')),
      ),
    [allRows, status, mode, from, to, debtOnly, claimOnly, query],
  );

  const pages = Math.max(1, Math.ceil(rows.length / PAGE_SIZE));
  const current = Math.min(page, pages - 1);
  const visible = rows.slice(current * PAGE_SIZE, current * PAGE_SIZE + PAGE_SIZE);
  const opened = state.orders.find((o) => o.id === openId) ?? null;

  const totalValue = rows.reduce((s, r) => s + (r.status === 'CANCELLED_EXPIRED' ? 0 : r.total), 0);
  const receivable = rows.reduce((s, r) => s + r.remaining, 0);
  const withClaims = rows.filter((r) => r.claims > 0).length;
  const filtered = status !== 'ALL' || mode !== 'ALL' || !!from || !!to || debtOnly || claimOnly;

  /** Đổi bộ lọc thì về trang 1; trạng thái đồng bộ lên URL để chia sẻ / quay lại được. */
  const update = <T,>(set: (v: T) => void) => (v: T) => {
    set(v);
    setPage(0);
  };
  const changeStatus = (v: StatusFilter) => {
    update(setStatus)(v);
    setParams(v === 'ALL' ? {} : { status: v }, { replace: true });
  };
  const reset = () => {
    changeStatus('ALL');
    setMode('ALL');
    setFrom('');
    setTo('');
    setDebtOnly(false);
    setClaimOnly(false);
  };

  const check = (label: string, checked: boolean, onChange: (v: boolean) => void) => (
    <label className="flex h-11 cursor-pointer items-center gap-2 rounded-xl border border-slate-200 bg-white px-3 text-sm font-medium text-slate-700 has-[:checked]:border-brand/40 has-[:checked]:bg-brand/5">
      <input type="checkbox" checked={checked} onChange={(e) => update(onChange)(e.target.checked)} className="size-4 rounded accent-[#FF5A1F]" />
      {label}
    </label>
  );

  return (
    <>
      <PageHeader
        eyebrow="Vận hành"
        title="Tra cứu đơn hàng"
      />

      <div className="mb-4 grid grid-cols-2 gap-4 xl:grid-cols-4">
        <Stat label="Đơn khớp bộ lọc" value={rows.length} icon={FileSearch} hint={`${rows.filter((r) => !r.archived).length} đơn trong hệ thống`} />
        <Stat label="Tổng giá trị" value={formatVND(totalValue)} icon={CircleDollarSign} tone="success" hint="Không tính đơn đã hủy" />
        <Stat label="Còn phải thu" value={formatVND(receivable)} icon={Hourglass} tone="warning" hint={`${rows.filter((r) => r.remaining > 0).length} đơn còn nợ`} />
        <Stat label="Có khiếu nại" value={withClaims} icon={FileWarning} tone="danger" />
      </div>

      <Card className="mb-4">
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <Field label="Trạng thái">
            <Select value={status} onChange={(e) => changeStatus(e.target.value as StatusFilter)}>
              <option value="ALL">Tất cả trạng thái</option>
              <option value="ACTIVE">Đang xử lý</option>
              {STATUS_ORDER.map((s) => (
                <option key={s} value={s}>{STATUS_META[s].label}</option>
              ))}
            </Select>
          </Field>
          <Field label="Gói dịch vụ">
            <Select value={mode} onChange={(e) => update(setMode)(e.target.value as ModeFilter)}>
              <option value="ALL">Tất cả gói</option>
              <option value="FULL_PACKAGE">{SERVICE_MODE_LABEL.FULL_PACKAGE}</option>
              <option value="TRUCK_ONLY">{SERVICE_MODE_LABEL.TRUCK_ONLY}</option>
            </Select>
          </Field>
          <Field label="Từ ngày">
            <Input type="date" value={from} max={to || undefined} onChange={(e) => update(setFrom)(e.target.value)} />
          </Field>
          <Field label="Đến ngày">
            <Input type="date" value={to} min={from || undefined} onChange={(e) => update(setTo)(e.target.value)} />
          </Field>
        </div>
        <div className="mt-3 flex flex-wrap items-center gap-2">
          {check('Còn nợ', debtOnly, setDebtOnly)}
          {check('Có khiếu nại', claimOnly, setClaimOnly)}
          {filtered && (
            <Button variant="ghost" size="sm" icon={RotateCcw} onClick={reset} className="ml-auto">
              Xóa bộ lọc
            </Button>
          )}
        </div>
        <p className="mt-2 text-xs text-slate-400">Tìm theo mã đơn, tên hoặc số điện thoại khách bằng ô tìm kiếm trên thanh đầu trang. Ngày lọc theo ngày chuyển.</p>
      </Card>

      <Card padded={false}>
        {rows.length === 0 ? (
          <div className="p-6">
            <EmptyState icon={FileSearch} title="Không tìm thấy đơn phù hợp">Thử nới bộ lọc hoặc đổi từ khóa tìm kiếm.</EmptyState>
          </div>
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[920px] text-sm">
                <thead>
                  <tr className="border-b border-slate-100 bg-slate-50/60 text-left text-xs font-semibold tracking-wide text-slate-500 uppercase">
                    <th className="px-6 py-3">Đơn hàng</th>
                    <th className="px-3 py-3">Khách hàng</th>
                    <th className="px-3 py-3">Ngày chuyển</th>
                    <th className="px-3 py-3">Gói</th>
                    <th className="px-3 py-3">Trạng thái</th>
                    <th className="px-3 py-3 text-right">Tổng giá trị</th>
                    <th className="px-6 py-3 text-right">Còn phải thu</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {visible.map((r) => (
                    <tr
                      key={r.id}
                      onClick={r.order ? () => setOpenId(r.id) : undefined}
                      onKeyDown={r.order ? (e) => (e.key === 'Enter' || e.key === ' ') && (e.preventDefault(), setOpenId(r.id)) : undefined}
                      tabIndex={r.order ? 0 : undefined}
                      title={r.archived ? 'Dữ liệu lưu trữ — chỉ có số liệu tổng, không có chi tiết' : 'Xem chi tiết đơn'}
                      className={cx(r.order ? 'cursor-pointer hover:bg-slate-50 focus-visible:bg-slate-50 focus-visible:outline-none' : 'text-slate-500')}
                    >
                      <td className="px-6 py-3.5 whitespace-nowrap">
                        <p className="flex items-center gap-1.5 font-semibold tabular-nums">
                          #{r.id}
                          {r.archived && <Badge tone="neutral" icon={Archive}>Lưu trữ</Badge>}
                          {r.claims > 0 && <Badge tone="danger">{r.claims} khiếu nại</Badge>}
                        </p>
                      </td>
                      <td className="px-3 py-3.5">
                        <p className="font-medium text-slate-800">{r.customerName}</p>
                        {r.phone && <p className="text-xs text-slate-500 tabular-nums">{r.phone}</p>}
                      </td>
                      <td className="px-3 py-3.5 whitespace-nowrap tabular-nums">{formatDateVN(r.date)}</td>
                      <td className="px-3 py-3.5 whitespace-nowrap">{SERVICE_MODE_LABEL[r.mode]}</td>
                      <td className="px-3 py-3.5"><StatusBadge status={r.status} /></td>
                      <td className="px-3 py-3.5 text-right font-semibold whitespace-nowrap tabular-nums">
                        {r.status === 'CANCELLED_EXPIRED' ? '—' : r.total > 0 ? formatVND(r.total) : 'Chưa báo giá'}
                      </td>
                      <td className={cx('px-6 py-3.5 text-right whitespace-nowrap tabular-nums', r.remaining > 0 ? 'font-semibold text-rose-600' : 'text-slate-400')}>
                        {r.remaining > 0 ? formatVND(r.remaining) : '—'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 px-6 py-3 text-sm text-slate-500">
              <span className="tabular-nums">
                {current * PAGE_SIZE + 1}–{Math.min(rows.length, (current + 1) * PAGE_SIZE)} / {rows.length} đơn
              </span>
              <div className="flex items-center gap-2">
                <Button size="sm" variant="secondary" icon={ChevronLeft} disabled={current === 0} onClick={() => setPage(current - 1)} aria-label="Trang trước" />
                <span className="tabular-nums">Trang {current + 1}/{pages}</span>
                <Button size="sm" variant="secondary" icon={ChevronRight} disabled={current >= pages - 1} onClick={() => setPage(current + 1)} aria-label="Trang sau" />
              </div>
            </div>
          </>
        )}
      </Card>

      {opened && <OrderLookupDrawer order={opened} onClose={() => setOpenId(null)} />}
    </>
  );
};
