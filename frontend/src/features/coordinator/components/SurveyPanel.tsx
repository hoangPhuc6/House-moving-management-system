import { type ReactNode, useMemo, useState } from 'react';
import {
  CalendarClock,
  Camera,
  CheckCircle2,
  Footprints,
  Image as ImageIcon,
  type LucideIcon,
  Phone,
  Sparkles,
  Truck,
  Undo2,
  Video,
} from 'lucide-react';
import { Alert, Badge, Button, cx, Field, Input, NumberStepper, Select } from '@/components/ui';
import { VEHICLE_PRICING } from '@/constants/pricing';
import { surveyInputFromOrder } from '@/features/coordinator/utils';
import {
  appointmentLabel,
  isMediaSubmitted,
  mediaOfCategory,
  SURVEY_MEDIA_CATEGORIES,
  SURVEY_STAGE_META,
  type SurveyStageKind,
  surveyStageOf,
  uncategorizedMedia,
} from '@/features/order/surveyStage';
import { parseCapacityTons } from '@/services/dispatch';
import { formatTime, formatVND } from '@/services/format';
import { buildSurveyQuote, quoteSubtotal } from '@/services/pricing';
import { useApp } from '@/services/store/AppStore';
import type { Order, SurveyInput } from '@/types';

const KIND_ICON: Record<SurveyStageKind, LucideIcon> = {
  TRUCK_CONFIRM: Truck,
  VIDEO_CALL: Video,
  ON_SITE: Footprints,
  PHOTOS: Camera,
};

const telHref = (phone: string) => `tel:${phone.replace(/\s/g, '')}`;

const CallButton = ({ order, children }: { order: Order; children: ReactNode }) => (
  <a
    href={telHref(order.phone)}
    className="inline-flex h-9 items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 text-sm font-semibold text-slate-700 hover:bg-slate-50"
  >
    <Phone className="size-4" /> {children}
  </a>
);

/** Ảnh / video khách gửi, nhóm theo danh mục gợi ý ở trang khách. */
const MediaGallery = ({ order }: { order: Order }) => {
  const groups = [
    ...SURVEY_MEDIA_CATEGORIES.map((c) => ({ label: c.label, files: mediaOfCategory(order, c) })),
    { label: 'Khác', files: uncategorizedMedia(order) },
  ].filter((g) => g.files.length > 0);
  if (groups.length === 0) return null;
  return (
    <div className="space-y-3">
      {groups.map((g) => (
        <div key={g.label}>
          <p className="mb-1.5 text-xs font-semibold text-slate-500">
            {g.label} <span className="tabular-nums">({g.files.length})</span>
          </p>
          <ul className="grid grid-cols-4 gap-2 sm:grid-cols-6">
            {g.files.map((name, i) => (
              <li
                key={`${name}-${i}`}
                className="flex aspect-square flex-col items-center justify-center gap-1 rounded-xl bg-slate-100 p-1.5 text-slate-400"
              >
                <ImageIcon className="size-5" />
                <span className="w-full truncate text-center text-[10px] font-medium">{name}</span>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </div>
  );
};

/** Thông tin & việc cần làm tương ứng hình thức khảo sát khách đã chọn. */
const KindContext = ({ order, kind }: { order: Order; kind: SurveyStageKind }) => {
  const d = order.bookingDetails;
  const appt = appointmentLabel(order);
  const mediaCount = order.customerPhotos.length;

  switch (kind) {
    case 'TRUCK_CONFIRM': {
      const vehicle = order.requestedServices.find((s) => parseCapacityTons(s) !== null);
      return (
        <Alert tone="info" title="Khách chỉ thuê xe — không cần khảo sát" action={<CallButton order={order}>Gọi xác nhận</CallButton>}>
          Gọi khách xác nhận loại xe và số người bốc vác, rồi gửi báo giá.
          <span className="mt-1 block">
            Khách chọn: <b>{vehicle ?? 'chưa chọn xe'}</b> ·{' '}
            <b>{d && d.laborCount > 0 ? `${d.laborCount} người bốc vác` : 'không thuê bốc vác'}</b>
          </span>
        </Alert>
      );
    }
    case 'VIDEO_CALL':
      return (
        <div className="space-y-3">
          <Alert
            tone={appt ? 'info' : 'warning'}
            icon={CalendarClock}
            title={appt ? `Lịch video call: ${appt}` : 'Khách chưa hẹn giờ video call'}
            action={<CallButton order={order}>{appt ? 'Gọi khách' : 'Gọi hẹn giờ'}</CallButton>}
          >
            Gọi Zalo / Messenger qua số <b className="tabular-nums">{order.phone}</b> để xem đồ đạc và lối đi, sau đó điều chỉnh báo giá bên dưới.
          </Alert>
          {mediaCount > 0 && <MediaGallery order={order} />}
        </div>
      );
    case 'ON_SITE':
      return (
        <Alert
          tone="warning"
          icon={Footprints}
          title={appt ? `Khách hẹn khảo sát tận nơi: ${appt}` : 'Khách muốn khảo sát tận nơi'}
          action={<CallButton order={order}>Gọi khách</CallButton>}
        >
          Cử một nhân viên vận chuyển đến khảo sát. Nhân viên đó sẽ lập và gửi báo giá cho khách.
        </Alert>
      );
    case 'PHOTOS':
      return (
        <div className="space-y-3">
          {isMediaSubmitted(order) ? (
            <Alert tone="success" title={`Khách đã gửi ${mediaCount} ảnh / video`}>
              {order.surveyMediaSubmittedAt ? <>Gửi lúc <span className="tabular-nums">{formatTime(order.surveyMediaSubmittedAt)}</span>. </> : null}
              Lập báo giá dựa trên ảnh bên dưới.
            </Alert>
          ) : (
            <Alert tone="warning" title="Khách chưa gửi ảnh / video" action={<CallButton order={order}>Nhắc khách</CallButton>}>
              {mediaCount > 0 ? `Khách đã tải ${mediaCount} tệp nhưng chưa bấm gửi.` : 'Khách chưa tải tệp nào.'} Chỉ gửi được báo giá sau khi khách gửi ảnh.
            </Alert>
          )}
          <MediaGallery order={order} />
        </div>
      );
  }
};

/** Biểu mẫu lập báo giá — điền sẵn theo thông tin khách khai báo. */
const QuoteForm = ({ order, truckOnly, blocked }: { order: Order; truckOnly: boolean; blocked: string | null }) => {
  const { dispatch } = useApp();
  const initial = useMemo(() => surveyInputFromOrder(order), [order.id]);
  const [s, setS] = useState<SurveyInput>(initial);
  const set = <K extends keyof SurveyInput>(k: K, v: SurveyInput[K]) => setS((x) => ({ ...x, [k]: v }));
  const items = useMemo(() => buildSurveyQuote(truckOnly ? { ...s, cartons: 0, tapeRolls: 0, peFilmRolls: 0 } : s), [s, truckOnly]);
  const maxLabor = VEHICLE_PRICING[s.vehicleCapacity]?.maxLabor;

  const stepper = (label: string, k: Exclude<keyof SurveyInput, 'vehicleCapacity'>, opts: { min?: number; max: number; step?: number; suffix?: string }, hint?: string) => (
    <Field label={label} hint={hint}>
      <NumberStepper value={s[k]} onChange={(v) => set(k, v)} {...opts} />
    </Field>
  );

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-2">
        <p className="text-sm font-semibold text-slate-900">Báo giá chính thức</p>
        <span className="flex items-center gap-1.5 text-xs text-slate-500">
          <Sparkles className="size-3.5" /> Đã điền sẵn theo thông tin khách khai báo
          <button type="button" className="font-semibold text-brand hover:underline" onClick={() => setS(initial)}>
            Đặt lại
          </button>
        </span>
      </div>
      <div className="grid gap-4 sm:grid-cols-3">
        {stepper('Quãng đường', 'distanceKm', { min: 1, max: 200, suffix: 'km' })}
        <Field label="Loại xe">
          <Select value={s.vehicleCapacity} onChange={(e) => set('vehicleCapacity', Number(e.target.value))} className="h-11">
            {Object.keys(VEHICLE_PRICING)
              .map(Number)
              .sort((a, b) => a - b)
              .map((cap) => (
                <option key={cap} value={cap}>
                  {VEHICLE_PRICING[cap]?.label}
                </option>
              ))}
          </Select>
        </Field>
        {stepper('Số lượng xe', 'vehicleQty', { min: 1, max: 5 })}
        {stepper(
          truckOnly ? 'Bốc vác thuê thêm' : 'Nhân viên bốc xếp',
          'workers',
          { min: 0, max: 12, suffix: 'người' },
          truckOnly && maxLabor !== undefined && s.workers > maxLabor * s.vehicleQty ? `Vượt số chỗ trên xe (tối đa ${maxLabor}/xe)` : undefined,
        )}
        {!truckOnly && stepper('Thùng carton', 'cartons', { max: 200, step: 5 })}
        {!truckOnly && stepper('Băng keo', 'tapeRolls', { max: 50, suffix: 'cuộn' })}
        {!truckOnly && stepper('Màng PE', 'peFilmRolls', { max: 20, suffix: 'cuộn' })}
        {stepper('Số lầu (thang bộ)', 'floors', { max: 20 })}
        {stepper('Độ sâu hẻm', 'alleyMeters', { max: 500, step: 50, suffix: 'm' })}
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-slate-50 p-4">
        <div>
          <p className="text-xs text-slate-500">Báo giá tự động · {items.length} hạng mục</p>
          <p className="text-xl font-bold tabular-nums">{formatVND(quoteSubtotal(items))}</p>
          {order.referenceQuote && (
            <p className="text-xs text-slate-500">
              Báo giá tham khảo khách đã xem: <span className="tabular-nums">{formatVND(order.referenceQuote.financialSummary.totalEstimated)}</span>
            </p>
          )}
        </div>
        <div className="flex flex-col items-end gap-1">
          <Button
            icon={CheckCircle2}
            disabled={Boolean(blocked)}
            onClick={() => dispatch({ type: 'SUBMIT_SURVEY', orderId: order.id, items, by: 'COORDINATOR' })}
          >
            Gửi báo giá cho khách
          </Button>
          {blocked && <span className="text-xs text-slate-500">{blocked}</span>}
        </div>
      </div>
    </div>
  );
};

/** Cử nhân viên vận chuyển khảo sát trực tiếp. */
const DispatchForm = ({ order, primary }: { order: Order; primary: boolean }) => {
  const { state, dispatch } = useApp();
  const appt = appointmentLabel(order);
  const [note, setNote] = useState(primary ? (appt ? `Khách hẹn ${appt}` : 'Khách chọn khảo sát tận nơi') : '');
  const [surveyorId, setSurveyorId] = useState('');
  const [submitted, setSubmitted] = useState(false);

  return (
    <div className={cx('flex flex-col gap-3 sm:flex-row sm:items-end', !primary && 'border-t border-slate-100 pt-5')}>
      <Field label={primary ? 'Ghi chú cho nhân viên khảo sát' : 'Ảnh / cuộc gọi chưa đủ căn cứ? Lý do cần khảo sát trực tiếp'} className="flex-1">
        <Input value={note} onChange={(e) => setNote(e.target.value)} placeholder="VD: Nhiều đồ cồng kềnh cần tháo lắp" />
      </Field>
      <Field label="Nhân viên khảo sát *" error={submitted && !surveyorId ? 'Bắt buộc chọn nhân viên khảo sát' : null} className="sm:w-60">
        <Select value={surveyorId} onChange={(e) => setSurveyorId(e.target.value)} className={cx(submitted && !surveyorId && 'border-rose-400')}>
          <option value="">— Chọn nhân viên —</option>
          {state.resources.staff
            .filter((s) => s.isAvailable)
            .map((s) => (
              <option key={s.id} value={s.id}>
                {s.name} ({s.code})
              </option>
            ))}
        </Select>
      </Field>
      <Button
        variant={primary ? 'primary' : 'secondary'}
        icon={Footprints}
        onClick={() => {
          setSubmitted(true);
          if (surveyorId) dispatch({ type: 'REQUEST_ONSITE_SURVEY', orderId: order.id, note, surveyorId });
        }}
      >
        Cử nhân viên khảo sát
      </Button>
    </div>
  );
};

/**
 * Bảng khảo sát của Điều phối, đổi theo hình thức khách chọn khi đặt đơn:
 * thuê xe → gọi xác nhận; video call → gọi theo lịch hẹn; gửi ảnh → duyệt ảnh;
 * tận nơi → cử nhân viên vận chuyển (chỉ nhân viên đó gửi báo giá).
 */
export const SurveyPanel = ({ order }: { order: Order }) => {
  const { state, dispatch } = useApp();
  const kind = surveyStageOf(order);
  const Icon = KIND_ICON[kind];
  const req = order.onSiteSurveyRequest;
  const appt = appointmentLabel(order);

  const header = (
    <div className="flex items-center justify-between gap-2">
      <p className="flex items-center gap-2 text-sm font-semibold text-slate-900">
        <Icon className="size-4 text-brand" /> {SURVEY_STAGE_META[kind].label}
      </p>
      {order.bookingDetails?.serviceMode !== 'TRUCK_ONLY' && order.bookingDetails?.homeScale && <Badge tone="neutral">{order.bookingDetails.homeScale}</Badge>}
    </div>
  );

  if (req) {
    return (
      <div className="space-y-4 p-6">
        {header}
        <div className="rounded-xl border border-amber-200 bg-amber-50/60 p-4 text-sm text-slate-600">
          <p>
            Đã cử <b>{state.resources.staff.find((x) => x.id === req.surveyorId)?.name ?? 'nhân viên'}</b> lúc{' '}
            <span className="tabular-nums">{formatTime(req.requestedAt)}</span>
            {req.note && <> — {req.note}</>}.
          </p>
          {appt && <p className="mt-1">Lịch khách hẹn: <b>{appt}</b></p>}
          <p className="mt-1">Đang chờ nhân viên khảo sát và gửi báo giá.</p>
          <Button className="mt-3" size="sm" variant="secondary" icon={Undo2} onClick={() => dispatch({ type: 'CANCEL_ONSITE_SURVEY', orderId: order.id })}>
            Hủy yêu cầu khảo sát trực tiếp
          </Button>
        </div>
      </div>
    );
  }

  const blocked = kind === 'PHOTOS' && !isMediaSubmitted(order) ? 'Chờ khách gửi ảnh / video' : null;

  return (
    <div className="space-y-5 p-6">
      {header}
      <KindContext order={order} kind={kind} />
      {kind === 'ON_SITE' ? (
        <DispatchForm order={order} primary />
      ) : (
        <>
          <QuoteForm order={order} truckOnly={kind === 'TRUCK_CONFIRM'} blocked={blocked} />
          {kind !== 'TRUCK_CONFIRM' && <DispatchForm order={order} primary={false} />}
        </>
      )}
    </div>
  );
};
