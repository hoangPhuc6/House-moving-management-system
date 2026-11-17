import { History } from 'lucide-react';
import { Card, CardHeader, cx } from '@/components/ui';
import { formatTime } from '@/services/format';
import type { Order } from '@/types';

/** Mục "Nhật ký" của trang chi tiết đơn — mọi thao tác trên đơn, mới nhất lên đầu. */
export const OrderLogPanel = ({ order }: { order: Order }) => (
  <Card>
    <CardHeader
      title="Nhật ký thao tác"
      subtitle={`${order.auditLog.length} sự kiện`}
      icon={History}
    />
    <ol className="relative space-y-4 border-l border-slate-200 pl-5">
      {[...order.auditLog].reverse().map((l) => (
        <li key={l.id} className="relative">
          <span
            className={cx(
              "absolute top-1 -left-[25px] size-2.5 rounded-full ring-4 ring-white",
              l.actorRole === "COORDINATOR"
                ? "bg-brand"
                : l.actorRole === "CUSTOMER"
                  ? "bg-blue-500"
                  : l.actorRole === "STAFF"
                    ? "bg-emerald-500"
                    : "bg-slate-400",
            )}
          />
          <p className="text-sm font-semibold text-slate-900">
            {l.action}
          </p>
          <p className="mt-0.5 text-sm text-slate-600">{l.detail}</p>
          <p className="mt-1 text-xs text-slate-400">
            {l.actor} ·{" "}
            <span className="tabular-nums">
              {formatTime(l.timestamp)}
            </span>
          </p>
        </li>
      ))}
      {order.auditLog.length === 0 && (
        <li className="text-sm text-slate-400">Chưa có lịch sử.</li>
      )}
    </ol>
  </Card>
);
