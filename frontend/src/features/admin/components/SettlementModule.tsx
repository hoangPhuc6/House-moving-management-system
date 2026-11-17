import {
  CheckCircle2,
  CircleDollarSign,
  Clock,
  Lock,
  PackageCheck,
  Receipt,
  Scale,
  Warehouse,
  XCircle,
} from 'lucide-react';
import {
  Badge,
  Card,
  CardHeader,
  EmptyState,
  PageHeader,
  Stat,
  StatusBadge,
} from '@/components/ui';
import { isTruckOnly } from '@/constants/order';
import { matches } from '@/features/admin/utils';
import { formatVND } from '@/services/format';
import { computeTotals, selectSettlement } from '@/services/pricing';
import { useApp } from '@/services/store/AppStore';

/**
 * Theo dõi quyết toán (chỉ xem): khách trả đủ + đội xe trả dụng cụ về kho thì hệ thống tự đóng hồ sơ.
 * Admin chỉ cần nhìn đơn nào đang kẹt ở điều kiện nào để nhắc đúng người.
 */
export const SettlementModule = ({ query }: { query: string }) => {
  const { state } = useApp();
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
        title="Theo dõi quyết toán"
        subtitle="Hệ thống tự đóng hồ sơ và khóa doanh thu khi khách đã trả đủ và đội xe đã trả dụng cụ về kho."
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
          label="Dụng cụ chưa nhập kho"
          value={pending.filter((o) => !o.isWarehouseReturned).length}
          icon={Warehouse}
          tone="info"
          hint="Bộ dụng cụ đang ngoài hiện trường"
        />
      </div>

      <Card padded={false}>
        <div className="p-6 pb-4">
          <CardHeader
            title="Hồ sơ chờ đóng"
            icon={Scale}
            subtitle="Tự đóng khi đủ 2 điều kiện: công nợ = 0đ và đội xe đã trả dụng cụ về kho"
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
                  <th className="px-3 py-3">Thu hồi dụng cụ</th>
                  <th className="px-6 py-3 text-right">Đang chờ</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {pending.map((o) => {
                  const s = selectSettlement(o);
                  return (
                    <tr key={o.id} className="align-middle">
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
                        {isTruckOnly(o) ? (
                          <Badge tone="neutral">Không xuất dụng cụ (chỉ thuê xe)</Badge>
                        ) : s.isWarehouseReturned ? (
                          <Badge tone="success" icon={CheckCircle2}>
                            Đã thu hồi & nhập kho
                          </Badge>
                        ) : (
                          <Badge tone="warning" icon={Clock}>
                            Đội xe chưa trả về kho
                          </Badge>
                        )}
                      </td>
                      <td className="px-6 py-4 text-right text-xs text-slate-500">
                        {[
                          s.remainingBalance > 0 ? "Khách thanh toán" : null,
                          !s.isWarehouseReturned ? "Đội xe trả dụng cụ" : null,
                        ]
                          .filter(Boolean)
                          .join(" · ")}
                      </td>
                    </tr>
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
    </>
  );
};
