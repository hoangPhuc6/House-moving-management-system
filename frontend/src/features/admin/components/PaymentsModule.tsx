import { useMemo, useState } from 'react';
import {
  Banknote,
  CheckCircle2,
  CircleDollarSign,
  Clock,
  Plus,
  Wallet,
  XCircle,
} from 'lucide-react';
import {
  Alert,
  Badge,
  Button,
  Card,
  CardHeader,
  EmptyState,
  Field,
  Input,
  PageHeader,
  Select,
  Stat,
  Tooltip,
} from '@/components/ui';
import {
  ADMIN_NAME,
  KIND_LABEL,
  METHOD_LABEL,
  PAYMENT_STATUS_META,
} from '@/features/admin/constants';
import { formatTime, formatVND } from '@/services/format';
import { computeTotals } from '@/services/pricing';
import { useApp } from '@/services/store/AppStore';
import { useCatalog } from '@/services/store/CatalogStore';
import type { PaymentKind, PaymentMethod, PaymentRecord } from '@/types';

/** Module 9 — Thanh toán & công nợ. */
export const PaymentsModule = ({ query }: { query: string }) => {
  const { state, dispatch } = useApp();
  const { catalog, dispatchCatalog } = useCatalog();
  const [orderId, setOrderId] = useState('');
  const [kind, setKind] = useState<PaymentKind>('FINAL');
  const method: PaymentMethod = 'TRANSFER';
  const [amount, setAmount] = useState(0);

  const payable = state.orders.filter((o) => o.status !== 'CANCELLED_EXPIRED' && o.status !== 'NEW_PENDING_SURVEY');
  const order = state.orders.find((o) => o.id === orderId);
  const totals = order ? computeTotals(order) : null;

  const suggest = (o: typeof order, k: PaymentKind): number => (o ? (k === 'DEPOSIT' ? Math.max(0, o.depositRequired - o.depositPaid) : computeTotals(o).remaining) : 0);
  const pick = (id: string, k: PaymentKind) => {
    setOrderId(id);
    setKind(k);
    setAmount(suggest(state.orders.find((o) => o.id === id), k));
  };

  // Sổ giao dịch = giao dịch lưu riêng + giao dịch đã thanh toán trên đơn (chưa có bản ghi xác nhận thủ công).
  const ledger: PaymentRecord[] = useMemo(() => {
    const confirmed = new Set(catalog.payments.filter((p) => p.status === 'SUCCESS').map((p) => `${p.orderId}:${p.kind}`));
    const derived: PaymentRecord[] = state.orders.flatMap((o) => {
      const rows: PaymentRecord[] = [];
      if (o.depositPaid > 0 && !confirmed.has(`${o.id}:DEPOSIT`))
        rows.push({ id: `${o.id}-dep`, orderId: o.id, kind: 'DEPOSIT', method: 'TRANSFER', amount: o.depositPaid, status: 'SUCCESS', receivedBy: 'VietQR', confirmedBy: 'Hệ thống', confirmedAt: o.createdAt, createdAt: o.createdAt });
      if (o.balancePaid > 0 && !confirmed.has(`${o.id}:FINAL`))
        rows.push({ id: `${o.id}-fin`, orderId: o.id, kind: 'FINAL', method: 'TRANSFER', amount: o.balancePaid, status: 'SUCCESS', receivedBy: 'VietQR', confirmedBy: 'Hệ thống', confirmedAt: o.createdAt, createdAt: o.createdAt });
      return rows;
    });
    return [...catalog.payments, ...derived]
      .filter((p) => !query.trim() || `${p.orderId} ${p.id}`.toLowerCase().includes(query.trim().toLowerCase()))
      .sort((a, b) => b.createdAt - a.createdAt);
  }, [catalog.payments, state.orders, query]);

  const collected = state.orders.reduce((s, o) => s + o.depositPaid + o.balancePaid, 0);
  const debtors = state.orders
    .filter((o) => o.status !== 'CANCELLED_EXPIRED' && o.status !== 'COMPLETED' && o.depositPaid > 0)
    .map((o) => ({ o, t: computeTotals(o) }))
    .filter(({ t }) => t.remaining > 0);
  const receivable = debtors.reduce((s, { t }) => s + t.remaining, 0);
  const pending = catalog.payments.filter((p) => p.status === 'PENDING');

  const canConfirm = (p: PaymentRecord): string | null => {
    const o = state.orders.find((x) => x.id === p.orderId);
    if (!o) return 'Không tìm thấy đơn hàng.';
    if (p.kind === 'DEPOSIT') return o.status === 'SURVEYED_PENDING_CONFIRM' ? null : 'Đơn không ở trạng thái chờ đặt cọc.';
    if (o.status !== 'PENDING_PAYMENT_OR_CLAIM') return 'Chỉ thu thanh toán cuối sau khi bàn giao.';
    return p.amount === computeTotals(o).remaining ? null : 'Số tiền phải bằng công nợ còn lại.';
  };

  const confirm = (p: PaymentRecord) => {
    dispatchCatalog({ type: 'SET_PAYMENT_STATUS', id: p.id, status: 'SUCCESS', by: ADMIN_NAME });
    dispatch({ type: p.kind === 'DEPOSIT' ? 'PAY_DEPOSIT' : 'PAY_BALANCE', orderId: p.orderId });
  };

  const submit = () => {
    if (!order || amount <= 0) return;
    dispatchCatalog({ type: 'RECORD_PAYMENT', payment: { orderId: order.id, kind, method, amount, receivedBy: ADMIN_NAME } });
    dispatch({ type: 'PUSH_TOAST', tone: 'info', message: `Đã ghi nhận ${formatVND(amount)} — chờ người có thẩm quyền xác nhận.` });
  };

  const amountError = order && amount > 0 && kind === 'FINAL' && totals && amount > totals.remaining ? 'Vượt quá công nợ còn lại.' : null;

  return (
    <>
      <PageHeader eyebrow="Tài chính" title="Thanh toán & công nợ" subtitle="Đặt cọc và thanh toán cuối là hai giao dịch riêng. Giao dịch chỉ tính khi được xác nhận." />
      <div className="mb-4 grid gap-4 sm:grid-cols-3">
        <Stat label="Đã thu thành công" value={formatVND(collected)} icon={Wallet} tone="success" />
        <Stat label="Còn phải thu" value={formatVND(receivable)} icon={CircleDollarSign} tone="warning" hint={`${debtors.length} đơn còn nợ`} />
        <Stat label="Chờ xác nhận" value={pending.length} icon={Clock} tone="brand" hint="Không tính vào số đã thu" />
      </div>

      <div className="grid gap-4 xl:grid-cols-[1fr_360px] xl:items-start">
        <div className="space-y-4">
          <Card padded={false}>
            <div className="p-6 pb-4"><CardHeader title="Lịch sử giao dịch" icon={Banknote} /></div>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[820px] text-sm">
                <thead>
                  <tr className="border-y border-slate-100 bg-slate-50/60 text-left text-xs font-semibold tracking-wide text-slate-500 uppercase">
                    <th className="px-6 py-3">Đơn</th><th className="px-3 py-3">Loại</th><th className="px-3 py-3">Phương thức</th>
                    <th className="px-3 py-3 text-right">Số tiền</th><th className="px-3 py-3">Trạng thái</th><th className="px-6 py-3 text-right">Xử lý</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {ledger.length === 0 && <tr><td colSpan={6} className="p-6"><EmptyState icon={Banknote} title="Chưa có giao dịch" /></td></tr>}
                  {ledger.map((p) => {
                    const reason = p.status === 'PENDING' ? canConfirm(p) : null;
                    return (
                      <tr key={p.id}>
                        <td className="px-6 py-3.5"><p className="font-semibold tabular-nums">#{p.orderId}</p><p className="text-xs text-slate-500">{formatTime(p.createdAt)}</p></td>
                        <td className="px-3 py-3.5">{KIND_LABEL[p.kind]}</td>
                        <td className="px-3 py-3.5"><p>{METHOD_LABEL[p.method]}</p><p className="text-xs text-slate-500">Thu bởi {p.receivedBy}{p.confirmedBy ? ` · XN: ${p.confirmedBy}` : ''}</p></td>
                        <td className="px-3 py-3.5 text-right font-semibold tabular-nums">{formatVND(p.amount)}</td>
                        <td className="px-3 py-3.5"><Badge tone={PAYMENT_STATUS_META[p.status].tone} dot>{PAYMENT_STATUS_META[p.status].label}</Badge></td>
                        <td className="px-6 py-3.5">
                          {p.status === 'PENDING' && (
                            <div className="flex justify-end gap-1.5">
                              <Button size="sm" variant="ghost" icon={XCircle} className="text-rose-600 hover:bg-rose-50" onClick={() => dispatchCatalog({ type: 'SET_PAYMENT_STATUS', id: p.id, status: 'FAILED', by: ADMIN_NAME })}>Từ chối</Button>
                              <Tooltip content={reason ?? ''} enabled={!!reason}>
                                <Button size="sm" variant="success" icon={CheckCircle2} disabled={!!reason} onClick={() => confirm(p)}>Xác nhận đã nhận</Button>
                              </Tooltip>
                            </div>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </Card>

          <Card padded={false}>
            <div className="p-6 pb-4"><CardHeader title="Công nợ theo đơn" icon={CircleDollarSign} subtitle="Tổng phải trả − giao dịch thành công (đã gồm phụ phí được khách xác nhận)" /></div>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[640px] text-sm">
                <thead>
                  <tr className="border-y border-slate-100 bg-slate-50/60 text-left text-xs font-semibold tracking-wide text-slate-500 uppercase">
                    <th className="px-6 py-3">Đơn</th><th className="px-3 py-3 text-right">Tổng phải trả</th><th className="px-3 py-3 text-right">Đã thu</th><th className="px-6 py-3 text-right">Còn nợ</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {debtors.length === 0 && <tr><td colSpan={4} className="p-6"><EmptyState icon={CheckCircle2} title="Không có công nợ" /></td></tr>}
                  {debtors.map(({ o, t }) => (
                    <tr key={o.id}>
                      <td className="px-6 py-3.5"><p className="font-semibold tabular-nums">#{o.id}</p><p className="text-xs text-slate-500">{o.customerName}</p></td>
                      <td className="px-3 py-3.5 text-right tabular-nums">{formatVND(t.grandTotal)}</td>
                      <td className="px-3 py-3.5 text-right tabular-nums text-emerald-600">{formatVND(t.paid)}</td>
                      <td className="px-6 py-3.5 text-right font-semibold tabular-nums text-rose-600">{formatVND(t.remaining)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
        </div>

        <Card className="xl:sticky xl:top-4">
          <CardHeader title="Ghi nhận thanh toán" icon={Plus} subtitle="Giao dịch mới ở trạng thái chờ xác nhận" />
          <div className="space-y-4">
            <Field label="Đơn hàng">
              <Select value={orderId} onChange={(e) => pick(e.target.value, kind)}>
                <option value="">— Chọn đơn —</option>
                {payable.map((o) => <option key={o.id} value={o.id}>#{o.id} · {o.customerName}</option>)}
              </Select>
            </Field>
            <Field label="Loại giao dịch">
              <Select value={kind} onChange={(e) => pick(orderId, e.target.value as PaymentKind)}>
                <option value="DEPOSIT">Đặt cọc</option><option value="FINAL">Thanh toán cuối</option>
              </Select>
            </Field>
            <Field label="Số tiền" hint={formatVND(amount)} error={amountError}>
              <Input type="number" min={0} step={50_000} value={amount} onChange={(e) => setAmount(Math.max(0, Number(e.target.value)))} />
            </Field>
            {totals && <p className="rounded-lg bg-slate-50 p-3 text-xs text-slate-500">Công nợ hiện tại: <b className="tabular-nums text-slate-800">{formatVND(totals.remaining)}</b></p>}
            <Button block icon={Plus} disabled={!order || amount <= 0 || !!amountError} onClick={submit}>Ghi nhận giao dịch</Button>
            <Alert tone="info">QR/VietQR chỉ hiển thị thông tin chuyển khoản; hệ thống chưa tự đối soát nên cần Admin xác nhận.</Alert>
          </div>
        </Card>
      </div>
    </>
  );
};
