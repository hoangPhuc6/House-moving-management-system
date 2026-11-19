import { type ReactNode, useMemo, useState } from 'react';
import { CheckCircle2, Plus, RotateCcw, Sparkles, Trash2, X } from 'lucide-react';
import { Button, cx, Field, Input, NumberStepper } from '@/components/ui';
import { maxLaborOf, PROPOSAL_DEVIATION_LIMIT, qtyOf, setVehicleQty, totalVehicles, VEHICLE_CAPACITIES, VEHICLE_PRICING } from '@/constants/pricing';
import { surveyInputFromOrder } from '@/features/coordinator/utils';
import { QuoteBreakdownTable } from '@/features/order/components/QuoteBreakdownTable';
import { removeBtnCls, SuggestChip } from '@/features/order/components/surveyUi';
import { PROPOSAL_FIELDS, type ProposalField } from '@/features/order/proposal';
import type { SurveyStageKind } from '@/features/order/surveyStage';
import { formatVND, uid } from '@/services/format';
import { buildSurveyQuote, isQuoteExtraService, quoteItemFromService, quoteSubtotal } from '@/services/pricing';
import { useApp } from '@/services/store/AppStore';
import { useCatalog } from '@/services/store/CatalogStore';
import type { Order, QuoteItem, ServiceItem, SurveyInput, SurveyItem } from '@/types';

/** Gói chỉ thuê xe không xuất vật tư đóng gói. */
const withoutMaterials = (s: SurveyInput): SurveyInput => ({ ...s, cartons: { S: 0, M: 0, L: 0 }, tapeRolls: 0, peFilmRolls: 0 });

interface ExtraLine {
  serviceId: string;
  quantity: number;
  /** Khách chọn lúc đặt đơn — bỏ ra phải ghi lý do. */
  requested: boolean;
  /** Đưa vào từ kết quả khảo sát (VD "Điều hòa ×2") — hiện cho Điều phối biết căn cứ số lượng. */
  source?: string;
}

const AC_RE = /máy lạnh|điều hòa|điều hoà/i;

/**
 * Dịch vụ tháo lắp ứng với một món người khảo sát đánh dấu "Tháo lắp":
 * máy lạnh / điều hòa → dịch vụ tháo lắp máy lạnh; món khác → dịch vụ tháo lắp nội thất (tủ / giường).
 */
const assemblyServiceFor = (itemName: string, services: ServiceItem[]): ServiceItem | undefined => {
  const assembly = services.filter((s) => s.group === 'ASSEMBLY');
  return AC_RE.test(itemName) ? assembly.find((s) => AC_RE.test(s.name)) : assembly.find((s) => !AC_RE.test(s.name));
};

/** Gộp các món cần tháo lắp theo dịch vụ: serviceId → số lượng & mô tả nguồn. */
const assemblyFromItems = (items: SurveyItem[], services: ServiceItem[]): Map<string, { qty: number; source: string[] }> => {
  const out = new Map<string, { qty: number; source: string[] }>();
  for (const it of items.filter((x) => x.disassemble)) {
    const svc = assemblyServiceFor(it.name, services);
    if (!svc) continue;
    const cur = out.get(svc.id) ?? { qty: 0, source: [] };
    out.set(svc.id, { qty: cur.qty + it.qty, source: [...cur.source, `${it.name} ×${it.qty}`] });
  }
  return out;
};

interface CustomLine {
  key: string;
  name: string;
  unit: string;
  price: string;
  quantity: number;
  reason: string;
}

const Section = ({ title, hint, children, action }: { title: string; hint?: ReactNode; children: ReactNode; action?: ReactNode }) => (
  <section className="space-y-3 rounded-xl border border-slate-200 p-4">
    <div className="flex flex-wrap items-start justify-between gap-2">
      <div>
        <p className="text-sm font-semibold text-slate-900">{title}</p>
        {hint && <p className="mt-0.5 text-xs text-slate-500">{hint}</p>}
      </div>
      {action}
    </div>
    {children}
  </section>
);

/**
 * Soạn báo giá chính thức:
 * - Phần tự tính từ số liệu khảo sát (xe — nhiều loại, bốc xếp, vật tư, lầu, hẻm) + thời gian dự kiến (bắt buộc).
 * - Dịch vụ thêm từ bảng giá (đơn giá cố định); dịch vụ khách chọn lúc đặt đơn được đưa vào sẵn, bỏ ra phải ghi lý do.
 * - Khoản ngoài bảng giá (bắt buộc lý do, hiện rõ cho khách).
 * - Ghi chú & ảnh bổ sung của Điều phối (tùy chọn) — ảnh / ghi chú chính đã có trong kết quả khảo sát.
 */
export const QuoteForm = ({
  order,
  kind,
  blocked,
  proposal = null,
}: {
  order: Order;
  kind: SurveyStageKind;
  blocked: string | null;
  proposal?: SurveyInput | null;
}) => {
  const { dispatch } = useApp();
  const { catalog } = useCatalog();
  const truckOnly = kind === 'TRUCK_CONFIRM';

  const initial = useMemo(() => proposal ?? surveyInputFromOrder(order), [order.id, proposal]);
  const [s, setS] = useState<SurveyInput>(initial);
  const set = <K extends keyof SurveyInput>(k: K, v: SurveyInput[K]) => setS((x) => ({ ...x, [k]: v }));

  const extraServices = useMemo(() => catalog.services.filter(isQuoteExtraService), [catalog.services]);
  const serviceById = (id: string): ServiceItem | undefined => catalog.services.find((x) => x.id === id);
  const requestedInitial = useMemo<ExtraLine[]>(() => {
    const lines: ExtraLine[] = order.requestedServices
      .map((name) => extraServices.find((x) => x.name === name))
      .filter((x): x is ServiceItem => Boolean(x))
      .map((x) => ({ serviceId: x.id, quantity: 1, requested: true }));
    // Tháo lắp theo các món người khảo sát đánh dấu — số lượng lấy theo món.
    for (const [serviceId, a] of assemblyFromItems(order.surveyReport?.items ?? [], extraServices)) {
      const i = lines.findIndex((l) => l.serviceId === serviceId);
      const source = `Theo khảo sát: ${a.source.join(', ')}`;
      if (i >= 0) lines[i] = { ...lines[i]!, quantity: a.qty, source };
      else lines.push({ serviceId, quantity: a.qty, requested: false, source });
    }
    // Dịch vụ bảng giá người khảo sát đề xuất thêm: đưa vào sẵn (trùng dịch vụ khách chọn thì lấy số lượng đề xuất).
    for (const x of proposal?.extras ?? []) {
      if (!x.serviceId || !extraServices.some((svc) => svc.id === x.serviceId)) continue;
      const i = lines.findIndex((l) => l.serviceId === x.serviceId);
      if (i >= 0) lines[i] = { ...lines[i]!, quantity: x.qty };
      else lines.push({ serviceId: x.serviceId, quantity: x.qty, requested: false });
    }
    return lines;
  }, [order.id, order.surveyReport, extraServices, proposal]);
  const [extras, setExtras] = useState<ExtraLine[]>(requestedInitial);
  const [removed, setRemoved] = useState<{ serviceId: string; reason: string }[]>([]);
  // Hạng mục nhập tay của người khảo sát → khoản ngoài bảng giá, Điều phối điền đơn giá.
  const [custom, setCustom] = useState<CustomLine[]>(() =>
    (proposal?.extras ?? [])
      .filter((x) => !x.serviceId)
      .map((x) => ({ key: uid('qi'), name: x.name, unit: x.unit, price: '', quantity: x.qty, reason: x.note ? `Người khảo sát đề xuất: ${x.note}` : 'Người khảo sát đề xuất' })),
  );

  const [evNote, setEvNote] = useState('');
  const [reason, setReason] = useState('');
  const [submitted, setSubmitted] = useState(false);

  const autoItems = useMemo(() => buildSurveyQuote(truckOnly ? withoutMaterials(s) : s), [s, truckOnly]);
  const extraItems: QuoteItem[] = extras.flatMap((e) => {
    const svc = serviceById(e.serviceId);
    return svc ? [quoteItemFromService(svc, e.quantity, e.requested ? 'Khách chọn khi đặt đơn · theo bảng giá' : undefined)] : [];
  });
  const customItems: QuoteItem[] = custom.map((c) => ({
    id: c.key,
    name: c.name.trim() || 'Khoản ngoài bảng giá',
    category: 'SURCHARGE',
    unit: c.unit.trim() || 'gói',
    unitPrice: Number(c.price) || 0,
    quantity: c.quantity,
    note: c.reason.trim(),
    offCatalog: true,
  }));
  const items = [...autoItems, ...extraItems, ...customItems];
  const total = quoteSubtotal(items);

  // Lệch so với đề xuất NV chỉ xét phần tự tính (dịch vụ thêm / khoản ngoài bảng giá đã có lý do riêng).
  const autoTotal = quoteSubtotal(autoItems);
  const proposalTotal = useMemo(() => (proposal ? quoteSubtotal(buildSurveyQuote(proposal)) : null), [proposal]);
  const deviation = proposalTotal ? (autoTotal - proposalTotal) / proposalTotal : 0;
  const needsReason = proposalTotal !== null && Math.abs(deviation) > PROPOSAL_DEVIATION_LIMIT;

  const noVehicle = totalVehicles(s.vehicles) === 0;
  const noHours = !(s.estimatedHours > 0);
  const errors = {
    deviation: needsReason && !reason.trim(),
    vehicle: noVehicle,
    hours: noHours,
    removed: removed.some((r) => !r.reason.trim()),
    custom: custom.some((c) => !c.name.trim() || !(Number(c.price) > 0) || !c.reason.trim()),
  };
  const invalid = Object.values(errors).some(Boolean);
  const show = (k: keyof typeof errors) => submitted && errors[k];

  const seatCount = maxLaborOf(s.vehicles);

  // Quãng đường hệ thống tự tính từ hai địa chỉ — chỉ cho sửa khi đơn chưa có số liệu.
  const distanceLocked = (order.referenceQuote?.distanceKm ?? 0) > 0;
  // Gói chỉ thuê xe không có vật tư đóng gói.
  const fields = PROPOSAL_FIELDS.filter((f) => !(truckOnly && f.group === 'MATERIAL'));
  const shownOf = (x: SurveyInput) => fields.filter((f) => f.get(x) > 0).map((f) => f.key);
  const [shown, setShown] = useState<string[]>(() => shownOf(initial));
  const addField = (f: ProposalField) => {
    setShown((xs) => [...xs, f.key]);
    if (f.get(s) === 0) setS(f.set(s, f.initial));
  };
  const removeField = (f: ProposalField) => {
    setShown((xs) => xs.filter((k) => k !== f.key));
    setS(f.set(s, 0));
  };
  const resetToProposal = () => {
    setS(initial);
    setShown(shownOf(initial));
  };
  /** "NV đề xuất 3" khi Điều phối sửa khác đề xuất của người khảo sát. */
  const proposedHint = (now: number, before: number | undefined, unit: string) =>
    proposal && before !== undefined && before !== now ? `Người khảo sát đề xuất ${before} ${unit}` : undefined;

  const removeExtra = (e: ExtraLine) => {
    setExtras((xs) => xs.filter((x) => x.serviceId !== e.serviceId));
    if (e.requested) setRemoved((r) => [...r, { serviceId: e.serviceId, reason: '' }]);
  };
  const restore = (serviceId: string) => {
    setRemoved((r) => r.filter((x) => x.serviceId !== serviceId));
    setExtras((xs) => [...xs, { serviceId, quantity: 1, requested: true }]);
  };
  const addPicked = (id: string) => {
    if (!id) return;
    if (removed.some((r) => r.serviceId === id)) restore(id);
    else setExtras((xs) => [...xs, { serviceId: id, quantity: 1, requested: false }]);
  };
  const pickable = extraServices.filter((x) => !extras.some((e) => e.serviceId === x.id));
  const patchCustom = (key: string, p: Partial<CustomLine>) => setCustom((cs) => cs.map((c) => (c.key === key ? { ...c, ...p } : c)));

  const submit = () => {
    setSubmitted(true);
    if (invalid || blocked) return;
    dispatch({
      type: 'SUBMIT_SURVEY',
      orderId: order.id,
      items,
      estimatedHours: s.estimatedHours,
      input: truckOnly ? withoutMaterials(s) : s,
      evidence: { note: evNote, photos: [] },
      removedRequested: removed.map((r) => ({ name: serviceById(r.serviceId)?.name ?? r.serviceId, reason: r.reason })),
      adjustReason: needsReason ? reason : undefined,
    });
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm font-semibold text-slate-900">Báo giá chính thức</p>
        <span className="flex items-center gap-1.5 text-xs text-slate-500">
          <Sparkles className="size-3.5" /> {proposal ? 'Đã điền sẵn theo đề xuất của người khảo sát' : 'Đã điền sẵn theo thông tin khách khai báo'}
          <button type="button" className="font-semibold text-brand hover:underline" onClick={resetToProposal}>
            Đặt lại số liệu
          </button>
        </span>
      </div>

      <Section
        title="Số liệu khảo sát"
        hint={proposal ? 'Lấy từ kết quả người khảo sát — chỉ hiện mục có số liệu, thêm mục khác qua gợi ý bên dưới.' : 'Xe, bốc xếp, vật tư, lầu và hẻm được tính tự động theo bảng giá.'}
      >
        <div className="grid gap-4 sm:grid-cols-3">
          <Field label="Quãng đường" hint={distanceLocked ? 'Tự tính từ hai địa chỉ' : 'Đơn chưa có quãng đường tự tính'}>
            {distanceLocked ? (
              <div className="flex h-11 items-center justify-center rounded-xl bg-slate-50 text-sm font-semibold text-slate-700 tabular-nums">
                {s.distanceKm} <span className="ml-1 font-medium text-slate-400">km</span>
              </div>
            ) : (
              <NumberStepper value={s.distanceKm} onChange={(v) => set('distanceKm', v)} min={1} max={200} suffix="km" />
            )}
          </Field>
          <Field
            label="Thời gian dự kiến *"
            hint={
              noHours
                ? 'Bắt buộc — xe & người bận trong khoảng này'
                : (proposedHint(s.estimatedHours, proposal?.estimatedHours, 'giờ') ?? (truckOnly ? 'Gợi ý theo quãng đường — xác nhận lại khi gọi khách' : 'Bốc xếp + di chuyển + dỡ'))
            }
          >
            <NumberStepper value={s.estimatedHours} onChange={(v) => set('estimatedHours', v)} max={24} step={0.5} suffix="giờ" />
          </Field>
          <Field
            label={truckOnly ? 'Bốc vác thuê thêm' : 'Nhân viên bốc xếp'}
            hint={truckOnly && s.workers > seatCount ? `Vượt số chỗ trên xe (tối đa ${seatCount})` : proposedHint(s.workers, proposal?.workers, 'người')}
          >
            <NumberStepper value={s.workers} onChange={(v) => set('workers', v)} max={12} suffix="người" />
          </Field>
        </div>

        <div>
          <p className="mb-1.5 text-xs font-semibold text-slate-500">Xe *</p>
          <div className="grid gap-3 sm:grid-cols-3">
            {VEHICLE_CAPACITIES.filter((cap) => qtyOf(s.vehicles, cap) > 0).map((cap) => (
              <div key={cap} className="flex items-end gap-1.5">
                <Field label={VEHICLE_PRICING[cap]!.label} hint={proposedHint(qtyOf(s.vehicles, cap), proposal ? qtyOf(proposal.vehicles, cap) : undefined, 'xe')} className="flex-1">
                  <NumberStepper value={qtyOf(s.vehicles, cap)} onChange={(v) => set('vehicles', setVehicleQty(s.vehicles, cap, v))} min={1} max={5} suffix="xe" />
                </Field>
                <button type="button" onClick={() => set('vehicles', setVehicleQty(s.vehicles, cap, 0))} aria-label={`Bỏ ${VEHICLE_PRICING[cap]!.label}`} className={cx(removeBtnCls, 'mb-1')}>
                  <X className="size-4" />
                </button>
              </div>
            ))}
          </div>
          {VEHICLE_CAPACITIES.some((cap) => qtyOf(s.vehicles, cap) === 0) && (
            <div className="mt-2 flex flex-wrap gap-1.5">
              {VEHICLE_CAPACITIES.filter((cap) => qtyOf(s.vehicles, cap) === 0).map((cap) => (
                <SuggestChip key={cap} onClick={() => set('vehicles', setVehicleQty(s.vehicles, cap, 1))}>
                  {VEHICLE_PRICING[cap]!.label}
                </SuggestChip>
              ))}
            </div>
          )}
          {noVehicle && <p className="mt-1 text-xs font-medium text-rose-600">Báo giá cần ít nhất một xe.</p>}
        </div>

        {(['MATERIAL', 'ACCESS'] as const).map((group) => {
          const all = fields.filter((f) => f.group === group);
          if (all.length === 0) return null;
          const active = all.filter((f) => shown.includes(f.key));
          const rest = all.filter((f) => !shown.includes(f.key));
          return (
            <div key={group}>
              <p className="mb-1.5 text-xs font-semibold text-slate-500">{group === 'MATERIAL' ? 'Vật tư đóng gói' : 'Lối đi & tầng lầu'}</p>
              {active.length > 0 && (
                <div className="grid gap-3 sm:grid-cols-3">
                  {active.map((f) => (
                    <div key={f.key} className="flex items-end gap-1.5">
                      <Field label={f.label} hint={proposedHint(f.get(s), proposal ? f.get(proposal) : undefined, f.unit) ?? f.hint} className="flex-1">
                        <NumberStepper value={f.get(s)} onChange={(v) => setS(f.set(s, v))} max={f.max} step={f.step} suffix={f.unit} />
                      </Field>
                      <button type="button" onClick={() => removeField(f)} aria-label={`Bỏ ${f.label}`} className={cx(removeBtnCls, 'mb-1')}>
                        <X className="size-4" />
                      </button>
                    </div>
                  ))}
                </div>
              )}
              {rest.length > 0 && (
                <div className={cx('flex flex-wrap gap-1.5', active.length > 0 && 'mt-2')}>
                  {rest.map((f) => (
                    <SuggestChip key={f.key} onClick={() => addField(f)}>
                      {f.label}
                    </SuggestChip>
                  ))}
                </div>
              )}
            </div>
          );
        })}
      </Section>

      <Section title="Dịch vụ thêm" hint="Theo bảng giá, đơn giá cố định. Đã đưa sẵn dịch vụ khách chọn và tháo lắp theo các món người khảo sát đánh dấu.">
        {extras.length === 0 && <p className="text-sm text-slate-500">Chưa có dịch vụ thêm.</p>}
        <ul className="space-y-2">
          {extras.map((e) => {
            const svc = serviceById(e.serviceId);
            if (!svc) return null;
            return (
              <li key={e.serviceId} className="flex flex-wrap items-center gap-3 rounded-lg bg-slate-50 p-2.5">
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-medium text-slate-900">{svc.name}</p>
                  <p className="text-xs text-slate-500 tabular-nums">
                    {formatVND(svc.basePrice)}/{svc.unit}
                    {e.requested && <span className="ml-1.5 font-semibold text-brand">· Khách chọn khi đặt đơn</span>}
                  </p>
                  {e.source && <p className="text-xs text-emerald-700">{e.source}</p>}
                </div>
                <div className="w-40">
                  <NumberStepper
                    value={e.quantity}
                    onChange={(q) => setExtras((xs) => xs.map((x) => (x.serviceId === e.serviceId ? { ...x, quantity: q } : x)))}
                    min={1}
                    max={50}
                    suffix={svc.unit}
                  />
                </div>
                <button
                  type="button"
                  onClick={() => removeExtra(e)}
                  className="grid size-10 shrink-0 place-items-center rounded-lg text-slate-400 hover:bg-rose-50 hover:text-rose-600"
                  aria-label={`Bỏ ${svc.name}`}
                >
                  <Trash2 className="size-4" />
                </button>
              </li>
            );
          })}
        </ul>

        {removed.length > 0 && (
          <ul className="space-y-2">
            {removed.map((r) => {
              const name = serviceById(r.serviceId)?.name ?? r.serviceId;
              const missing = show('removed') && !r.reason.trim();
              return (
                <li key={r.serviceId} className="flex flex-wrap items-end gap-2 rounded-lg border border-amber-200 bg-amber-50/60 p-2.5">
                  <Field label={`Bỏ "${name}" khách đã chọn — lý do *`} error={missing ? 'Bắt buộc ghi lý do' : null} className="min-w-56 flex-1">
                    <Input
                      value={r.reason}
                      invalid={missing}
                      onChange={(e) => setRemoved((rs) => rs.map((x) => (x.serviceId === r.serviceId ? { ...x, reason: e.target.value } : x)))}
                      placeholder="VD: Khách xác nhận qua điện thoại không cần tháo máy lạnh"
                    />
                  </Field>
                  <Button variant="secondary" icon={RotateCcw} onClick={() => restore(r.serviceId)}>
                    Khôi phục
                  </Button>
                </li>
              );
            })}
          </ul>
        )}

        {pickable.length > 0 && (
          <div className="flex flex-wrap gap-1.5">
            {pickable.map((x) => (
              <SuggestChip key={x.id} onClick={() => addPicked(x.id)}>
                {x.name} <span className="font-normal text-slate-400">· {formatVND(x.basePrice)}/{x.unit}</span>
              </SuggestChip>
            ))}
          </div>
        )}
      </Section>

      <Section
        title="Khoản ngoài bảng giá"
        hint="Chỉ dùng cho trường hợp đặc biệt (VD: đàn piano, két sắt). Bắt buộc lý do — khách thấy rõ là khoản ngoài bảng giá."
        action={
          <Button
            variant="soft"
            icon={Plus}
            onClick={() => setCustom((cs) => [...cs, { key: uid('qi'), name: '', unit: 'gói', price: '', quantity: 1, reason: '' }])}
          >
            Thêm khoản
          </Button>
        }
      >
        {custom.length === 0 ? (
          <p className="text-sm text-slate-500">Không có.</p>
        ) : (
          <ul className="space-y-2">
            {custom.map((c) => {
              const err = (k: 'name' | 'price' | 'reason') =>
                show('custom') && (k === 'price' ? !(Number(c.price) > 0) : !c[k].trim());
              return (
                <li key={c.key} className="space-y-2 rounded-lg border border-amber-200 bg-amber-50/40 p-3">
                  <div className="grid gap-2 sm:grid-cols-[1fr_100px_140px_auto]">
                    <Field label="Tên khoản *" error={err('name') ? 'Bắt buộc' : null}>
                      <Input value={c.name} invalid={err('name')} onChange={(e) => patchCustom(c.key, { name: e.target.value })} placeholder="VD: Khiêng đàn piano đứng" />
                    </Field>
                    <Field label="Đơn vị">
                      <Input value={c.unit} onChange={(e) => patchCustom(c.key, { unit: e.target.value })} />
                    </Field>
                    <Field label="Đơn giá (đ) *" error={err('price') ? 'Bắt buộc' : null}>
                      <Input inputMode="numeric" value={c.price} invalid={err('price')} onChange={(e) => patchCustom(c.key, { price: e.target.value.replace(/\D/g, '') })} />
                    </Field>
                    <button
                      type="button"
                      onClick={() => setCustom((cs) => cs.filter((x) => x.key !== c.key))}
                      className="grid size-10 shrink-0 place-items-center rounded-lg text-slate-400 hover:bg-rose-50 hover:text-rose-600 self-end"
                      aria-label="Bỏ khoản này"
                    >
                      <Trash2 className="size-4" />
                    </button>
                  </div>
                  <div className="grid gap-2 sm:grid-cols-[160px_1fr]">
                    <Field label="Số lượng">
                      <NumberStepper value={c.quantity} onChange={(q) => patchCustom(c.key, { quantity: q })} min={1} max={50} />
                    </Field>
                    <Field label="Lý do (khách xem được) *" error={err('reason') ? 'Bắt buộc ghi lý do' : null}>
                      <Input value={c.reason} invalid={err('reason')} onChange={(e) => patchCustom(c.key, { reason: e.target.value })} placeholder="VD: Đàn nặng ~250kg, cần 4 người & dây đai chuyên dụng" />
                    </Field>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </Section>

      <Section title="Ghi chú cho khách" hint="Tùy chọn — VD điều đã thống nhất với khách qua điện thoại. Khách xem được cạnh báo giá.">
        <Input value={evNote} onChange={(e) => setEvNote(e.target.value)} placeholder="VD: Đã gọi xác nhận khách có 1 tủ lạnh side-by-side" />
      </Section>

      <QuoteBreakdownTable items={items} reference={order.referenceQuote?.financialSummary.totalEstimated ?? null} />

      {needsReason && (
        <Field
          label={`Lý do điều chỉnh so với đề xuất của người khảo sát (lệch ${deviation > 0 ? '+' : ''}${Math.round(deviation * 100)}%) *`}
          error={errors.deviation ? `Phần tự tính lệch quá ${Math.round(PROPOSAL_DEVIATION_LIMIT * 100)}% so với đề xuất — bắt buộc ghi lý do` : null}
        >
          <Input value={reason} onChange={(e) => setReason(e.target.value)} invalid={errors.deviation} placeholder="VD: Đã gọi khách xác nhận bỏ bớt tủ gỗ, chỉ cần 1 xe" />
        </Field>
      )}

      <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-slate-50 p-4">
        <div>
          <p className="text-xs text-slate-500">Tổng gửi khách</p>
          <p className="text-xl font-bold tabular-nums">{formatVND(total)}</p>
          {proposalTotal !== null && (
            <p className="text-xs text-slate-500 tabular-nums">
              Phần tự tính {formatVND(autoTotal)} · đề xuất khảo sát {formatVND(proposalTotal)}
              {autoTotal !== proposalTotal && (
                <span className={cx('ml-1 font-semibold', needsReason ? 'text-amber-700' : 'text-slate-600')}>
                  ({deviation > 0 ? '+' : ''}
                  {(deviation * 100).toFixed(1)}%)
                </span>
              )}
            </p>
          )}
        </div>
        <div className="flex flex-col items-end gap-1">
          <Button icon={CheckCircle2} disabled={Boolean(blocked) || (submitted && invalid)} onClick={submit}>
            Gửi báo giá cho khách
          </Button>
          {blocked && <span className="text-xs text-slate-500">{blocked}</span>}
          {submitted && invalid && !blocked && <span className="text-xs text-rose-600">Còn mục bắt buộc chưa điền — xem các ô / dòng báo đỏ.</span>}
        </div>
      </div>
    </div>
  );
};
