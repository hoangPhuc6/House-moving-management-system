import { PauseCircle } from 'lucide-react';
import { Badge, Card, cx } from '@/components/ui';
import { WAIT_FEE_PER_HOUR, WAIT_FREE_MINUTES } from '@/constants/pricing';
import { formatVND } from '@/services/format';
import { billableWaitHours, waitFee } from '@/services/pricing';
import type { Order } from '@/types';

export const WaitTimerCard = ({ order }: { order: Order }) => {
  const paused = order.status === "PAUSED_WAITING_CUSTOMER";
  const fee = waitFee(order.flags.waitingMinutes);
  const hours = billableWaitHours(order.flags.waitingMinutes);
  const pct = Math.min(
    100,
    (order.flags.waitingMinutes / WAIT_FREE_MINUTES) * 100,
  );
  return (
    <Card className={cx(paused && "border-amber-300 ring-4 ring-amber-100")}>
      <div className="mb-4 flex items-center justify-between">
        <h3 className="flex items-center gap-2 text-[15px] font-semibold">
          <PauseCircle className="size-4 text-amber-600" /> Thời gian chờ
        </h3>
        {paused ? (
          <Badge tone="warning" dot pulse>
            Đội xe đang chờ
          </Badge>
        ) : (
          <Badge>Đã kết thúc</Badge>
        )}
      </div>
      <div className="flex items-end justify-between">
        <p className="text-4xl font-bold tracking-tight tabular-nums">
          {order.flags.waitingMinutes}
          <span className="text-base font-semibold text-slate-400"> phút</span>
        </p>
        <div className="text-right">
          <p className="text-xs text-slate-500">Phí chờ</p>
          <p
            className={cx(
              "text-lg font-bold tabular-nums",
              fee > 0 ? "text-amber-600" : "text-emerald-600",
            )}
          >
            {fee > 0 ? `+${formatVND(fee)}` : "Miễn phí"}
          </p>
        </div>
      </div>
      <div className="mt-4 h-2 overflow-hidden rounded-full bg-slate-100">
        <div
          className={cx(
            "h-2 rounded-full transition-all",
            fee > 0 ? "bg-amber-500" : "bg-emerald-500",
          )}
          style={{ width: `${pct}%` }}
        />
      </div>
      <p className="mt-3 text-xs text-slate-500">
        {hours > 0
          ? `${WAIT_FREE_MINUTES} phút đầu miễn phí · đã tính ${hours} giờ × ${formatVND(WAIT_FEE_PER_HOUR)}`
          : `Miễn phí ${WAIT_FREE_MINUTES} phút đầu, sau đó ${formatVND(WAIT_FEE_PER_HOUR)}/giờ.`}
      </p>
    </Card>
  );
};
