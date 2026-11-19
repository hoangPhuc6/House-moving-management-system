import { type ReactNode, useState } from 'react';
import { AlarmClock, CheckCircle2, ChevronRight, Clock, Phone, Send, Undo2, XCircle } from 'lucide-react';
import { Badge, Button, Card, cx, Field, Input, NumberStepper, PageHeader, Select, Stat } from '@/components/ui';
import {
  groupSurchargesByReport,
  SURCHARGE_REVIEW_SLA_MS,
  SURCHARGE_STATUS_META,
  SURCHARGE_TYPE_META,
  surchargeAmount,
  type SurchargeReport,
  sumSurcharges,
} from '@/constants/surcharge';
import { SurchargeDetails } from '@/features/order/components/SurchargeDetails';
import { formatTime, formatVND } from '@/services/format';
import { useApp, useNow } from '@/services/store/AppStore';
import { useCatalog } from '@/services/store/CatalogStore';
import type { Order, ServiceItem, Surcharge } from '@/types';

interface Group {
  order: Order;
  report: SurchargeReport;
}

const isActionable = (s: Surcharge) => s.status === 'PROPOSED' || s.status === 'REJECTED_BY_CUSTOMER';

/** Thời điểm bắt đầu chờ Điều phối: NV đề xuất, hoặc khách vừa từ chối. */
const waitingSince = (s: Surcharge): number => (s.status === 'REJECTED_BY_CUSTOMER' ? (s.respondedAt ?? s.proposedAt) : s.proposedAt);

const groupWaitingSince = (g: Group): number => Math.min(...g.report.lines.filter(isActionable).map(waitingSince));

const lastTouched = (g: Group): number => Math.max(...g.report.lines.map((s) => s.respondedAt ?? s.reviewedAt ?? s.proposedAt));

/**
 * Duyệt phát sinh hiện trường theo lần báo: mỗi lần NV báo có thể gồm nhiều dòng.
 * Điều phối chỉnh số lượng (chỉ loại "Khác" sửa được đơn giá) hoặc bác từng dòng, rồi chốt & gửi khách tất cả trong một lần.
 */
export const SurchargeTab = () => {
  const { state } = useApp();
  const now = useNow(30_000);
  const groups: Group[] = state.orders.flatMap((order) => groupSurchargesByReport(order.surcharges).map((report) => ({ order, report })));
  const pending = groups.filter((g) => g.report.lines.some(isActionable)).sort((a, b) => groupWaitingSince(a) - groupWaitingSince(b));
  const waiting = groups.filter((g) => !g.report.lines.some(isActionable) && g.report.lines.some((s) => s.status === 'WAITING_CUSTOMER'));
  const done = groups
    .filter((g) => g.report.lines.every((s) => s.status === 'ACCEPTED' || s.status === 'REJECTED' || s.status === 'CANCELLED'))
    .sort((a, b) => lastTouched(b) - lastTouched(a))
    .slice(0, 10);
  const overdue = pending.filter((g) => now - groupWaitingSince(g) > SURCHARGE_REVIEW_SLA_MS).length;

  return (
    <>
      <PageHeader
        eyebrow="Hiện trường"
        title="Phát sinh hiện trường"
        subtitle="Đội xe đang tạm dừng tới khi khoản phát sinh được duyệt và khách xác nhận — xử lý trong 15 phút."
      />
      <div className="mb-5 grid gap-3 sm:grid-cols-3">
        <Stat label="Lần báo cần xử lý" value={pending.length} tone={pending.length > 0 ? 'warning' : 'neutral'} icon={AlarmClock} />
        <Stat label="Quá 15 phút" value={overdue} tone={overdue > 0 ? 'danger' : 'neutral'} icon={Clock} />
        <Stat label="Chờ khách xác nhận" value={waiting.length} tone="info" icon={Send} />
      </div>

      <Section title="Cần xử lý" empty="Không có đề xuất nào chờ duyệt.">
        {pending.map((g) => (
          <ReportCard key={g.report.reportId} group={g} now={now} />
        ))}
      </Section>
      <Section title="Chờ khách xác nhận" empty="Không có khoản nào đang chờ khách.">
        {waiting.map((g) => (
          <ReportCard key={g.report.reportId} group={g} now={now} />
        ))}
      </Section>
      <Section title="Đã xử lý gần đây" empty="Chưa có lịch sử.">
        {done.map((g) => (
          <ReportCard key={g.report.reportId} group={g} now={now} />
        ))}
      </Section>
    </>
  );
};

const Section = ({ title, empty, children }: { title: string; empty: string; children: ReactNode[] }) => (
  <section className="mb-6">
    <h2 className="mb-2.5 text-sm font-semibold text-slate-700">
      {title} <span className="text-slate-400 tabular-nums">({children.length})</span>
    </h2>
    {children.length === 0 ? (
      <p className="rounded-xl border border-dashed border-slate-200 px-4 py-6 text-center text-sm text-slate-400">{empty}</p>
    ) : (
      <div className="space-y-3">{children}</div>
    )}
  </section>
);

interface Draft {
  quantity: number;
  price: string;
  /** Dịch vụ bảng giá Điều phối đổi sang (cùng loại) — undefined = giữ dịch vụ NV chọn. */
  serviceId?: string;
}

/** Dịch vụ bảng giá cùng loại với dòng phát sinh (chỉ loại tính theo bảng giá). */
const sameTypeServices = (s: Surcharge, services: ServiceItem[]): ServiceItem[] => {
  const meta = SURCHARGE_TYPE_META[s.type];
  return meta.mode === 'CATALOG' ? services.filter((x) => x.active && meta.catalogGroups?.includes(x.group)) : [];
};

/** Tên dòng phí theo dịch vụ — cùng quy ước với form báo của NV ("Tháo lắp phát sinh: Tháo lắp máy lạnh"). */
const lineNameOf = (s: Surcharge, svc: ServiceItem) => `${SURCHARGE_TYPE_META[s.type].label}: ${svc.name}`;

const ReportCard = ({ group: { order, report }, now }: { group: Group; now: number }) => {
  const { state, dispatch } = useApp();
  const { catalog } = useCatalog();
  const leader = state.resources.staff.find((x) => x.id === order.leaderId);
  const actionable = report.lines.filter(isActionable);
  const since = actionable.length > 0 ? Math.min(...actionable.map(waitingSince)) : null;
  const late = since !== null && now - since > SURCHARGE_REVIEW_SLA_MS;
  const resend = actionable.some((s) => s.status === 'REJECTED_BY_CUSTOMER');
  // Thẻ mặc định thu gọn — chỉ mở chi tiết & thao tác khi bấm.
  const [open, setOpen] = useState(false);
  const bodyId = `report-${report.reportId}`;

  // Số liệu Điều phối đang chỉnh cho từng dòng (mặc định = số liệu hiện tại của dòng).
  const [drafts, setDrafts] = useState<Record<string, Draft>>({});
  const [note, setNote] = useState('');
  const draftOf = (s: Surcharge): Draft => drafts[s.id] ?? { quantity: s.quantity, price: String(s.unitPrice) };
  const swappedOf = (s: Surcharge) => {
    const id = draftOf(s).serviceId;
    const svc = id ? catalog.services.find((x) => x.id === id) : undefined;
    return svc && lineNameOf(s, svc) !== s.name ? svc : undefined;
  };
  const priceOf = (s: Surcharge) =>
    SURCHARGE_TYPE_META[s.type].mode === 'MANUAL' ? Number(draftOf(s).price) || 0 : (swappedOf(s)?.basePrice ?? s.unitPrice);
  const setDraft = (s: Surcharge, p: Partial<Draft>) => setDrafts((d) => ({ ...d, [s.id]: { ...draftOf(s), ...p } }));
  const total = actionable.reduce((sum, s) => sum + surchargeAmount({ quantity: draftOf(s).quantity, unitPrice: priceOf(s) }), 0);
  const invalid = actionable.some((s) => priceOf(s) <= 0 || draftOf(s).quantity < 1);

  const sendAll = () =>
    dispatch({
      type: 'COORD_SEND_SURCHARGE',
      orderId: order.id,
      items: actionable.map((s) => {
        const svc = swappedOf(s);
        return {
          surchargeId: s.id,
          quantity: draftOf(s).quantity,
          unitPrice: priceOf(s),
          ...(svc ? { service: { name: lineNameOf(s, svc), unit: svc.unit, unitPrice: svc.basePrice } } : {}),
        };
      }),
      note,
    });

  return (
    <Card className={late ? 'border-rose-300' : undefined}>
      <div className={cx('flex flex-wrap items-start justify-between gap-3', open && 'mb-4 border-b border-slate-100 pb-4')}>
        <button
          type="button"
          aria-expanded={open}
          aria-controls={bodyId}
          onClick={() => setOpen((v) => !v)}
          className="flex min-w-0 flex-1 items-start gap-3 text-left"
        >
          <ChevronRight className={cx('mt-0.5 size-4 shrink-0 text-slate-400 transition-transform', open && 'rotate-90')} />
          <span className="min-w-0">
            <span className="block text-sm font-bold tabular-nums">
              #{order.id} · <span className="font-medium">{order.customerName}</span>
            </span>
            <span className="block truncate text-xs text-slate-500">
              {leader ? `Trưởng nhóm ${leader.name}` : 'Đội hiện trường'} · {order.fromAddress}
            </span>
            <span className="mt-0.5 block text-xs text-slate-400 tabular-nums">
              Báo lúc {formatTime(report.proposedAt)} · {report.lines.length} dòng
            </span>
            {!open && (
              <span className="mt-2 flex flex-wrap gap-1.5">
                {report.lines.map((s) => (
                  <Badge key={s.id} tone={SURCHARGE_STATUS_META[s.status].tone} dot>
                    {s.name} · <span className="tabular-nums">+{formatVND(surchargeAmount(s))}</span>
                  </Badge>
                ))}
              </span>
            )}
          </span>
        </button>
        <div className="flex items-center gap-1.5">
          {!open && actionable.length > 0 && (
            <span className="mr-1 text-sm font-bold tabular-nums">+{formatVND(sumSurcharges(actionable))}</span>
          )}
          {since !== null && (
            <Badge tone={late ? 'danger' : 'warning'} icon={Clock}>
              Đội chờ {Math.floor((now - since) / 60_000)}′
            </Badge>
          )}
          <a href={`tel:${order.phone.replace(/\s/g, '')}`} className="grid size-8 place-items-center rounded-lg text-slate-500 hover:bg-slate-100" aria-label="Gọi khách">
            <Phone className="size-4" />
          </a>
        </div>
      </div>

      {open && (
      <div id={bodyId}>
      {report.note && (
        <p className="mb-4 rounded-xl bg-slate-50 px-3 py-2 text-sm text-slate-700">
          <span className="font-semibold">Ghi chú chung:</span> {report.note}
        </p>
      )}
      {actionable.some((s) => s.photos.length === 0) && (
        <p className="mb-4 text-xs font-medium text-amber-700">Có dòng không kèm ảnh — nên gọi trưởng nhóm xác minh trước khi chốt.</p>
      )}

      <ol className="space-y-4">
        {report.lines.map((s, i) => (
          <li
            key={s.id}
            className={cx('grid gap-4', i > 0 && 'border-t border-slate-100 pt-4', isActionable(s) && 'lg:grid-cols-[minmax(0,1fr)_minmax(300px,360px)]')}
          >
            <SurchargeDetails
              s={s}
              compact
              priceLabel={
                s.quantity !== s.proposedQuantity || s.unitPrice !== s.proposedUnitPrice
                  ? `${s.quantity} ${s.unit} × ${formatVND(s.unitPrice)} (NV đề xuất ${s.proposedQuantity} × ${formatVND(s.proposedUnitPrice)})`
                  : s.status === 'PROPOSED'
                    ? `NV đề xuất ${s.quantity} ${s.unit} × ${formatVND(s.unitPrice)}`
                    : undefined
              }
            >
              {s.status === 'WAITING_CUSTOMER' && (
                <Button
                  size="sm"
                  variant="secondary"
                  icon={Undo2}
                  onClick={() => dispatch({ type: 'COORD_WITHDRAW_SURCHARGE', orderId: order.id, surchargeId: s.id, note: '' })}
                >
                  Rút lại để thương lượng
                </Button>
              )}
            </SurchargeDetails>
            {isActionable(s) && (
              <LineControls
                order={order}
                s={s}
                draft={draftOf(s)}
                unitPrice={priceOf(s)}
                services={sameTypeServices(s, catalog.services)}
                onChange={(p) => setDraft(s, p)}
              />
            )}
          </li>
        ))}
      </ol>

      {actionable.length > 0 && (
        <div className="mt-4 flex flex-col gap-3 rounded-xl border border-orange-200 bg-orange-50/60 p-4 sm:flex-row sm:items-end">
          <Field label={resend ? 'Phương án mới / ghi chú cho khách' : 'Ghi chú gửi kèm (tùy chọn)'} className="flex-1">
            <Input
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder={resend ? 'VD: Đã gọi khách, giảm còn 2 đoạn hẻm' : 'VD: Đã gọi trưởng nhóm xác nhận qua video'}
            />
          </Field>
          <div className="flex items-center justify-between gap-3 sm:justify-end">
            <div className="text-right">
              <p className="text-xs text-slate-500">{actionable.length} dòng gửi khách</p>
              <p className="text-lg font-bold tabular-nums">+{formatVND(total)}</p>
            </div>
            <Button icon={CheckCircle2} disabled={invalid} onClick={sendAll}>
              {resend ? 'Gửi lại cho khách' : actionable.length > 1 ? 'Chốt tất cả & gửi khách' : 'Chốt & gửi khách'}
            </Button>
          </div>
        </div>
      )}
      </div>
      )}
    </Card>
  );
};

/** Chỉnh một dòng: số lượng, đơn giá (chỉ loại "Khác"), bác (đề xuất mới) hoặc rút (khách đã từ chối). */
const LineControls = ({
  order,
  s,
  draft,
  unitPrice,
  services,
  onChange,
}: {
  order: Order;
  s: Surcharge;
  draft: Draft;
  unitPrice: number;
  services: ServiceItem[];
  onChange: (p: Partial<Draft>) => void;
}) => {
  const { dispatch } = useApp();
  const manual = SURCHARGE_TYPE_META[s.type].mode === 'MANUAL';
  const currentId = draft.serviceId ?? services.find((x) => lineNameOf(s, x) === s.name)?.id ?? '';
  const swapped = services.find((x) => x.id === draft.serviceId && lineNameOf(s, x) !== s.name);
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState('');
  const [tried, setTried] = useState(false);
  const proposed = s.status === 'PROPOSED';

  return (
    <div className="space-y-3 rounded-xl bg-slate-50 p-3 lg:self-start">
      {services.length > 1 && (
        <Field label="Dịch vụ (cùng loại)" hint={swapped ? 'Đổi so với NV chọn — sẽ ghi vào nhật ký' : 'NV chọn nhầm thì đổi tại đây'}>
          <Select value={currentId} onChange={(e) => onChange({ serviceId: e.target.value })}>
            {services.map((x) => (
              <option key={x.id} value={x.id}>
                {x.name} — {formatVND(x.basePrice)}/{x.unit}
              </option>
            ))}
          </Select>
        </Field>
      )}
      <div className="grid grid-cols-2 gap-3">
        <Field label={`Số lượng (${swapped?.unit ?? s.unit})`}>
          <NumberStepper value={draft.quantity} onChange={(v) => onChange({ quantity: v })} min={1} max={100} />
        </Field>
        <Field label="Đơn giá" hint={manual ? 'Được điều chỉnh' : 'Theo bảng giá'}>
          <Input
            value={manual ? draft.price : formatVND(unitPrice)}
            disabled={!manual}
            inputMode="numeric"
            onChange={(e) => onChange({ price: e.target.value.replace(/\D/g, '') })}
          />
        </Field>
      </div>
      <p className="flex items-baseline justify-between text-xs text-slate-500">
        Thành tiền <b className="text-sm text-slate-900 tabular-nums">+{formatVND(surchargeAmount({ quantity: draft.quantity, unitPrice }))}</b>
      </p>
      {proposed && !rejecting && (
        <Button size="sm" variant="secondary" icon={XCircle} onClick={() => setRejecting(true)}>
          Bác dòng này
        </Button>
      )}
      {proposed && rejecting && (
        <div className="space-y-2">
          <Field label="Lý do bác *" error={tried && !reason.trim() ? 'Nhập lý do để nhân viên nắm' : null}>
            <Input autoFocus value={reason} onChange={(e) => setReason(e.target.value)} placeholder="VD: Đã có trong báo giá khảo sát" />
          </Field>
          <div className="flex gap-2">
            <Button
              size="sm"
              variant="danger"
              icon={XCircle}
              onClick={() => {
                setTried(true);
                if (reason.trim()) dispatch({ type: 'COORD_REJECT_SURCHARGE', orderId: order.id, surchargeId: s.id, note: reason });
              }}
            >
              Xác nhận bác
            </Button>
            <Button size="sm" variant="secondary" onClick={() => setRejecting(false)}>
              Thôi
            </Button>
          </div>
        </div>
      )}
      {s.status === 'REJECTED_BY_CUSTOMER' && (
        <Button
          size="sm"
          variant="secondary"
          icon={Undo2}
          onClick={() => dispatch({ type: 'COORD_WITHDRAW_SURCHARGE', orderId: order.id, surchargeId: s.id, note: '' })}
        >
          Rút dòng này (không thu)
        </Button>
      )}
    </div>
  );
};
