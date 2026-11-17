import { type ReactNode, useEffect, useState } from 'react';
import { Camera, ClipboardList, Footprints, MapPin, Navigation, Phone, Send, Truck, Video } from 'lucide-react';
import { Alert, Badge, Button, cx, EmptyState, NumberStepper, Select } from '@/components/ui';
import { VEHICLE_PRICING } from '@/constants/pricing';
import { surveyInputFromOrder } from '@/features/coordinator/utils';
import { SurveyMediaGallery } from '@/features/order/components/SurveyMediaGallery';
import { appointmentLabel, isMediaSubmitted, SURVEY_STAGE_META, surveyStageOf } from '@/features/order/surveyStage';
import { Block } from '@/features/staff/components/Block';
import { CustomerInfo } from '@/features/staff/components/CustomerInfo';
import { StickyActionBar } from '@/layouts/MobileShell';
import { formatDateVN, formatTime } from '@/services/format';
import { useApp } from '@/services/store/AppStore';
import type { Order, SurveyInput } from '@/types';

const KIND_ICON = { VIDEO_CALL: Video, ON_SITE: Footprints, PHOTOS: Camera, TRUCK_CONFIRM: Truck } as const;

const textareaCls =
  'w-full rounded-xl border border-slate-200 p-3 text-base focus:border-brand focus:ring-4 focus:ring-brand/10 focus:outline-none sm:text-sm';

/** Biểu mẫu kết quả khảo sát của một đơn — người khảo sát ghi nhận & đề xuất, không định giá. */
const ReportForm = ({ order }: { order: Order }) => {
  const { dispatch } = useApp();
  const [itemsNote, setItemsNote] = useState('');
  const [note, setNote] = useState('');
  const [p, setP] = useState<SurveyInput>(() => surveyInputFromOrder(order));
  const [tried, setTried] = useState(false);
  const set = <K extends keyof SurveyInput>(k: K, v: SurveyInput[K]) => setP((x) => ({ ...x, [k]: v }));

  const kind = surveyStageOf(order);
  const mediaMissing = kind === 'PHOTOS' && !isMediaSubmitted(order);
  const itemsErr = tried && !itemsNote.trim();

  const row = (label: string, node: ReactNode) => (
    <div className="flex items-center justify-between gap-3 py-2.5">
      <span className="text-sm font-medium text-slate-700">{label}</span>
      <div className="w-40 shrink-0">{node}</div>
    </div>
  );

  const submit = () => {
    setTried(true);
    if (!itemsNote.trim() || mediaMissing) return;
    dispatch({ type: 'SUBMIT_SURVEY_REPORT', orderId: order.id, itemsNote, note, proposal: p });
  };

  return (
    <>
      <Block title="Danh sách đồ đạc" icon={ClipboardList}>
        <textarea
          value={itemsNote}
          onChange={(e) => setItemsNote(e.target.value)}
          rows={5}
          placeholder={'VD:\n- Tủ lạnh 2 cánh ×1\n- Giường 1m8 (cần tháo) ×1\n- Thùng đồ ~15 thùng'}
          className={cx(textareaCls, itemsErr && 'border-rose-400')}
        />
        {itemsErr && <p className="mt-1 text-xs font-medium text-rose-600">Nhập danh sách đồ đạc đã khảo sát</p>}
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
                      p.vehicleCapacity === cap ? 'border-slate-900 bg-slate-900 text-white' : 'border-slate-200 text-slate-600',
                    )}
                  >
                    {String(cap).replace('.', ',')} tấn
                  </button>
                ))}
            </div>
          </div>
          {row('Số lượng xe', <NumberStepper value={p.vehicleQty} onChange={(v) => set('vehicleQty', v)} min={1} max={5} />)}
          {row('Nhân viên bốc xếp', <NumberStepper value={p.workers} onChange={(v) => set('workers', v)} min={0} max={12} suffix="người" />)}
          {row('Thùng carton', <NumberStepper value={p.cartons} onChange={(v) => set('cartons', v)} max={200} step={5} />)}
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
              {p.vehicleQty} × xe {String(p.vehicleCapacity).replace('.', ',')} tấn · {p.workers} người · {p.distanceKm} km
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
      <EmptyState icon={Footprints} title="Chưa có đơn cần khảo sát">
        Đơn được Điều phối phân cho bạn sẽ hiện ở đây.
      </EmptyState>
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
    </div>
  );
};
