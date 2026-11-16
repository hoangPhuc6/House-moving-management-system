import { type ChangeEvent, type ReactNode, useMemo, useState } from 'react';
import { Camera, CheckCircle2, CheckCircle, Plus, RotateCcw, Sparkles, Trash2, Truck } from 'lucide-react';
import { Alert, Button, controlBase, cx, Field, Input, NumberStepper, Select } from '@/components/ui';
import { PROPOSAL_DEVIATION_LIMIT, VEHICLE_PRICING } from '@/constants/pricing';
import { surveyInputFromOrder } from '@/features/coordinator/utils';
import { QuoteBreakdownTable } from '@/features/order/components/QuoteBreakdownTable';
import { photosFromFiles, SitePhotoGrid } from '@/features/order/components/SitePhotoGallery';
import {
  isMediaSubmitted,
  MIN_VIDEO_PHOTOS,
  missingVideoPhotos,
  SITE_PHOTO_CHECKLIST,
  type SurveyStageKind,
  VIDEO_REQUIRED_CATEGORIES,
} from '@/features/order/surveyStage';
import { formatVND, uid } from '@/services/format';
import { buildSurveyQuote, isQuoteExtraService, quoteItemFromService, quoteSubtotal } from '@/services/pricing';
import { useApp } from '@/services/store/AppStore';
import { useCatalog } from '@/services/store/CatalogStore';
import type { Order, QuoteItem, ServiceItem, SitePhoto, SitePhotoCategory, SurveyInput } from '@/types';

const CAPACITIES = Object.keys(VEHICLE_PRICING)
  .map(Number)
  .sort((a, b) => a - b);

interface ExtraLine {
  serviceId: string;
  quantity: number;
  /** Khách chọn lúc đặt đơn — bỏ ra phải ghi lý do. */
  requested: boolean;
}

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
 * - Phần tự tính từ số liệu khảo sát (xe — nhiều loại, bốc xếp, vật tư, lầu, hẻm).
 * - Dịch vụ thêm từ bảng giá (đơn giá cố định); dịch vụ khách chọn lúc đặt đơn được đưa vào sẵn, bỏ ra phải ghi lý do.
 * - Khoản ngoài bảng giá (bắt buộc lý do, hiện rõ cho khách).
 * - Video call: bắt buộc biên bản + ảnh chụp màn hình làm căn cứ (trừ khi khách đã gửi ảnh).
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
  const videoCall = kind === 'VIDEO_CALL';

  const initial = useMemo(() => proposal ?? surveyInputFromOrder(order), [order.id, proposal]);
  const [s, setS] = useState<SurveyInput>(initial);
  const set = <K extends keyof SurveyInput>(k: K, v: SurveyInput[K]) => setS((x) => ({ ...x, [k]: v }));
  const extraVehicles = s.extraVehicles ?? [];
  const setExtraVehicles = (v: { capacity: number; qty: number }[]) => set('extraVehicles', v);

  const extraServices = useMemo(() => catalog.services.filter(isQuoteExtraService), [catalog.services]);
  const serviceById = (id: string): ServiceItem | undefined => catalog.services.find((x) => x.id === id);
  const requestedInitial = useMemo<ExtraLine[]>(
    () =>
      order.requestedServices
        .map((name) => extraServices.find((x) => x.name === name))
        .filter((x): x is ServiceItem => Boolean(x))
        .map((x) => ({ serviceId: x.id, quantity: 1, requested: true })),
    [order.id, extraServices],
  );
  const [extras, setExtras] = useState<ExtraLine[]>(requestedInitial);
  const [removed, setRemoved] = useState<{ serviceId: string; reason: string }[]>([]);
  const [custom, setCustom] = useState<CustomLine[]>([]);

  const [evNote, setEvNote] = useState('');
  const [evPhotos, setEvPhotos] = useState<SitePhoto[]>([]);
  const [reason, setReason] = useState('');
  const [submitted, setSubmitted] = useState(false);

  const autoItems = useMemo(() => buildSurveyQuote(truckOnly ? { ...s, cartons: 0, tapeRolls: 0, peFilmRolls: 0 } : s), [s, truckOnly]);
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

  const customerSentMedia = isMediaSubmitted(order);
  const videoMissing = videoCall ? missingVideoPhotos(evPhotos) : null;
  const errors = {
    deviation: needsReason && !reason.trim(),
    evNote: videoCall && !evNote.trim(),
    evPhotos: Boolean(videoMissing) && !customerSentMedia,
    removed: removed.some((r) => !r.reason.trim()),
    custom: custom.some((c) => !c.name.trim() || !(Number(c.price) > 0) || !c.reason.trim()),
  };
  const invalid = Object.values(errors).some(Boolean);
  const show = (k: keyof typeof errors) => submitted && errors[k];

  const maxLabor = VEHICLE_PRICING[s.vehicleCapacity]?.maxLabor;
  const seatCount = (maxLabor ?? 0) * s.vehicleQty + extraVehicles.reduce((n, v) => n + (VEHICLE_PRICING[v.capacity]?.maxLabor ?? 0) * v.qty, 0);

  const stepper = (label: string, k: 'distanceKm' | 'vehicleQty' | 'workers' | 'cartons' | 'tapeRolls' | 'peFilmRolls' | 'floorsFrom' | 'floorsTo' | 'alleyFrom' | 'alleyTo', opts: { min?: number; max: number; step?: number; suffix?: string }, hint?: string) => (
    <Field label={label} hint={hint}>
      <NumberStepper value={s[k]} onChange={(v) => set(k, v)} {...opts} />
    </Field>
  );

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

  const addEvPhotos = (category: SitePhotoCategory) => (e: ChangeEvent<HTMLInputElement>) => {
    const next = photosFromFiles(e.target.files);
    e.target.value = '';
    setEvPhotos((ps) => [...ps, ...next.map((p) => ({ ...p, category }))]);
  };
  const removeEvPhoto = (id: string) =>
    setEvPhotos((ps) => {
      ps.filter((p) => p.id === id).forEach((p) => URL.revokeObjectURL(p.url));
      return ps.filter((p) => p.id !== id);
    });

  const submit = () => {
    setSubmitted(true);
    if (invalid || blocked) return;
    dispatch({
      type: 'SUBMIT_SURVEY',
      orderId: order.id,
      items,
      input: truckOnly ? { ...s, cartons: 0, tapeRolls: 0, peFilmRolls: 0 } : s,
      evidence: { note: evNote, photos: evPhotos },
      removedRequested: removed.map((r) => ({ name: serviceById(r.serviceId)?.name ?? r.serviceId, reason: r.reason })),
      adjustReason: needsReason ? reason : undefined,
    });
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm font-semibold text-slate-900">Báo giá chính thức</p>
        <span className="flex items-center gap-1.5 text-xs text-slate-500">
          <Sparkles className="size-3.5" /> {proposal ? 'Đã điền sẵn theo đề xuất của NV khảo sát' : 'Đã điền sẵn theo thông tin khách khai báo'}
          <button type="button" className="font-semibold text-brand hover:underline" onClick={() => setS(initial)}>
            Đặt lại số liệu
          </button>
        </span>
      </div>

      {videoCall && (
        <Section
          title="Biên bản & ảnh chụp màn hình video call"
          hint={`Tối thiểu ${MIN_VIDEO_PHOTOS} ảnh, bắt buộc có ${SITE_PHOTO_CHECKLIST.filter((c) => VIDEO_REQUIRED_CATEGORIES.includes(c.key))
            .map((c) => c.label.toLowerCase())
            .join(' và ')}. Khách xem được biên bản & ảnh cạnh báo giá.`}
        >
          {customerSentMedia && (
            <Alert tone="info" title={`Khách đã gửi ${order.customerPhotos.length} ảnh / video`}>
              Ảnh chụp màn hình không bắt buộc — nhưng nên chụp thêm lối đi và chỗ đỗ xe nếu ảnh khách gửi chưa có.
            </Alert>
          )}
          <Field label="Biên bản khảo sát *" error={show('evNote') ? 'Bắt buộc ghi biên bản' : null}>
            <textarea
              value={evNote}
              onChange={(e) => setEvNote(e.target.value)}
              rows={3}
              maxLength={2000}
              placeholder="Đồ lớn đã xem, đồ cần tháo lắp, cầu thang / thang máy, lối vào, chỗ đỗ xe, lưu ý đặc biệt…"
              className={cx(controlBase, 'h-auto min-h-[80px] py-2.5', show('evNote') && 'border-rose-400')}
            />
          </Field>
          <div className="grid gap-3 sm:grid-cols-2">
            {SITE_PHOTO_CHECKLIST.map((c) => {
              const photos = evPhotos.filter((p) => p.category === c.key);
              const required = VIDEO_REQUIRED_CATEGORIES.includes(c.key);
              const missing = show('evPhotos') && required && photos.length === 0;
              return (
                <div key={c.key} className={cx('rounded-lg border p-2.5', missing ? 'border-rose-300' : 'border-slate-100')}>
                  <p className="mb-2 flex items-center gap-1.5 text-xs font-semibold text-slate-700">
                    {photos.length > 0 ? <CheckCircle className="size-3.5 text-emerald-600" /> : <span className="size-3.5 rounded-full border-2 border-slate-300" />}
                    {c.label}
                    {required && <span className="text-rose-500">*</span>}
                  </p>
                  <SitePhotoGrid
                    photos={photos}
                    onRemove={removeEvPhoto}
                    addTile={
                      <label className="relative flex size-full cursor-pointer flex-col items-center justify-center gap-0.5 rounded-xl border border-dashed border-slate-300 text-[10px] font-semibold text-slate-600 hover:bg-slate-50">
                        <Camera className="size-4" />
                        Thêm
                        <input type="file" accept="image/*" multiple className="sr-only" onChange={addEvPhotos(c.key)} />
                      </label>
                    }
                  />
                </div>
              );
            })}
          </div>
          {show('evPhotos') && <p className="text-xs font-medium text-rose-600">{videoMissing} Hoặc yêu cầu khách gửi bổ sung ảnh trước khi báo giá.</p>}
        </Section>
      )}

      <Section
        title="Số liệu khảo sát"
        hint="Xe, bốc xếp, vật tư, lầu và hẻm được tính tự động theo bảng giá."
      >
        <div className="grid gap-4 sm:grid-cols-3">
          {stepper('Quãng đường', 'distanceKm', { min: 1, max: 200, suffix: 'km' })}
          <Field label="Loại xe chính">
            <Select value={s.vehicleCapacity} onChange={(e) => set('vehicleCapacity', Number(e.target.value))} className="h-11">
              {CAPACITIES.map((cap) => (
                <option key={cap} value={cap}>
                  {VEHICLE_PRICING[cap]?.label}
                </option>
              ))}
            </Select>
          </Field>
          {stepper('Số lượng xe chính', 'vehicleQty', { min: 1, max: 5 })}
        </div>

        {extraVehicles.length > 0 && (
          <ul className="space-y-2">
            {extraVehicles.map((v, i) => (
              <li key={i} className="flex flex-wrap items-end gap-3 rounded-lg bg-slate-50 p-2.5">
                <Field label={`Xe bổ sung ${i + 1}`} className="min-w-44 flex-1">
                  <Select
                    value={v.capacity}
                    onChange={(e) => setExtraVehicles(extraVehicles.map((x, j) => (j === i ? { ...x, capacity: Number(e.target.value) } : x)))}
                    className="h-11"
                  >
                    {CAPACITIES.map((cap) => (
                      <option key={cap} value={cap}>
                        {VEHICLE_PRICING[cap]?.label}
                      </option>
                    ))}
                  </Select>
                </Field>
                <Field label="Số lượng" className="w-40">
                  <NumberStepper value={v.qty} onChange={(q) => setExtraVehicles(extraVehicles.map((x, j) => (j === i ? { ...x, qty: q } : x)))} min={1} max={5} />
                </Field>
                <button
                  type="button"
                  onClick={() => setExtraVehicles(extraVehicles.filter((_, j) => j !== i))}
                  className="grid size-10 shrink-0 place-items-center rounded-lg text-slate-400 hover:bg-rose-50 hover:text-rose-600"
                  aria-label={`Bỏ xe bổ sung ${i + 1}`}
                >
                  <Trash2 className="size-4" />
                </button>
              </li>
            ))}
          </ul>
        )}
        <div>
          <Button size="sm" variant="secondary" icon={Truck} onClick={() => setExtraVehicles([...extraVehicles, { capacity: CAPACITIES[0] ?? 1.25, qty: 1 }])}>
            Thêm xe khác loại
          </Button>
        </div>
        <div className="grid gap-4 sm:grid-cols-3">
          {stepper(
            truckOnly ? 'Bốc vác thuê thêm' : 'Nhân viên bốc xếp',
            'workers',
            { min: 0, max: 12, suffix: 'người' },
            truckOnly && maxLabor !== undefined && s.workers > seatCount ? `Vượt số chỗ trên xe (tối đa ${seatCount})` : undefined,
          )}
          {!truckOnly && stepper('Thùng carton', 'cartons', { max: 200, step: 5 })}
          {!truckOnly && stepper('Băng keo', 'tapeRolls', { max: 50, suffix: 'cuộn' })}
          {!truckOnly && stepper('Màng PE', 'peFilmRolls', { max: 20, suffix: 'cuộn' })}
          {stepper('Thang bộ — điểm đi', 'floorsFrom', { max: 20, suffix: 'lầu' })}
          {stepper('Thang bộ — điểm đến', 'floorsTo', { max: 20, suffix: 'lầu' })}
          {stepper('Hẻm — điểm đi', 'alleyFrom', { max: 500, step: 50, suffix: 'm' })}
          {stepper('Hẻm — điểm đến', 'alleyTo', { max: 500, step: 50, suffix: 'm' })}
        </div>
      </Section>

      <Section title="Dịch vụ thêm" hint="Theo bảng giá, đơn giá cố định. Dịch vụ khách chọn khi đặt đơn đã được đưa vào sẵn.">
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
          <div>
            <Select value="" onChange={(e) => addPicked(e.target.value)} aria-label="Thêm dịch vụ từ bảng giá">
              <option value="">＋ Thêm dịch vụ từ bảng giá…</option>
              {pickable.map((x) => (
                <option key={x.id} value={x.id}>
                  {x.name} — {formatVND(x.basePrice)}/{x.unit}
                </option>
              ))}
            </Select>
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

      {!videoCall && (
        <Field label="Ghi chú căn cứ báo giá (tùy chọn, khách xem được)">
          <Input value={evNote} onChange={(e) => setEvNote(e.target.value)} placeholder="VD: Đã gọi xác nhận khách có 1 tủ lạnh side-by-side" />
        </Field>
      )}

      <QuoteBreakdownTable items={items} reference={order.referenceQuote?.financialSummary.totalEstimated ?? null} />

      {needsReason && (
        <Field
          label={`Lý do điều chỉnh so với đề xuất của NV (lệch ${deviation > 0 ? '+' : ''}${Math.round(deviation * 100)}%) *`}
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
              Phần tự tính {formatVND(autoTotal)} · đề xuất NV {formatVND(proposalTotal)}
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
          {submitted && invalid && !blocked && <span className="text-xs text-rose-600">Còn mục bắt buộc chưa điền — xem các ô viền đỏ.</span>}
        </div>
      </div>
    </div>
  );
};
