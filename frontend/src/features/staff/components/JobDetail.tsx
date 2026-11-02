import { Link } from 'react-router-dom';
import {
  AlertOctagon,
  ArrowLeft,
  CheckCircle2,
  Lock,
  Navigation,
  Phone,
  Siren,
  StopCircle,
  Timer,
  UserRound,
  Wrench,
} from 'lucide-react';
import { Button, cx, StatusBadge } from '@/components/ui';
import { FIELD_STAGE_LABEL, FIELD_STAGE_ORDER, FIELD_STAGES } from '@/constants/order';
import { STAFF_ROLE_LABEL } from '@/constants/resources';
import { Block } from '@/features/staff/components/Block';
import { CustomerInfo } from '@/features/staff/components/CustomerInfo';
import { RUNNING, STAGE_ICONS } from '@/features/staff/constants';
import { StickyActionBar } from '@/layouts/MobileShell';
import { ROUTES } from '@/routes/paths';
import { formatDateVN, formatVND } from '@/services/format';
import { waitFee } from '@/services/pricing';
import { useApp } from '@/services/store/AppStore';
import type { FieldStage, Order } from '@/types';

export const JobDetail = ({ order }: { order: Order }) => {
  const { state, dispatch } = useApp();
  const vehicle = state.resources.vehicles.find(
    (v) => v.id === order.vehicleId,
  );
  const crew = state.resources.staff.filter((s) =>
    order.crewIds.includes(s.id),
  );
  const stageIdx = FIELD_STAGE_ORDER.indexOf(order.fieldStage);
  const next = FIELD_STAGES[stageIdx];
  const locked = order.flags.hasFieldDiscrepancyLock;
  const paused = order.status === "PAUSED_WAITING_CUSTOMER";
  const active = RUNNING.includes(order.status);
  const nextLocked =
    !!next && (next.stage === "WORKING" || next.stage === "MOVING") && locked;
  const incidentBlock =
    !!next && next.stage === "MOVING" && order.flags.isVehicleIncident;
  const blocked = nextLocked || incidentBlock;
  const NextIcon = next
    ? STAGE_ICONS[next.stage as Exclude<FieldStage, "NOT_STARTED">]
    : CheckCircle2;
  const checked = order.equipment.filter((e) => e.checked).length;
  // Trước khi xác nhận bước 2 (Bắt đầu làm): chỉ đường tới nơi đi để bốc hàng; sau đó: tới nơi đến.
  const headingToDestination = stageIdx >= FIELD_STAGE_ORDER.indexOf("WORKING");
  const navTarget = headingToDestination ? order.toAddress : order.fromAddress;

  return (
    <div className="space-y-3 pb-24 md:pb-0">
      <Link
        to={ROUTES.staff.jobs}
        className="-ml-1 inline-flex h-10 items-center gap-1.5 rounded-xl px-2 text-sm font-semibold text-slate-600 hover:bg-slate-100"
      >
        <ArrowLeft className="size-4" /> Tất cả lệnh việc
      </Link>

      {/* Header card */}
      <section className="overflow-hidden rounded-2xl bg-white ring-1 ring-slate-200">
        <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
          <span className="text-sm font-semibold tabular-nums">
            Lệnh #{order.id}
          </span>
          <StatusBadge status={order.status} />
        </div>
        <div className="p-4">
          <div className="mb-4"><CustomerInfo order={order} /></div>
          {vehicle && (
            <div className="mb-4 flex items-center gap-3">
              <div className="rounded-xl border-2 border-slate-900 bg-amber-50 px-3 py-1.5 font-mono text-xl font-bold tracking-wider tabular-nums">
                {vehicle.plateNumber}
              </div>
              <div className="text-xs text-slate-500">
                <p className="font-semibold text-slate-800">
                  Xe tải {String(vehicle.capacityTons).replace(".", ",")} tấn
                </p>
                <p className="tabular-nums">
                  {formatDateVN(order.scheduledDate)} · {order.timeSlot}
                </p>
              </div>
            </div>
          )}
          <div className="relative space-y-3 pl-6">
            <span className="absolute top-2 bottom-2 left-[7px] w-px border-l border-dashed border-slate-300" />
            <div className="relative">
              <span className="absolute top-1 -left-6 size-3.5 rounded-full border-2 border-slate-400 bg-white" />
              <p className="text-sm text-slate-600">{order.fromAddress}</p>
            </div>
            <div className="relative">
              <span className="absolute top-1 -left-6 size-3.5 rounded-full bg-brand" />
              <p className="text-sm font-semibold text-slate-900">
                {order.toAddress}
              </p>
            </div>
          </div>
          <div className="mt-4 grid grid-cols-2 gap-2">
            <a
              href={`tel:${order.phone.replace(/\s/g, "")}`}
              className="flex h-12 items-center justify-center gap-2 rounded-xl border border-slate-200 text-sm font-semibold text-slate-700 active:bg-slate-50"
            >
              <Phone className="size-4" /> Gọi khách
            </a>
            <a
              href={`https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(navTarget)}`}
              target="_blank"
              rel="noreferrer"
              title={navTarget}
              className="flex h-12 items-center justify-center gap-2 rounded-xl border border-slate-200 text-sm font-semibold text-slate-700 active:bg-slate-50"
            >
              <Navigation className="size-4" /> Chỉ đường
            </a>
          </div>
        </div>
      </section>

      {locked && (
        <div
          className="rounded-2xl border-2 border-rose-300 bg-rose-50 p-4"
          role="alert"
        >
          <div className="flex items-start gap-3">
            <Lock className="mt-0.5 size-5 shrink-0 text-rose-600" />
            <div>
              <p className="text-sm font-semibold text-rose-900">
                Đang khóa luồng: Chờ khách hàng bấm Xác nhận báo giá điều chỉnh
                trên Web.
              </p>
              <p className="mt-1 text-xs text-rose-700 tabular-nums">
                Phát sinh đề xuất: +
                {formatVND(order.pendingAdjustment?.unitPrice ?? 350_000)}
              </p>
            </div>
          </div>
        </div>
      )}
      {order.flags.isVehicleIncident && (
        <div
          className="rounded-2xl border-2 border-rose-300 bg-rose-50 p-4 text-sm font-semibold text-rose-900"
          role="alert"
        >
          <Siren className="mr-2 inline size-4 text-rose-600" /> Đã báo sự cố xe
          — Điều phối đang điều xe thay thế.
        </div>
      )}

      {/* Progress */}
      <Block
        title="Tiến độ hiện trường"
        right={
          <span className="text-xs font-semibold text-slate-500 tabular-nums">
            {Math.max(0, stageIdx)}/5
          </span>
        }
      >
        <div className="mb-4 flex gap-1">
          {FIELD_STAGES.map((s, i) => (
            <div
              key={s.stage}
              className={cx(
                "h-1.5 flex-1 rounded-full",
                i < stageIdx ? "bg-brand" : "bg-slate-200",
              )}
            />
          ))}
        </div>
        <ul className="space-y-1.5">
          {FIELD_STAGES.map((s, i) => {
            const Icon =
              STAGE_ICONS[s.stage as Exclude<FieldStage, "NOT_STARTED">];
            const done = i < stageIdx;
            const isNext = i === stageIdx;
            return (
              <li
                key={s.stage}
                className={cx(
                  "flex h-11 items-center gap-3 rounded-xl px-3 text-sm",
                  done
                    ? "bg-emerald-50 font-semibold text-emerald-700"
                    : isNext
                      ? blocked
                        ? "bg-rose-50 font-semibold text-rose-600"
                        : "bg-orange-50 font-semibold text-brand"
                      : "text-slate-400",
                )}
              >
                {done ? (
                  <CheckCircle2 className="size-5" />
                ) : isNext && blocked ? (
                  <Lock className="size-5" />
                ) : (
                  <Icon className="size-5" />
                )}
                <span className="flex-1">{s.label}</span>
                {isNext && !blocked && (
                  <span className="text-[11px] font-semibold tracking-wide uppercase">
                    Tiếp theo
                  </span>
                )}
              </li>
            );
          })}
        </ul>
        {paused && (
          <p className="mt-3 rounded-lg bg-amber-50 p-2.5 text-center text-xs font-medium text-amber-800">
            Đang tạm dừng chờ khách — dừng đồng hồ chờ để tiếp tục.
          </p>
        )}
      </Block>

      {/* Exceptions */}
      {active && order.fieldStage !== "HANDED_OVER" && (
        <Block title="Xử lý phát sinh">
          <div className="grid gap-2">
            <Button
              variant="secondary"
              size="lg"
              icon={AlertOctagon}
              className="h-14 border-amber-300 text-amber-800 hover:bg-amber-50"
              disabled={locked}
              onClick={() =>
                dispatch({
                  type: "REPORT_FIELD_DISCREPANCY",
                  orderId: order.id,
                })
              }
            >
              {locked
                ? "Đã báo — chờ khách xác nhận"
                : "Báo cáo sai lệch hiện trường"}
            </Button>
            <div className="grid grid-cols-2 gap-2">
              <Button
                variant="danger"
                size="lg"
                icon={Siren}
                className="h-16 flex-col !gap-1 text-xs leading-tight whitespace-normal"
                disabled={order.flags.isVehicleIncident}
                onClick={() =>
                  dispatch({
                    type: "REPORT_VEHICLE_INCIDENT",
                    orderId: order.id,
                  })
                }
              >
                {order.flags.isVehicleIncident
                  ? "Đã báo sự cố"
                  : "Báo sự cố xe hỏng"}
              </Button>
              {paused ? (
                <Button
                  variant="dark"
                  size="lg"
                  icon={StopCircle}
                  className="h-16 flex-col !gap-1 text-xs leading-tight whitespace-normal"
                  onClick={() =>
                    dispatch({ type: "STOP_WAIT_TIMER", orderId: order.id })
                  }
                >
                  Dừng chờ ·{" "}
                  <span className="tabular-nums">
                    {order.flags.waitingMinutes}′ ·{" "}
                    {formatVND(waitFee(order.flags.waitingMinutes))}
                  </span>
                </Button>
              ) : (
                <Button
                  variant="secondary"
                  size="lg"
                  icon={Timer}
                  className="h-16 flex-col !gap-1 text-xs leading-tight whitespace-normal"
                  onClick={() =>
                    dispatch({ type: "START_WAIT_TIMER", orderId: order.id })
                  }
                >
                  Bật đồng hồ chờ khách
                </Button>
              )}
            </div>
          </div>
        </Block>
      )}

      <Block title={`Đội ngũ (${crew.length})`} icon={UserRound}>
        <ul className="space-y-2.5">
          {crew.map((c) => (
            <li key={c.id} className="flex items-center gap-3">
              <span className="grid size-9 place-items-center rounded-full bg-slate-100 text-xs font-bold text-slate-600">
                {c.name.split(" ").slice(-1)[0]?.[0]}
              </span>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-semibold">{c.name}</p>
                <p className="text-xs text-slate-500">
                  <span className="tabular-nums">{c.code}</span> ·{" "}
                  {STAFF_ROLE_LABEL[c.role]}
                </p>
              </div>
              <a
                href="tel:0909000000"
                className="grid size-9 place-items-center rounded-lg text-slate-400 hover:bg-slate-100"
                aria-label={`Gọi ${c.name}`}
              >
                <Phone className="size-4" />
              </a>
            </li>
          ))}
        </ul>
      </Block>

      {order.equipment.length > 0 && (
        <Block
          title="Dụng cụ xuất kho"
          icon={Wrench}
          right={
            <span className="text-xs font-semibold text-slate-500 tabular-nums">
              {checked}/{order.equipment.length}
            </span>
          }
        >
          <ul className="-mx-1">
            {order.equipment.map((e) => (
              <li key={e.id}>
                <label className="flex h-12 cursor-pointer items-center gap-3 rounded-xl px-1 active:bg-slate-50">
                  <input
                    type="checkbox"
                    checked={e.checked}
                    onChange={() =>
                      dispatch({
                        type: "TOGGLE_EQUIPMENT",
                        orderId: order.id,
                        itemId: e.id,
                      })
                    }
                    className="size-5 rounded accent-[#FF5A1F]"
                  />
                  <span
                    className={cx(
                      "flex-1 text-sm",
                      e.checked
                        ? "text-slate-400 line-through"
                        : "font-medium text-slate-800",
                    )}
                  >
                    {e.name}
                  </span>
                  <span className="text-sm font-semibold text-slate-500 tabular-nums">
                    ×{e.quantity}
                  </span>
                </label>
              </li>
            ))}
          </ul>
        </Block>
      )}

      {/* Primary progress action */}
      {active && next && (
        <StickyActionBar className="md:!sticky md:bottom-4">
          <button
            type="button"
            disabled={paused || blocked}
            onClick={() =>
              dispatch({
                type: "ADVANCE_FIELD_STAGE",
                orderId: order.id,
                stage: next.stage,
              })
            }
            className={cx(
              "flex h-14 w-full items-center justify-center gap-2.5 rounded-xl text-base font-bold transition-all active:scale-[0.98]",
              blocked
                ? "cursor-not-allowed border-2 border-dashed border-rose-300 bg-rose-50 text-rose-500"
                : "bg-brand text-white shadow-lg shadow-brand/25 disabled:bg-slate-200 disabled:text-slate-400 disabled:shadow-none",
            )}
          >
            {blocked ? (
              <Lock className="size-5" />
            ) : (
              <NextIcon className="size-5" />
            )}
            {blocked
              ? nextLocked
                ? "Khóa: chờ khách xác nhận"
                : "Khóa: chờ xe thay thế"
              : next.label}
          </button>
        </StickyActionBar>
      )}
      {!active && (
        <div className="flex items-center gap-3 rounded-2xl bg-emerald-50 p-4 text-sm font-semibold text-emerald-700">
          <CheckCircle2 className="size-5" />{" "}
          {FIELD_STAGE_LABEL[order.fieldStage]} — lệnh đã kết thúc.
        </div>
      )}
    </div>
  );
};
