import { type ReactNode, useState } from 'react';
import {
  CalendarClock,
  Camera,
  ClipboardList,
  Footprints,
  type LucideIcon,
  Phone,
  Truck,
  Undo2,
  UserCheck,
  Video,
} from 'lucide-react';
import { Alert, Button, cx, Field, Input, Select } from '@/components/ui';
import { QuoteForm } from '@/features/coordinator/components/QuoteForm';
import { SitePhotoGallery } from '@/features/order/components/SitePhotoGallery';
import { proposalParts } from '@/features/order/proposal';
import { SurveyMediaGallery } from '@/features/order/components/SurveyMediaGallery';
import { SurveyItemList } from '@/features/order/components/SurveyItemList';
import { photosOutsideItems, surveyItemsTotal } from '@/features/order/surveyItems';
import {
  appointmentLabel,
  isMediaSubmitted,
  SURVEY_STAGE_META,
  type SurveyStageKind,
  surveyPhaseOf,
  surveyStageOf,
} from '@/features/order/surveyStage';
import { parseCapacityTons } from '@/services/dispatch';
import { downtimeLabel, downtimeOn, todayLocalISO } from '@/services/downtime';
import { formatTime } from '@/services/format';
import { isActiveStaff, WORK_STATUS_META } from '@/services/staff';
import { useApp } from '@/services/store/AppStore';
import type { Order } from '@/types';

const KIND_ICON: Record<SurveyStageKind, LucideIcon> = {
  TRUCK_CONFIRM: Truck,
  VIDEO_CALL: Video,
  ON_SITE: Footprints,
  PHOTOS: Camera,
};

const PHASE_STEPS = ['Phân người khảo sát', 'Đang khảo sát', 'Lập báo giá'] as const;

const telHref = (phone: string) => `tel:${phone.replace(/\s/g, '')}`;

const CallButton = ({ order, children }: { order: Order; children: ReactNode }) => (
  <a
    href={telHref(order.phone)}
    className="inline-flex h-9 items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 text-sm font-semibold text-slate-700 hover:bg-slate-50"
  >
    <Phone className="size-4" /> {children}
  </a>
);

/** Thanh 3 bước của giai đoạn khảo sát — Điều phối luôn biết đơn đang ở đâu. */
const PhaseSteps = ({ current }: { current: number }) => (
  <ol className="grid grid-cols-3 gap-1.5">
    {PHASE_STEPS.map((label, i) => (
      <li key={label}>
        <div className={cx('h-1.5 rounded-full', i < current ? 'bg-brand' : i === current ? 'bg-brand/50' : 'bg-slate-200')} />
        <p className={cx('mt-1.5 text-[11px] font-semibold', i === current ? 'text-slate-900' : 'text-slate-400')}>
          {i + 1}. {label}
        </p>
      </li>
    ))}
  </ol>
);

/** Yêu cầu của khách về hình thức khảo sát — bối cảnh để phân người phù hợp. */
const KindContext = ({ order, kind }: { order: Order; kind: SurveyStageKind }) => {
  const appt = appointmentLabel(order);
  const mediaCount = order.customerPhotos.length;
  switch (kind) {
    case 'VIDEO_CALL':
      return (
        <Alert tone={appt ? 'info' : 'warning'} icon={CalendarClock} title={appt ? `Khách hẹn video call: ${appt}` : 'Khách chưa hẹn giờ video call'} action={<CallButton order={order}>Gọi khách</CallButton>}>
          Người khảo sát gọi Zalo / Messenger qua số <b className="tabular-nums">{order.phone}</b> đúng lịch hẹn.
        </Alert>
      );
    case 'ON_SITE':
      return (
        <Alert tone="info" icon={Footprints} title={appt ? `Khách hẹn khảo sát tận nơi: ${appt}` : 'Khách muốn khảo sát tận nơi'} action={<CallButton order={order}>Gọi khách</CallButton>}>
          Người khảo sát đến <b>{order.fromAddress}</b>.
        </Alert>
      );
    case 'PHOTOS':
      return isMediaSubmitted(order) ? (
        <Alert tone="success" icon={Camera} title={`Khách đã gửi ${mediaCount} ảnh / video`}>
          {order.surveyMediaSubmittedAt ? <>Gửi lúc <span className="tabular-nums">{formatTime(order.surveyMediaSubmittedAt)}</span>. </> : null}
          Người khảo sát kiểm tra ảnh và gửi kết quả.
        </Alert>
      ) : (
        <Alert tone="warning" icon={Camera} title="Khách chưa gửi ảnh / video" action={<CallButton order={order}>Nhắc khách</CallButton>}>
          {mediaCount > 0 ? `Khách đã tải ${mediaCount} tệp nhưng chưa bấm gửi.` : 'Khách chưa tải tệp nào.'} Vẫn có thể phân người khảo sát trước.
        </Alert>
      );
    default:
      return null;
  }
};

/** Bước 1 — phân (hoặc đổi) người khảo sát. */
const AssignForm = ({ order, onDone }: { order: Order; onDone?: () => void }) => {
  const { state, dispatch } = useApp();
  const appt = appointmentLabel(order);
  const current = order.surveyAssignment;
  const [note, setNote] = useState(current?.note ?? (appt ? `Khách hẹn ${appt}` : ''));
  const [surveyorId, setSurveyorId] = useState(current?.surveyorId ?? '');
  const [tried, setTried] = useState(false);

  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
      <Field label="Người khảo sát *" error={tried && !surveyorId ? 'Chọn người khảo sát' : null} className="sm:w-64">
        <Select value={surveyorId} onChange={(e) => setSurveyorId(e.target.value)} className={cx(tried && !surveyorId && 'border-rose-400')}>
          <option value="">— Chọn nhân viên —</option>
          {state.resources.staff
            .filter((s) => s.status !== 'RESIGNED')
            .map((s) => {
              // Ảnh khách gửi không có ngày hẹn → xét theo hôm nay.
              const off = downtimeOn(state.resources.downtimes, 'STAFF', s.id, order.bookingDetails?.surveyDate || todayLocalISO());
              const inactive = !isActiveStaff(s);
              return (
                <option key={s.id} value={s.id} disabled={!!off || inactive}>
                  {s.name} ({s.code}){off ? ` — ${downtimeLabel(off)}` : inactive ? ` — ${WORK_STATUS_META[s.status].label.toLowerCase()}` : ''}
                </option>
              );
            })}
        </Select>
      </Field>
      <Field label="Ghi chú cho người khảo sát" className="flex-1">
        <Input value={note} onChange={(e) => setNote(e.target.value)} placeholder="VD: Nhiều đồ cồng kềnh, chú ý tủ gỗ lớn cần tháo" />
      </Field>
      <Button
        icon={UserCheck}
        onClick={() => {
          setTried(true);
          if (!surveyorId) return;
          dispatch({ type: 'ASSIGN_SURVEYOR', orderId: order.id, note, surveyorId });
          onDone?.();
        }}
      >
        {current ? 'Lưu thay đổi' : 'Phân người khảo sát'}
      </Button>
    </div>
  );
};

/** Bước 2 — đã phân người, chờ kết quả. */
const SurveyingStatus = ({ order, kind }: { order: Order; kind: SurveyStageKind }) => {
  const { state, dispatch } = useApp();
  const [editing, setEditing] = useState(false);
  const a = order.surveyAssignment!;
  const surveyor = state.resources.staff.find((x) => x.id === a.surveyorId);
  const waitingMedia = kind === 'PHOTOS' && !isMediaSubmitted(order);

  return (
    <div className="space-y-3 rounded-xl border border-blue-200 bg-blue-50/50 p-4 text-sm text-slate-700">
      <p>
        <b>{surveyor?.name ?? 'Nhân viên'}</b> {surveyor && <span className="text-slate-500">({surveyor.code})</span>} được phân lúc{' '}
        <span className="tabular-nums">{formatTime(a.assignedAt)}</span>
        {a.note && <> — {a.note}</>}.
      </p>
      <p className="font-medium text-blue-900">
        {waitingMedia ? 'Đang chờ khách gửi ảnh / video để người khảo sát kiểm tra.' : 'Đang chờ người khảo sát gửi kết quả (đồ đạc, ghi chú, đề xuất xe & nhân sự).'}
      </p>
      {editing ? (
        <div className="rounded-xl bg-white p-3">
          <AssignForm order={order} onDone={() => setEditing(false)} />
        </div>
      ) : (
        <div className="flex flex-wrap gap-2">
          <Button size="sm" variant="secondary" icon={UserCheck} onClick={() => setEditing(true)}>
            Đổi người khảo sát
          </Button>
          <Button size="sm" variant="ghost" icon={Undo2} onClick={() => dispatch({ type: 'UNASSIGN_SURVEYOR', orderId: order.id })}>
            Hủy phân công
          </Button>
        </div>
      )}
    </div>
  );
};

/** Kết quả người khảo sát gửi về — căn cứ lập báo giá. */
export const ReportSummary = ({ order }: { order: Order }) => {
  const { state } = useApp();
  const r = order.surveyReport!;
  const p = r.proposal;
  const surveyor = state.resources.staff.find((x) => x.id === r.surveyorId);
  const proposal = proposalParts(p);

  return (
    <div className="space-y-3 rounded-xl border border-emerald-200 bg-emerald-50/40 p-4 text-sm">
      <p className="flex flex-wrap items-center gap-2 font-semibold text-emerald-900">
        <ClipboardList className="size-4" /> Kết quả khảo sát
        <span className="font-normal text-emerald-800/80">
          · {surveyor?.name ?? 'Người khảo sát'} gửi lúc <span className="tabular-nums">{formatTime(r.submittedAt)}</span>
        </span>
      </p>
      <div className="grid gap-3 md:grid-cols-2">
        <div>
          <p className="mb-1 text-xs font-semibold text-slate-500">Danh sách đồ đạc · {surveyItemsTotal(r.items)} món</p>
          <SurveyItemList items={r.items} />
        </div>
        <div>
          <p className="mb-1 text-xs font-semibold text-slate-500">Ghi chú hiện trường</p>
          <p className="whitespace-pre-line text-slate-800">{r.note || '—'}</p>
        </div>
      </div>
      {photosOutsideItems(r).length > 0 && (
        <div>
          <p className="mb-1 text-xs font-semibold text-slate-500">Lối đi & chỗ đỗ xe · {photosOutsideItems(r).length} ảnh / video</p>
          <SitePhotoGallery photos={photosOutsideItems(r)} />
        </div>
      )}
      <div>
        <p className="mb-1 text-xs font-semibold text-slate-500">Đề xuất nguồn lực</p>
        <p className="text-slate-800">{proposal.join(' · ')}</p>
      </div>
    </div>
  );
};

/**
 * Bảng khảo sát của Điều phối cho đơn đang chờ khảo sát:
 * 1. Phân người khảo sát (cả video call, tận nơi, khách gửi ảnh) → 2. chờ người khảo sát gửi kết quả
 * → 3. lập báo giá từ kết quả khảo sát. Gói chỉ thuê xe: gọi xác nhận rồi lập báo giá ngay.
 */
export const SurveyPanel = ({ order }: { order: Order }) => {
  const kind = surveyStageOf(order);
  const phase = surveyPhaseOf(order);
  const Icon = KIND_ICON[kind];
  const d = order.bookingDetails;

  const header = (
    <p className="flex items-center gap-2 text-sm font-semibold text-slate-900">
      <Icon className="size-4 text-brand" /> {SURVEY_STAGE_META[kind].label}
    </p>
  );

  if (kind === 'TRUCK_CONFIRM') {
    const vehicle = order.requestedServices.find((x) => parseCapacityTons(x) !== null);
    return (
      <div className="space-y-5 p-6">
        {header}
        <Alert tone="info" title="Khách chỉ thuê xe — không cần khảo sát" action={<CallButton order={order}>Gọi xác nhận</CallButton>}>
          Gọi khách xác nhận loại xe và số người bốc vác, rồi gửi báo giá.
          <span className="mt-1 block">
            Khách chọn: <b>{vehicle ?? 'chưa chọn xe'}</b> · <b>{d && d.laborCount > 0 ? `${d.laborCount} người bốc vác` : 'không thuê bốc vác'}</b>
          </span>
        </Alert>
        <QuoteForm order={order} kind={kind} blocked={null} />
      </div>
    );
  }

  return (
    <div className="space-y-5 p-6">
      {header}
      <PhaseSteps current={phase === 'ASSIGN' ? 0 : phase === 'SURVEYING' ? 1 : 2} />
      {phase !== 'QUOTE' && <KindContext order={order} kind={kind} />}
      {phase === 'ASSIGN' && <AssignForm order={order} />}
      {phase === 'SURVEYING' && <SurveyingStatus order={order} kind={kind} />}
      {phase === 'QUOTE' && (
        <>
          <ReportSummary order={order} />
          {kind === 'PHOTOS' && order.customerPhotos.length > 0 && (
            <details className="rounded-xl border border-slate-200 p-3">
              <summary className="cursor-pointer text-sm font-semibold text-slate-700">Ảnh / video khách gửi ({order.customerPhotos.length})</summary>
              <div className="mt-3">
                <SurveyMediaGallery order={order} />
              </div>
            </details>
          )}
          <QuoteForm order={order} kind={kind} blocked={null} proposal={order.surveyReport?.proposal ?? null} />
        </>
      )}
    </div>
  );
};
