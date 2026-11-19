import { type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowRight, History } from 'lucide-react';
import { Badge, Button, cx, Drawer, KeyValue, StatusBadge } from '@/components/ui';
import { MaterialLinesTable, MaterialStatusBadge } from '@/features/admin/components/MaterialSlip';
import { QuoteBreakdownTable } from '@/features/order/components/QuoteBreakdownTable';
import { ROUTES } from '@/routes/paths';
import { formatDateVN, formatTime, formatVND } from '@/services/format';
import { computeTotals } from '@/services/pricing';
import { describeCrew } from '@/services/staff';
import { useApp } from '@/services/store/AppStore';
import { useCatalog } from '@/services/store/CatalogStore';
import type { ActorRole, ClaimStatus, Order, SurveyMethod } from '@/types';

export const SERVICE_MODE_LABEL = { FULL_PACKAGE: 'Trọn gói', TRUCK_ONLY: 'Chỉ thuê xe' } as const;

const SURVEY_METHOD_LABEL: Record<SurveyMethod, string> = {
  PHOTO: 'Qua ảnh / video khách gửi',
  VIDEO_CALL: 'Video call',
  TRUCK_CONFIRM: 'Xác nhận xe qua điện thoại',
  ON_SITE: 'Trực tiếp tại nhà',
};

const CLAIM_STATUS: Record<ClaimStatus, { label: string; tone: 'warning' | 'info' | 'success' | 'neutral' }> = {
  OPEN: { label: 'Mới', tone: 'warning' },
  INVESTIGATING: { label: 'Đang xác minh', tone: 'info' },
  PENDING_APPROVAL: { label: 'Chờ Admin duyệt', tone: 'warning' },
  APPROVED: { label: 'Đã duyệt', tone: 'success' },
  REJECTED: { label: 'Từ chối', tone: 'neutral' },
};

const ACTOR_DOT: Record<ActorRole, string> = {
  COORDINATOR: 'bg-brand',
  CUSTOMER: 'bg-blue-500',
  STAFF: 'bg-emerald-500',
  ADMIN: 'bg-violet-500',
  SYSTEM: 'bg-slate-400',
};

const Section = ({ title, right, children }: { title: string; right?: ReactNode; children: ReactNode }) => (
  <section className="border-b border-slate-100 py-5 first:pt-0 last:border-0">
    <div className="mb-3 flex items-center justify-between gap-2">
      <h4 className="text-xs font-semibold tracking-wide text-slate-500 uppercase">{title}</h4>
      {right}
    </div>
    {children}
  </section>
);

const Empty = ({ children }: { children: ReactNode }) => <p className="text-sm text-slate-400">{children}</p>;

/** Chi tiết một đơn cho Admin — chỉ xem; thao tác thực hiện ở module tương ứng (nút ở chân panel). */
export const OrderLookupDrawer = ({ order, onClose }: { order: Order; onClose: () => void }) => {
  const navigate = useNavigate();
  const { state } = useApp();
  const { catalog } = useCatalog();
  const t = computeTotals(order);
  const d = order.bookingDetails;
  const vehicles = state.resources.vehicles.filter((v) => order.vehicleIds.includes(v.id));
  const crew = state.resources.staff.filter((s) => order.crewIds.includes(s.id));
  const payments = catalog.payments.filter((p) => p.orderId === order.id);
  const claims = state.claims.filter((c) => c.orderId === order.id);
  const refunds = state.refunds.filter((r) => r.orderId === order.id);
  const feedback = catalog.feedbacks.find((f) => f.orderId === order.id);

  const go = (to: string) => {
    onClose();
    navigate(to);
  };

  return (
    <Drawer
      open
      size="lg"
      onClose={onClose}
      title={<span className="flex flex-wrap items-center gap-2">Đơn #{order.id} <StatusBadge status={order.status} /></span>}
      subtitle={`${order.customerName} · ${order.phone} · tạo lúc ${formatTime(order.createdAt)}`}
      footer={
        <div className="flex w-full flex-wrap justify-end gap-2">
          {order.status === 'PENDING_PAYMENT_OR_CLAIM' && (
            <Button size="sm" variant="secondary" icon={ArrowRight} onClick={() => go(ROUTES.admin.settlement)}>Quyết toán</Button>
          )}
          {order.materials.lines.length > 0 && (
            <Button size="sm" variant="secondary" icon={ArrowRight} onClick={() => go(ROUTES.admin.inventory)}>Phiếu vật tư</Button>
          )}
          {claims.length > 0 && (
            <Button size="sm" variant="secondary" icon={ArrowRight} onClick={() => go(ROUTES.admin.claims)}>Khiếu nại</Button>
          )}
          <Button size="sm" variant="secondary" icon={ArrowRight} onClick={() => go(ROUTES.admin.payments)}>Thanh toán</Button>
        </div>
      }
    >
      <Section title="Thông tin đơn">
        <KeyValue label="Lộ trình" value={<span className="text-right">{order.fromAddress} → {order.toAddress}</span>} />
        <KeyValue label="Ngày chuyển" value={`${formatDateVN(order.scheduledDate)} · ${order.timeSlot}`} />
        <KeyValue label="Gói dịch vụ" value={SERVICE_MODE_LABEL[d?.serviceMode ?? 'FULL_PACKAGE']} />
        <KeyValue label="Khảo sát" value={order.surveyMethod ? SURVEY_METHOD_LABEL[order.surveyMethod] : 'Chưa khảo sát'} />
        <KeyValue label="Xe" value={vehicles.length ? vehicles.map((v) => v.plateNumber).join(', ') : 'Chưa gán'} />
        <KeyValue
          label="Đội"
          value={<span className="text-right">{order.crew.length ? describeCrew(order.crew, order.leaderId, state.resources.staff, vehicles) : crew.length ? crew.map((s) => s.name).join(', ') : 'Chưa gán'}</span>}
        />
        {d?.customerNote && <KeyValue label="Ghi chú của khách" value={d.customerNote} />}
      </Section>

      <Section title="Tài chính" right={order.finalRevenue !== null && <Badge tone="success">Doanh thu đã khóa</Badge>}>
        <KeyValue label="Báo giá" value={formatVND(t.baseQuote)} />
        {t.fieldAdjustments > 0 && <KeyValue label="Phát sinh hiện trường" value={`+${formatVND(t.fieldAdjustments)}`} />}
        {t.discount > 0 && <KeyValue label={`Giảm giá ${order.discountPercent}%`} value={`−${formatVND(t.discount)}`} />}
        {t.waitFee > 0 && <KeyValue label="Phí chờ" value={`+${formatVND(t.waitFee)}`} />}
        <KeyValue label="Tổng giá trị" value={formatVND(t.grandTotal)} strong />
        <KeyValue label="Đã cọc" value={formatVND(order.depositPaid)} />
        <KeyValue label="Đã thanh toán cuối" value={formatVND(order.balancePaid)} />
        <KeyValue label="Còn phải thu" value={formatVND(t.remaining)} tone={t.remaining > 0 ? 'danger' : 'success'} strong />
        {order.depositReservedUntil && <KeyValue label="Cọc bảo lưu tới" value={order.depositReservedUntil} />}
        {payments.length > 0 && (
          <ul className="mt-3 divide-y divide-slate-100 rounded-xl border border-slate-200 text-sm">
            {payments.map((p) => (
              <li key={p.id} className="flex items-center justify-between gap-3 px-3 py-2">
                <span>
                  {p.kind === 'DEPOSIT' ? 'Đặt cọc' : 'Thanh toán cuối'} · <span className="text-slate-500 tabular-nums">{formatTime(p.createdAt)}</span>
                </span>
                <span className="flex items-center gap-2">
                  <span className="font-semibold tabular-nums">{formatVND(p.amount)}</span>
                  <Badge tone={p.status === 'SUCCESS' ? 'success' : p.status === 'PENDING' ? 'warning' : 'neutral'}>
                    {p.status === 'SUCCESS' ? 'Thành công' : p.status === 'PENDING' ? 'Chờ xác nhận' : p.status === 'FAILED' ? 'Từ chối' : 'Đã hủy'}
                  </Badge>
                </span>
              </li>
            ))}
          </ul>
        )}
      </Section>

      <Section title="Báo giá">
        {order.quoteItems.length ? (
          <QuoteBreakdownTable items={order.quoteItems} reference={order.referenceQuote?.financialSummary.totalEstimated ?? null} />
        ) : (
          <Empty>
            Chưa có báo giá chính thức.
            {order.referenceQuote && ` Giá tham khảo lúc đặt: ${formatVND(order.referenceQuote.financialSummary.totalEstimated)}.`}
          </Empty>
        )}
      </Section>

      <Section title="Vật tư & dụng cụ" right={order.materials.lines.length > 0 && <MaterialStatusBadge slip={order.materials} />}>
        {order.materials.lines.length ? (
          <>
            <MaterialLinesTable slip={order.materials} />
            {order.materials.receiveNote && <p className="mt-2 text-xs text-amber-700">Đội xe báo nhận thiếu: {order.materials.receiveNote}</p>}
            {order.materials.lines.some((l) => l.lost > 0) && (
              <p className="mt-2 text-xs text-amber-700">Biên bản chênh lệch: {order.materials.resolutionNote}</p>
            )}
          </>
        ) : (
          <Empty>Đơn không dùng vật tư kho hoặc chưa gán đội.</Empty>
        )}
      </Section>

      <Section title="Khiếu nại · hoàn tiền · đánh giá">
        {claims.length === 0 && refunds.length === 0 && !feedback && <Empty>Không có.</Empty>}
        <ul className="space-y-2 text-sm">
          {claims.map((c) => (
            <li key={c.id} className="flex items-start justify-between gap-3">
              <span>
                <b>{c.id}</b> · {c.title}
                <span className="block text-xs text-slate-500">
                  Yêu cầu {formatVND(c.requestedAmount)}
                  {c.approvedAmount !== null && ` · duyệt ${formatVND(c.approvedAmount)}`}
                </span>
              </span>
              <Badge tone={CLAIM_STATUS[c.status].tone}>{CLAIM_STATUS[c.status].label}</Badge>
            </li>
          ))}
          {refunds.map((r) => (
            <li key={r.id} className="flex items-center justify-between gap-3">
              <span>
                <b>{r.id}</b> · Hoàn cọc {formatVND(r.amount)}
              </span>
              <Badge tone={r.status === 'APPROVED' ? 'success' : 'warning'}>{r.status === 'APPROVED' ? 'Đã chi' : 'Chờ duyệt'}</Badge>
            </li>
          ))}
          {feedback && (
            <li>
              <span className="font-semibold text-amber-600">{'★'.repeat(feedback.stars)}{'☆'.repeat(5 - feedback.stars)}</span>{' '}
              <span className="text-slate-600">{feedback.comment || 'Không có nhận xét'}</span>
            </li>
          )}
        </ul>
      </Section>

      <Section title="Nhật ký đơn" right={<span className="text-xs text-slate-400">{order.auditLog.length} sự kiện</span>}>
        {order.auditLog.length === 0 ? (
          <Empty>Chưa có sự kiện.</Empty>
        ) : (
          <ol className="relative space-y-4 border-l border-slate-200 pl-5">
            {[...order.auditLog].reverse().map((l) => (
              <li key={l.id} className="relative">
                <span className={cx('absolute top-1 -left-[25px] size-2.5 rounded-full ring-4 ring-white', ACTOR_DOT[l.actorRole])} />
                <p className="text-sm font-semibold text-slate-900">{l.action}</p>
                <p className="mt-0.5 text-sm text-slate-600">{l.detail}</p>
                <p className="mt-1 text-xs text-slate-400">
                  {l.actor} · <span className="tabular-nums">{formatTime(l.timestamp)}</span>
                </p>
              </li>
            ))}
          </ol>
        )}
        <p className="mt-3 flex items-center gap-1.5 text-xs text-slate-400">
          <History className="size-3.5" /> Thao tác của nhân viên hiện trường không ghi vào nhật ký; số liệu của họ nằm trên phiếu vật tư.
        </p>
      </Section>
    </Drawer>
  );
};
