import { Link } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import { Badge, cx } from '@/components/ui';
import { customerActionOf, customerStatusOf, needsAction, routeLabelOf } from '@/features/customer/utils';
import { AutoCancelCountdown } from '@/features/order/components/AutoCancelCountdown';
import { shortDate } from '@/features/order/surveyStage';
import type { Order } from '@/types';

/**
 * Thẻ một đơn trong danh sách: trạng thái, lộ trình rút gọn, ngày chuyển và — nếu khách cần xử lý — dòng việc cần làm.
 * `compact` dùng cho cột danh sách bên trái trang chi tiết (desktop).
 */
export const OrderCard = ({ order, to, selected = false, compact = false }: { order: Order; to: string; selected?: boolean; compact?: boolean }) => {
  const status = customerStatusOf(order);
  const urgent = needsAction(order);
  const action = urgent ? customerActionOf(order) : null;

  return (
    <Link
      to={to}
      aria-current={selected ? 'page' : undefined}
      className={cx(
        'group block min-w-0 rounded-2xl border bg-white transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand/40',
        compact ? 'p-3' : 'p-4',
        selected ? 'border-slate-900 shadow-[inset_3px_0_0_#0F172A]' : urgent ? 'border-brand/40 hover:border-brand' : 'border-slate-200 hover:border-slate-300',
      )}
    >
      <div className="flex items-center justify-between gap-2">
        <Badge tone={status.tone} className="min-w-0 truncate">
          {status.label}
        </Badge>
        <span className="shrink-0 text-xs font-medium text-slate-500 tabular-nums">{shortDate(order.scheduledDate)}</span>
      </div>
      <p className={cx('mt-2 truncate font-semibold text-slate-900', compact ? 'text-sm' : 'text-[15px]')}>{routeLabelOf(order)}</p>
      {!compact && <p className="mt-0.5 text-xs text-slate-400 tabular-nums">#{order.id} · {order.timeSlot}</p>}
      {action && (
        <div className="mt-2.5 flex flex-wrap items-center justify-between gap-2 rounded-xl bg-orange-50 px-3 py-2 text-xs font-semibold text-brand">
          <span className="inline-flex min-w-0 items-center gap-1.5">
            <action.icon className="size-3.5 shrink-0" />
            <span className="truncate">{action.label}</span>
          </span>
          {order.status === 'SURVEYED_PENDING_CONFIRM' && order.autoCancelDeadline ? (
            <AutoCancelCountdown deadline={order.autoCancelDeadline} compact />
          ) : (
            !compact && <ChevronRight className="size-4 shrink-0 transition-transform group-hover:translate-x-0.5" />
          )}
        </div>
      )}
    </Link>
  );
};
