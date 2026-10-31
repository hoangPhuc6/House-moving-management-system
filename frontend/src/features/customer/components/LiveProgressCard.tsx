import { CheckCircle2, Lock, Phone } from 'lucide-react';
import { Card, cx, Divider, StatusBadge } from '@/components/ui';
import { FIELD_STAGE_ORDER, FIELD_STAGES } from '@/constants/order';
import { STAFF_ROLE_LABEL } from '@/constants/resources';
import type { Order } from '@/types';

export const LiveProgressCard = ({
  order,
  vehicle,
  capacity,
  crew,
}: {
  order: Order;
  vehicle?: string;
  capacity?: number;
  crew: { id: string; name: string; role: keyof typeof STAFF_ROLE_LABEL }[];
}) => {
  const stageIdx = FIELD_STAGE_ORDER.indexOf(order.fieldStage);
  const paused = order.status === "PAUSED_WAITING_CUSTOMER";
  const leader = crew.find((c) => c.role === "TEAM_LEADER") ?? crew[0];
  return (
    <Card className="lg:row-span-2">
      <div className="mb-4 flex items-center justify-between">
        <h3 className="text-[15px] font-semibold">Tiến độ trực tiếp</h3>
        <StatusBadge status={order.status} />
      </div>
      <ol>
        {FIELD_STAGES.map((s, i) => {
          const idx = i + 1;
          const done = stageIdx >= idx;
          const current =
            stageIdx + 1 === idx &&
            (order.status === "ASSIGNED" ||
              order.status === "IN_TRANSIT" ||
              paused);
          const locked =
            (s.stage === "WORKING" || s.stage === "MOVING") &&
            order.flags.hasFieldDiscrepancyLock &&
            !done;
          return (
            <li key={s.stage} className="relative flex gap-3.5 pb-5 last:pb-0">
              {i < FIELD_STAGES.length - 1 && (
                <span
                  className={cx(
                    "absolute top-7 bottom-0 left-[13px] w-0.5",
                    done ? "bg-brand" : "bg-slate-200",
                  )}
                />
              )}
              <span
                className={cx(
                  "relative z-10 grid size-7 shrink-0 place-items-center rounded-full",
                  locked
                    ? "bg-rose-50 text-rose-600 ring-2 ring-rose-300"
                    : done
                      ? "bg-brand text-white"
                      : current
                        ? "bg-white text-brand ring-2 ring-brand"
                        : "bg-slate-100 text-slate-400",
                )}
              >
                {locked ? (
                  <Lock className="size-3" />
                ) : done ? (
                  <CheckCircle2 className="size-4" />
                ) : (
                  <span className="text-[11px] font-bold">{idx}</span>
                )}
              </span>
              <div className="pt-0.5">
                <p
                  className={cx(
                    "text-sm font-semibold",
                    done || current ? "text-slate-900" : "text-slate-400",
                  )}
                >
                  {s.label.replace(" (Bắt đầu vận chuyển)", "")}
                </p>
                {current && !locked && (
                  <p className="text-xs text-brand">
                    {paused ? "Đang chờ bạn…" : "Đang thực hiện…"}
                  </p>
                )}
                {locked && (
                  <p className="text-xs font-medium text-rose-600">
                    Chờ bạn xác nhận báo giá điều chỉnh
                  </p>
                )}
              </div>
            </li>
          );
        })}
      </ol>
      <Divider className="my-4" />
      <div className="flex items-center gap-3">
        <div className="rounded-lg border-2 border-slate-900 bg-amber-50 px-2.5 py-1 font-mono text-sm font-bold tracking-wider tabular-nums">
          {vehicle ?? "—"}
        </div>
        <div className="min-w-0 flex-1 text-xs text-slate-500">
          <p className="font-semibold text-slate-800">
            {capacity
              ? `Xe tải ${String(capacity).replace(".", ",")} tấn`
              : "Chưa phân xe"}
          </p>
          <p className="truncate">
            {leader
              ? `${leader.name} · ${crew.length} nhân sự`
              : "Chưa phân đội"}
          </p>
        </div>
        <a
          href="tel:0909000000"
          className="grid size-10 place-items-center rounded-xl border border-slate-200 text-slate-700"
          aria-label="Gọi đội xe"
        >
          <Phone className="size-4" />
        </a>
      </div>
    </Card>
  );
};
