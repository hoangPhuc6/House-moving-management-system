import { type ReactNode } from 'react';
import { type LucideIcon } from 'lucide-react';

export const Block = ({
  title,
  icon: Icon,
  right,
  children,
}: {
  title: string;
  icon?: LucideIcon;
  right?: ReactNode;
  children: ReactNode;
}) => (
  <section className="rounded-2xl bg-white p-4 ring-1 ring-slate-200">
    <div className="mb-3 flex items-center justify-between">
      <h3 className="flex items-center gap-2 text-sm font-semibold text-slate-900">
        {Icon && <Icon className="size-4 text-slate-400" />}
        {title}
      </h3>
      {right}
    </div>
    {children}
  </section>
);
