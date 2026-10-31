import { useState } from 'react';
import { Camera, CheckCircle2, FileWarning, Lock, Scale, Search, XCircle } from 'lucide-react';
import {
  Alert,
  Badge,
  Button,
  Card,
  CardHeader,
  cx,
  EmptyState,
  Field,
  Input,
  KeyValue,
  PageHeader,
  StatusBadge,
} from '@/components/ui';
import { EvidenceTile } from '@/features/admin/components/EvidenceTile';
import { CLAIM_STATUS } from '@/features/admin/constants';
import { matches } from '@/features/admin/utils';
import { formatVND } from '@/services/format';
import { computeTotals } from '@/services/pricing';
import { useApp } from '@/services/store/AppStore';
import type { ClaimTicket } from '@/types';

export const ClaimsModule = ({ query }: { query: string }) => {
  const { state, dispatch } = useApp();
  const [selectedId, setSelectedId] = useState(state.claims[0]?.id ?? "");
  const claim =
    state.claims.find((c) => c.id === selectedId) ?? state.claims[0];
  const [amount, setAmount] = useState<number>(claim?.requestedAmount ?? 0);
  const order = claim
    ? state.orders.find((o) => o.id === claim.orderId)
    : undefined;
  // Khiếu nại có thể đến trước khi đóng hồ sơ: khi đó doanh thu chưa khóa, hiển thị tổng hóa đơn hiện tại.
  const revenueLocked = order?.finalRevenue != null;
  const orderRevenue = order
    ? (order.finalRevenue ?? computeTotals(order).grandTotal)
    : 0;
  const visibleClaims = state.claims.filter((c) =>
    matches(query, c.id, c.orderId, c.customerName, c.title),
  );
  const select = (c: ClaimTicket) => {
    setSelectedId(c.id);
    setAmount(c.approvedAmount ?? c.requestedAmount);
  };

  return (
    <>
      <PageHeader
        eyebrow="Chăm sóc sau bán"
        title="Quản lý khiếu nại"
        subtitle="Khiếu nại được xử lý trên phiếu riêng — doanh thu gốc của đơn không bị thay đổi."
      />
      <div className="grid gap-4 xl:grid-cols-[360px_1fr]">
        <div className="h-fit space-y-4">
          {[
            {
              title: "Đơn khiếu nại chưa xử lý",
              emptyTitle: "Không có khiếu nại chờ xử lý",
              emptyHint: "Khiếu nại mới từ khách hàng sẽ xuất hiện tại đây.",
              items: visibleClaims.filter(
                (c) => c.status === "OPEN" || c.status === "INVESTIGATING",
              ),
            },
            {
              title: "Đơn khiếu nại đã xử lý",
              emptyTitle: "Chưa có khiếu nại đã xử lý",
              emptyHint: "Phiếu đã duyệt hoặc từ chối sẽ được lưu tại đây.",
              items: visibleClaims.filter(
                (c) => c.status === "APPROVED" || c.status === "REJECTED",
              ),
            },
          ].map((section) => (
            <Card key={section.title} padded={false}>
              <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
                <p className="text-sm font-semibold">{section.title}</p>
                <span className="text-xs text-slate-400 tabular-nums">
                  {section.items.length} phiếu
                </span>
              </div>
              {section.items.length === 0 ? (
                <div className="p-4">
                  <EmptyState icon={FileWarning} title={section.emptyTitle}>
                    {section.emptyHint}
                  </EmptyState>
                </div>
              ) : (
                <ul className="divide-y divide-slate-100">
                  {section.items.map((c) => (
                    <li key={c.id}>
                      <button
                        type="button"
                        onClick={() => select(c)}
                        className={cx(
                          "w-full px-4 py-3.5 text-left transition-colors",
                          c.id === claim?.id
                            ? "bg-slate-50"
                            : "hover:bg-slate-50/60",
                        )}
                      >
                        <div className="flex items-center justify-between gap-2">
                          <span className="font-semibold tabular-nums">
                            #{c.id}
                          </span>
                          <Badge tone={CLAIM_STATUS[c.status].tone}>
                            {CLAIM_STATUS[c.status].label}
                          </Badge>
                        </div>
                        <p className="mt-1 truncate text-sm text-slate-700">
                          {c.title}
                        </p>
                        <p className="mt-0.5 text-xs text-slate-500 tabular-nums">
                          Liên kết #{c.orderId} · {c.customerName}
                        </p>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </Card>
          ))}
        </div>

        {claim ? (
          <div className="space-y-4">
            <Card>
              <CardHeader
                eyebrow={`#${claim.id} → #${claim.orderId}`}
                title={claim.title}
                subtitle={claim.description}
                action={
                  <Badge tone={CLAIM_STATUS[claim.status].tone}>
                    {CLAIM_STATUS[claim.status].label}
                  </Badge>
                }
              />
              <div className="grid gap-4 md:grid-cols-2">
                <div className="rounded-2xl border border-slate-200 bg-slate-50/60 p-4">
                  <p className="mb-3 flex items-center gap-2 text-sm font-semibold text-slate-900">
                    <Camera className="size-4 text-slate-500" /> Ảnh/Video lúc
                    đóng gói (Đội xe chụp)
                  </p>
                  <div className="grid grid-cols-2 gap-2">
                    {claim.packingEvidence.map((m) => (
                      <EvidenceTile key={m.id} m={m} />
                    ))}
                  </div>
                </div>
                <div className="rounded-2xl border border-rose-200 bg-rose-50/40 p-4">
                  <p className="mb-3 flex items-center gap-2 text-sm font-semibold text-rose-900">
                    <FileWarning className="size-4 text-rose-500" /> Ảnh khách
                    hàng khiếu nại
                  </p>
                  <div className="grid grid-cols-2 gap-2">
                    {claim.customerEvidence.map((m) => (
                      <EvidenceTile key={m.id} m={m} />
                    ))}
                  </div>
                </div>
              </div>
            </Card>

            <div className="grid gap-4 lg:grid-cols-2">
              <Card>
                <CardHeader
                  title="Hồ sơ đơn gốc"
                  icon={Lock}
                  subtitle={
                    revenueLocked
                      ? "Read-only — bất biến"
                      : "Chưa đóng hồ sơ — doanh thu chưa khóa"
                  }
                />
                <KeyValue label="Đơn hàng" value={`#${claim.orderId}`} />
                <KeyValue
                  label="Trạng thái"
                  value={order ? <StatusBadge status={order.status} /> : "—"}
                />
                <KeyValue
                  label={
                    revenueLocked ? (
                      <span className="inline-flex items-center gap-1.5">
                        <Lock className="size-3.5" /> Doanh thu gốc
                      </span>
                    ) : (
                      "Tổng hóa đơn (chưa khóa)"
                    )
                  }
                  value={formatVND(orderRevenue)}
                  strong
                />
                <p className="mt-3 rounded-lg bg-slate-50 p-3 text-xs text-slate-500">
                  Khoản bồi thường được hạch toán vào “Chi phí bồi thường khiếu
                  nại” trên sổ cái — doanh thu của đơn gốc giữ nguyên.
                </p>
              </Card>
              <Card>
                <CardHeader title="Phê duyệt bồi thường" icon={Scale} />
                <KeyValue
                  label="Khách đề nghị"
                  value={formatVND(claim.requestedAmount)}
                />
                {claim.status === "APPROVED" ? (
                  <Alert
                    tone="success"
                    className="mt-3"
                    title={`Đã duyệt ${formatVND(claim.approvedAmount ?? 0)}`}
                  >
                    {revenueLocked ? "Doanh thu gốc" : "Tổng hóa đơn"} #
                    {claim.orderId} vẫn là{" "}
                    <b className="tabular-nums">{formatVND(orderRevenue)}</b>.
                  </Alert>
                ) : claim.status === "REJECTED" ? (
                  <Alert
                    tone="danger"
                    className="mt-3"
                    title="Đã từ chối khiếu nại"
                  />
                ) : (
                  <>
                    <Field
                      label="Số tiền duyệt bồi thường"
                      hint={formatVND(amount)}
                      className="mt-3"
                    >
                      <Input
                        type="number"
                        min={0}
                        step={50_000}
                        value={amount}
                        onChange={(e) =>
                          setAmount(Math.max(0, Number(e.target.value)))
                        }
                      />
                    </Field>
                    <div className="mt-4 flex flex-wrap gap-2">
                      {claim.status === "OPEN" && (
                        <Button
                          variant="secondary"
                          icon={Search}
                          onClick={() =>
                            dispatch({
                              type: "SET_CLAIM_STATUS",
                              claimId: claim.id,
                              status: "INVESTIGATING",
                              approvedAmount: null,
                            })
                          }
                        >
                          Đối chiếu
                        </Button>
                      )}
                      <Button
                        variant="ghost"
                        icon={XCircle}
                        className="text-rose-600 hover:bg-rose-50"
                        onClick={() =>
                          dispatch({
                            type: "SET_CLAIM_STATUS",
                            claimId: claim.id,
                            status: "REJECTED",
                            approvedAmount: null,
                          })
                        }
                      >
                        Từ chối
                      </Button>
                      <Button
                        variant="success"
                        icon={CheckCircle2}
                        disabled={amount <= 0}
                        onClick={() =>
                          dispatch({
                            type: "SET_CLAIM_STATUS",
                            claimId: claim.id,
                            status: "APPROVED",
                            approvedAmount: amount,
                          })
                        }
                      >
                        Duyệt bồi thường
                      </Button>
                    </div>
                  </>
                )}
              </Card>
            </div>
          </div>
        ) : (
          <Card>
            <EmptyState icon={FileWarning} title="Chưa có phiếu khiếu nại" />
          </Card>
        )}
      </div>
    </>
  );
};
