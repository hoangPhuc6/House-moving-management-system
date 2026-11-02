import { useState } from 'react';
import { AlertTriangle, Camera, CheckCircle2, QrCode, ShieldCheck, Star } from 'lucide-react';
import {
  Badge,
  Button,
  Card,
  cx,
  Divider,
  Drawer,
  Field,
  Input,
  KeyValue,
  QrMock,
} from '@/components/ui';
import { SectionTitle } from '@/features/customer/components/SectionTitle';
import { CLAIM_PRESETS } from '@/features/customer/constants';
import { StickyActionBar } from '@/layouts/MobileShell';
import { formatVND } from '@/services/format';
import { canFileClaim, computeTotals } from '@/services/pricing';
import { useApp } from '@/services/store/AppStore';
import { useCatalog } from '@/services/store/CatalogStore';
import type { Order } from '@/types';

export const PaymentSection = ({ order }: { order: Order }) => {
  const { state, dispatch } = useApp();
  const [drawer, setDrawer] = useState(false);
  const [title, setTitle] = useState(CLAIM_PRESETS[0] ?? "");
  const [desc, setDesc] = useState("");
  const [amount, setAmount] = useState(1_000_000);
  const { catalog, dispatchCatalog } = useCatalog();
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState("");
  const feedback = catalog.feedbacks.find((f) => f.orderId === order.id);
  const payments = [
    { label: "Đặt cọc", amount: order.depositPaid },
    { label: "Thanh toán cuối", amount: order.balancePaid },
  ].filter((p) => p.amount > 0);
  const totals = computeTotals(order);
  const extras = totals.fieldAdjustments + totals.waitFee;
  const payable =
    order.status === "PENDING_PAYMENT_OR_CLAIM" && totals.remaining > 0;
  const completed = order.status === "COMPLETED";
  const claimable = canFileClaim(order);
  const claims = state.claims.filter((c) => c.orderId === order.id);

  return (
    <section id="payment-section" className="scroll-mt-24">
      <SectionTitle>Thanh toán</SectionTitle>
      <div className="grid gap-4 lg:grid-cols-[1fr_360px] lg:items-start">
        <div className="space-y-4">
          <Card>
            <KeyValue
              label="Tổng báo giá"
              value={formatVND(totals.baseQuote)}
            />
            {totals.discount > 0 && (
              <KeyValue
                label={`Giảm giá toàn đơn (${order.discountPercent}%)`}
                value={`−${formatVND(totals.discount)}`}
                tone="success"
              />
            )}
            <KeyValue
              label="Tiền đã cọc"
              value={`−${formatVND(order.depositPaid)}`}
              tone="success"
            />
            <KeyValue
              label="Phụ phí phát sinh"
              value={`+${formatVND(extras)}`}
              tone={extras > 0 ? "danger" : undefined}
            />
            {order.balancePaid > 0 && (
              <KeyValue
                label="Đã thanh toán"
                value={`−${formatVND(order.balancePaid)}`}
                tone="success"
              />
            )}
            <Divider className="my-3" />
            <div className="flex items-center justify-between">
              <span className="font-semibold text-slate-900">
                Cần thanh toán
              </span>
              <span
                className={cx(
                  "text-2xl font-bold tracking-tight tabular-nums",
                  totals.remaining === 0
                    ? "text-emerald-600"
                    : "text-slate-900",
                )}
              >
                {formatVND(totals.remaining)}
              </span>
            </div>
            {totals.remaining === 0 && order.balancePaid > 0 && (
              <p className="mt-3 flex items-center gap-2 rounded-xl bg-emerald-50 p-3 text-sm font-medium text-emerald-700">
                <CheckCircle2 className="size-4" /> Bạn đã thanh toán đầy đủ.
                Cảm ơn bạn!
              </p>
            )}
          </Card>

          {payable && (
            <Card className="flex flex-col items-center text-center">
              <div className="rounded-2xl border border-slate-200 p-3">
                <QrMock
                  seed={`${order.id}-balance-${totals.remaining}`}
                  size={180}
                />
              </div>
              <p className="mt-3 text-sm text-slate-500">
                Quét mã bằng ứng dụng ngân hàng hoặc ví điện tử
              </p>
              <div className="mt-5 hidden w-full max-w-sm lg:block">
                <Button
                  block
                  size="lg"
                  icon={QrCode}
                  onClick={() =>
                    dispatch({ type: "PAY_BALANCE", orderId: order.id })
                  }
                >
                  Tôi đã thanh toán {formatVND(totals.remaining)}
                </Button>
              </div>
            </Card>
          )}
        </div>

        <div className="space-y-4 lg:sticky lg:top-24">
          {completed && (
            <Card>
              <p className="text-sm font-semibold">
                {feedback
                  ? "Đánh giá của bạn"
                  : "Bạn hài lòng với chuyến chuyển nhà?"}
              </p>
              <div className="mt-3 flex gap-1.5">
                {[1, 2, 3, 4, 5].map((n) => (
                  <button
                    key={n}
                    type="button"
                    disabled={!!feedback}
                    onClick={() => setRating(n)}
                    aria-label={`${n} sao`}
                    className="p-1"
                  >
                    <Star
                      className={cx(
                        "size-7",
                        n <= (feedback?.stars ?? rating)
                          ? "fill-amber-400 text-amber-400"
                          : "text-slate-300",
                      )}
                    />
                  </button>
                ))}
              </div>
              {feedback ? (
                <p className="mt-2 text-sm text-slate-500">
                  {feedback.comment || "Cảm ơn bạn đã đánh giá!"}
                </p>
              ) : (
                <>
                  <textarea
                    value={comment}
                    onChange={(e) => setComment(e.target.value)}
                    rows={3}
                    placeholder="Chia sẻ trải nghiệm của bạn (không bắt buộc)"
                    className="mt-3 w-full rounded-xl border border-slate-200 p-3 text-base focus:border-brand focus:ring-4 focus:ring-brand/10 focus:outline-none sm:text-sm"
                  />
                  <Button
                    block
                    className="mt-3"
                    disabled={rating === 0}
                    onClick={() => {
                      dispatchCatalog({
                        type: "SUBMIT_FEEDBACK",
                        orderId: order.id,
                        customerName: order.customerName,
                        stars: rating,
                        comment: comment.trim(),
                      });
                      dispatch({
                        type: "PUSH_TOAST",
                        tone: "success",
                        message: "Cảm ơn bạn đã đánh giá!",
                      });
                    }}
                  >
                    Gửi đánh giá
                  </Button>
                </>
              )}
            </Card>
          )}
          {payments.length > 0 && (
            <Card>
              <h3 className="text-[15px] font-semibold">Lịch sử giao dịch</h3>
              <ul className="mt-3 divide-y divide-slate-100 text-sm">
                {payments.map((p) => (
                  <li
                    key={p.label}
                    className="flex items-center justify-between gap-3 py-2.5"
                  >
                    <span className="text-slate-600">{p.label}</span>
                    <span className="flex items-center gap-2 font-semibold tabular-nums">
                      {formatVND(p.amount)}{" "}
                      <Badge tone="success">Thành công</Badge>
                    </span>
                  </li>
                ))}
              </ul>
            </Card>
          )}
          <Card>
            <h3 className="text-[15px] font-semibold">Hỗ trợ sau chuyển nhà</h3>
            <p className="mt-1 text-sm text-slate-500">
              Đồ đạc hư hỏng? Gửi khiếu nại kèm ảnh, chúng tôi phản hồi trong 48
              giờ.
            </p>
            <Button
              block
              variant={claimable ? "dark" : "secondary"}
              icon={AlertTriangle}
              disabled={!claimable}
              onClick={() => setDrawer(true)}
              className="mt-4"
            >
              Gửi khiếu nại / Báo hỏng đồ
            </Button>
            {!claimable && (
              <p className="mt-2 text-xs text-slate-400">
                Khả dụng sau khi bàn giao và thanh toán đủ.
              </p>
            )}
            {claims.length > 0 && (
              <ul className="mt-4 space-y-2">
                {claims.map((c) => (
                  <li
                    key={c.id}
                    className="flex items-center justify-between gap-2 rounded-xl border border-slate-200 p-3 text-sm"
                  >
                    <div className="min-w-0">
                      <p className="font-semibold tabular-nums">{c.id}</p>
                      <p className="truncate text-xs text-slate-500">
                        {c.title}
                      </p>
                    </div>
                    <Badge
                      tone={
                        c.status === "APPROVED"
                          ? "success"
                          : c.status === "REJECTED"
                            ? "danger"
                            : "warning"
                      }
                    >
                      {c.status === "APPROVED"
                        ? `Bồi thường ${formatVND(c.approvedAmount ?? 0)}`
                        : c.status === "REJECTED"
                          ? "Từ chối"
                          : "Đang xử lý"}
                    </Badge>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>

        {payable && (
          <div className="lg:hidden">
            <StickyActionBar>
              <div className="flex items-center gap-3">
                <div className="min-w-0 flex-1">
                  <p className="text-xs text-slate-500">Cần thanh toán</p>
                  <p className="text-lg font-bold tabular-nums">
                    {formatVND(totals.remaining)}
                  </p>
                </div>
                <Button
                  size="lg"
                  icon={QrCode}
                  onClick={() =>
                    dispatch({ type: "PAY_BALANCE", orderId: order.id })
                  }
                >
                  Đã thanh toán QR
                </Button>
              </div>
            </StickyActionBar>
          </div>
        )}
      </div>

      <Drawer
        open={drawer}
        onClose={() => setDrawer(false)}
        title="Gửi khiếu nại"
        subtitle={`Đơn #${order.id}`}
        footer={
          <>
            <Button variant="secondary" onClick={() => setDrawer(false)}>
              Hủy
            </Button>
            <Button
              block
              disabled={!desc.trim() || amount <= 0}
              onClick={() => {
                dispatch({
                  type: "CREATE_CLAIM",
                  orderId: order.id,
                  title,
                  description: desc,
                  requestedAmount: amount,
                });
                setDrawer(false);
                setDesc("");
              }}
            >
              Gửi khiếu nại
            </Button>
          </>
        }
      >
        <div className="space-y-5">
          <div>
            <p className="mb-2 text-[13px] font-semibold text-slate-700">
              Loại sự việc
            </p>
            <div className="flex flex-wrap gap-2">
              {CLAIM_PRESETS.map((p) => (
                <button
                  key={p}
                  type="button"
                  onClick={() => setTitle(p)}
                  className={cx(
                    "rounded-full border px-3 py-2 text-xs font-semibold",
                    title === p
                      ? "border-brand bg-orange-50 text-brand"
                      : "border-slate-200 text-slate-600 hover:border-slate-300",
                  )}
                >
                  {p}
                </button>
              ))}
            </div>
          </div>
          <Field label="Mô tả chi tiết">
            <textarea
              value={desc}
              onChange={(e) => setDesc(e.target.value)}
              rows={4}
              placeholder="Mô tả đồ vật, tình trạng, thời điểm phát hiện…"
              className="w-full rounded-xl border border-slate-200 p-3 text-base focus:border-brand focus:ring-4 focus:ring-brand/10 focus:outline-none sm:text-sm"
            />
          </Field>
          <Field label="Số tiền đề nghị bồi thường" hint={formatVND(amount)}>
            <Input
              type="number"
              inputMode="numeric"
              min={0}
              step={50_000}
              value={amount}
              onChange={(e) => setAmount(Number(e.target.value))}
              className="text-base sm:text-sm"
            />
          </Field>
          <label className="flex cursor-pointer items-center gap-3 rounded-xl border-2 border-dashed border-slate-200 p-4 text-sm text-slate-500 hover:border-brand/50">
            <Camera className="size-5" /> Đính kèm ảnh / video hiện trạng
            <input
              type="file"
              accept="image/*,video/*"
              multiple
              capture="environment"
              className="sr-only"
            />
          </label>
          <p className="flex gap-2 text-xs text-slate-500">
            <ShieldCheck className="size-4 shrink-0 text-emerald-600" /> Mọi đồ
            đạc đã được đội xe chụp ảnh trước khi đóng gói để đối chiếu minh
            bạch.
          </p>
        </div>
      </Drawer>
    </section>
  );
};
