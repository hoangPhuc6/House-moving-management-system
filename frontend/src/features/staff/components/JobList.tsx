import { Link } from 'react-router-dom';
import { AlertOctagon, ChevronRight, ClipboardList, Truck } from 'lucide-react';
import { cx, EmptyState, StatusBadge } from '@/components/ui';
import { CustomerInfo } from '@/features/staff/components/CustomerInfo';
import { RUNNING } from '@/features/staff/constants';
import { ROUTES } from '@/routes/paths';
import { useApp } from '@/services/store/AppStore';
import type { Order } from '@/types';

export const JobList = ({ jobs }: { jobs: Order[] }) => {
  const { state } = useApp();
  const running = jobs.filter((j) => RUNNING.includes(j.status));
  const done = jobs.filter((j) => !RUNNING.includes(j.status));

  const row = (o: Order) => {
    const v = state.resources.vehicles.find((x) => x.id === o.vehicleId);
    const alert = o.flags.hasFieldDiscrepancyLock || o.flags.isVehicleIncident;
    return (
      <li key={o.id}>
        <Link
          to={ROUTES.staff.job(o.id)}
          className={cx(
            "flex w-full items-center gap-3 rounded-2xl border bg-white p-4 text-left transition-colors active:bg-slate-50",
            alert ? "border-rose-300" : "border-slate-200",
          )}
        >
          <span
            className={cx(
              "grid size-12 shrink-0 place-items-center rounded-2xl",
              RUNNING.includes(o.status)
                ? "bg-brand text-white"
                : "bg-slate-100 text-slate-500",
            )}
          >
            {alert ? (
              <AlertOctagon className="size-5" />
            ) : (
              <Truck className="size-5" />
            )}
          </span>
          <div className="min-w-0 flex-1">
            <div className="flex items-center justify-between gap-2">
              <p className="font-semibold whitespace-nowrap tabular-nums">
                #{o.id}
              </p>
              <span className="truncate text-xs text-slate-400 tabular-nums">
                {o.timeSlot}
              </span>
            </div>
            <CustomerInfo order={o} compact />
            <p className="truncate text-sm text-slate-600">{o.toAddress}</p>
            <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
              <StatusBadge status={o.status} />
              {v && (
                <span className="rounded-md bg-slate-100 px-1.5 py-0.5 font-mono text-[11px] font-semibold text-slate-600">
                  {v.plateNumber}
                </span>
              )}
            </div>
          </div>
          <ChevronRight className="size-5 shrink-0 text-slate-300" />
        </Link>
      </li>
    );
  };

  return (
    <div className="space-y-5">
      <div className="grid grid-cols-2 gap-3">
        <div className="rounded-2xl bg-white p-4 ring-1 ring-slate-200">
          <p className="text-xs text-slate-500">Đang thực hiện</p>
          <p className="mt-1 text-2xl font-bold tabular-nums">
            {running.length}
          </p>
        </div>
        <div className="rounded-2xl bg-white p-4 ring-1 ring-slate-200">
          <p className="text-xs text-slate-500">Đã bàn giao</p>
          <p className="mt-1 text-2xl font-bold tabular-nums">{done.length}</p>
        </div>
      </div>
      <section>
        <h2 className="mb-2.5 px-1 text-[15px] font-semibold">
          Lệnh đang chạy
        </h2>
        {running.length ? (
          <ul className="space-y-2.5">{running.map(row)}</ul>
        ) : (
          <EmptyState icon={ClipboardList} title="Chưa có lệnh đang chạy">
            Bạn sẽ nhận thông báo ngay khi Điều phối gán lệnh công tác mới.
          </EmptyState>
        )}
      </section>
      {done.length > 0 && (
        <section>
          <h2 className="mb-2.5 px-1 text-[15px] font-semibold text-slate-500">
            Đã hoàn thành
          </h2>
          <ul className="space-y-2.5">{done.map(row)}</ul>
        </section>
      )}
    </div>
  );
};
