import { type ReactNode } from 'react';
import { type LucideIcon } from 'lucide-react';
import { cx } from '@/components/ui';

export const BentoCard = ({
  icon: Icon,
  eyebrow,
  title,
  desc,
  className,
  children,
}: {
  icon: LucideIcon;
  eyebrow: string;
  title: string;
  desc: string;
  className?: string;
  children: ReactNode;
}) => (
  <div
    className={cx(
      "group flex flex-col overflow-hidden rounded-3xl border border-slate-200/80 bg-white p-6 transition-shadow hover:shadow-xl hover:shadow-slate-900/5 sm:p-7",
      className,
    )}
  >
    <div className="mb-5 flex-1">{children}</div>
    <div className="flex items-center gap-2 text-xs font-semibold tracking-wider text-brand uppercase">
      <Icon className="size-4" /> {eyebrow}
    </div>
    <h3 className="mt-2 text-xl font-semibold tracking-tight text-slate-900">
      {title}
    </h3>
    <p className="mt-2 text-sm leading-relaxed text-slate-500">{desc}</p>
  </div>
);
