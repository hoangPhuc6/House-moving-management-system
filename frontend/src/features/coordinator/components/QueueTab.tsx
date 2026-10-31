import { Clock, KanbanSquare, PhoneCall, PlayCircle, Siren, Truck } from 'lucide-react';
import { Badge, Button, cx, PageHeader, Stat, StatusBadge } from '@/components/ui';
import { SURVEY_METHOD_META } from '@/constants/order';
import { AUTO_CANCEL_MS, TELESALES_THRESHOLD_MS } from '@/constants/pricing';
import { COLUMNS } from '@/features/coordinator/constants';
import type { CoordTab } from '@/features/coordinator/types';
import { AutoCancelCountdown } from '@/features/order/components/AutoCancelCountdown';
import { formatVND } from '@/services/format';
import { computeTotals } from '@/services/pricing';
import { useApp, useNow } from '@/services/store/AppStore';

export const QueueTab = ({
  onOpen,
  query,
}: {
  onOpen: (id: string, t: CoordTab) => void;
  query: string;
}) => {
  const { state, dispatch } = useApp();
  const now = useNow(1000);
  const pending = state.orders.filter(
    (o) => o.status === "SURVEYED_PENDING_CONFIRM",
  );
  const expired = pending.filter(
    (o) => o.autoCancelDeadline !== null && o.autoCancelDeadline <= now,
  ).length;
  const available = state.resources.vehicles.filter(
    (v) => v.status === "AVAILABLE",
  ).length;

  return (
    <>
      <PageHeader
        eyebrow="Ca sáng · Thứ Năm, 29/10/2026"
        title="Hàng đợi đơn hàng"
        subtitle="Theo dõi vòng đời đơn hàng. Đơn chưa chốt sau 24h sẽ tự động hủy."
        actions={
          <Button
            variant="dark"
            icon={PlayCircle}
            onClick={() => dispatch({ type: "RUN_AUTO_CANCEL_CRON" })}
          >
            Quét đơn quá hạn 24h{" "}
            {expired > 0 && (
              <span className="rounded-full bg-rose-500 px-1.5 text-[11px] tabular-nums">
                {expired}
              </span>
            )}
          </Button>
        }
      />
      <div className="mb-4 grid grid-cols-2 gap-4 xl:grid-cols-3">
        <Stat
          label="Tổng đơn đang mở"
          value={
            state.orders.filter(
              (o) => !["COMPLETED", "CANCELLED_EXPIRED"].includes(o.status),
            ).length
          }
          icon={KanbanSquare}
          tone="info"
          hint="Toàn hệ thống"
        />
        <Stat
          label="Chờ chốt báo giá"
          value={pending.length}
          icon={Clock}
          tone="warning"
          hint={`${expired} đơn đã quá hạn 24h`}
        />
        <Stat
          label="Xe sẵn sàng"
          value={`${available}/${state.resources.vehicles.length}`}
          icon={Truck}
          tone="success"
          hint="Cập nhật theo thời gian thực"
        />
      </div>

      <div className="scrollbar-none -mx-2 overflow-x-auto px-2 pb-2">
        <div className="grid min-w-[1080px] grid-cols-6 gap-3">
          {COLUMNS.map((col) => {
            const q = query.trim().toLowerCase();
            const items = state.orders.filter(
              (o) =>
                col.statuses.includes(o.status) &&
                (!q ||
                  `${o.id} ${o.customerName} ${o.phone}`
                    .toLowerCase()
                    .includes(q)),
            );
            return (
              <div
                key={col.title}
                className="flex flex-col rounded-2xl border border-slate-200/80 bg-slate-100/50 p-2"
              >
                <div className="flex items-center justify-between px-2 pt-1 pb-3">
                  <span className="flex items-center gap-2 text-[13px] font-semibold text-slate-700">
                    <span className={cx("size-2 rounded-full", col.accent)} />{" "}
                    {col.title}
                  </span>
                  <span className="text-xs font-semibold text-slate-400 tabular-nums">
                    {items.length}
                  </span>
                </div>
                <div className="flex flex-col gap-2">
                  {items.map((o) => {
                    const surveyedAgo = o.autoCancelDeadline
                      ? AUTO_CANCEL_MS - (o.autoCancelDeadline - now)
                      : 0;
                    const needsCall =
                      o.status === "SURVEYED_PENDING_CONFIRM" &&
                      surveyedAgo > TELESALES_THRESHOLD_MS;
                    return (
                      <button
                        key={o.id}
                        type="button"
                        onClick={() =>
                          onOpen(
                            o.id,
                            o.status === "QUOTED_PENDING_SCHEDULE"
                              ? "dispatch"
                              : o.flags.isVehicleIncident
                                ? "incident"
                                : "editor",
                          )
                        }
                        className={cx(
                          "rounded-xl border bg-white p-3 text-left shadow-[0_1px_2px_rgba(15,23,42,0.04)] transition-all hover:-translate-y-0.5 hover:shadow-md",
                          o.flags.isVehicleIncident
                            ? "border-rose-300"
                            : "border-slate-200",
                        )}
                      >
                        <div className="flex items-center justify-between gap-2">
                          <span className="text-[13px] font-bold tabular-nums">
                            #{o.id.replace("ORD-2026-", "ORD-")}
                          </span>
                          {o.flags.isVehicleIncident && (
                            <Siren className="size-4 text-rose-600" />
                          )}
                        </div>
                        <p className="mt-1 truncate text-sm font-medium text-slate-800">
                          {o.customerName}
                        </p>
                        <p className="truncate text-xs text-slate-500">
                          {o.toAddress}
                        </p>
                        <div className="mt-2.5 flex flex-wrap gap-1.5">
                          {o.status === "SURVEYED_PENDING_CONFIRM" &&
                          o.autoCancelDeadline ? (
                            <AutoCancelCountdown
                              deadline={o.autoCancelDeadline}
                              compact
                            />
                          ) : (
                            <StatusBadge status={o.status} />
                          )}
                          {o.status === "NEW_PENDING_SURVEY" && (
                            <Badge
                              tone={
                                o.onSiteSurveyRequest ? "warning" : "neutral"
                              }
                            >
                              {
                                SURVEY_METHOD_META[
                                  o.onSiteSurveyRequest ? "ON_SITE" : "PHOTO"
                                ].short
                              }
                            </Badge>
                          )}
                          {needsCall && (
                            <span className="flex w-full items-start gap-1.5 rounded-lg bg-orange-50 px-2 py-1.5 text-[11px] leading-snug font-semibold text-brand ring-1 ring-brand/15 ring-inset">
                              <PhoneCall className="mt-px size-3 shrink-0" />{" "}
                              Cần gọi Tele-sales chăm sóc (&gt;2h chưa chốt)
                            </span>
                          )}
                        </div>
                        {o.quoteItems.length > 0 && (
                          <p className="mt-2 text-xs font-semibold text-slate-600 tabular-nums">
                            {formatVND(computeTotals(o).grandTotal)}
                          </p>
                        )}
                      </button>
                    );
                  })}
                  {items.length === 0 && (
                    <p className="px-2 py-6 text-center text-xs text-slate-400">
                      Trống
                    </p>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </>
  );
};
