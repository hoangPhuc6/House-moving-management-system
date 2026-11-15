import { Fragment, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  ArrowRight,
  CheckCircle2,
  ChevronDown,
  CircleDollarSign,
  Lock,
  LockOpen,
  PackageCheck,
  Receipt,
  Scale,
  Warehouse,
  XCircle,
} from 'lucide-react';
import {
  Badge,
  Button,
  Card,
  CardHeader,
  cx,
  EmptyState,
  PageHeader,
  Stat,
  StatusBadge,
  Tooltip,
} from '@/components/ui';
import { MaterialLinesTable, MaterialSlipActions, MaterialStatusBadge, ReturnMaterialsModal } from '@/features/admin/components/MaterialSlip';
import { matches } from '@/features/admin/utils';
import { ROUTES } from '@/routes/paths';
import { formatVND } from '@/services/format';
import { canCloseOrder, computeTotals, selectSettlement } from '@/services/pricing';
import { useApp } from '@/services/store/AppStore';

export const SettlementModule = ({ query }: { query: string }) => {
  const { state, dispatch } = useApp();
  const [expanded, setExpanded] = useState<string | null>(null);
  const [returning, setReturning] = useState<string | null>(null);
  const returningOrder = state.orders.find((o) => o.id === returning);
  const pending = state.orders.filter(
    (o) =>
      o.status === "PENDING_PAYMENT_OR_CLAIM" &&
      matches(query, o.id, o.customerName),
  );
  const completed = state.orders.filter((o) => o.status === "COMPLETED");
  const revenue = completed.reduce((s, o) => s + (o.finalRevenue ?? 0), 0);
  const receivable = pending.reduce(
    (s, o) => s + computeTotals(o).remaining,
    0,
  );

  return (
    <>
      <PageHeader
        eyebrow="Quyết toán"
        title="Kiểm soát vật tư & đóng hồ sơ"
        subtitle="Hồ sơ tự đóng ngay khi công nợ khách hàng = 0đ và phiếu vật tư đã quyết toán (đối chiếu xuất – dùng – trả). Khách đã trả đủ thì thấy đơn Hoàn tất, không phải chờ bước nội bộ này."
      />
      <div className="mb-4 grid grid-cols-2 gap-4 xl:grid-cols-4">
        <Stat
          label="Chờ quyết toán"
          value={pending.length}
          icon={Receipt}
          tone="warning"
          hint="Đã bàn giao, chờ đóng hồ sơ"
        />
        <Stat
          label="Công nợ phải thu"
          value={formatVND(receivable)}
          icon={CircleDollarSign}
          tone="danger"
        />
        <Stat
          label="Doanh thu đã khóa"
          value={formatVND(revenue)}
          icon={Lock}
          tone="success"
          hint={`${completed.length} đơn hoàn tất`}
        />
        <Stat
          label="Vật tư chưa quyết toán"
          value={pending.filter((o) => !selectSettlement(o).materialsSettled).length}
          icon={Warehouse}
          tone="info"
          hint="Đơn còn vật tư / dụng cụ chưa kiểm nhận trả"
        />
      </div>

      <Card padded={false}>
        <div className="p-6 pb-4">
          <CardHeader
            title="Hồ sơ chờ đóng"
            icon={Scale}
            subtitle="Cần đủ 2 điều kiện: công nợ = 0đ và đã quyết toán phiếu vật tư"
          />
        </div>
        {pending.length === 0 ? (
          <div className="px-6 pb-6">
            <EmptyState icon={PackageCheck} title="Không có hồ sơ chờ đóng">
              Đơn sẽ xuất hiện sau khi đội xe lập biên bản bàn giao.
            </EmptyState>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[920px] text-sm">
              <thead>
                <tr className="border-y border-slate-100 bg-slate-50/60 text-left text-xs font-semibold tracking-wide text-slate-500 uppercase">
                  <th className="px-6 py-3">Đơn hàng</th>
                  <th className="px-3 py-3 text-right whitespace-nowrap">
                    Tổng giá trị
                  </th>
                  <th className="px-3 py-3">Công nợ khách hàng</th>
                  <th className="px-3 py-3">Vật tư & dụng cụ</th>
                  <th className="px-6 py-3 text-right">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {pending.map((o) => {
                  const s = selectSettlement(o);
                  const ok = canCloseOrder(s);
                  const hasLines = o.materials.lines.length > 0;
                  const open = expanded === o.id;
                  return (
                    <Fragment key={o.id}>
                    <tr className="align-middle">
                      <td className="px-6 py-4 whitespace-nowrap">
                        <p className="font-semibold tabular-nums">#{o.id}</p>
                        <p className="text-xs text-slate-500">
                          {o.customerName}
                        </p>
                      </td>
                      <td className="px-3 py-4 text-right font-semibold whitespace-nowrap tabular-nums">
                        {formatVND(computeTotals(o).grandTotal)}
                      </td>
                      <td className="px-3 py-4">
                        <Badge
                          tone={s.remainingBalance === 0 ? "success" : "danger"}
                          icon={
                            s.remainingBalance === 0 ? CheckCircle2 : XCircle
                          }
                          className="tabular-nums"
                        >
                          {s.remainingBalance === 0
                            ? "Đã thu đủ · 0 đ"
                            : `Còn nợ ${formatVND(s.remainingBalance)}`}
                        </Badge>
                      </td>
                      <td className="px-3 py-4">
                        {hasLines ? (
                          <div className="flex flex-col items-start gap-1.5">
                            <div className="flex items-center gap-1.5">
                              <MaterialStatusBadge slip={o.materials} />
                              <button
                                type="button"
                                onClick={() => setExpanded(open ? null : o.id)}
                                className="inline-flex items-center gap-0.5 text-xs font-semibold text-slate-500 hover:text-slate-800"
                                aria-expanded={open}
                              >
                                Đối chiếu
                                <ChevronDown className={cx("size-3.5 transition-transform", open && "rotate-180")} />
                              </button>
                            </div>
                            <MaterialSlipActions order={o} onIssue={() => undefined} onReturn={() => setReturning(o.id)} />
                          </div>
                        ) : (
                          <Badge tone="neutral" icon={PackageCheck}>
                            Không dùng vật tư
                          </Badge>
                        )}
                      </td>
                      <td className="px-6 py-4 text-right">
                        <Tooltip
                          enabled={!ok}
                          content={`Hard-Lock: ${[
                            s.remainingBalance > 0 ? "khách còn nợ " + formatVND(s.remainingBalance) : "",
                            !s.materialsSettled ? "phiếu vật tư chưa quyết toán" : "",
                          ].filter(Boolean).join(" · ")}.`}
                        >
                          <Button
                            size="sm"
                            variant={ok ? "success" : "secondary"}
                            icon={ok ? LockOpen : Lock}
                            disabled={!ok}
                            onClick={() =>
                              dispatch({ type: "CLOSE_ORDER", orderId: o.id })
                            }
                          >
                            Đóng hồ sơ (Hoàn tất)
                          </Button>
                        </Tooltip>
                      </td>
                    </tr>
                    {open && hasLines && (
                      <tr>
                        <td colSpan={5} className="bg-slate-50/60 px-6 py-4">
                          <MaterialLinesTable slip={o.materials} />
                          {o.materials.resolutionNote && o.materials.lines.some((l) => l.lost > 0) && (
                            <p className="mt-3 rounded-lg bg-amber-50 p-3 text-xs text-amber-800">
                              Biên bản chênh lệch: {o.materials.resolutionNote}
                            </p>
                          )}
                        </td>
                      </tr>
                    )}
                    </Fragment>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Card className="mt-4" padded={false}>
        <div className="p-6 pb-4">
          <CardHeader
            title="Hồ sơ đã đóng gần đây"
            icon={Lock}
            subtitle="Doanh thu đã khóa — không thể chỉnh sửa"
            action={
              <Link
                to={`${ROUTES.admin.orders}?status=COMPLETED`}
                className="inline-flex items-center gap-1 text-sm font-semibold text-brand hover:underline"
              >
                Xem tất cả <ArrowRight className="size-4" />
              </Link>
            }
          />
        </div>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[640px] text-sm">
            <tbody className="divide-y divide-slate-100 border-t border-slate-100">
              {completed.map((o) => (
                <tr key={o.id}>
                  <td className="px-6 py-3.5 font-semibold tabular-nums">
                    #{o.id}
                  </td>
                  <td className="px-3 py-3.5 text-slate-600">
                    {o.customerName}
                  </td>
                  <td className="px-3 py-3.5">
                    <StatusBadge status={o.status} />
                  </td>
                  <td className="px-6 py-3.5 text-right font-semibold tabular-nums">
                    <span className="inline-flex items-center gap-1.5">
                      <Lock className="size-3.5 text-slate-400" />
                      {formatVND(o.finalRevenue ?? 0)}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      {returningOrder && <ReturnMaterialsModal order={returningOrder} onClose={() => setReturning(null)} />}
    </>
  );
};
