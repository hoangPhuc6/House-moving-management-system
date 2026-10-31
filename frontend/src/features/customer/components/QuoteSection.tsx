import { useState } from 'react';
import {
  CheckCircle2,
  ChevronDown,
  FileText,
  History,
  Lock,
  MessageSquare,
  Phone,
  QrCode,
} from 'lucide-react';
import { Button, Card, cx, EmptyState, Field, KeyValue, Modal, QrMock } from '@/components/ui';
import { SectionTitle } from '@/features/customer/components/SectionTitle';
import { AutoCancelCountdown } from '@/features/order/components/AutoCancelCountdown';
import { StickyActionBar } from '@/layouts/MobileShell';
import { formatTime, formatVND } from '@/services/format';
import { computeTotals, lineTotal } from '@/services/pricing';
import { useApp } from '@/services/store/AppStore';
import type { Order } from '@/types';

export const QuoteSection = ({ order }: { order: Order }) => {
  const { dispatch } = useApp();
  const [qrOpen, setQrOpen] = useState(false);
  const [negotiateOpen, setNegotiateOpen] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [message, setMessage] = useState("");
  const totals = computeTotals(order);
  const discountLog = [...order.auditLog]
    .reverse()
    .find(
      (l) =>
        l.actorRole === "COORDINATOR" &&
        (l.action === "Chỉnh sửa báo giá" ||
          l.action === "Cập nhật giảm giá toàn đơn"),
    );
  const canDeposit = order.status === "SURVEYED_PENDING_CONFIRM";

  if (order.quoteItems.length === 0) {
    return (
      <section id="quote-section" className="scroll-mt-24">
        <SectionTitle>Báo giá</SectionTitle>
        <Card>
          <EmptyState icon={FileText} title="Báo giá đang được chuẩn bị">
            {order.onSiteSurveyRequest
              ? "Nhân viên sẽ gửi báo giá chi tiết ngay sau buổi khảo sát trực tiếp tại nhà bạn."
              : "Điều phối viên đang lập báo giá chi tiết dựa trên ảnh bạn cung cấp."}
          </EmptyState>
        </Card>
      </section>
    );
  }

  return (
    <section
      id="quote-section"
      className="grid scroll-mt-24 gap-4 lg:grid-cols-[1fr_340px] lg:items-start"
    >
      <div className="space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-slate-200 bg-white px-4 py-4 shadow-sm sm:px-5">
          <div>
            <h2 className="text-sm font-semibold text-slate-500">Báo giá</h2>
            <p className="text-3xl font-bold tracking-tight text-slate-900 tabular-nums">
              {formatVND(totals.grandTotal)}
            </p>
          </div>
          {canDeposit && order.autoCancelDeadline && (
            <AutoCancelCountdown deadline={order.autoCancelDeadline} />
          )}
        </div>

        {discountLog && (
          <div className="flex items-start gap-2.5 rounded-2xl border border-blue-200 bg-blue-50/70 px-4 py-3 text-sm text-blue-900">
            <History className="mt-0.5 size-4 shrink-0 text-blue-600" />
            <span>
              <b>Đơn giá vừa được CSKH cập nhật.</b> {discountLog.detail}
            </span>
          </div>
        )}

        <Card padded={false}>
          <ul className="divide-y divide-slate-100">
            {order.quoteItems.map((it) => (
              <li
                key={it.id}
                className={cx(
                  "flex items-start gap-3 px-4 py-3.5 sm:px-5",
                  it.category === "FIELD_ADJUSTMENT" && "bg-amber-50/60",
                )}
              >
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-medium text-slate-900">
                    {it.name}
                  </p>
                  <p className="mt-0.5 text-xs text-slate-500 tabular-nums">
                    {it.quantity} {it.unit} × {formatVND(it.unitPrice)}
                    {it.category === "FIELD_ADJUSTMENT" && (
                      <span className="ml-1.5 text-amber-700">
                        · Phát sinh đã xác nhận
                      </span>
                    )}
                  </p>
                </div>
                <div className="text-right">
                  <p className="text-sm font-semibold tabular-nums">
                    {formatVND(lineTotal(it))}
                  </p>
                </div>
              </li>
            ))}
            {order.pendingAdjustment && (
              <li className="flex items-start gap-3 bg-rose-50/60 px-4 py-3.5 sm:px-5">
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-medium text-slate-900">
                    {order.pendingAdjustment.name}
                  </p>
                  <p className="mt-0.5 flex items-center gap-1 text-xs font-medium text-rose-600">
                    <Lock className="size-3" /> Chờ bạn xác nhận
                  </p>
                </div>
                <p className="text-sm font-semibold text-rose-600 tabular-nums">
                  +{formatVND(order.pendingAdjustment.unitPrice)}
                </p>
              </li>
            )}
          </ul>
          <div className="border-t border-slate-100 bg-slate-50/60 px-4 py-3 sm:px-5">
            <KeyValue label="Tạm tính" value={formatVND(totals.baseQuote)} />
            {totals.fieldAdjustments > 0 && (
              <KeyValue
                label="Phát sinh hiện trường"
                value={formatVND(totals.fieldAdjustments)}
              />
            )}
            {totals.discount > 0 && (
              <KeyValue
                label={`Giảm giá toàn đơn (${order.discountPercent}%)`}
                value={`−${formatVND(totals.discount)}`}
                tone="success"
              />
            )}
            {totals.waitFee > 0 && (
              <KeyValue label="Phí chờ" value={formatVND(totals.waitFee)} />
            )}
            <KeyValue
              label="Tổng cộng"
              value={formatVND(totals.grandTotal)}
              strong
            />
          </div>
        </Card>

        <Card padded={false}>
          <button
            type="button"
            onClick={() => setHistoryOpen((v) => !v)}
            className="flex w-full items-center justify-between px-4 py-4 text-left sm:px-5"
          >
            <span className="flex items-center gap-2 text-sm font-semibold">
              <History className="size-4 text-slate-400" /> Lịch sử thay đổi báo
              giá
            </span>
            <ChevronDown
              className={cx(
                "size-4 text-slate-400 transition-transform",
                historyOpen && "rotate-180",
              )}
            />
          </button>
          {historyOpen && (
            <ul className="space-y-3 border-t border-slate-100 px-4 py-4 sm:px-5">
              {[...order.auditLog].reverse().map((l) => (
                <li key={l.id} className="text-sm">
                  <p className="font-medium text-slate-800">{l.action}</p>
                  <p className="text-slate-500">{l.detail}</p>
                  <p className="mt-0.5 text-xs text-slate-400">
                    {l.actor} ·{" "}
                    <span className="tabular-nums">
                      {formatTime(l.timestamp)}
                    </span>
                  </p>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>

      {/* Action panel */}
      <div className="lg:sticky lg:top-24">
        {canDeposit ? (
          <StickyActionBar className="lg:mt-0">
            <div className="flex items-center gap-3 md:flex-col md:items-stretch">
              <div className="min-w-0 flex-1 md:mb-1">
                <p className="text-xs text-slate-500">Đặt cọc giữ lịch</p>
                <p className="text-lg font-bold tabular-nums">
                  {formatVND(order.depositRequired)}
                </p>
              </div>
              <Button
                variant="secondary"
                size="lg"
                icon={MessageSquare}
                onClick={() => setNegotiateOpen(true)}
                className="px-3 md:order-last"
                aria-label="Liên hệ thương lượng"
              >
                <span className="hidden md:inline">Liên hệ thương lượng</span>
              </Button>
              <Button size="lg" icon={QrCode} onClick={() => setQrOpen(true)}>
                Đồng ý & Đặt cọc
              </Button>
            </div>
          </StickyActionBar>
        ) : (
          <Card>
            <div className="flex items-center gap-3">
              <span className="grid size-10 place-items-center rounded-xl bg-emerald-50 text-emerald-600">
                <CheckCircle2 className="size-5" />
              </span>
              <div>
                <p className="text-sm font-semibold">
                  {order.depositPaid > 0 ? "Đã đặt cọc" : "Báo giá đã đóng"}
                </p>
                <p className="text-xs text-slate-500 tabular-nums">
                  {order.depositPaid > 0 ? formatVND(order.depositPaid) : "—"}
                </p>
              </div>
            </div>
            <Button
              variant="secondary"
              block
              className="mt-4"
              icon={MessageSquare}
              onClick={() => setNegotiateOpen(true)}
            >
              Liên hệ CSKH
            </Button>
          </Card>
        )}
      </div>

      <Modal
        open={qrOpen}
        onClose={() => setQrOpen(false)}
        title="Đặt cọc qua VietQR"
        footer={
          <>
            <Button variant="secondary" onClick={() => setQrOpen(false)}>
              Để sau
            </Button>
            <Button
              icon={CheckCircle2}
              onClick={() => {
                dispatch({ type: "PAY_DEPOSIT", orderId: order.id });
                setQrOpen(false);
              }}
            >
              Tôi đã chuyển khoản
            </Button>
          </>
        }
      >
        <div className="flex flex-col items-center gap-4 sm:flex-row sm:items-start">
          <div className="rounded-2xl border border-slate-200 p-3">
            <QrMock seed={`${order.id}-deposit`} />
          </div>
          <div className="w-full flex-1">
            <KeyValue
              label="Số tiền"
              value={formatVND(order.depositRequired)}
              strong
            />
            <KeyValue
              label="Nội dung"
              value={<span className="font-mono text-xs">COC {order.id}</span>}
            />
            <KeyValue label="Người nhận" value="CTY MOVEFLOW" />
            <p className="mt-3 rounded-lg bg-slate-50 p-3 text-xs text-slate-500">
              Mở app ngân hàng và quét mã. Hệ thống tự xác nhận khi nhận được
              giao dịch.
            </p>
          </div>
        </div>
      </Modal>

      <Modal
        open={negotiateOpen}
        onClose={() => setNegotiateOpen(false)}
        title="Liên hệ thương lượng"
        footer={
          <>
            <a
              href="tel:19006868"
              className="inline-flex h-10 items-center justify-center gap-2 rounded-xl border border-slate-200 bg-white px-4 text-sm font-semibold text-slate-800"
            >
              <Phone className="size-4" /> Gọi 1900 6868
            </a>
            <Button
              icon={MessageSquare}
              disabled={!message.trim()}
              onClick={() => {
                dispatch({
                  type: "PUSH_TOAST",
                  tone: "success",
                  message: "Đã gửi yêu cầu. CSKH sẽ phản hồi trong 30 phút.",
                });
                setMessage("");
                setNegotiateOpen(false);
              }}
            >
              Gửi yêu cầu
            </Button>
          </>
        }
      >
        <Field label="Bạn muốn trao đổi điều gì?">
          <textarea
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            rows={4}
            placeholder="VD: Tôi tự đóng thùng carton, có thể giảm 2 nhân công không?"
            className="w-full rounded-xl border border-slate-200 p-3 text-base focus:border-brand focus:ring-4 focus:ring-brand/10 focus:outline-none sm:text-sm"
          />
        </Field>
      </Modal>
    </section>
  );
};
