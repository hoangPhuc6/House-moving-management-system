import { cx } from '@/components/ui';
import { PROCESS_STEPS } from '@/constants/order';
import { stepIndexOf } from '@/services/order';
import type { Order } from '@/types';

export const CompactProgress = ({ order }: { order: Order }) => {
  const idx = stepIndexOf(order.status);
  const done = order.status === "COMPLETED";
  const cancelled = order.status === "CANCELLED_EXPIRED";
  const step = PROCESS_STEPS[Math.max(0, idx)];
  return (
    <div className="md:hidden">
      <div className="flex gap-1">
        {PROCESS_STEPS.map((s, i) => (
          <div
            key={s.title}
            className={cx(
              "h-1.5 flex-1 rounded-full",
              cancelled
                ? "bg-slate-200"
                : i < idx || done
                  ? "bg-brand"
                  : i === idx
                    ? "bg-brand/40"
                    : "bg-slate-200",
            )}
          />
        ))}
      </div>
      {!cancelled && step && (
        <p className="mt-2 text-xs text-slate-500">
          Bước <b className="text-slate-900 tabular-nums">{idx + 1}/7</b> ·{" "}
          {step.title}
        </p>
      )}
    </div>
  );
};
