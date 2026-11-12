import { useState } from 'react';
import { CalendarClock, CheckCircle2, ClipboardCheck, CloudUpload, Headphones, ImagePlus, Phone, Send, Truck, UserCheck, Users } from 'lucide-react';
import { Badge, Button, Card, Field, Input, Modal, cx } from '@/components/ui';
import { SURVEY_PREFERENCES, SURVEY_TIME_SLOTS, needsSurveyAppointment } from '@/features/customer/booking/constants';
import { todayISO } from '@/features/customer/booking/useBookingWizard';
import {
  SURVEY_MEDIA_CATEGORIES,
  type SurveyMediaCategory,
  hasAppointment,
  isMediaSubmitted,
  mediaOfCategory,
  mediaPrefix,
  surveyStageOf,
  uncategorizedMedia,
  weekdayOf,
} from '@/features/customer/surveyStage';
import { parseCapacityTons } from '@/services/dispatch';
import { formatDateVN, formatTime } from '@/services/format';
import { useApp } from '@/services/store/AppStore';
import type { Order, SurveyPreference } from '@/types';

const HOTLINE_TEL = 'tel:19006868';

const PREP_TIPS: Record<'VIDEO_CALL' | 'ON_SITE', string[]> = {
  VIDEO_CALL: ['Sạc đầy điện thoại, bật Zalo / Messenger theo số đã đăng ký', 'Mở sẵn các phòng, tủ lớn cần chuyển', 'Chuẩn bị quay hành lang, thang máy / thang bộ và cổng hẻm'],
  ON_SITE: ['Có người lớn ở nhà trong khung giờ hẹn (~30 phút)', 'Mở sẵn các phòng, tủ lớn cần chuyển', 'Báo trước với bảo vệ / ban quản lý nếu ở chung cư'],
};

const prevDay = (iso: string): string => {
  const [y, m, d] = iso.split('-').map(Number);
  if (!y || !m || !d) return '';
  const dt = new Date(y, m - 1, d - 1);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${dt.getFullYear()}-${p(dt.getMonth() + 1)}-${p(dt.getDate())}`;
};

/** Tải ảnh / video theo danh mục gợi ý. Bản demo chỉ lưu tên tệp vào order.customerPhotos. */
const MediaUploader = ({ order, compact = false }: { order: Order; compact?: boolean }) => {
  const { dispatch } = useApp();
  const add = (c: SurveyMediaCategory, files: FileList | null) => {
    const names = Array.from(files ?? [])
      .filter((f) => f.type.startsWith('image/') || f.type.startsWith('video/'))
      .map((f) => mediaPrefix(c) + f.name);
    if (names.length) dispatch({ type: 'CUSTOMER_ADD_SURVEY_MEDIA', orderId: order.id, names });
  };

  const earlier = uncategorizedMedia(order);
  return (
    <ul className="space-y-2">
      {earlier.length > 0 && (
        <li className="rounded-xl border border-slate-200 bg-slate-50/60 p-3">
          <p className="text-xs font-semibold text-slate-500">Đã gửi kèm khi đặt đơn ({earlier.length})</p>
          <div className="mt-1.5 flex flex-wrap gap-1.5">
            {earlier.map((n, i) => (
              <span key={`${n}-${i}`} className="max-w-[180px] truncate rounded-full border border-slate-200 bg-white px-2 py-0.5 text-[11px] text-slate-600">
                {n}
              </span>
            ))}
          </div>
        </li>
      )}
      {SURVEY_MEDIA_CATEGORIES.map((c) => {
        const files = mediaOfCategory(order, c);
        const done = files.length > 0;
        return (
          <li key={c.key} className={cx('rounded-xl border p-3 transition-colors', done ? 'border-emerald-200 bg-emerald-50/40' : 'border-slate-200')}>
            <div className="flex items-center gap-3">
              <span className={cx('grid size-8 shrink-0 place-items-center rounded-lg', done ? 'bg-emerald-500 text-white' : 'bg-slate-100 text-slate-400')}>
                {done ? <CheckCircle2 className="size-4" /> : <ImagePlus className="size-4" />}
              </span>
              <div className="min-w-0 flex-1">
                <p className="text-sm font-semibold text-slate-900">{c.label}</p>
                {!compact && <p className="text-xs text-slate-500">{c.hint}</p>}
              </div>
              {done && <Badge tone="success">{files.length} tệp</Badge>}
              <label className="inline-flex h-9 cursor-pointer items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3 text-xs font-semibold text-slate-700 hover:border-brand/50 hover:text-brand">
                <CloudUpload className="size-4" /> Thêm
                <input
                  type="file"
                  multiple
                  accept="image/*,video/*"
                  className="sr-only"
                  onChange={(e) => {
                    add(c, e.target.files);
                    e.target.value = '';
                  }}
                />
              </label>
            </div>
            {done && (
              <div className="mt-2 flex flex-wrap gap-1.5 pl-11">
                {files.map((n, i) => (
                  <span key={`${n}-${i}`} className="max-w-[180px] truncate rounded-full border border-slate-200 bg-white px-2 py-0.5 text-[11px] text-slate-600">
                    {n}
                  </span>
                ))}
              </div>
            )}
          </li>
        );
      })}
    </ul>
  );
};

/** Đổi lịch hẹn hoặc đổi hình thức khảo sát. */
const SurveyScheduleModal = ({ order, open, onClose, initial }: { order: Order; open: boolean; onClose: () => void; initial?: SurveyPreference }) => {
  const { dispatch } = useApp();
  const d = order.bookingDetails;
  const [pref, setPref] = useState<SurveyPreference>(initial ?? d?.surveyPreference ?? 'VIDEO_CALL');
  const [date, setDate] = useState(d?.surveyDate || todayISO());
  const [slot, setSlot] = useState(d?.surveyContactSlot || SURVEY_TIME_SLOTS[1]!);
  const [tried, setTried] = useState(false);
  const appointment = needsSurveyAppointment(pref);
  const dateError = !appointment
    ? null
    : !date
      ? 'Chọn ngày khảo sát'
      : date < todayISO()
        ? 'Ngày khảo sát không được ở quá khứ'
        : date >= order.scheduledDate
          ? 'Ngày khảo sát phải trước ngày chuyển nhà'
          : null;

  const save = () => {
    setTried(true);
    if (dateError) return;
    dispatch({ type: 'CUSTOMER_UPDATE_SURVEY', orderId: order.id, preference: pref, date, slot });
    onClose();
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Đổi lịch / hình thức khảo sát"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Hủy
          </Button>
          <Button onClick={save}>Lưu thay đổi</Button>
        </>
      }
    >
      <div className="space-y-4">
        <div className="grid gap-2 sm:grid-cols-3" role="radiogroup" aria-label="Hình thức khảo sát">
          {(Object.keys(SURVEY_PREFERENCES) as SurveyPreference[]).map((k) => {
            const meta = SURVEY_PREFERENCES[k];
            const sel = pref === k;
            return (
              <button
                key={k}
                type="button"
                role="radio"
                aria-checked={sel}
                onClick={() => setPref(k)}
                className={cx('flex flex-col items-start gap-1 rounded-xl border p-3 text-left transition-colors', sel ? 'border-brand bg-orange-50/50 ring-1 ring-brand' : 'border-slate-200 hover:border-slate-300')}
              >
                <meta.icon className={cx('size-4', sel ? 'text-brand' : 'text-slate-400')} />
                <span className="text-sm font-semibold text-slate-900">{meta.title}</span>
              </button>
            );
          })}
        </div>
        {appointment ? (
          <>
            <Field label="Ngày khảo sát *" hint="Trước ngày chuyển nhà ít nhất 1 ngày" error={tried ? dateError : null}>
              <Input type="date" min={todayISO()} max={prevDay(order.scheduledDate)} value={date} invalid={tried && Boolean(dateError)} onChange={(e) => setDate(e.target.value)} className="text-base sm:text-sm" />
            </Field>
            <div>
              <p className="mb-2 text-[13px] font-medium text-slate-700">Khung giờ *</p>
              <div className="grid gap-2 sm:grid-cols-3">
                {SURVEY_TIME_SLOTS.map((s) => (
                  <button
                    key={s}
                    type="button"
                    aria-pressed={slot === s}
                    onClick={() => setSlot(s)}
                    className={cx(
                      'min-h-11 rounded-xl border px-3 py-2 text-xs font-semibold transition-colors',
                      slot === s ? 'border-slate-900 bg-slate-900 text-white' : 'border-slate-200 bg-white text-slate-700 hover:border-slate-300',
                    )}
                  >
                    {s}
                  </button>
                ))}
              </div>
            </div>
          </>
        ) : (
          <p className="rounded-xl bg-slate-50 px-3 py-2 text-xs text-slate-600">Không cần hẹn lịch — bạn tải ảnh / video ngay trên trang đơn hàng.</p>
        )}
      </div>
    </Modal>
  );
};

/** Thẻ chính của bước 2 trên trang đơn hàng của khách — nội dung theo hình thức khảo sát / gói dịch vụ. */
export const SurveyStageCard = ({ order }: { order: Order }) => {
  const { state, dispatch } = useApp();
  const [modal, setModal] = useState<{ open: boolean; initial?: SurveyPreference }>({ open: false });
  const [showOptionalUpload, setShowOptionalUpload] = useState(false);
  const kind = surveyStageOf(order);
  const d = order.bookingDetails;
  const coordinatorOnSite = Boolean(order.onSiteSurveyRequest);
  const canChange = Boolean(d) && !coordinatorOnSite;
  const openModal = (initial?: SurveyPreference) => setModal({ open: true, initial });

  if (kind === 'TRUCK_CONFIRM') {
    const vehicle = order.requestedServices.find((n) => parseCapacityTons(n) !== null);
    const labor = d?.laborCount ?? 0;
    return (
      <Card className="space-y-4">
        <div className="flex items-start gap-3">
          <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-orange-50 text-brand">
            <Truck className="size-5" />
          </span>
          <div className="min-w-0 flex-1">
            <p className="text-[15px] font-semibold text-slate-900">Đang xác nhận xe cho bạn</p>
            <p className="text-sm text-slate-500">Gói chỉ thuê xe không cần khảo sát. Điều phối viên sẽ gọi xác nhận và gửi báo giá trong khoảng 1 giờ làm việc.</p>
          </div>
          <Badge tone="warning" dot pulse>
            Chờ xác nhận
          </Badge>
        </div>
        <div className="grid gap-2 sm:grid-cols-2">
          <div className="flex items-center gap-3 rounded-xl bg-slate-50 p-3">
            <Truck className="size-4 text-slate-500" />
            <div className="text-sm">
              <p className="text-xs text-slate-500">Xe đã chọn</p>
              <p className="font-semibold text-slate-900">{vehicle ?? 'Chưa chọn'}</p>
            </div>
          </div>
          <div className="flex items-center gap-3 rounded-xl bg-slate-50 p-3">
            <Users className="size-4 text-slate-500" />
            <div className="text-sm">
              <p className="text-xs text-slate-500">Nhân viên bốc vác</p>
              <p className="font-semibold text-slate-900">{labor > 0 ? `${labor} người` : 'Không thuê'}</p>
            </div>
          </div>
        </div>
        <a href={HOTLINE_TEL} className="inline-flex h-10 items-center gap-2 rounded-xl border border-slate-200 px-4 text-sm font-semibold text-slate-700 hover:border-slate-300">
          <Phone className="size-4" /> Gọi điều phối 1900 6868
        </a>
      </Card>
    );
  }

  if (kind === 'PHOTOS') {
    const submitted = isMediaSubmitted(order);
    const submittedAt = order.surveyMediaSubmittedAt;
    const filled = SURVEY_MEDIA_CATEGORIES.filter((c) => mediaOfCategory(order, c).length > 0).length;
    return (
      <Card className="space-y-4">
        <div className="flex flex-wrap items-start gap-3">
          <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-orange-50 text-brand">
            <CloudUpload className="size-5" />
          </span>
          <div className="min-w-0 flex-1">
            <p className="text-[15px] font-semibold text-slate-900">Gửi ảnh / video để nhận báo giá</p>
            <p className="text-sm text-slate-500">Chụp đủ 4 mục bên dưới giúp chuyên viên báo giá chính xác, hạn chế phát sinh ngày chuyển.</p>
          </div>
          <Badge tone={filled === SURVEY_MEDIA_CATEGORIES.length ? 'success' : 'neutral'}>
            {filled}/{SURVEY_MEDIA_CATEGORIES.length} mục
          </Badge>
        </div>

        {submitted && (
          <p className="flex items-start gap-2 rounded-xl border border-emerald-200 bg-emerald-50/70 px-3 py-2.5 text-sm text-emerald-900">
            <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-emerald-600" />
            <span>
              {submittedAt ? (
                <>
                  Đã gửi lúc <b className="tabular-nums">{formatTime(submittedAt)}</b> —{' '}
                </>
              ) : (
                'Đã nhận ảnh — '
              )}
              chuyên viên đang lập báo giá. Bạn vẫn có thể gửi bổ sung.
            </span>
          </p>
        )}

        <MediaUploader order={order} />

        <div className="flex flex-wrap items-center justify-between gap-3">
          {canChange ? (
            <button type="button" onClick={() => openModal('VIDEO_CALL')} className="text-sm font-semibold text-brand hover:underline">
              Khó chụp? Đổi sang video call →
            </button>
          ) : (
            <span />
          )}
          <Button icon={Send} disabled={order.customerPhotos.length === 0} onClick={() => dispatch({ type: 'CUSTOMER_SUBMIT_SURVEY_MEDIA', orderId: order.id })}>
            {submitted ? 'Gửi bổ sung' : 'Gửi cho chuyên viên'}
          </Button>
        </div>
        {modal.open && <SurveyScheduleModal order={order} open onClose={() => setModal({ open: false })} initial={modal.initial} />}
      </Card>
    );
  }

  // VIDEO_CALL / ON_SITE
  const surveyor = order.onSiteSurveyRequest ? state.resources.staff.find((s) => s.id === order.onSiteSurveyRequest?.surveyorId) : undefined;
  const confirmed = Boolean(surveyor);
  const tips = PREP_TIPS[kind];
  const title = kind === 'VIDEO_CALL' ? 'Lịch khảo sát qua video call' : 'Lịch khảo sát trực tiếp tận nơi';

  return (
    <Card className="space-y-4">
      <div className="flex flex-wrap items-start gap-3">
        <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-orange-50 text-brand">
          <CalendarClock className="size-5" />
        </span>
        <div className="min-w-0 flex-1">
          <p className="text-[15px] font-semibold text-slate-900">{title}</p>
          <p className="text-sm text-slate-500">{kind === 'VIDEO_CALL' ? 'Khoảng 10 phút qua Zalo / Messenger.' : 'Khoảng 30 phút tại nhà bạn.'}</p>
        </div>
        <Badge tone={confirmed ? 'success' : 'warning'} dot pulse={!confirmed}>
          {confirmed ? 'Đã xác nhận' : 'Chờ xác nhận'}
        </Badge>
      </div>

      <div className="grid gap-2 sm:grid-cols-2">
        <div className="flex items-center gap-3 rounded-xl bg-slate-50 p-3">
          <CalendarClock className="size-4 text-slate-500" />
          <div className="text-sm">
            <p className="text-xs text-slate-500">Thời gian hẹn</p>
            <p className="font-semibold text-slate-900">
              {d && hasAppointment(order) ? `${weekdayOf(d.surveyDate)}, ${formatDateVN(d.surveyDate)} · ${d.surveyContactSlot}` : 'Chuyên viên sẽ gọi hẹn giờ'}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-3 rounded-xl bg-slate-50 p-3">
          {surveyor ? <UserCheck className="size-4 text-emerald-600" /> : <Headphones className="size-4 text-slate-500" />}
          <div className="text-sm">
            <p className="text-xs text-slate-500">Chuyên viên phụ trách</p>
            <p className="font-semibold text-slate-900">{surveyor ? `${surveyor.name} (${surveyor.code})` : 'Đang phân công'}</p>
          </div>
        </div>
      </div>

      <div>
        <p className="mb-2 flex items-center gap-2 text-[13px] font-semibold text-slate-700">
          <ClipboardCheck className="size-4 text-slate-500" /> Chuẩn bị trước buổi khảo sát
        </p>
        <ul className="space-y-1.5">
          {tips.map((t) => (
            <li key={t} className="flex items-start gap-2 text-sm text-slate-600">
              <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-slate-300" /> {t}
            </li>
          ))}
        </ul>
      </div>

      <div className="rounded-xl border border-dashed border-slate-300 p-3">
        <button type="button" onClick={() => setShowOptionalUpload((v) => !v)} className="flex w-full items-center justify-between gap-2 text-left text-sm font-semibold text-slate-700">
          <span className="flex items-center gap-2">
            <ImagePlus className="size-4 text-brand" /> Gửi trước ảnh / video (không bắt buộc)
          </span>
          <span className="text-xs font-medium text-slate-500">{order.customerPhotos.length > 0 ? `${order.customerPhotos.length} tệp` : showOptionalUpload ? 'Thu gọn' : 'Mở'}</span>
        </button>
        {showOptionalUpload && (
          <div className="mt-3">
            <MediaUploader order={order} compact />
          </div>
        )}
      </div>

      <div className="flex flex-wrap gap-2">
        {canChange ? (
          <>
            <Button variant="secondary" icon={CalendarClock} onClick={() => openModal()}>
              Đổi lịch hẹn
            </Button>
            <Button variant="secondary" icon={CloudUpload} onClick={() => openModal('PHOTOS_ONLY')}>
              Chuyển sang gửi ảnh / video
            </Button>
          </>
        ) : (
          <a href={HOTLINE_TEL} className="inline-flex h-10 items-center gap-2 rounded-xl border border-slate-200 px-4 text-sm font-semibold text-slate-700 hover:border-slate-300">
            <Phone className="size-4" /> Cần đổi lịch? Gọi 1900 6868
          </a>
        )}
      </div>
      {modal.open && <SurveyScheduleModal order={order} open onClose={() => setModal({ open: false })} initial={modal.initial} />}
    </Card>
  );
};
