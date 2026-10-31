import { useState } from 'react';
import { Banknote, CheckCircle2, CircleDollarSign, Receipt, ShieldCheck } from 'lucide-react';
import { Badge, Button, Card, cx, PageHeader, Stat, Tabs } from '@/components/ui';
import { formatTime, formatVND } from '@/services/format';
import { useApp } from '@/services/store/AppStore';

export const FinanceModule = () => {
  const { state, dispatch } = useApp();
  const [tab, setTab] = useState<"refunds" | "ledger">("refunds");
  const pendingRefund = state.refunds
    .filter((r) => r.status === "PENDING")
    .reduce((s, r) => s + r.amount, 0);
  const penalty = state.ledger
    .filter((l) => l.category === "Thu nhập khác / Phí phạt hủy đơn")
    .reduce((s, l) => s + l.amount, 0);
  const comp = state.ledger
    .filter((l) => l.category === "Chi phí bồi thường khiếu nại")
    .reduce((s, l) => s + l.amount, 0);

  return (
    <>
      <PageHeader
        eyebrow="Tài chính"
        title="Hoàn tiền"
        subtitle="Hủy ≥ 12h: tự sinh phiếu chi hoàn 100%. Hủy < 12h: tự hạch toán phí phạt."
      />
      <div className="mb-4 grid gap-4 sm:grid-cols-3">
        <Stat
          label="Phiếu chi chờ duyệt"
          value={formatVND(pendingRefund)}
          icon={Receipt}
          tone="warning"
        />
        <Stat
          label="Thu nhập khác (phí phạt)"
          value={formatVND(penalty)}
          icon={CircleDollarSign}
          tone="success"
        />
        <Stat
          label="Chi phí bồi thường"
          value={formatVND(comp)}
          icon={ShieldCheck}
          tone="danger"
          hint="Hạch toán riêng — không trừ doanh thu đơn"
        />
      </div>
      <Tabs
        className="mb-4"
        value={tab}
        onChange={setTab}
        tabs={[
          {
            id: "refunds",
            label: "Phiếu yêu cầu chi (Hoàn cọc 100%)",
            icon: Receipt,
          },
          { id: "ledger", label: "Sổ cái thu nhập khác", icon: Banknote },
        ]}
      />
      {tab === "refunds" ? (
        <Card padded={false}>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] text-sm">
              <thead>
                <tr className="border-b border-slate-100 bg-slate-50/60 text-left text-xs font-semibold tracking-wide text-slate-500 uppercase">
                  <th className="px-6 py-3">Phiếu chi</th>
                  <th className="px-3 py-3">Đơn / Khách</th>
                  <th className="px-3 py-3">Hủy trước</th>
                  <th className="px-3 py-3 text-right">Số tiền</th>
                  <th className="px-6 py-3 text-right">Duyệt</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {state.refunds.map((r) => (
                  <tr key={r.id}>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <p className="font-semibold tabular-nums">{r.id}</p>
                      <p className="text-xs text-slate-500 tabular-nums">
                        Tự động · {formatTime(r.createdAt)}
                      </p>
                    </td>
                    <td className="px-3 py-4">
                      <p className="font-medium tabular-nums">#{r.orderId}</p>
                      <p className="text-xs text-slate-500">{r.customerName}</p>
                    </td>
                    <td className="px-3 py-4">
                      <Badge tone="success" className="tabular-nums">
                        {r.hoursBeforeDeparture}h ≥ 12h
                      </Badge>
                    </td>
                    <td className="px-3 py-4 text-right font-semibold tabular-nums">
                      {formatVND(r.amount)}
                    </td>
                    <td className="px-6 py-4 text-right">
                      {r.status === "PENDING" ? (
                        <Button
                          size="sm"
                          icon={CheckCircle2}
                          onClick={() =>
                            dispatch({ type: "APPROVE_REFUND", refundId: r.id })
                          }
                        >
                          Approve / Duyệt hoàn tiền
                        </Button>
                      ) : (
                        <Badge tone="success" icon={CheckCircle2}>
                          Đã chi
                        </Badge>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      ) : (
        <Card padded={false}>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] text-sm">
              <thead>
                <tr className="border-b border-slate-100 bg-slate-50/60 text-left text-xs font-semibold tracking-wide text-slate-500 uppercase">
                  <th className="px-6 py-3">Bút toán</th>
                  <th className="px-3 py-3">Phân loại</th>
                  <th className="px-3 py-3">Diễn giải</th>
                  <th className="px-6 py-3 text-right">Số tiền</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {state.ledger.map((l) => (
                  <tr key={l.id}>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <p className="font-semibold tabular-nums">#{l.orderId}</p>
                      <p className="text-xs text-slate-500 tabular-nums">
                        {formatTime(l.createdAt)}
                      </p>
                    </td>
                    <td className="px-3 py-4">
                      <Badge
                        tone={
                          l.category === "Chi phí bồi thường khiếu nại"
                            ? "danger"
                            : "success"
                        }
                      >
                        {l.category}
                      </Badge>
                    </td>
                    <td className="px-3 py-4 text-slate-600">{l.note}</td>
                    <td
                      className={cx(
                        "px-6 py-4 text-right font-semibold tabular-nums",
                        l.category === "Chi phí bồi thường khiếu nại"
                          ? "text-rose-600"
                          : "text-emerald-600",
                      )}
                    >
                      {l.category === "Chi phí bồi thường khiếu nại"
                        ? "−"
                        : "+"}
                      {formatVND(l.amount)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}
    </>
  );
};
