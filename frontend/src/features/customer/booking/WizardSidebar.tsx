import { ArrowLeft, ArrowRight, BadgeCheck, Clock, Phone, Route, Send, ShieldCheck, Wallet } from 'lucide-react';
import { Button, Card, KeyValue, cx } from '@/components/ui';
import { formatDateVN, formatVND } from '@/services/format';
import { HOTLINE, WIZARD_STEPS } from './constants';
import { EditLink } from './parts';
import type { BookingWizard } from './useBookingWizard';

export interface WizardNavProps {
  step: number;
  onNext: () => void;
  onBack: () => void;
}

const isLast = (step: number) => step === WIZARD_STEPS.length - 1;

/** Các nút điều hướng wizard — chỉ dùng cho sidebar desktop (lưu nháp nằm ở đầu trang). */
const NavButtons = ({ step, onNext, onBack }: WizardNavProps) => {
  const last = isLast(step);
  const NextIcon = last ? Send : ArrowRight;
  return (
    <div className="space-y-2">
      <button
        type="button"
        onClick={onNext}
        className={cx(
          'group flex h-11 w-full items-center gap-3 rounded-xl pr-2 pl-4 text-left text-white transition-all duration-150 active:scale-[0.98]',
          'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand/40 focus-visible:ring-offset-2',
          last ? 'bg-brand shadow-sm shadow-brand/20 hover:bg-brand-hover' : 'bg-slate-900 hover:bg-slate-800',
        )}
      >
        <span className="min-w-0 flex-1 text-[15px] font-semibold">{last ? 'Gửi yêu cầu chuyển nhà' : 'Tiếp tục'}</span>
        <span className={cx('grid size-7 shrink-0 place-items-center rounded-lg transition-transform group-hover:translate-x-0.5', last ? 'bg-white/20' : 'bg-white/10')}>
          <NextIcon className="size-4" strokeWidth={2.25} />
        </span>
      </button>
      {step > 0 && (
        <Button variant="secondary" block icon={ArrowLeft} onClick={onBack}>
          Quay lại Bước {step}
        </Button>
      )}
      <p className="text-center text-[11px] text-slate-500">
        {last ? 'Miễn phí hủy lịch trước 24 giờ so với giờ vận chuyển.' : 'Thông tin được giữ lại khi chuyển bước.'}
      </p>
    </div>
  );
};

export const WizardSidebar = ({ wizard, nav, onEdit }: { wizard: BookingWizard; nav: WizardNavProps; onEdit: (s: number) => void }) => {
  const { draft, fromAddress, toAddress, distanceKm, estimateReady, referenceQuote, requestedServices } = wizard;
  const { step } = nav;
  const hasRoute = Boolean(draft.from.street.trim() || draft.to.street.trim());
  const showEstimate = step >= 1 && estimateReady;
  const truckOnly = draft.serviceMode === 'TRUCK_ONLY';

  return (
    <div className="space-y-4">
      <Card className="hidden space-y-4 lg:block">
        <div className="flex items-center justify-between gap-2 text-xs">
          <span className="inline-flex items-center gap-1.5 font-semibold text-slate-900">
            <span className="size-2 rounded-full bg-brand" /> Tiến trình: Bước {step + 1}/{WIZARD_STEPS.length}
          </span>
          <span className="truncate text-slate-500">{WIZARD_STEPS[step]!.title}</span>
        </div>
        <div className="h-1.5 overflow-hidden rounded-full bg-slate-100">
          <div className="h-full rounded-full bg-brand transition-all duration-300" style={{ width: `${((step + 1) / WIZARD_STEPS.length) * 100}%` }} />
        </div>
        <NavButtons {...nav} />
      </Card>

      {hasRoute && step > 0 && (
        <Card className="space-y-4">
          <div className="flex items-center justify-between gap-2">
            <p className="flex items-center gap-2 text-sm font-semibold text-slate-900">
              <Route className="size-4 text-brand" /> Lộ trình đã chọn
            </p>
            <EditLink onClick={() => onEdit(0)} />
          </div>
          <ol className="relative space-y-4 pl-5">
            <span aria-hidden className="absolute top-1.5 bottom-1.5 left-[5px] w-px bg-slate-200" />
            <li className="relative">
              <span className="absolute top-1 -left-5 size-2.5 rounded-full bg-slate-900 ring-4 ring-white" />
              <p className="text-[11px] font-semibold uppercase tracking-wider text-slate-400">Điểm đón đi</p>
              <p className="text-sm font-semibold text-slate-900">{fromAddress || '—'}</p>
            </li>
            <li className="relative">
              <span className="absolute top-1 -left-5 size-2.5 rounded-full bg-brand ring-4 ring-white" />
              <p className="text-[11px] font-semibold uppercase tracking-wider text-slate-400">Điểm đón đến</p>
              <p className="text-sm font-semibold text-slate-900">{toAddress || '—'}</p>
            </li>
          </ol>
          <div className="grid grid-cols-2 gap-2 rounded-xl bg-slate-50 p-3 text-xs">
            <div>
              <p className="text-slate-500">Khoảng cách</p>
              <p className="font-semibold text-slate-900 tabular-nums">{distanceKm !== null ? `${distanceKm} km` : '—'}</p>
            </div>
            <div>
              <p className="text-slate-500">Thời gian hẹn</p>
              <p className="font-semibold text-slate-900 tabular-nums">
                {formatDateVN(draft.scheduledDate)} • {draft.timeSlot.split(' - ')[0]}
              </p>
            </div>
          </div>
          {step >= 2 && requestedServices.length > 0 && (
            <div className="border-t border-slate-100 pt-3">
              <p className="mb-1.5 text-[11px] font-semibold uppercase tracking-wider text-slate-400">Dịch vụ đã chọn ({requestedServices.length})</p>
              <ul className="space-y-1 text-xs text-slate-700">
                {requestedServices.map((s) => (
                  <li key={s} className="flex items-center gap-1.5">
                    <BadgeCheck className="size-3.5 shrink-0 text-brand" /> {s}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </Card>
      )}

      {showEstimate && (
        <Card className="space-y-2">
          <p className="flex items-center gap-2 text-sm font-semibold text-slate-900">
            <Wallet className="size-4 text-brand" /> Ước lượng tham khảo
          </p>
          <KeyValue label="Vận chuyển" value={formatVND(referenceQuote.categoryFees.transportFee)} />
          <KeyValue label="Dịch vụ" value={formatVND(referenceQuote.categoryFees.serviceFee)} />
          <div className="flex items-center justify-between gap-3 rounded-xl bg-orange-50 px-3 py-2.5">
            <span className="text-sm font-semibold text-slate-900">Tạm tính</span>
            <span className="text-lg font-bold text-brand tabular-nums">{formatVND(referenceQuote.financialSummary.totalEstimated)}</span>
          </div>
          <p className="text-[11px] leading-relaxed text-slate-500">{referenceQuote.disclaimer}</p>
        </Card>
      )}

      <div className="rounded-2xl bg-slate-900 p-5 text-white">
        <p className="flex items-center gap-2 text-sm font-semibold">
          <ShieldCheck className="size-4 text-brand" /> Giá tạm tính &amp; giá chốt
        </p>
        <p className="mt-2 text-[13px] leading-relaxed text-white/70">
          Số tạm tính chỉ để bạn hình dung chi phí. <span className="font-semibold text-white">Giá chốt</span> nằm trong báo giá chi tiết từng hạng mục, gửi cho bạn{' '}
          {truckOnly ? 'sau khi điều phối viên xác nhận xe' : 'sau khi khảo sát'} và chỉ áp dụng khi bạn đồng ý.
        </p>
        <p className="mt-3 text-xs font-semibold text-brand">Phát sinh tại hiện trường (nếu có) luôn cần bạn xác nhận trước khi tính</p>
      </div>

      <Card className="flex items-center gap-3">
        <span className="grid size-11 shrink-0 place-items-center rounded-xl bg-orange-50 text-brand ring-1 ring-brand/15">
          <Phone className="size-5" />
        </span>
        <div className="min-w-0 flex-1">
          <p className="text-xs text-slate-500">Tư vấn viên trực tuyến (24/7)</p>
          <p className="text-lg font-bold text-slate-900 tabular-nums">{HOTLINE.display}</p>
        </div>
        <a href={HOTLINE.tel} className="inline-flex h-9 items-center rounded-xl bg-slate-900 px-3.5 text-xs font-semibold text-white hover:bg-slate-800">
          Gọi ngay
        </a>
      </Card>

      {step === 0 && (
        <div className="grid grid-cols-3 gap-2">
          {[
            { icon: ShieldCheck, t: 'Bảo hiểm 100%', d: 'Bồi thường đổ vỡ' },
            { icon: Clock, t: 'Đúng hẹn 100%', d: 'Có mặt đúng giờ' },
            { icon: Wallet, t: 'Minh bạch giá', d: 'Không phí ẩn' },
          ].map((x) => (
            <Card key={x.t} padded={false} className="px-2 py-3 text-center">
              <x.icon className="mx-auto size-5 text-brand" />
              <p className="mt-1.5 text-[11px] font-semibold text-slate-900">{x.t}</p>
              <p className="text-[10px] text-slate-500">{x.d}</p>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
};
