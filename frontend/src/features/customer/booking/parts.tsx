import type { ReactNode } from 'react';
import { Check, type LucideIcon, Pencil } from 'lucide-react';
import { Card, cx } from '@/components/ui';
import { WIZARD_STEPS } from './constants';

/** Thanh tiến trình 4 bước — cho phép quay lại các bước đã đi qua. */
export const WizardStepper = ({ step, maxReached, onJump }: { step: number; maxReached: number; onJump: (s: number) => void }) => (
  <Card padded={false} className="px-3 py-4 sm:px-6 sm:py-5">
    <ol className="flex items-start">
      {WIZARD_STEPS.map((s, i) => {
        const done = i < step;
        const active = i === step;
        const reachable = i <= maxReached && i !== step;
        return (
          <li key={s.title} className="relative flex flex-1 flex-col items-center text-center">
            {i > 0 && (
              <span aria-hidden className={cx('absolute top-[17px] right-[calc(50%+24px)] left-[calc(-50%+24px)] h-0.5 rounded-full', i <= step ? 'bg-brand' : 'bg-slate-200')} />
            )}
            <button
              type="button"
              disabled={!reachable}
              onClick={() => onJump(i)}
              aria-current={active ? 'step' : undefined}
              className={cx(
                'relative z-10 grid size-9 place-items-center rounded-full text-sm font-bold tabular-nums ring-4 ring-white transition-colors',
                active && 'bg-slate-900 text-white shadow-md',
                done && 'bg-brand text-white hover:bg-brand-hover',
                !active && !done && 'border border-slate-200 bg-white text-slate-400',
                reachable && !done && 'hover:border-slate-300 hover:text-slate-700',
              )}
              aria-label={`Bước ${i + 1}: ${s.title}`}
            >
              {done ? <Check className="size-4" strokeWidth={3} /> : i + 1}
            </button>
            <span className={cx('mt-2 text-[11px] font-semibold uppercase tracking-wider', active || done ? 'text-brand' : 'text-slate-400')}>Bước {i + 1}</span>
            <span className={cx('mt-0.5 hidden text-sm font-semibold sm:block', active ? 'text-slate-900' : 'text-slate-500')}>{s.title}</span>
            <span className="hidden text-xs text-slate-400 lg:block">{s.subtitle}</span>
          </li>
        );
      })}
    </ol>
    <p className="mt-3 text-center text-sm font-semibold text-slate-900 sm:hidden">{WIZARD_STEPS[step]!.title}</p>
  </Card>
);

/** Khung một nhóm thông tin trong bước — tiêu đề có icon, mô tả, hành động phụ. */
export const SectionCard = ({
  icon: Icon,
  title,
  subtitle,
  action,
  tone = 'neutral',
  children,
  className,
  id,
}: {
  icon: LucideIcon;
  title: ReactNode;
  subtitle?: ReactNode;
  action?: ReactNode;
  tone?: 'neutral' | 'brand' | 'dark';
  children: ReactNode;
  className?: string;
  id?: string;
}) => (
  <Card className={cx('space-y-5 scroll-mt-24', className)}>
    <div id={id} className="flex flex-wrap items-start justify-between gap-3">
      <div className="flex min-w-0 items-start gap-3">
        <span
          className={cx(
            'grid size-10 shrink-0 place-items-center rounded-xl',
            tone === 'brand' && 'bg-orange-50 text-brand ring-1 ring-brand/15',
            tone === 'dark' && 'bg-slate-900 text-white',
            tone === 'neutral' && 'border border-slate-200 bg-slate-50 text-slate-700',
          )}
        >
          <Icon className="size-5" strokeWidth={2} />
        </span>
        <div className="min-w-0">
          <h2 className="text-base font-semibold tracking-tight text-slate-900 sm:text-[17px]">{title}</h2>
          {subtitle && <p className="mt-0.5 text-sm text-slate-500">{subtitle}</p>}
        </div>
      </div>
      {action}
    </div>
    {children}
  </Card>
);

export const EditLink = ({ onClick, label = 'Sửa' }: { onClick: () => void; label?: string }) => (
  <button type="button" onClick={onClick} className="inline-flex items-center gap-1 rounded-lg px-2 py-1 text-xs font-semibold text-brand hover:bg-orange-50">
    <Pencil className="size-3.5" /> {label}
  </button>
);

/** Ô chọn dạng thẻ (radio) theo phong cách chọn loại nhà ở hiện có. */
export const choiceClass = (selected: boolean) =>
  cx('rounded-2xl border text-left transition-all', selected ? 'border-brand bg-orange-50/60 ring-4 ring-brand/10' : 'border-slate-200 bg-white hover:border-slate-300');

/** Bộ tăng giảm số lượng thu gọn dùng trong danh mục đồ đạc. */
export const CompactStepper = ({ value, onChange, label, max = 99 }: { value: number; onChange: (n: number) => void; label: string; max?: number }) => (
  <div className="flex h-8 shrink-0 items-stretch overflow-hidden rounded-lg border border-slate-200 bg-white">
    <button type="button" className="w-8 text-base font-semibold text-slate-600 hover:bg-slate-50 disabled:text-slate-300" onClick={() => onChange(Math.max(0, value - 1))} disabled={value <= 0} aria-label={`Giảm ${label}`}>
      −
    </button>
    <span className={cx('grid w-8 place-items-center border-x border-slate-200 text-sm font-semibold tabular-nums', value > 0 ? 'text-slate-900' : 'text-slate-400')}>{value}</span>
    <button type="button" className="w-8 text-base font-semibold text-slate-600 hover:bg-slate-50 disabled:text-slate-300" onClick={() => onChange(Math.min(max, value + 1))} disabled={value >= max} aria-label={`Tăng ${label}`}>
      +
    </button>
  </div>
);

export const FieldError = ({ message }: { message?: string | null }) =>
  message ? <p className="text-xs font-medium text-rose-600" role="alert">{message}</p> : null;
