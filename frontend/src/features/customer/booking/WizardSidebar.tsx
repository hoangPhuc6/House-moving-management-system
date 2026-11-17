import { BadgeCheck, Clock, Phone, Route, ShieldCheck, Wallet } from 'lucide-react';
import { Card, KeyValue } from '@/components/ui';
import { formatDateVN, formatVND } from '@/services/format';
import { HOTLINE } from './constants';
import { EditLink } from './parts';
import type { BookingWizard } from './useBookingWizard';

export const WizardSidebar = ({ wizard, step, onEdit }: { wizard: BookingWizard; step: number; onEdit: (s: number) => void }) => {
  const { draft, fromAddress, toAddress, distanceKm, estimateReady, referenceQuote, displayServices: requestedServices } = wizard;
  const hasRoute = Boolean(draft.from.street.trim() || draft.to.street.trim());
  const showEstimate = step >= 1 && estimateReady;
  const truckOnly = draft.serviceMode === 'TRUCK_ONLY';

  return (
    <div className="space-y-4">
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
          <ShieldCheck className="size-4 text-brand" /> {truckOnly ? <>Giá tạm tính &amp; giá chốt</> : "Báo giá sau khảo sát"}
        </p>
        <p className="mt-2 text-[13px] leading-relaxed text-white/70">
          {truckOnly ? (
            <>
              Số tạm tính tính theo loại xe, quãng đường và số người bạn chọn. <span className="font-semibold text-white">Giá chốt</span> nằm trong báo giá
              chi tiết gửi cho bạn sau khi điều phối viên xác nhận xe, và chỉ áp dụng khi bạn đồng ý.
            </>
          ) : (
            <>
              Chi phí trọn gói phụ thuộc khối lượng đồ, lối đi và tầng lầu thực tế. Chuyên viên gửi bạn <span className="font-semibold text-white">báo giá chi tiết từng hạng mục</span> sau
              khi khảo sát — chỉ áp dụng khi bạn đồng ý.
            </>
          )}
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
