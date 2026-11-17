import { useState } from 'react';
import { Clock, Info } from 'lucide-react';
import { Input, cx } from '@/components/ui';
import { LAST_START_TIME, OPEN_TIME, POPULAR_START_TIMES, isValidStartTime } from '@/constants/resources';

const chipCls = (sel: boolean) =>
  cx(
    'h-10 rounded-xl border px-3.5 text-sm font-semibold tabular-nums transition-colors',
    sel ? 'border-brand bg-orange-50/60 text-slate-900 ring-4 ring-brand/10' : 'border-slate-200 text-slate-700 hover:border-slate-300',
  );

/**
 * Chọn giờ xe bắt đầu đi: vài giờ phổ biến dạng nút + "Giờ khác" để nhập giờ tự do
 * (trong giờ hoạt động OPEN_TIME – LAST_START_TIME).
 */
export const StartTimePicker = ({
  value,
  onChange,
  error,
  showCurfewNote = true,
}: {
  value: string;
  onChange: (v: string) => void;
  error?: string | null;
  showCurfewNote?: boolean;
}) => {
  const isPopular = POPULAR_START_TIMES.includes(value);
  const [custom, setCustom] = useState(!isPopular);
  const invalid = Boolean(error) || (custom && !isValidStartTime(value));

  return (
    <div className="space-y-2.5">
      <div className="flex flex-wrap gap-2" role="radiogroup" aria-label="Giờ bắt đầu">
        {POPULAR_START_TIMES.map((t) => {
          const sel = !custom && value === t;
          return (
            <button
              key={t}
              type="button"
              role="radio"
              aria-checked={sel}
              onClick={() => {
                setCustom(false);
                onChange(t);
              }}
              className={chipCls(sel)}
            >
              {t}
            </button>
          );
        })}
        <button type="button" role="radio" aria-checked={custom} onClick={() => setCustom(true)} className={cx(chipCls(custom), 'inline-flex items-center gap-1.5')}>
          <Clock className="size-4" /> {custom && !isPopular && isValidStartTime(value) ? value : 'Giờ khác…'}
        </button>
      </div>

      {custom && (
        <div className="relative max-w-[200px]">
          <Clock className="pointer-events-none absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-slate-400" />
          <Input
            type="time"
            min={OPEN_TIME}
            max={LAST_START_TIME}
            step={900}
            value={value}
            invalid={invalid}
            autoFocus
            onChange={(e) => onChange(e.target.value)}
            className="pl-10 text-base tabular-nums sm:text-sm"
            aria-label="Nhập giờ bắt đầu"
          />
        </div>
      )}

      {invalid ? (
        <p className="text-xs font-medium text-rose-600">{error ?? `Chọn giờ trong khoảng ${OPEN_TIME} – ${LAST_START_TIME}`}</p>
      ) : (
        showCurfewNote && (
          <p className="flex items-start gap-1.5 text-xs text-slate-500">
            <Info className="mt-0.5 size-3.5 shrink-0" />
            Giờ sớm / tối có thể trùng giờ cấm tải nội đô tuỳ loại xe — điều phối viên sẽ xác nhận lại với bạn.
          </p>
        )
      )}
    </div>
  );
};
