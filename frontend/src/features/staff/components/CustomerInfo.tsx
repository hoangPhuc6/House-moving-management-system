import { UserRound } from 'lucide-react';
import type { Order } from '@/types';

export const CustomerInfo = ({ order, compact }: { order: Order; compact?: boolean }) =>
  compact ? (
    <p className="mt-0.5 flex items-center gap-1.5 text-sm text-slate-800">
      <UserRound className="size-3.5 shrink-0 text-slate-400" />
      <span className="truncate font-semibold">{order.customerName}</span>
      <span className="shrink-0 text-slate-400">·</span>
      <span className="shrink-0 tabular-nums">{order.phone}</span>
    </p>
  ) : (
    <div className="flex items-center gap-3 rounded-xl bg-slate-50 p-3">
      <span className="grid size-10 shrink-0 place-items-center rounded-full bg-white text-slate-500 ring-1 ring-slate-200">
        <UserRound className="size-5" />
      </span>
      <div className="min-w-0 flex-1">
        <p className="text-xs text-slate-500">Khách hàng</p>
        <p className="truncate text-sm font-semibold text-slate-900">{order.customerName}</p>
        <a href={`tel:${order.phone.replace(/\s/g, "")}`} className="text-sm font-semibold text-brand tabular-nums">
          {order.phone}
        </a>
      </div>
    </div>
  );
