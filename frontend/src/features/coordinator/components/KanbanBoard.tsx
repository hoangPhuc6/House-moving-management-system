import { useState } from 'react';
import { PhoneCall, Siren } from 'lucide-react';
import { Badge, cx, StatusBadge } from '@/components/ui';
import { AUTO_CANCEL_MS, TELESALES_THRESHOLD_MS } from '@/constants/pricing';
import { COLUMNS } from '@/features/coordinator/constants';
import type { CoordTab } from '@/features/coordinator/types';
import { AutoCancelCountdown } from '@/features/order/components/AutoCancelCountdown';
import { coordinatorSurveyBadge } from '@/features/order/surveyStage';
import { routeLabelOf } from '@/features/order/route';
import { FieldStageBadge } from '@/features/order/components/FieldProgress';
import { hasFieldProgress } from '@/features/order/fieldProgress';
import { formatVND } from '@/services/format';
import { computeTotals } from '@/services/pricing';
import { useApp, useNow } from '@/services/store/AppStore';

const COLUMN_PAGE = 15;

/** Kanban toàn bộ đơn theo trạng thái — tab "Tất cả đơn" của trang Hàng đợi. */
export const KanbanBoard = ({
  onOpen,
  query,
}: {
  onOpen: (id: string, t: CoordTab) => void;
  query: string;
}) => {
  const { state } = useApp();
  const now = useNow(1000);
  // Số thẻ đang hiện ở mỗi cột — cột dài chỉ render một phần, bấm "xem thêm" để mở rộng.
  const [limits, setLimits] = useState<Record<string, number>>({});

  return (
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
          // Cột Đóng / Hủy tích lũy theo thời gian — đơn mới nhất lên đầu.
          if (col.statuses.includes("COMPLETED")) items.sort((a, b) => b.createdAt - a.createdAt);
          const limit = limits[col.title] ?? COLUMN_PAGE;
          const shown = items.slice(0, limit);
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
              <div className="flex max-h-[calc(100dvh-260px)] flex-col gap-2 overflow-y-auto pr-0.5">
                {shown.map((o) => {
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
                              : "quote",
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
                        {routeLabelOf(o)}
                      </p>
                      <div className="mt-2.5 flex flex-wrap gap-1.5">
                        {o.status === "SURVEYED_PENDING_CONFIRM" &&
                        o.autoCancelDeadline ? (
                          <AutoCancelCountdown
                            deadline={o.autoCancelDeadline}
                            compact
                          />
                        ) : (
                          hasFieldProgress(o) ? <FieldStageBadge order={o} /> : <StatusBadge status={o.status} />
                        )}
                        {o.negotiation && <Badge tone="warning">Khách thương lượng</Badge>}
                        {o.status === "NEW_PENDING_SURVEY" && (() => {
                          const b = coordinatorSurveyBadge(o);
                          return <Badge tone={b.tone}>{b.label}</Badge>;
                        })()}
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
                {items.length > shown.length && (
                  <button
                    type="button"
                    onClick={() => setLimits((m) => ({ ...m, [col.title]: limit + COLUMN_PAGE }))}
                    className="rounded-xl border border-dashed border-slate-300 py-2 text-xs font-semibold text-slate-600 hover:border-slate-400 hover:bg-white"
                  >
                    + {items.length - shown.length} đơn nữa
                  </button>
                )}
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
  );
};
