import { useState } from 'react';
import { cx } from '@/components/ui';
import { ROLE_FLOWS } from '@/features/landing/constants';

/** Luồng làm việc của từng vai trò — mỗi bước tương ứng một màn hình / thao tác có thật trong ứng dụng. */
export const RoleFlows = () => {
  const [active, setActive] = useState(0);
  const flow = ROLE_FLOWS[active] ?? ROLE_FLOWS[0]!;
  const FlowIcon = flow.icon;

  return (
    <section id="roles" className="scroll-mt-20 bg-canvas py-20 sm:py-28">
      <div className="mx-auto max-w-7xl px-4 sm:px-6">
        <div className="mx-auto mb-10 max-w-2xl text-center">
          <p className="text-sm font-semibold text-brand">Một hệ thống, bốn vai trò</p>
          <h2 className="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">Mỗi người một màn hình, cùng một đơn hàng</h2>
          <p className="mt-4 text-slate-500">
            Khách hàng, nhân viên vận chuyển, điều phối viên và admin cùng làm việc trên một nguồn dữ liệu — thao tác của người này cập nhật ngay cho người kia.
          </p>
        </div>

        <div role="tablist" className="scrollbar-none -mx-4 flex gap-2 overflow-x-auto px-4 sm:mx-0 sm:justify-center sm:px-0">
          {ROLE_FLOWS.map((f, i) => {
            const Icon = f.icon;
            return (
              <button
                key={f.id}
                type="button"
                role="tab"
                aria-selected={i === active}
                onClick={() => setActive(i)}
                className={cx(
                  'inline-flex h-11 shrink-0 items-center gap-2 rounded-xl px-4 text-sm font-semibold transition-colors',
                  i === active ? 'bg-slate-900 text-white' : 'border border-slate-200 bg-white text-slate-600 hover:border-slate-300',
                )}
              >
                <Icon className="size-4" /> {f.role}
              </button>
            );
          })}
        </div>

        <div key={flow.id} className="anim-pop mt-8 grid gap-6 rounded-3xl border border-slate-200 bg-white p-6 sm:p-8 lg:grid-cols-[1fr_2fr]">
          <div>
            <span className="grid size-12 place-items-center rounded-2xl bg-brand/10">
              <FlowIcon className="size-6 text-brand" />
            </span>
            <p className="mt-4 text-xs font-semibold tracking-wider text-slate-400 uppercase">{flow.app}</p>
            <h3 className="mt-1 text-2xl font-semibold tracking-tight">{flow.role}</h3>
            <p className="mt-2 text-sm leading-relaxed text-slate-500">{flow.summary}</p>
          </div>
          <ol className="relative space-y-5 border-l border-slate-200 pl-6">
            {flow.steps.map((s, i) => (
              <li key={s.title} className="relative">
                <span className="absolute top-0 -left-[37px] grid size-6 place-items-center rounded-full bg-slate-900 text-[11px] font-bold text-white tabular-nums">
                  {i + 1}
                </span>
                <p className="font-semibold text-slate-900">{s.title}</p>
                <p className="mt-1 text-sm leading-relaxed text-slate-500">{s.desc}</p>
              </li>
            ))}
          </ol>
        </div>
      </div>
    </section>
  );
};
