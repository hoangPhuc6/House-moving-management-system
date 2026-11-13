import { type ReactNode, useState } from 'react';
import { ArrowDown, Bookmark, CalendarDays, CheckCircle2, CircleDot, Home, LocateFixed, MapPin, Plus, Route } from 'lucide-react';
import { Badge, Field, Input, Select, cx } from '@/components/ui';
import { TIME_SLOTS } from '@/constants/resources';
import { formatDateVN } from '@/services/format';
import type { AddressDetail } from '@/types';
import { ADMIN_AREAS, FLOOR_OPTIONS, PROPERTY_TYPES, SAVED_ADDRESSES } from './constants';
import { SectionCard } from './parts';
import { type BookingWizard, type StepErrors, minMoveDate } from './useBookingWizard';

/** Ghi chú tùy chọn — gập sau một nút "+ Thêm ghi chú", tự mở nếu đã có nội dung (VD: nháp khôi phục). */
const OptionalNote = ({ label, filled, children }: { label: string; filled: boolean; children: ReactNode }) => {
  const [open, setOpen] = useState(filled);
  if (open) return <>{children}</>;
  return (
    <button
      type="button"
      onClick={() => setOpen(true)}
      className="inline-flex items-center gap-1.5 rounded-lg px-1 py-1 text-sm font-semibold text-slate-600 hover:text-brand"
    >
      <Plus className="size-4" /> {label}
    </button>
  );
};

const AddressSection = ({
  side,
  value,
  errors,
  onChange,
}: {
  side: 'from' | 'to';
  value: AddressDetail;
  errors: StepErrors;
  onChange: (patch: Partial<AddressDetail>) => void;
}) => {
  const isFrom = side === 'from';
  const wards = ADMIN_AREAS[value.province] ?? [];
  const err = (k: string) => errors[`${side}.${k}`] ?? null;
  const isSaved = (a: (typeof SAVED_ADDRESSES)[number]['address']) => a.street === value.street && a.ward === value.ward && a.province === value.province;

  return (
    <SectionCard
      icon={isFrom ? CircleDot : MapPin}
      tone={isFrom ? 'dark' : 'brand'}
      title={isFrom ? 'Điểm xuất phát (Địa chỉ chuyển đi)' : 'Điểm đến (Địa chỉ chuyển đến)'}
      subtitle={isFrom ? 'Nơi xe sẽ đến bốc xếp đồ đạc' : 'Nơi bàn giao và sắp xếp tài sản hoàn thiện'}
      action={
        isFrom ? (
          <button
            type="button"
            onClick={() => onChange({ province: 'TP. Hồ Chí Minh', ward: 'Phường Thạnh Mỹ Tây', street: value.street || '128/4 Ung Văn Khiêm' })}
            className="inline-flex items-center gap-1.5 rounded-full border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-600 hover:border-brand hover:text-brand"
          >
            <LocateFixed className="size-3.5" /> Dùng vị trí hiện tại
          </button>
        ) : value.ward ? (
          <Badge tone="neutral">{value.ward}</Badge>
        ) : null
      }
    >
      {SAVED_ADDRESSES.length > 0 && (
        <div className="flex flex-wrap items-center gap-2">
          <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-500">
            <Bookmark className="size-3.5" /> Địa chỉ đã lưu:
          </span>
          {SAVED_ADDRESSES.map((s) => {
            const sel = isSaved(s.address);
            return (
              <button
                key={s.label}
                type="button"
                aria-pressed={sel}
                onClick={() => onChange(s.address)}
                className={cx(
                  'rounded-full border px-3 py-1 text-xs font-semibold transition-colors',
                  sel ? 'border-brand bg-orange-50 text-brand' : 'border-slate-200 text-slate-600 hover:border-slate-300',
                )}
              >
                {s.label}
              </button>
            );
          })}
        </div>
      )}
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Tỉnh / Thành phố *">
          <Select value={value.province} onChange={(e) => onChange({ province: e.target.value, ward: '' })}>
            {Object.keys(ADMIN_AREAS).map((p) => (
              <option key={p}>{p}</option>
            ))}
          </Select>
        </Field>
        <Field label="Phường / Xã *" error={err('ward')}>
          <Select value={value.ward} onChange={(e) => onChange({ ward: e.target.value })} className={cx(err('ward') && 'border-rose-400')}>
            <option value="">Chọn phường / xã</option>
            {wards.map((w) => (
              <option key={w}>{w}</option>
            ))}
          </Select>
        </Field>
      </div>
      <Field label={isFrom ? 'Số nhà & Tên đường chi tiết *' : 'Số nhà & Tên toà nhà chi tiết *'} error={err('street')}>
        <div className="relative">
          <Home className="pointer-events-none absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-slate-400" />
          <Input
            invalid={Boolean(err('street'))}
            value={value.street}
            onChange={(e) => onChange({ street: e.target.value })}
            placeholder={isFrom ? 'VD: 128/4 Ung Văn Khiêm' : 'VD: Căn hộ 14.08, Toà S2.03, KĐT Vinhomes Grand Park'}
            className="pl-10 text-base sm:text-sm"
            autoComplete="street-address"
          />
        </div>
      </Field>
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label={isFrom ? 'Loại bất động sản & Lối đi' : 'Loại bất động sản'}>
          <Select value={value.propertyType} onChange={(e) => onChange({ propertyType: e.target.value })}>
            {PROPERTY_TYPES.map((p) => (
              <option key={p.label}>{p.label}</option>
            ))}
          </Select>
        </Field>
        <Field label={isFrom ? 'Tầng lầu bốc dỡ' : 'Tầng lầu bàn giao'}>
          <Select value={value.floor} onChange={(e) => onChange({ floor: e.target.value })}>
            {FLOOR_OPTIONS.map((f) => (
              <option key={f}>{f}</option>
            ))}
          </Select>
        </Field>
      </div>
      <OptionalNote label={isFrom ? 'Thêm ghi chú về hẻm, lối xe vào' : 'Thêm ghi chú ban quản lý & hầm đỗ'} filled={Boolean(value.note)}>
        <Field label={isFrom ? 'Ghi chú vận chuyển điểm đi' : 'Ghi chú ban quản lý & hầm đỗ'}>
          <Input
            autoFocus={!value.note}
            value={value.note}
            onChange={(e) => onChange({ note: e.target.value })}
            placeholder={isFrom ? 'VD: Hẻm 4m xe 1.5 tấn vào được thoải mái, không cấm giờ' : 'VD: Có thang máy hàng 1000kg, cần đăng ký trước với BQL'}
            className="text-base sm:text-sm"
          />
        </Field>
      </OptionalNote>
    </SectionCard>
  );
};

export const StepLocation = ({ wizard, errors }: { wizard: BookingWizard; errors: StepErrors }) => {
  const { draft, update, updateAddress, distanceKm } = wizard;

  return (
    <div className="space-y-4">
      {distanceKm !== null ? (
        <div className="flex items-start gap-3 rounded-2xl border border-orange-200 bg-orange-50/70 p-4">
          <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-brand text-white">
            <Route className="size-[18px]" />
          </span>
          <div className="min-w-0 flex-1 text-sm">
            <p className="font-semibold text-slate-900">
              Khoảng cách ước tính: <span className="text-brand tabular-nums">{distanceKm} km</span> (~{Math.round(distanceKm * 2.4)} phút di chuyển)
            </p>
            <p className="mt-0.5 text-slate-600">Lộ trình được tối ưu theo giờ cao điểm & quy định cấm tải nội đô.</p>
          </div>
          <Badge tone="brand" icon={CheckCircle2}>Đã định vị</Badge>
        </div>
      ) : (
        <div className="flex items-center gap-3 rounded-2xl border border-dashed border-slate-200 bg-white p-4 text-sm text-slate-500">
          <Route className="size-5 shrink-0 text-slate-400" /> Nhập đủ hai địa chỉ để hệ thống ước tính quãng đường & lộ trình.
        </div>
      )}

      <AddressSection side="from" value={draft.from} errors={errors} onChange={(p) => updateAddress('from', p)} />

      <div className="flex justify-center" aria-hidden>
        <span className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-4 py-1.5 text-xs font-semibold text-slate-500 shadow-sm">
          <ArrowDown className="size-3.5 text-brand" />
          Hành trình{distanceKm !== null && <span className="tabular-nums text-slate-900">{distanceKm} km</span>}
          <ArrowDown className="size-3.5 text-brand" />
        </span>
      </div>

      <AddressSection side="to" value={draft.to} errors={errors} onChange={(p) => updateAddress('to', p)} />

      <SectionCard icon={CalendarDays} title="Thời gian vận chuyển dự kiến" subtitle="Chọn ngày và khung giờ thuận tiện để MoveFlow sắp xếp đội xe">
        <div className="grid gap-4 sm:grid-cols-[minmax(0,220px)_1fr]">
          <Field label="Ngày thực hiện dọn nhà *" error={errors.scheduledDate ?? null}>
            <Input
              type="date"
              min={minMoveDate()}
              value={draft.scheduledDate}
              invalid={Boolean(errors.scheduledDate)}
              onChange={(e) => update('scheduledDate', e.target.value)}
              className="text-base sm:text-sm"
            />
          </Field>
          <div>
            <p className="mb-1.5 text-[13px] font-semibold text-slate-700">Khung giờ ưu tiên *</p>
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
              {TIME_SLOTS.map((s) => {
                const sel = draft.timeSlot === s.label;
                return (
                  <button
                    key={s.id}
                    type="button"
                    aria-pressed={sel}
                    onClick={() => update('timeSlot', s.label)}
                    className={cx(
                      'h-11 rounded-xl border px-3 text-sm font-semibold tabular-nums transition-colors',
                      sel ? 'border-brand bg-orange-50/60 text-slate-900 ring-4 ring-brand/10' : 'border-slate-200 text-slate-700 hover:border-slate-300',
                    )}
                  >
                    {s.label}
                  </button>
                );
              })}
            </div>
          </div>
        </div>
        {draft.scheduledDate && !errors.scheduledDate && (
          <p className="flex items-center gap-2 rounded-xl bg-emerald-50 px-3 py-2 text-xs font-medium text-emerald-700">
            <CheckCircle2 className="size-4 shrink-0" /> Lịch dự kiến: {formatDateVN(draft.scheduledDate)}, khung {draft.timeSlot}. Điều phối viên sẽ xác nhận xe & nhân sự.
          </p>
        )}
        <label className="flex items-start gap-2.5 text-sm text-slate-700">
          <input type="checkbox" checked={draft.flexibleTime} onChange={(e) => update('flexibleTime', e.target.checked)} className="mt-0.5 size-4 rounded accent-[#FF5A1F]" />
          Tôi chưa cố định giờ chính xác, cần tư vấn thêm
        </label>
        <OptionalNote label="Thêm ghi chú về thời gian & điều phối" filled={Boolean(draft.scheduleNote)}>
          <Field label="Ghi chú thêm về thời gian & điều phối">
            <Input
              autoFocus={!draft.scheduleNote}
              value={draft.scheduleNote}
              onChange={(e) => update('scheduleNote', e.target.value)}
              placeholder="VD: Ưu tiên xe đến đúng 08:30 sáng để kịp giờ vào nhà mới"
              className="text-base sm:text-sm"
            />
          </Field>
        </OptionalNote>
      </SectionCard>
    </div>
  );
};
