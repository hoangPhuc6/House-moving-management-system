import { type ReactNode, useMemo, useState } from 'react';
import { ArrowRight, Building2, ChevronDown, CircleDot, Home, MapPin, ShieldCheck, Sparkles } from 'lucide-react';
import { cx } from '@/components/ui';
import {
  ADMIN_AREAS,
  DRAFT_STORAGE_KEY,
  PROPERTY_TYPES,
  emptyAddress,
  formatAddress,
  isAddressComplete,
} from '@/features/customer/booking/constants';
import { estimate } from '@/features/landing/utils';
import { estimateDistanceKm } from '@/services/referenceQuote';
import { formatVND } from '@/services/format';
import type { AddressDetail } from '@/types';

/** Phần địa chỉ cần cho báo giá nhanh — không hỏi tầng lầu / ghi chú vì chỉ tạm tính theo quãng đường. */
type QuickAddress = Pick<AddressDetail, 'province' | 'ward' | 'street' | 'propertyType'>;

const fieldCls =
  'h-11 w-full appearance-none rounded-xl border bg-white/[0.04] pr-3 pl-10 text-sm font-medium text-white placeholder:text-white/30 focus:border-brand/60 focus:ring-4 focus:ring-brand/15 focus:outline-none';

const FieldIcon = ({ icon: Icon }: { icon: typeof Home }) => (
  <Icon className="pointer-events-none absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-white/40" />
);

const QuickField = ({ label, error, children }: { label: string; error?: string | null; children: ReactNode }) => (
  <label className="block">
    <span className="mb-1.5 block text-xs font-medium text-white/50">{label}</span>
    <div className="relative">{children}</div>
    {error && <span className="mt-1 block text-[11px] font-medium text-rose-300">{error}</span>}
  </label>
);

const AddressBlock = ({
  title,
  icon,
  value,
  showErrors,
  streetPlaceholder,
  onChange,
}: {
  title: string;
  icon: typeof Home;
  value: QuickAddress;
  showErrors: boolean;
  streetPlaceholder: string;
  onChange: (patch: Partial<QuickAddress>) => void;
}) => {
  const Icon = icon;
  const wards = ADMIN_AREAS[value.province] ?? [];
  const wardErr = showErrors && !value.ward ? 'Chọn phường / xã' : null;
  const streetErr = showErrors && !value.street.trim() ? 'Nhập số nhà & tên đường' : null;
  const border = (bad: boolean) => (bad ? 'border-rose-400/70' : 'border-white/10');

  return (
    <fieldset aria-label={title} className="space-y-2.5 rounded-2xl border border-white/10 bg-white/[0.02] p-3.5">
      <div className="flex items-center justify-between gap-2">
        <span className="flex items-center gap-1.5 text-xs font-semibold tracking-wide text-white/80 uppercase">
          <Icon className="size-3.5 text-brand" /> {title}
        </span>
        <label className="relative flex items-center">
          <span className="sr-only">Tỉnh / Thành phố</span>
          <select
            value={value.province}
            onChange={(e) => onChange({ province: e.target.value, ward: '' })}
            className="h-7 appearance-none rounded-lg border border-white/10 bg-white/[0.04] pr-7 pl-2.5 text-xs font-semibold text-white/80 hover:border-white/20 focus:border-brand/60 focus:outline-none"
          >
            {Object.keys(ADMIN_AREAS).map((p) => (
              <option key={p} value={p} className="text-slate-900">
                {p}
              </option>
            ))}
          </select>
          <ChevronDown className="pointer-events-none absolute right-2 size-3.5 text-white/50" />
        </label>
      </div>
      <div className="grid gap-2.5 sm:grid-cols-2">
        <QuickField label="Phường / Xã" error={wardErr}>
          <FieldIcon icon={MapPin} />
          <select value={value.ward} onChange={(e) => onChange({ ward: e.target.value })} className={cx(fieldCls, border(Boolean(wardErr)))}>
            <option value="" className="text-slate-900">
              Chọn phường / xã
            </option>
            {wards.map((w) => (
              <option key={w} value={w} className="text-slate-900">
                {w}
              </option>
            ))}
          </select>
        </QuickField>
        <QuickField label="Loại bất động sản">
          <FieldIcon icon={Building2} />
          <select value={value.propertyType} onChange={(e) => onChange({ propertyType: e.target.value })} className={cx(fieldCls, border(false))}>
            {PROPERTY_TYPES.map((p) => (
              <option key={p.label} value={p.label} className="text-slate-900">
                {p.label}
              </option>
            ))}
          </select>
        </QuickField>
      </div>
      <QuickField label="Số nhà & Tên đường" error={streetErr}>
        <FieldIcon icon={Home} />
        <input
          value={value.street}
          onChange={(e) => onChange({ street: e.target.value })}
          placeholder={streetPlaceholder}
          autoComplete="street-address"
          className={cx(fieldCls, border(Boolean(streetErr)))}
        />
      </QuickField>
    </fieldset>
  );
};

const initialAddress = (): QuickAddress => {
  const { province, ward, street, propertyType } = emptyAddress();
  return { province, ward, street, propertyType };
};

/** Đẩy thông tin báo giá nhanh vào nháp đặt đơn để form đặt lịch được điền sẵn (giữ các trường khác của nháp cũ). */
const prefillBookingDraft = (from: QuickAddress, to: QuickAddress) => {
  try {
    const raw = window.localStorage.getItem(DRAFT_STORAGE_KEY);
    const prev = raw ? (JSON.parse(raw) as Record<string, unknown>) : {};
    const merge = (side: unknown, a: QuickAddress) => ({ ...emptyAddress(), ...(side as Partial<AddressDetail> | undefined), ...a });
    window.localStorage.setItem(
      DRAFT_STORAGE_KEY,
      JSON.stringify({ ...prev, from: merge(prev.from, from), to: merge(prev.to, to) }),
    );
  } catch {
    // Không lưu được nháp (chế độ riêng tư…) — khách vẫn đặt lịch bình thường, chỉ phải nhập lại.
  }
};

export const QuickQuote = ({ onBook }: { onBook: () => void }) => {
  const [from, setFrom] = useState<QuickAddress>(initialAddress);
  const [to, setTo] = useState<QuickAddress>(initialAddress);
  const [submitted, setSubmitted] = useState(false);

  const ready = isAddressComplete({ ...emptyAddress(), ...from }) && isAddressComplete({ ...emptyAddress(), ...to });
  const result = useMemo(() => {
    if (!ready) return null;
    const km = estimateDistanceKm(formatAddress({ ...emptyAddress(), ...from }), formatAddress({ ...emptyAddress(), ...to }));
    return estimate(km);
  }, [ready, from, to]);

  const patch = (setter: typeof setFrom) => (p: Partial<QuickAddress>) => {
    setter((a) => ({ ...a, ...p }));
    setSubmitted(false);
  };

  const book = () => {
    prefillBookingDraft(from, to);
    onBook();
  };

  return (
    <div className="relative rounded-3xl border border-white/10 bg-slate-900/70 p-6 shadow-2xl shadow-black/40 backdrop-blur-xl sm:p-7">
      <div className="mb-5 flex items-center justify-between">
        <div>
          <p className="text-xs font-semibold tracking-wider text-brand uppercase">Báo giá nhanh</p>
          <h2 className="mt-1 text-xl font-semibold">Tạm tính theo quãng đường</h2>
        </div>
        <span className="grid size-10 place-items-center rounded-xl bg-white/5 ring-1 ring-white/10">
          <Sparkles className="size-5 text-brand" />
        </span>
      </div>

      <div className="space-y-3">
        <AddressBlock
          title="Điểm xuất phát"
          icon={CircleDot}
          value={from}
          showErrors={submitted}
          streetPlaceholder="VD: 128/4 Ung Văn Khiêm"
          onChange={patch(setFrom)}
        />
        <AddressBlock
          title="Điểm đến"
          icon={MapPin}
          value={to}
          showErrors={submitted}
          streetPlaceholder="VD: Toà S2.03, Vinhomes Grand Park"
          onChange={patch(setTo)}
        />
      </div>

      <button
        type="button"
        onClick={() => setSubmitted(true)}
        className="mt-5 inline-flex h-12 w-full items-center justify-center gap-2 rounded-xl bg-brand text-[15px] font-semibold text-white hover:bg-brand-hover"
      >
        Nhận Báo Giá Ngay <ArrowRight className="size-4" />
      </button>

      {submitted && result && (
        <div className="anim-pop mt-5 rounded-2xl border border-white/10 bg-white/[0.04] p-4">
          <div className="flex items-end justify-between gap-3">
            <div>
              <p className="text-xs text-white/50">Chi phí tạm tính · ~{result.km} km</p>
              <p className="mt-1 text-2xl font-bold tracking-tight tabular-nums">
                {formatVND(result.min)} <span className="text-white/40">–</span> {formatVND(result.max)}
              </p>
            </div>
            <button
              type="button"
              onClick={book}
              className="inline-flex h-10 shrink-0 items-center gap-1.5 rounded-xl bg-white px-3 text-sm font-semibold text-slate-900 hover:bg-white/90"
            >
              Đặt lịch <ArrowRight className="size-4" />
            </button>
          </div>
          <div className="mt-3 flex flex-wrap gap-2 text-[11px] font-medium text-white/60">
            {[result.vehicleLabel, `${result.labor} nhân công`, `${result.cartons} thùng carton`].map((t) => (
              <span key={t} className="rounded-full border border-white/10 px-2 py-0.5">
                {t}
              </span>
            ))}
          </div>
          <p className="mt-3 text-[11px] text-white/40">Chưa gồm phụ phí tầng lầu, hẻm sâu, lối đi — được chốt sau khảo sát.</p>
        </div>
      )}
      {!submitted && (
        <p className="mt-4 flex items-center gap-2 text-xs text-white/40">
          <ShieldCheck className="size-3.5" /> Giá cuối cùng chốt sau khảo sát — không phát sinh ngoài báo giá.
        </p>
      )}
    </div>
  );
};
