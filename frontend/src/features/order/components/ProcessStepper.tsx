import { Check } from 'lucide-react';
import { cx } from '@/components/ui';
import { PROCESS_STEPS } from '@/constants/order';
import { stepIndexOf } from '@/services/order';
import type { OrderStatus } from '@/types';

/** Stepper 7 bước ánh xạ từ OrderStatus. */
export const ProcessStepper = ({ status }: { status: OrderStatus }) => {
  const current = stepIndexOf(status);
  const cancelled = status === 'CANCELLED_EXPIRED';
  const done = status === 'COMPLETED';
  return (
    // overflow-x-auto cũng cắt theo chiều dọc → chừa py-2 (bù bằng -my-2) cho ring/halo của bước hiện tại.
    <ol className="scrollbar-none -mx-1 -my-2 flex overflow-x-auto px-1 py-2">

      {PROCESS_STEPS.map((step, i) => {
        const isDone = !cancelled && (i < current || done);
        const isCurrent = !cancelled && !done && i === current;
        return (
          <li key={step.title} className="flex min-w-[112px] flex-1 flex-col items-center text-center">
            <div className="flex w-full items-center">
              <div className={cx('h-0.5 flex-1', i === 0 ? 'invisible' : isDone || isCurrent ? 'bg-brand' : 'bg-slate-200')} />
              <div
                className={cx(
                  'relative grid size-8 shrink-0 place-items-center rounded-full text-xs font-bold tabular-nums transition-colors',
                  isDone && 'bg-brand text-white',
                  isCurrent && 'bg-white text-brand ring-2 ring-brand shadow-[0_0_0_6px_rgba(255,90,31,0.14)]',
                  !isDone && !isCurrent && 'bg-slate-100 text-slate-400 ring-1 ring-slate-200',
                )}
              >
                {isDone ? <Check className="size-4" strokeWidth={3} /> : i + 1}
              </div>
              <div className={cx('h-0.5 flex-1', i === PROCESS_STEPS.length - 1 ? 'invisible' : isDone ? 'bg-brand' : 'bg-slate-200')} />
            </div>
            <p className={cx('mt-2 text-xs font-semibold', isCurrent ? 'text-slate-900' : isDone ? 'text-slate-700' : 'text-slate-400')}>{step.title}</p>
          </li>
        );
      })}
    </ol>
  );
};
