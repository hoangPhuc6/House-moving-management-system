import { ChevronDown, ChevronLeft, ChevronRight, History } from 'lucide-react';
import { Alert, cx } from '@/components/ui';
import { useAdminPeriod } from '@/features/admin/hooks';
import { type Period, type PeriodKind, periodKey, periodLabel } from '@/features/admin/period';

/**
 * Bộ chọn kỳ gọn một hàng, cùng chiều cao nút (h-10):
 * [Tháng | Năm]  [‹ Tháng 11/2026 ⌄ ›]. Mũi tên lùi / tiến một kỳ; bấm tên kỳ để chọn từ danh sách.
 */
export const PeriodPicker = ({
  kind,
  onKindChange,
  period,
  options,
  onChange,
}: {
  kind: PeriodKind;
  onKindChange: (k: PeriodKind) => void;
  period: Period;
  /** Mới nhất trước. */
  options: Period[];
  onChange: (p: Period) => void;
}) => {
  const idx = options.findIndex((p) => periodKey(p) === periodKey(period));
  const older = idx >= 0 ? options[idx + 1] : undefined;
  const newer = idx > 0 ? options[idx - 1] : undefined;
  const unit = kind === 'month' ? 'tháng' : 'năm';
  const arrow = 'grid w-9 shrink-0 place-items-center text-slate-500 transition-colors hover:bg-slate-50 hover:text-slate-900 disabled:cursor-not-allowed disabled:text-slate-300 disabled:hover:bg-transparent';

  return (
    <div className="flex flex-wrap items-center gap-2">
      <div role="radiogroup" aria-label="Kiểu kỳ báo cáo" className="flex h-10 items-center gap-0.5 rounded-xl border border-slate-200 bg-white p-1">
        {(['month', 'year'] as const).map((k) => (
          <button
            key={k}
            type="button"
            role="radio"
            aria-checked={kind === k}
            onClick={() => onKindChange(k)}
            className={cx(
              'h-full rounded-lg px-3 text-sm font-semibold transition-colors',
              kind === k ? 'bg-slate-900 text-white shadow-sm' : 'text-slate-500 hover:bg-slate-50 hover:text-slate-900',
            )}
          >
            {k === 'month' ? 'Tháng' : 'Năm'}
          </button>
        ))}
      </div>
      <div className="flex h-10 items-stretch overflow-hidden rounded-xl border border-slate-200 bg-white">
        <button type="button" className={arrow} disabled={!older} onClick={() => older && onChange(older)} aria-label={`${unit} trước`}>
          <ChevronLeft className="size-4" />
        </button>
        <div className="relative flex items-center border-x border-slate-200">
          <select
            value={periodKey(period)}
            onChange={(e) => onChange(options.find((p) => periodKey(p) === e.target.value) ?? period)}
            aria-label={`Chọn ${unit}`}
            className="h-full cursor-pointer appearance-none bg-transparent py-0 pr-8 pl-3 text-sm font-semibold text-slate-900 tabular-nums focus:outline-none focus-visible:bg-slate-50"
          >
            {options.map((p) => (
              <option key={periodKey(p)} value={periodKey(p)}>{periodLabel(p)}</option>
            ))}
          </select>
          <ChevronDown className="pointer-events-none absolute right-2.5 size-4 text-slate-400" />
        </div>
        <button type="button" className={arrow} disabled={!newer} onClick={() => newer && onChange(newer)} aria-label={`${unit} sau`}>
          <ChevronRight className="size-4" />
        </button>
      </div>
    </div>
  );
};

/** Bộ chọn kỳ gắn với kỳ chọn chung của khu vực Admin — đặt vào `actions` của PageHeader. */
export const AdminPeriodPicker = () => {
  const { period, setPeriod, changeKind, options } = useAdminPeriod();
  return <PeriodPicker kind={period.kind} onKindChange={changeKind} period={period} options={options} onChange={setPeriod} />;
};

/**
 * Nhắc việc còn chờ xử lý nằm ngoài kỳ đang xem — lọc theo kỳ không được làm khuất việc tồn đọng.
 * Không hiện gì khi count = 0.
 */
export const OutOfPeriodNotice = ({ count, what }: { count: number; what: string }) =>
  count > 0 ? (
    <Alert tone="warning" icon={History} className="mb-4">
      Còn <b className="tabular-nums">{count}</b> {what} ở kỳ khác — chọn kỳ tương ứng để xử lý.
    </Alert>
  ) : null;
