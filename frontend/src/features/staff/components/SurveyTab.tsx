import { useEffect, useRef, useState } from 'react';
import { Camera, CheckCircle2, ChevronDown, ClipboardList, Footprints, MapPin, Navigation, Phone, Plus, Pencil, Send, Trash2, Truck, Video } from 'lucide-react';
import { Alert, Badge, Button, cx, EmptyState } from '@/components/ui';
import { totalVehicles, vehicleLinesLabel } from '@/constants/pricing';
import { hoursLabel } from '@/constants/resources';
import { surveyInputFromOrder } from '@/features/coordinator/utils';
import { PhotoButton, PhotoStrip, SitePhotoGallery } from '@/features/order/components/SitePhotoGallery';
import { SurveyItemList } from '@/features/order/components/SurveyItemList';
import { SurveyMediaGallery } from '@/features/order/components/SurveyMediaGallery';
import { proposalParts } from '@/features/order/proposal';
import { cleanSurveyItems, COMMON_ITEM_NOTES, COMMON_SURVEY_ITEMS, emptySurveyItem, photosOutsideItems, surveyItemsTotal } from '@/features/order/surveyItems';
import {
  appointmentLabel,
  hasAppointment,
  isMediaSubmitted,
  MIN_SITE_PHOTOS,
  MIN_VIDEO_PHOTOS,
  missingSitePhotos,
  missingVideoPhotos,
  REQUIRE_SURVEY_PHOTOS,
  REQUIRED_PHOTO_LABEL,
  SITE_REQUIRED_CATEGORIES,
  SURVEY_STAGE_META,
  surveyStageOf,
  VIDEO_REQUIRED_CATEGORIES,
} from '@/features/order/surveyStage';
import { AccessEditor, type AccessRow, accessNotes, accessPhotos, initialAccessRows } from '@/features/staff/components/AccessEditor';
import { Block } from '@/features/staff/components/Block';
import { CustomerInfo } from '@/features/staff/components/CustomerInfo';
import { ProposalEditor } from '@/features/staff/components/ProposalEditor';
import { removeBtnCls, SuggestChip, ToggleChip } from '@/features/order/components/surveyUi';
import { pendingSurveysOf, submittedSurveysOf, useCurrentStaffId } from '@/features/staff/session';
import { clearDraft, draftMediaCount, loadDraft, saveDraft } from '@/features/staff/surveyDraft';
import { StickyActionBar } from '@/layouts/MobileShell';
import { todayLocalISO } from '@/services/downtime';
import { formatDateVN, formatTime } from '@/services/format';
import { useApp } from '@/services/store/AppStore';
import type { Order, SitePhoto, SurveyInput, SurveyItem } from '@/types';

const KIND_ICON = { VIDEO_CALL: Video, ON_SITE: Footprints, PHOTOS: Camera, TRUCK_CONFIRM: Truck } as const;

const textareaCls =
  'w-full rounded-xl border border-slate-200 p-3 text-base focus:border-brand focus:ring-4 focus:ring-brand/10 focus:outline-none sm:text-sm';

/** Biểu mẫu kết quả khảo sát của một đơn — người khảo sát ghi nhận & đề xuất, không định giá. */
const ReportForm = ({ order }: { order: Order }) => {
  const { dispatch } = useApp();
  // Khôi phục nháp (nếu có) — chuyển trang không mất, tải lại trang chỉ mất ảnh / video.
  const [saved] = useState(() => loadDraft(order.id));
  const [restored, setRestored] = useState(saved?.restored ?? false);
  const [items, setItems] = useState<SurveyItem[]>(saved?.draft.items ?? []);
  const [note, setNote] = useState(saved?.draft.note ?? '');
  // Quãng đường hệ thống tự tính từ hai địa chỉ — người khảo sát không sửa (trừ khi đơn chưa có số liệu).
  const distanceLocked = (order.referenceQuote?.distanceKm ?? 0) > 0;
  const [p, setP] = useState<SurveyInput>(() => {
    const base = surveyInputFromOrder(order);
    const draft = saved?.draft.proposal;
    return draft ? { ...draft, distanceKm: distanceLocked ? base.distanceKm : draft.distanceKm } : base;
  });
  const [tried, setTried] = useState(false);
  const [savedAt, setSavedAt] = useState<number | null>(saved?.draft.savedAt ?? null);

  const kind = surveyStageOf(order);
  // Tận nơi: đồ đạc, lối đi, chỗ đỗ xe phải có ảnh; video call: ảnh chụp màn hình lối đi & chỗ đỗ xe.
  const needsPhotos = kind === 'ON_SITE' || kind === 'VIDEO_CALL';
  const capture = kind === 'ON_SITE';
  const [access, setAccess] = useState<AccessRow[]>(() => saved?.draft.access ?? initialAccessRows(p, needsPhotos));

  // Tự lưu nháp mỗi khi sửa — so với trạng thái lúc mở form để không tạo nháp khi chỉ mở xem.
  const snapshot = JSON.stringify({ items, note, p, access });
  const baseline = useRef(snapshot);
  useEffect(() => {
    if (snapshot === baseline.current) return;
    const now = Date.now();
    saveDraft(order.id, { items, note, proposal: p, access, savedAt: now });
    setSavedAt(now);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- snapshot đã gói đủ items / note / p / access
  }, [order.id, snapshot]);

  // Ảnh / video chưa gửi chỉ sống trong phiên — cảnh báo trước khi tải lại / đóng trang.
  const mediaCount = draftMediaCount({ items, access });
  useEffect(() => {
    if (mediaCount === 0) return;
    const warn = (e: BeforeUnloadEvent) => e.preventDefault();
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [mediaCount]);

  const discardDraft = () => {
    clearDraft(order.id);
    setItems([]);
    setNote('');
    const base = surveyInputFromOrder(order);
    const rows = initialAccessRows(base, needsPhotos);
    setP(base);
    setAccess(rows);
    setRestored(false);
    setSavedAt(null);
    baseline.current = JSON.stringify({ items: [], note: '', p: base, access: rows });
  };
  const mediaMissing = kind === 'PHOTOS' && !isMediaSubmitted(order);
  const filled = cleanSurveyItems(items);
  const itemsErr = tried && filled.length === 0;
  const vehiclesErr = tried && totalVehicles(p.vehicles) === 0;
  const hoursErr = tried && !(p.estimatedHours > 0);
  const extrasErr = tried && (p.extras ?? []).some((x) => !x.name.trim());
  // Ảnh gắn theo từng món đồ / dòng lối đi — gom lại thành ảnh hiện trường khi gửi.
  const photos: SitePhoto[] = [
    ...filled.flatMap((it) => (it.photos ?? []).map((ph) => ({ ...ph, category: 'BIG' as const, subject: it.name }))),
    ...accessPhotos(access),
  ];
  const photosMissing = kind === 'ON_SITE' ? missingSitePhotos(photos) : kind === 'VIDEO_CALL' ? missingVideoPhotos(photos) : null;
  const requiredCats = kind === 'ON_SITE' ? SITE_REQUIRED_CATEGORIES : kind === 'VIDEO_CALL' ? VIDEO_REQUIRED_CATEGORIES : [];
  const missingCats = requiredCats.filter((c) => !photos.some((x) => x.category === c));
  const minPhotos = kind === 'ON_SITE' ? MIN_SITE_PHOTOS : MIN_VIDEO_PHOTOS;

  const removeItemPhoto = (i: number, id: string) => {
    const it = items[i];
    const ph = it?.photos?.find((x) => x.id === id);
    if (ph) URL.revokeObjectURL(ph.url);
    patchItem(i, { photos: (it?.photos ?? []).filter((x) => x.id !== id) });
  };

  const patchItem = (i: number, patch: Partial<SurveyItem>) => setItems((xs) => xs.map((x, j) => (j === i ? { ...x, ...patch } : x)));
  const removeItem = (i: number) => {
    setItems((xs) => xs.filter((_, j) => j !== i));
    setNoteOpen(null);
  };
  // Chỉ mở ô ghi chú của một món tại một thời điểm; đóng lại thì chỉ còn một dòng tóm tắt.
  const [noteOpen, setNoteOpen] = useState<number | null>(null);
  const closeNote = () => {
    if (noteOpen !== null && !items[noteOpen]?.note?.trim()) patchItem(noteOpen, { note: undefined });
    setNoteOpen(null);
  };
  const toggleNote = (i: number) => {
    if (noteOpen === i) return closeNote();
    closeNote();
    setNoteOpen(i);
  };
  // Bấm món có sẵn: chưa có thì thêm, có rồi thì +1.
  const quickAdd = (c: (typeof COMMON_SURVEY_ITEMS)[number]) =>
    setItems((xs) => {
      const i = xs.findIndex((x) => x.name.trim().toLowerCase() === c.name.toLowerCase());
      if (i >= 0) return xs.map((x, j) => (j === i ? { ...x, qty: x.qty + 1 } : x));
      return [...xs, { ...emptySurveyItem(), name: c.name, fragile: !!c.fragile, disassemble: !!c.disassemble }];
    });

  const submit = () => {
    setTried(true);
    if (filled.length === 0 || totalVehicles(p.vehicles) === 0 || !(p.estimatedHours > 0) || mediaMissing || photosMissing) return;
    if ((p.extras ?? []).some((x) => !x.name.trim())) return;
    const proposal: SurveyInput = { ...p, extras: (p.extras ?? []).map((x) => ({ ...x, name: x.name.trim(), unit: x.unit.trim() || 'gói', note: x.note.trim() })) };
    const fullNote = [note.trim(), ...accessNotes(access)].filter(Boolean).join('\n');
    dispatch({ type: 'SUBMIT_SURVEY_REPORT', orderId: order.id, items: filled, note: fullNote, photos, proposal });
    clearDraft(order.id);
  };

  return (
    <>
      {restored && (
        <Alert tone="info" title="Đã khôi phục nháp khảo sát">
          Phần chữ & số đã nhập được giữ lại.{needsPhotos ? ' Ảnh / video chụp trước khi tải lại trang không giữ được — hãy chụp lại.' : ''}{' '}
          <button type="button" onClick={discardDraft} className="font-semibold underline underline-offset-2">
            Bỏ nháp, nhập lại từ đầu
          </button>
        </Alert>
      )}
      {needsPhotos && (
        <div className={cx('flex flex-wrap items-center gap-x-2 gap-y-1.5 rounded-2xl bg-white px-4 py-3 text-xs ring-1', tried && photosMissing ? 'ring-rose-300' : 'ring-slate-200')}>
          <span className="mr-1 inline-flex items-center gap-1.5 font-semibold text-slate-800">
            <Camera className="size-3.5" /> {kind === 'ON_SITE' ? 'Cần ảnh / video' : 'Cần ảnh chụp màn hình'}
            {!REQUIRE_SURVEY_PHOTOS && <span className="font-normal text-slate-400">(đang tắt bắt buộc)</span>}
          </span>
          {requiredCats.map((c) => {
            const ok = !missingCats.includes(c);
            return (
              <span key={c} className={cx('inline-flex h-6 items-center gap-1 rounded-full px-2 font-medium', ok ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-500')}>
                {ok ? <CheckCircle2 className="size-3.5" /> : <span className="size-2.5 rounded-full border-[1.5px] border-slate-400" />}
                {REQUIRED_PHOTO_LABEL[c]}
              </span>
            );
          })}
          <span className={cx('ml-auto font-semibold tabular-nums', photos.length >= minPhotos ? 'text-emerald-600' : 'text-slate-400')}>
            {photos.length}/{minPhotos} tệp
          </span>
          <span className="w-full text-slate-400">Bấm nút máy ảnh cạnh từng món đồ / vị trí để chụp hoặc tải lên.</span>
        </div>
      )}

      <Block
        title="Danh sách đồ đạc"
        icon={ClipboardList}
        right={filled.length > 0 && <Badge tone="neutral">{surveyItemsTotal(filled)} món</Badge>}
      >
        <p className="mb-1.5 text-xs text-slate-500">Bấm để thêm nhanh, bấm lại để tăng số lượng</p>
        <div className="flex flex-wrap gap-1.5">
          {COMMON_SURVEY_ITEMS.map((c) => (
            <SuggestChip key={c.name} onClick={() => quickAdd(c)}>
              {c.name}
            </SuggestChip>
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
                  <PhotoButton
                    count={it.photos?.length ?? 0}
                    capture={capture}
                    onAdd={(added) => patchItem(i, { photos: [...(it.photos ?? []), ...added] })}
                  />
                  <button
                    type="button"
                    onClick={() => removeItem(i)}
                    aria-label="Xóa món này"
                    className={removeBtnCls}
                  >
                    <Trash2 className="size-4" />
                  </button>
                </div>
                <div className="mt-1 flex gap-1 pl-1">
                  <ToggleChip on={it.fragile} onClick={() => patchItem(i, { fragile: !it.fragile })}>Dễ vỡ</ToggleChip>
                  <ToggleChip on={it.disassemble} onClick={() => patchItem(i, { disassemble: !it.disassemble })}>Tháo lắp</ToggleChip>
                  <ToggleChip on={!!it.note?.trim() || noteOpen === i} onClick={() => toggleNote(i)}>Ghi chú</ToggleChip>
                </div>
                <PhotoStrip photos={it.photos ?? []} onRemove={(id) => removeItemPhoto(i, id)} className="mt-1.5 pl-1" />
                {noteOpen === i ? (
                  <div className="mt-1.5 space-y-1.5 rounded-lg p-2 ring-1 ring-slate-200">
                    <div className="flex items-center gap-1.5">
                      <input
                        value={it.note ?? ''}
                        onChange={(e) => patchItem(i, { note: e.target.value })}
                        onKeyDown={(e) => e.key === 'Enter' && closeNote()}
                        placeholder="VD: tủ 3 cánh cao 2m2, kính cửa tủ đã nứt"
                        autoFocus
                        className="h-9 min-w-0 flex-1 rounded-lg border border-slate-200 bg-white px-2.5 text-base focus:border-brand focus:outline-none sm:text-sm"
                      />
                      <button type="button" onClick={closeNote} className="h-9 shrink-0 rounded-lg bg-brand/10 px-3 text-sm font-semibold text-brand hover:bg-brand/15">
                        Xong
                      </button>
                    </div>
                    <div className="flex flex-wrap gap-1">
                      {COMMON_ITEM_NOTES.filter((n) => !it.note?.includes(n)).map((n) => (
                        <SuggestChip key={n} size="sm" onClick={() => patchItem(i, { note: it.note?.trim() ? `${it.note.trim()}; ${n}` : n })}>
                          {n}
                        </SuggestChip>
                      ))}
                    </div>
                  </div>
                ) : (
                  it.note?.trim() && (
                    <button
                      type="button"
                      onClick={() => setNoteOpen(i)}
                      className="mt-0.5 flex w-full items-center gap-1.5 truncate pl-1 text-left text-xs text-slate-500 hover:text-slate-700"
                    >
                      <Pencil className="size-3 shrink-0" />
                      <span className="truncate">{it.note}</span>
                    </button>
                  )
                )}
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

      <AccessEditor value={p} onChange={setP} rows={access} onRowsChange={setAccess} capture={capture} />

      <ProposalEditor value={p} onChange={setP} distanceLocked={distanceLocked} errors={{ vehicles: vehiclesErr, hours: hoursErr }} />
      {extrasErr && <p className="px-1 text-xs font-medium text-rose-600">Hạng mục đề xuất thêm cần có tên.</p>}

      <StickyActionBar className="md:!sticky md:bottom-4">
        <div className="flex items-center gap-3">
          <div className="min-w-0 flex-1">
            <p className="text-xs text-slate-500">{savedAt ? `Nháp tự lưu lúc ${formatTime(savedAt)}` : 'Đề xuất gửi Điều phối lập báo giá'}</p>
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
const SubmittedSurveys = ({ staffId }: { staffId: string }) => {
  const { state } = useApp();
  const sent = submittedSurveysOf(state.orders, staffId)
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
                  <p className="text-slate-700">{proposalParts(r.proposal).join(' · ')}</p>
                  {r.note && <p className="rounded-lg bg-slate-50 p-2.5 text-slate-600">{r.note}</p>}
                  {photosOutsideItems(r).length > 0 && <SitePhotoGallery photos={photosOutsideItems(r)} />}
                </div>
              </details>
            </li>
          );
        })}
      </ul>
    </Block>
  );
};

/** Thứ tự làm việc: có lịch hẹn trước (sớm → muộn), chưa hẹn / qua ảnh để cuối, cùng nhóm thì phân trước làm trước. */
const apptKey = (o: Order): string => {
  const d = o.bookingDetails;
  return hasAppointment(o) && d ? `0|${d.surveyDate}|${d.surveyContactSlot}` : `1|${String(o.surveyAssignment?.assignedAt ?? 0).padStart(15, '0')}`;
};

/** Danh sách đơn cần khảo sát dạng thẻ — xem nhanh lịch hẹn & địa chỉ, hôm nay nổi lên đầu. */
const OrderPicker = ({ orders, selectedId, onSelect }: { orders: Order[]; selectedId: string; onSelect: (id: string) => void }) => {
  const today = todayLocalISO();
  return (
    <ul className="space-y-2">
      {orders.map((o) => {
        const k = surveyStageOf(o);
        const Icon = KIND_ICON[k];
        const appt = appointmentLabel(o);
        const isToday = o.bookingDetails?.surveyDate === today && hasAppointment(o);
        const selected = o.id === selectedId;
        const hasDraft = loadDraft(o.id) !== null;
        return (
          <li key={o.id}>
            <button
              type="button"
              onClick={() => onSelect(o.id)}
              aria-pressed={selected}
              className={cx(
                'flex w-full items-start gap-3 rounded-xl p-3 text-left ring-1 transition-colors',
                selected ? 'bg-brand/5 ring-brand/40' : 'bg-white ring-slate-200 hover:ring-slate-300',
              )}
            >
              <span className={cx('grid size-9 shrink-0 place-items-center rounded-lg', selected ? 'bg-brand text-white' : 'bg-slate-100 text-slate-500')}>
                <Icon className="size-4" />
              </span>
              <span className="min-w-0 flex-1">
                <span className="flex items-center gap-1.5">
                  <span className="truncate text-sm font-semibold text-slate-900">{o.customerName}</span>
                  <span className="shrink-0 text-xs text-slate-400 tabular-nums">#{o.id}</span>
                </span>
                <span className={cx('mt-0.5 block text-xs tabular-nums', isToday ? 'font-semibold text-brand' : 'text-slate-600')}>
                  {SURVEY_STAGE_META[k].short} · {appt || (k === 'PHOTOS' ? 'khách gửi ảnh, không cần hẹn' : 'chưa hẹn lịch')}
                </span>
                <span className="mt-0.5 block truncate text-xs text-slate-500">{o.fromAddress}</span>
              </span>
              <span className="flex shrink-0 flex-col items-end gap-1">
                {isToday && <Badge tone="brand">Hôm nay</Badge>}
                {hasDraft && <Badge tone="neutral">Nháp</Badge>}
              </span>
            </button>
          </li>
        );
      })}
    </ul>
  );
};

/** Khảo sát: các đơn Điều phối đã phân cho người khảo sát (video call, tận nơi hoặc kiểm tra ảnh khách gửi). */
export const SurveyTab = () => {
  const { state } = useApp();
  const staffId = useCurrentStaffId();
  // Chỉ đơn Điều phối phân cho chính người đang đăng nhập.
  const candidates = [...pendingSurveysOf(state.orders, staffId)].sort((a, b) => apptKey(a).localeCompare(apptKey(b)));
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
        <SubmittedSurveys staffId={staffId} />
      </div>
    );
  }

  const a = target.surveyAssignment;
  const kind = surveyStageOf(target);
  const KindIcon = KIND_ICON[kind];
  const appt = appointmentLabel(target);

  return (
    <div className="space-y-3 pb-24 md:pb-0">
      {candidates.length > 1 && (
        <Block title="Đơn cần khảo sát" icon={ClipboardList} right={<Badge tone="brand">{candidates.length}</Badge>}>
          <OrderPicker orders={candidates} selectedId={target.id} onSelect={setTargetId} />
        </Block>
      )}

      <Block title={`Đơn #${target.id}`} icon={MapPin}>
        <CustomerInfo order={target} />
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

      <SubmittedSurveys staffId={staffId} />
    </div>
  );
};
