import { type ReactNode } from 'react';

export const SectionTitle = ({
  children,
  action,
}: {
  children: ReactNode;
  action?: ReactNode;
}) => (
  <div className="mb-3 flex items-center justify-between px-1">
    <h2 className="text-[15px] font-semibold text-slate-900">{children}</h2>
    {action}
  </div>
);
