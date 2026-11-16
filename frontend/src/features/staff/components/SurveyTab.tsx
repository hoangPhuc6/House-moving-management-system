import { type ChangeEvent, type ReactNode, useMemo, useState } from 'react';
import {
  Camera,
  CheckCircle2,
  ClipboardCheck,
  Footprints,
  MapPin,
  Navigation,
  NotebookPen,
  PackageCheck,
  Phone,
  PlayCircle,
  Plus,
  Trash2,
  Truck,
} from 'lucide-react';
import { Alert, Badge, Button, controlBase, cx, EmptyState, NumberStepper, Select } from '@/components/ui';
import { HOUSE_TYPE_META } from '@/constants/order';
import { VEHICLE_PRICING } from '@/constants/pricing';
import { surveyInputFromOrder } from '@/features/coordinator/utils';
import { fleetText } from '@/services/pricing';
import { photosFromFiles, SitePhotoGallery, SitePhotoGrid } from '@/features/order/components/SitePhotoGallery';
import {
  MIN_SITE_PHOTOS,
  missingSitePhotos,
  onSiteProgressOf,
  SITE_PHOTO_CHECKLIST,
} from '@/features/order/surveyStage';
import { Block } from '@/features/staff/components/Block';
import { CustomerInfo } from '@/features/staff/components/CustomerInfo';
import { StickyActionBar } from '@/layouts/MobileShell';
import { formatDateVN, formatTime } from '@/services/format';
import { useApp } from '@/services/store/AppStore';
import type { OnSiteSurveyRequest, Order, SitePhotoCategory, SurveyInput } from '@/types';

const CAPACITIES = Object.keys(VEHICLE_PRICING)
  .map(Number)
  .sort((a, b) => a - b);

const PROGRESS_BADGE = {
  ASSIGNED: { label: 'Chưa bắt đầu', tone: 'neutral' },
  IN_PROGRESS: { label: 'Đang khảo sát', tone: 'info' },
  COMPLETED: { label: 'Chờ Điều phối báo giá', tone: 'success' },
} as const;

/**
 * Khảo sát trực tiếp của nhân viên vận chuyển: bắt đầu → chụp ảnh theo checklist + ghi đề xuất → hoàn tất.
 * Nhân viên KHÔNG thấy giá và KHÔNG gửi báo giá — Điều phối lập báo giá từ ảnh & đề xuất.
 */
export const SurveyTab = () => {
  const { state } = useApp();
  const candidates = state.orders.filter((o) => o.status === 'NEW_PENDING_SURVEY' && o.onSiteSurveyRequest);
  const [targetId, setTargetId] = useState(candidates[0]?.id ?? '');
  const target = candidates.find((o) => o.id === targetId) ?? candidates[0];

  if (!target?.onSiteSurveyRequest) {
    return (
      <EmptyState icon={Footprints} title="Chưa có yêu cầu khảo sát trực tiếp">
        Bạn chỉ đến khảo sát tại nhà khách khi Điều phối gửi yêu cầu.
      </EmptyState>
    );
  }

  return (
    <div className="space-y-3 pb-24 md:pb-0">
      <Block title="Đơn khảo sát trực tiếp" icon={MapPin} right={<ProgressBadge req={target.onSiteSurveyRequest} />}>
        <Select value={target.id} onChange={(e) => setTargetId(e.target.value)} className="text-base sm:text-sm">
          {candidates.map((o) => (
            <option key={o.id} value={o.id}>
              #{o.id} · {o.customerName}
              {o.onSiteSurveyRequest ? ` — ${PROGRESS_BADGE[onSiteProgressOf(o.onSiteSurveyRequest)].label}` : ''}
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
          <Badge tone="info">{HOUSE_TYPE_META[target.houseType].label}</Badge>
          <Badge tone="neutral">
            {formatDateVN(target.scheduledDate)} · {target.timeSlot}
          </Badge>
        </div>
      </Block>

      <SurveyWorkspace key={target.id} order={target} req={target.onSiteSurveyRequest} />
    </div>
  );
};

const ProgressBadge = ({ req }: { req: OnSiteSurveyRequest }) => {
  const b = PROGRESS_BADGE[onSiteProgressOf(req)];
  return (
    <Badge tone={b.tone} dot>
      {b.label}
    </Badge>
  );
};

const SurveyWorkspace = ({ order, req }: { order: Order; req: OnSiteSurveyRequest }) => {
  const { state, dispatch } = useApp();
  const progress = onSiteProgressOf(req);

  const requestBlock = (
    <Block
      title="Yêu cầu từ Điều phối"
      icon={Footprints}
      right={<span className="text-xs text-slate-400 tabular-nums">{formatTime(req.requestedAt)}</span>}
    >
      <p className="rounded-xl bg-amber-50 p-3 text-sm text-amber-900">{req.note || 'Cần đến tận nơi để khảo sát.'}</p>
      <p className="mt-2 text-xs font-semibold text-slate-700">
        Nhân viên được cử: {state.resources.staff.find((s) => s.id === req.surveyorId)?.name ?? '—'}
      </p>
      <p className="mt-2 text-xs text-slate-500">
        Khách đã gửi kèm <span className="tabular-nums">{order.customerPhotos.length}</span> ảnh đồ đạc.
      </p>
      {progress === 'ASSIGNED' && (
        <div className="mt-3 grid grid-cols-2 gap-2">
          <a
            href={`tel:${order.phone.replace(/\s/g, '')}`}
            className="flex h-12 items-center justify-center gap-2 rounded-xl border border-slate-200 text-sm font-semibold text-slate-700 active:bg-slate-50"
          >
            <Phone className="size-4" /> Hẹn giờ với khách
          </a>
          <a
            href={`https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(order.fromAddress)}`}
            target="_blank"
            rel="noreferrer"
            className="flex h-12 items-center justify-center gap-2 rounded-xl border border-slate-200 text-sm font-semibold text-slate-700 active:bg-slate-50"
          >
            <Navigation className="size-4" /> Đến nhà khách
          </a>
        </div>
      )}
    </Block>
  );

  if (progress === 'ASSIGNED') {
    return (
      <>
        {requestBlock}
        <Alert tone="info" icon={ClipboardCheck} title="Khi tới nhà khách, bấm Bắt đầu khảo sát">
          Bạn sẽ chụp ảnh hiện trường theo checklist (tối thiểu {MIN_SITE_PHOTOS} ảnh, đủ {SITE_PHOTO_CHECKLIST.length} nhóm) và ghi đề xuất xe, người,
          vật tư. Điều phối sẽ lập báo giá và gửi cho khách — vui lòng không báo giá trực tiếp cho khách.
        </Alert>
        <StickyActionBar className="md:!sticky md:bottom-4">
          <Button size="lg" block icon={PlayCircle} onClick={() => dispatch({ type: 'STAFF_START_SURVEY', orderId: order.id })}>
            Bắt đầu khảo sát
          </Button>
        </StickyActionBar>
      </>
    );
  }

  if (progress === 'COMPLETED') {
    return (
      <>
        <Alert tone="success" title={`Đã gửi kết quả khảo sát lúc ${formatTime(req.completedAt ?? 0)}`}>
          Điều phối đang lập báo giá dựa trên ảnh và đề xuất của bạn. Nếu khách hỏi giá, hãy hẹn khách nhận báo giá chính thức trên ứng dụng.
        </Alert>
        <Block title={`Ảnh hiện trường (${req.sitePhotos.length})`} icon={Camera}>
          <SitePhotoGallery photos={req.sitePhotos} />
        </Block>
        {req.proposal && <ProposalSummary proposal={req.proposal} note={req.resultNote} />}
      </>
    );
  }

  return <InProgressForm order={order} req={req} requestBlock={requestBlock} />;
};

const InProgressForm = ({ order, req, requestBlock }: { order: Order; req: OnSiteSurveyRequest; requestBlock: ReactNode }) => {
  const { dispatch } = useApp();
  const [s, setS] = useState<SurveyInput>(() => surveyInputFromOrder(order));
  const [note, setNote] = useState('');
  const set = <K extends keyof SurveyInput>(k: K, v: SurveyInput[K]) => setS((x) => ({ ...x, [k]: v }));
  const missing = missingSitePhotos(req);
  const coveredGroups = useMemo(
    () => SITE_PHOTO_CHECKLIST.filter((c) => req.sitePhotos.some((p) => p.category === c.key)).length,
    [req.sitePhotos],
  );

  const addPhotos = (category: SitePhotoCategory) => (e: ChangeEvent<HTMLInputElement>) => {
    const photos = photosFromFiles(e.target.files);
    e.target.value = '';
    if (photos.length === 0) return;
    dispatch({ type: 'STAFF_ADD_SITE_PHOTOS', orderId: order.id, photos: photos.map((p) => ({ ...p, category })) });
  };

  const removePhoto = (id: string) => {
    const photo = req.sitePhotos.find((p) => p.id === id);
    if (photo) URL.revokeObjectURL(photo.url);
    dispatch({ type: 'STAFF_REMOVE_SITE_PHOTO', orderId: order.id, photoId: id });
  };

  const row = (label: string, node: ReactNode) => (
    <div className="flex items-center justify-between gap-3 py-2.5">
      <span className="text-sm font-medium text-slate-700">{label}</span>
      <div className="w-40 shrink-0">{node}</div>
    </div>
  );

  return (
    <>
      {requestBlock}

      <Block
        title="Ảnh hiện trường"
        icon={Camera}
        right={
          <span className={cx('text-xs font-semibold tabular-nums', missing ? 'text-amber-600' : 'text-emerald-600')}>
            {req.sitePhotos.length}/{MIN_SITE_PHOTOS} ảnh · {coveredGroups}/{SITE_PHOTO_CHECKLIST.length} nhóm
          </span>
        }
      >
        <div className="divide-y divide-slate-100">
          {SITE_PHOTO_CHECKLIST.map((c) => {
            const photos = req.sitePhotos.filter((p) => p.category === c.key);
            return (
              <div key={c.key} className="space-y-2 py-3 first:pt-0 last:pb-0">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="flex items-center gap-1.5 text-sm font-medium text-slate-800">
                      {photos.length > 0 ? (
                        <CheckCircle2 className="size-4 shrink-0 text-emerald-600" />
                      ) : (
                        <span className="size-4 shrink-0 rounded-full border-2 border-slate-300" />
                      )}
                      {c.label}
                    </p>
                    <p className="mt-0.5 pl-[22px] text-xs text-slate-500">{c.hint}</p>
                  </div>
                  <label className="relative flex h-10 shrink-0 cursor-pointer items-center gap-1.5 rounded-xl border border-slate-200 px-3 text-sm font-semibold text-slate-700 active:bg-slate-50">
                    <Camera className="size-4" /> Chụp
                    <input type="file" accept="image/*" capture="environment" multiple className="sr-only" onChange={addPhotos(c.key)} />
                  </label>
                </div>
                {photos.length > 0 && <SitePhotoGrid photos={photos} onRemove={removePhoto} />}
              </div>
            );
          })}
        </div>
      </Block>

      <Block title="Đề xuất xe & nhân viên bốc xếp" icon={Truck}>
        <div className="divide-y divide-slate-100">
          {row('Quãng đường', <NumberStepper value={s.distanceKm} onChange={(v) => set('distanceKm', v)} min={1} max={200} suffix="km" />)}
          <div className="py-2.5">
            <span className="text-sm font-medium text-slate-700">Loại xe</span>
            <div className="mt-2 grid grid-cols-3 gap-1.5">
              {Object.keys(VEHICLE_PRICING)
                .map(Number)
                .sort((a, b) => a - b)
                .map((cap) => (
                  <button
                    key={cap}
                    type="button"
                    onClick={() => set('vehicleCapacity', cap)}
                    className={cx(
                      'h-12 rounded-xl border text-sm font-semibold tabular-nums',
                      s.vehicleCapacity === cap ? 'border-slate-900 bg-slate-900 text-white' : 'border-slate-200 text-slate-600',
                    )}
                  >
                    {String(cap).replace('.', ',')} tấn
                  </button>
                ))}
            </div>
          </div>
          {row('Số lượng xe', <NumberStepper value={s.vehicleQty} onChange={(v) => set('vehicleQty', v)} min={1} max={5} />)}
          {(s.extraVehicles ?? []).map((v, i) => (
            <div key={i} className="space-y-2 py-2.5">
              <div className="flex items-center justify-between gap-3">
                <span className="text-sm font-medium text-slate-700">Xe bổ sung {i + 1}</span>
                <button
                  type="button"
                  onClick={() => set('extraVehicles', (s.extraVehicles ?? []).filter((_, j) => j !== i))}
                  className="grid size-9 place-items-center rounded-lg text-slate-400 active:bg-rose-50 active:text-rose-600"
                  aria-label={`Bỏ xe bổ sung ${i + 1}`}
                >
                  <Trash2 className="size-4" />
                </button>
              </div>
              <div className="grid grid-cols-[1fr_auto] items-center gap-2">
                <div className="grid grid-cols-3 gap-1.5">
                  {CAPACITIES.map((cap) => (
                    <button
                      key={cap}
                      type="button"
                      onClick={() => set('extraVehicles', (s.extraVehicles ?? []).map((x, j) => (j === i ? { ...x, capacity: cap } : x)))}
                      className={cx(
                        'h-10 rounded-lg border text-xs font-semibold tabular-nums',
                        v.capacity === cap ? 'border-slate-900 bg-slate-900 text-white' : 'border-slate-200 text-slate-600',
                      )}
                    >
                      {String(cap).replace('.', ',')} tấn
                    </button>
                  ))}
                </div>
                <div className="w-32">
                  <NumberStepper
                    value={v.qty}
                    onChange={(q) => set('extraVehicles', (s.extraVehicles ?? []).map((x, j) => (j === i ? { ...x, qty: q } : x)))}
                    min={1}
                    max={5}
                  />
                </div>
              </div>
            </div>
          ))}
          <div className="py-2.5">
            <Button variant="soft" block icon={Plus} onClick={() => set('extraVehicles', [...(s.extraVehicles ?? []), { capacity: CAPACITIES[0] ?? 1.25, qty: 1 }])}>
              Thêm xe khác loại
            </Button>
          </div>
          {row('Nhân viên bốc xếp', <NumberStepper value={s.workers} onChange={(v) => set('workers', v)} min={0} max={12} suffix="người" />)}
        </div>
      </Block>

      <Block title="Đề xuất vật tư & điều kiện ra vào" icon={PackageCheck}>
        <div className="divide-y divide-slate-100">
          {row('Thùng carton', <NumberStepper value={s.cartons} onChange={(v) => set('cartons', v)} max={200} step={5} />)}
          {row('Băng keo', <NumberStepper value={s.tapeRolls} onChange={(v) => set('tapeRolls', v)} max={50} suffix="cuộn" />)}
          {row('Màng PE', <NumberStepper value={s.peFilmRolls} onChange={(v) => set('peFilmRolls', v)} max={20} suffix="cuộn" />)}
          {row('Thang bộ — điểm đi', <NumberStepper value={s.floorsFrom} onChange={(v) => set('floorsFrom', v)} max={20} suffix="lầu" />)}
          {row('Thang bộ — điểm đến', <NumberStepper value={s.floorsTo} onChange={(v) => set('floorsTo', v)} max={20} suffix="lầu" />)}
          {row('Hẻm — điểm đi', <NumberStepper value={s.alleyFrom} onChange={(v) => set('alleyFrom', v)} max={500} step={50} suffix="m" />)}
          {row('Hẻm — điểm đến', <NumberStepper value={s.alleyTo} onChange={(v) => set('alleyTo', v)} max={500} step={50} suffix="m" />)}
        </div>
      </Block>

      <Block title="Nhận xét hiện trường" icon={NotebookPen}>
        <textarea
          value={note}
          onChange={(e) => setNote(e.target.value)}
          maxLength={2000}
          rows={3}
          placeholder="VD: Cầu thang hẹp, phải tháo cửa tủ lạnh; xe tải không vào được hẻm sau 16h…"
          className={cx(controlBase, 'h-auto py-2.5 text-base sm:text-sm')}
        />
      </Block>

      <StickyActionBar className="md:!sticky md:bottom-4">
        <div className="flex items-center gap-3">
          <div className="min-w-0 flex-1">
            <p className={cx('text-xs font-medium', missing ? 'text-amber-700' : 'text-emerald-700')}>
              {missing ?? 'Đủ ảnh hiện trường — sẵn sàng gửi Điều phối.'}
            </p>
            <p className="text-[11px] text-slate-500">Điều phối sẽ lập và gửi báo giá cho khách.</p>
          </div>
          <Button
            size="lg"
            icon={CheckCircle2}
            disabled={Boolean(missing)}
            onClick={() => dispatch({ type: 'STAFF_COMPLETE_SURVEY', orderId: order.id, proposal: s, resultNote: note })}
          >
            Hoàn tất khảo sát
          </Button>
        </div>
      </StickyActionBar>
    </>
  );
};

/** Tóm tắt đề xuất đã gửi (không kèm giá). */
export const ProposalSummary = ({ proposal: p, note }: { proposal: SurveyInput; note: string }) => (
  <Block title="Đề xuất đã gửi" icon={ClipboardCheck}>
    <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
      {[
        ['Xe', fleetText(p)],
        ['Bốc xếp', `${p.workers} người`],
        ['Quãng đường', `${p.distanceKm} km`],
        ['Carton / băng keo / PE', `${p.cartons} / ${p.tapeRolls} / ${p.peFilmRolls}`],
        ['Thang bộ (đi / đến)', `${p.floorsFrom} / ${p.floorsTo} lầu`],
        ['Hẻm (đi / đến)', `${p.alleyFrom} / ${p.alleyTo} m`],
      ].map(([k, v]) => (
        <div key={k}>
          <dt className="text-xs text-slate-500">{k}</dt>
          <dd className="font-semibold text-slate-800 tabular-nums">{v}</dd>
        </div>
      ))}
    </dl>
    {note && <p className="mt-3 rounded-xl bg-slate-50 p-3 text-sm text-slate-700">{note}</p>}
  </Block>
);
