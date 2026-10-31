import { useState } from 'react';
import { Calendar } from 'lucide-react';
import { cx } from '@/components/ui';
import { PROCESS_STEPS } from '@/constants/order';
import { STEP_ICONS } from '@/features/landing/constants';

export const ProcessTimeline = () => {
  const [active, setActive] = useState(0);
  const step = PROCESS_STEPS[active] ?? PROCESS_STEPS[0]!;
  const ActiveIcon = STEP_ICONS[active] ?? Calendar;
  return (
    <section
      id="process"
      className="scroll-mt-20 border-y border-slate-200 bg-white py-20 sm:py-28"
    >
      <div className="mx-auto max-w-7xl px-4 sm:px-6">
        <div className="mb-12 flex flex-wrap items-end justify-between gap-4">
          <div className="max-w-xl">
            <p className="text-sm font-semibold text-brand">
              Quy trình chuẩn hóa
            </p>
            <h2 className="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">
              7 bước từ đặt lịch đến bàn giao
            </h2>
          </div>
          <p className="max-w-sm text-sm text-slate-500">
            Nhấn vào từng bước để xem chi tiết. Mỗi bước tương ứng một trạng
            thái trong hệ thống.
          </p>
        </div>

        <div className="scrollbar-none -mx-4 overflow-x-auto px-4">
          <ol className="relative flex min-w-[760px]">
            <div className="absolute top-8 right-[7%] left-[7%] h-0.5 bg-slate-200" />
            <div
              className="absolute top-8 left-[7%] h-0.5 bg-brand transition-all duration-500"
              style={{
                width: `${(active / (PROCESS_STEPS.length - 1)) * 86}%`,
              }}
            />
            {PROCESS_STEPS.map((s, i) => {
              const Icon = STEP_ICONS[i] ?? Calendar;
              const reached = i <= active;
              return (
                <li
                  key={s.title}
                  className="relative flex flex-1 flex-col items-center"
                >
                  <button
                    type="button"
                    onClick={() => setActive(i)}
                    onMouseEnter={() => setActive(i)}
                    className={cx(
                      "relative z-10 top-2 grid size-12 place-items-center rounded-2xl transition-all",
                      i === active
                        ? "scale-110 bg-brand text-white shadow-lg shadow-brand/30"
                        : reached
                          ? "bg-slate-900 text-white"
                          : "border border-slate-200 bg-white text-slate-400",
                    )}
                    aria-label={s.title}
                  >
                    <Icon className="size-5" />
                  </button>
                  <span className="mt-3 top-2 text-[11px] font-semibold text-slate-400 tabular-nums">
                    BƯỚC {i + 1}
                  </span>
                  <span
                    className={cx(
                      "mt-0.5 text-sm font-semibold",
                      i === active ? "text-slate-900" : "text-slate-500",
                    )}
                  >
                    {s.title}
                  </span>
                </li>
              );
            })}
          </ol>
        </div>

        <div
          key={active}
          className="anim-pop mx-auto mt-10 flex max-w-3xl flex-col gap-5 rounded-3xl border border-slate-200 bg-canvas p-6 sm:flex-row sm:items-center sm:p-8"
        >
          <div className="grid size-14 shrink-0 place-items-center rounded-2xl bg-white ring-1 ring-slate-200">
            <ActiveIcon className="size-6 text-brand" />
          </div>
          <div className="flex-1">
            <p className="text-xs font-semibold tracking-wider text-slate-400 uppercase">
              Bước {active + 1} · {step.short}
            </p>
            <h3 className="mt-1 text-xl font-semibold tracking-tight">
              {step.title}
            </h3>
            <p className="mt-1.5 text-sm leading-relaxed text-slate-600">
              {step.detail}
            </p>
          </div>
          <div className="flex flex-wrap gap-1.5 sm:max-w-[180px] sm:flex-col">
            {step.statuses.map((st) => (
              <code
                key={st}
                className="rounded-md bg-slate-900 px-2 py-1 font-mono text-[10px] text-white/80"
              >
                {st}
              </code>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
};
