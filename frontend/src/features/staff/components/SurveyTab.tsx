import { type ReactNode, useEffect, useState } from 'react';
import { Camera, CheckCircle2, ChevronDown, ClipboardList, Footprints, MapPin, Navigation, Phone, Plus, Send, Trash2, Truck, Video } from 'lucide-react';
import { Alert, Badge, Button, cx, EmptyState, NumberStepper, Select } from '@/components/ui';
import { CARTON_ORDER, CARTON_SIZES, qtyOf, setVehicleQty, totalVehicles, VEHICLE_CAPACITIES, vehicleLinesLabel } from '@/constants/pricing';
import { hoursLabel } from '@/constants/resources';
import { surveyInputFromOrder } from '@/features/coordinator/utils';
import { SurveyItemList } from '@/features/order/components/SurveyItemList';
import { SurveyMediaGallery } from '@/features/order/components/SurveyMediaGallery';
import { cleanSurveyItems, COMMON_SURVEY_ITEMS, emptySurveyItem, surveyItemsTotal } from '@/features/order/surveyItems';
import { appointmentLabel, isMediaSubmitted, SURVEY_STAGE_META, surveyStageOf } from '@/features/order/surveyStage';
import { Block } from '@/features/staff/components/Block';
import { CustomerInfo } from '@/features/staff/components/CustomerInfo';
import { StickyActionBar } from '@/layouts/MobileShell';
import { formatDateVN, formatTime } from '@/services/format';
import { useApp } from '@/services/store/AppStore';
import type { Order, SurveyInput, SurveyItem } from '@/types';

const KIND_ICON = { VIDEO_CALL: Video, ON_SITE: Footprints, PHOTOS: Camera, TRUCK_CONFIRM: Truck } as const;

const textareaCls =
  'w-full rounded-xl border border-slate-200 p-3 text-base focus:border-brand focus:ring-4 focus:ring-brand/10 focus:outline-none sm:text-sm';

/** Biểu mẫu kết quả khảo sát của một đơn — người khảo sát ghi nhận & đề xuất, không định giá. */
const ReportForm = ({ order }: { order: Order }) => {
  const { dispatch } = useApp();
  const [items, setItems] = useState<SurveyItem[]>([]);
  const [note, setNote] = useState('');
  const [p, setP] = useState<SurveyInput>(() => surveyInputFromOrder(order));
  const [tried, setTried] = useState(false);
  const set = <K extends keyof SurveyInput>(k: K, v: SurveyInput[K]) => setP((x) => ({ ...x, [k]: v }));

  const kind = surveyStageOf(order);
  const mediaMissing = kind === 'PHOTOS' && !isMediaSubmitted(order);
  const filled = cleanSurveyItems(items);
  const itemsErr = tried && filled.length === 0;
  const vehiclesErr = tried && totalVehicles(p.vehicles) === 0;
  const hoursErr = tried && !(p.estimatedHours > 0);

  const patchItem = (i: number, patch: Partial<SurveyItem>) => setItems((xs) => xs.map((x, j) => (j === i ? { ...x, ...patch } : x)));
  const removeItem = (i: number) => setItems((xs) => xs.filter((_, j) => j !== i));
  // Bấm món có sẵn: chưa có thì thêm, có rồi thì +1.
  const quickAdd = (c: (typeof COMMON_SURVEY_ITEMS)[number]) =>
    setItems((xs) => {
      const i = xs.findIndex((x) => x.name.trim().toLowerCase() === c.name.toLowerCase());
      if (i >= 0) return xs.map((x, j) => (j === i ? { ...x, qty: x.qty + 1 } : x));
      return [...xs, { ...emptySurveyItem(), name: c.name, fragile: !!c.fragile, disassemble: !!c.disassemble }];
    });

  const row = (label: string, node: ReactNode) => (
    <div className="flex items-center justify-between gap-3 py-2.5">
      <span className="text-sm font-medium text-slate-700">{label}</span>
      <div className="w-40 shrink-0">{node}</div>
    </div>
  );

  const submit = () => {
    setTried(true);
    if (filled.length === 0 || totalVehicles(p.vehicles) === 0 || !(p.estimatedHours > 0) || mediaMissing) return;
    dispatch({ type: 'SUBMIT_SURVEY_REPORT', orderId: order.id, items: filled, note, proposal: p });
  };

  const flagBtn = (on: boolean, label: string, onClick: () => void, tone: 'amber' | 'blue') => (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={on}
      className={cx(
        'h-6 rounded-full px-2 text-[11px] font-semibold',
        on ? (tone === 'amber' ? 'bg-amber-100 text-amber-800' : 'bg-blue-100 text-blue-800') : 'text-slate-400 hover:text-slate-600',
      )}
    >
      {on ? `✓ ${label}` : `+ ${label}`}
    </button>
  );

  return (
    <>
      <Block
        title="Danh sách đồ đạc"
        icon={ClipboardList}
        right={filled.length > 0 && <Badge tone="neutral">{surveyItemsTotal(filled)} món</Badge>}
      >
        <p className="mb-1.5 text-xs text-slate-500">Bấm để thêm nhanh, bấm lại để tăng số lượng</p>
        <div className="flex flex-wrap gap-1.5">
          {COMMON_SURVEY_ITEMS.map((c) => (
            <button
              key={c.name}
              type="button"
              onClick={() => quickAdd(c)}
              className="h-8 rounded-full border border-slate-200 px-2.5 text-xs font-medium text-slate-700 active:bg-slate-100"
            >
              + {c.name}
            </button>
          ))}
        </div>

        {items.length > 0 && (
          <ul className="mt-3 divide-y divide-slate-100 rounded-xl border border-slate-200">
            {items.map((it, i) => (
              <li key={i} className="px-2.5 py-2">
                <div className="flex items-center gap-1.5">
                  <input
                    value={it.name}
                    onChange={(e) => patchItem(i, { name: e.target.value })}
                    placeholder="Tên đồ (VD: Giường 1m8)"
                    aria-invalid={tried && !it.name.trim()}
                    className={cx(
                      'h-9 min-w-0 flex-1 rounded-lg border px-2.5 text-base focus:border-brand focus:outline-none sm:text-sm',
                      tried && !it.name.trim() ? 'border-rose-400' : 'border-transparent bg-slate-50',
                    )}
                  />
                  <div className="flex h-9 shrink-0 items-center rounded-lg border border-slate-200">
                    <button
                      type="button"
                      onClick={() => patchItem(i, { qty: Math.max(1, it.qty - 1) })}
                      disabled={it.qty <= 1}
                      aria-label="Giảm"
                      className="w-8 text-base font-semibold text-slate-600 disabled:text-slate-300"
                    >
                      −
                    </button>
                    <span className="w-7 text-center text-sm font-semibold tabular-nums">{it.qty}</span>
                    <button
                      type="button"
                      onClick={() => patchItem(i, { qty: Math.min(200, it.qty + 1) })}
                      aria-label="Tăng"
                      className="w-8 text-base font-semibold text-slate-600"
                    >
                      +
                    </button>
                  </div>
                  <button
                    type="button"
                    onClick={() => removeItem(i)}
                    aria-label="Xóa món này"
                    className="grid size-9 shrink-0 place-items-center rounded-lg text-slate-400 hover:bg-rose-50 hover:text-rose-600"
                  >
                    <Trash2 className="size-4" />
                  </button>
                </div>
                <div className="mt-1 flex gap-1 pl-1">
                  {flagBtn(it.fragile, 'Dễ vỡ', () => patchItem(i, { fragile: !it.fragile }), 'amber')}
                  {flagBtn(it.disassemble, 'Tháo lắp', () => patchItem(i, { disassemble: !it.disassemble }), 'blue')}
                </div>
              </li>
            ))}
          </ul>
        )}
        <button
          type="button"
          onClick={() => setItems((xs) => [...xs, emptySurveyItem()])}
          className="mt-2 inline-flex h-9 items-center gap-1 text-sm font-semibold text-brand"
        >
          <Plus className="size-4" /> Thêm món khác
        </button>
        {itemsErr && <p className="mt-1 text-xs font-medium text-rose-600">Thêm ít nhất một món đồ đã khảo sát</p>}
        <p className="mt-3 mb-1.5 text-sm font-medium text-slate-700">Ghi chú hiện trường</p>
        <textarea
          value={note}
          onChange={(e) => setNote(e.target.value)}
          rows={3}
          placeholder="VD: Hẻm 3m xe 2 tấn vào được, tủ gỗ lớn cần 4 người khiêng, chung cư cần đăng ký thang máy hàng"
          className={textareaCls}
        />
      </Block>

      <Block title="Đề xuất xe & nhân sự" icon={Truck}>
        <div className="divide-y divide-slate-100">
          {row('Quãng đường', <NumberStepper value={p.distanceKm} onChange={(v) => set('distanceKm', v)} min={1} max={200} suffix="km" />)}
          <div>
            {row('Thời gian dự kiến *', <NumberStepper value={p.estimatedHours} onChange={(v) => set('estimatedHours', v)} max={24} step={0.5} suffix="giờ" />)}
            <p className={cx('-mt-1 pb-2 text-xs', hoursErr ? 'font-medium text-rose-600' : 'text-slate-500')}>
              {hoursErr ? 'Bắt buộc — ' : ''}Tổng thời gian bốc xếp + di chuyển + dỡ đồ. Xe & người được giữ lịch trong khoảng này.
            </p>
          </div>
          <div className="py-2.5">
            <span className="text-sm font-medium text-slate-700">Xe</span>
            <span className="ml-1.5 text-xs text-slate-500">ghép nhiều loại được, VD 1 xe 5 tấn + 1 xe 1,25 tấn</span>
            <div className="mt-1 divide-y divide-slate-50">
              {VEHICLE_CAPACITIES.map((cap) => (
                <div key={cap}>
                  {row(
                    `Xe ${String(cap).replace('.', ',')} tấn`,
                    <NumberStepper value={qtyOf(p.vehicles, cap)} onChange={(v) => set('vehicles', setVehicleQty(p.vehicles, cap, v))} max={5} />,
                  )}
                </div>
              ))}
            </div>
            {vehiclesErr && <p className="text-xs font-medium text-rose-600">Đề xuất ít nhất một xe</p>}
          </div>
          {row('Nhân viên bốc xếp', <NumberStepper value={p.workers} onChange={(v) => set('workers', v)} min={0} max={12} suffix="người" />)}
          <div className="py-2.5">
            <span className="text-sm font-medium text-slate-700">Thùng carton</span>
            <div className="mt-1 divide-y divide-slate-50">
              {CARTON_ORDER.map((size) => {
                const c = CARTON_SIZES[size];
                return (
                  <div key={size} className="flex items-center justify-between gap-3 py-2">
                    <span className="min-w-0 text-sm text-slate-700">
                      {c.label} <span className="text-slate-400 tabular-nums">{c.dims}</span>
                      <span className="block text-xs text-slate-500">{c.use}</span>
                    </span>
                    <div className="w-40 shrink-0">
                      <NumberStepper value={p.cartons[size]} onChange={(v) => set('cartons', { ...p.cartons, [size]: v })} max={200} step={5} />
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
          {row('Băng keo', <NumberStepper value={p.tapeRolls} onChange={(v) => set('tapeRolls', v)} max={50} suffix="cuộn" />)}
          {row('Màng PE', <NumberStepper value={p.peFilmRolls} onChange={(v) => set('peFilmRolls', v)} max={20} suffix="cuộn" />)}
          {row('Số lầu (thang bộ)', <NumberStepper value={p.floors} onChange={(v) => set('floors', v)} max={20} />)}
          {row('Độ sâu hẻm', <NumberStepper value={p.alleyMeters} onChange={(v) => set('alleyMeters', v)} max={500} step={50} suffix="m" />)}
        </div>
      </Block>

      <StickyActionBar className="md:!sticky md:bottom-4">
        <div className="flex items-center gap-3">
          <div className="min-w-0 flex-1">
            <p className="text-xs text-slate-500">Đề xuất gửi Điều phối lập báo giá</p>
            <p className="truncate text-sm font-semibold text-slate-900 tabular-nums">
              {vehicleLinesLabel(p.vehicles)} · {p.workers} người · {p.distanceKm} km{p.estimatedHours > 0 ? ` · ${hoursLabel(p.estimatedHours)}` : ''}
            </p>
          </div>
          <Button size="lg" icon={Send} onClick={submit} disabled={mediaMissing}>
            Gửi kết quả
          </Button>
        </div>
      </StickyActionBar>
    </>
  );
};

/** Kết quả đã gửi — người khảo sát xem lại sau khi gửi (chỉ đọc), kèm tình trạng báo giá của Điều phối. */
const SubmittedSurveys = () => {
  const { state } = useApp();
  const sent = state.orders
    .filter((o) => o.surveyReport)
    .sort((a, b) => b.surveyReport!.submittedAt - a.surveyReport!.submittedAt)
    .slice(0, 10);
  if (sent.length === 0) return null;
  return (
    <Block title="Đã gửi kết quả" icon={CheckCircle2} right={<Badge tone="neutral">{sent.length}</Badge>}>
      <ul className="-mx-1 divide-y divide-slate-100">
        {sent.map((o) => {
          const r = o.surveyReport!;
          const quoting = o.status === 'NEW_PENDING_SURVEY';
          return (
            <li key={o.id}>
              <details className="group px-1 py-2.5">
                <summary className="flex cursor-pointer list-none items-center gap-2 [&::-webkit-details-marker]:hidden">
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-semibold tabular-nums">
                      #{o.id} · {o.customerName}
                    </span>
                    <span className="block text-xs text-slate-500 tabular-nums">Gửi lúc {formatTime(r.submittedAt)}</span>
                  </span>
                  <Badge tone={o.status === 'CANCELLED_EXPIRED' ? 'neutral' : quoting ? 'warning' : 'success'}>
                    {o.status === 'CANCELLED_EXPIRED' ? 'Đơn đã hủy' : quoting ? 'Chờ Điều phối báo giá' : 'Đã báo giá khách'}
                  </Badge>
                  <ChevronDown className="size-4 shrink-0 text-slate-400 transition-transform group-open:rotate-180" />
                </summary>
                <div className="mt-2 space-y-2 text-sm">
                  <SurveyItemList items={r.items} />
                  <p className="text-slate-700">
                    {vehicleLinesLabel(r.proposal.vehicles)} · {r.proposal.workers} người · {r.proposal.distanceKm} km
                    {r.proposal.estimatedHours > 0 && ` · ${hoursLabel(r.proposal.estimatedHours)}`}
                  </p>
                  {r.note && <p className="rounded-lg bg-slate-50 p-2.5 text-slate-600">{r.note}</p>}
                </div>
              </details>
            </li>
          );
        })}
      </ul>
    </Block>
  );
};

/** Khảo sát: các đơn Điều phối đã phân cho người khảo sát (video call, tận nơi hoặc kiểm tra ảnh khách gửi). */
export const SurveyTab = () => {
  const { state } = useApp();
  const candidates = state.orders.filter((o) => o.status === 'NEW_PENDING_SURVEY' && o.surveyAssignment && !o.surveyReport);
  const [targetId, setTargetId] = useState(candidates[0]?.id ?? '');
  const target = candidates.find((o) => o.id === targetId) ?? candidates[0];

  // Đơn vừa gửi kết quả rời danh sách → chuyển sang đơn kế tiếp.
  useEffect(() => {
    if (target && target.id !== targetId) setTargetId(target.id);
  }, [target, targetId]);

  if (!target?.surveyAssignment) {
    return (
      <div className="space-y-3">
        <EmptyState icon={Footprints} title="Chưa có đơn cần khảo sát">
          Đơn được Điều phối phân cho bạn sẽ hiện ở đây.
        </EmptyState>
        <SubmittedSurveys />
      </div>
    );
  }

  const a = target.surveyAssignment;
  const kind = surveyStageOf(target);
  const KindIcon = KIND_ICON[kind];
  const appt = appointmentLabel(target);
  const surveyor = state.resources.staff.find((s) => s.id === a.surveyorId);

  return (
    <div className="space-y-3 pb-24 md:pb-0">
      <Block title="Đơn cần khảo sát" icon={MapPin} right={<Badge tone="brand">{candidates.length}</Badge>}>
        <Select value={target.id} onChange={(e) => setTargetId(e.target.value)} className="text-base sm:text-sm">
          {candidates.map((o) => (
            <option key={o.id} value={o.id}>
              #{o.id} · {o.customerName} · {SURVEY_STAGE_META[surveyStageOf(o)].short}
            </option>
          ))}
        </Select>
        <div className="mt-3">
          <CustomerInfo order={target} />
        </div>
        <p className="mt-2.5 text-sm text-slate-600">
          {target.fromAddress} → {target.toAddress}
        </p>
        <div className="mt-2 flex flex-wrap gap-1.5">
          <Badge tone="neutral">
            Chuyển {formatDateVN(target.scheduledDate)} · {target.timeSlot}
          </Badge>
        </div>
      </Block>

      <Block
        title={SURVEY_STAGE_META[kind].label}
        icon={KindIcon}
        right={<span className="text-xs text-slate-400 tabular-nums">Phân lúc {formatTime(a.assignedAt)}</span>}
      >
        {appt && (
          <p className="text-sm text-slate-700">
            Lịch khách hẹn: <b>{appt}</b>
          </p>
        )}
        {a.note && <p className="mt-2 rounded-xl bg-amber-50 p-3 text-sm text-amber-900">{a.note}</p>}
        <p className="mt-2 text-xs text-slate-500">Người khảo sát: {surveyor?.name ?? '—'}</p>

        {kind === 'PHOTOS' &&
          (isMediaSubmitted(target) ? (
            <div className="mt-3">
              <SurveyMediaGallery order={target} />
            </div>
          ) : (
            <Alert tone="warning" title="Khách chưa gửi ảnh / video" className="mt-3">
              {target.customerPhotos.length > 0 ? `Khách đã tải ${target.customerPhotos.length} tệp nhưng chưa bấm gửi.` : 'Khách chưa tải tệp nào.'} Gọi nhắc khách nếu cần.
            </Alert>
          ))}

        <div className={cx('mt-3 grid gap-2', kind === 'ON_SITE' ? 'grid-cols-2' : 'grid-cols-1')}>
          <a
            href={`tel:${target.phone.replace(/\s/g, '')}`}
            className="flex h-12 items-center justify-center gap-2 rounded-xl border border-slate-200 text-sm font-semibold text-slate-700 active:bg-slate-50"
          >
            <Phone className="size-4" /> {kind === 'VIDEO_CALL' ? 'Gọi khách (Zalo / Messenger)' : kind === 'PHOTOS' ? 'Gọi khách' : 'Hẹn giờ với khách'}
          </a>
          {kind === 'ON_SITE' && (
            <a
              href={`https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(target.fromAddress)}`}
              target="_blank"
              rel="noreferrer"
              className="flex h-12 items-center justify-center gap-2 rounded-xl border border-slate-200 text-sm font-semibold text-slate-700 active:bg-slate-50"
            >
              <Navigation className="size-4" /> Đến nhà khách
            </a>
          )}
        </div>
      </Block>

      <ReportForm key={target.id} order={target} />

      <SubmittedSurveys />
    </div>
  );
};
