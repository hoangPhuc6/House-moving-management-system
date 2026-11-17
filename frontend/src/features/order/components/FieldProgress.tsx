import { CheckCircle2, Lock, Radio, Siren, Timer } from 'lucide-react';
import { Badge, Card, CardHeader, cx, type Tone } from '@/components/ui';
import { FIELD_STAGE_LABEL, fieldStagesOf, isFieldStageDone, nextFieldStage } from '@/constants/order';
import { STAFF_ROLE_LABEL } from '@/constants/resources';
import { agoText, fieldStageTimes, lastFieldUpdate } from '@/features/order/fieldProgress';
import { useApp, useNow } from '@/services/store/AppStore';
import type { Order } from '@/types';

const hhmm = (epoch: number) => new Date(epoch).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });

/** Nhãn gọn cho danh sách: bước hiện tại + giờ cập nhật, VD "Đang di chuyển · 10:32". */
export const FieldStageBadge = ({ order }: { order: Order }) => {
  const at = lastFieldUpdate(order);
  const tone: Tone = order.flags.isVehicleIncident
    ? 'danger'
    : order.status === 'PAUSED_WAITING_CUSTOMER'
      ? 'warning'
      : order.fieldStage === 'NOT_STARTED'
        ? 'neutral'
        : 'info';
  const label = order.status === 'PAUSED_WAITING_CUSTOMER' ? 'Đang chờ khách' : FIELD_STAGE_LABEL[order.fieldStage];
  return (
    <Badge tone={tone} dot={order.fieldStage !== 'NOT_STARTED' && order.fieldStage !== 'HANDED_OVER'}>
      {label}
      {at && <span className="font-medium tabular-nums opacity-70">· {hhmm(at)}</span>}
    </Badge>
  );
};

/** Thẻ tiến độ hiện trường cho Điều phối: các bước đội xe đã bấm kèm giờ, đội xe, cảnh báo đang chặn. */
export const FieldProgressCard = ({ order }: { order: Order }) => {
  const { state } = useApp();
  const now = useNow(30_000);
  const times = fieldStageTimes(order);
  const last = lastFieldUpdate(order);
  const vehicles = state.resources.vehicles.filter((v) => order.vehicleIds.includes(v.id));
  const crew = state.resources.staff.filter((s) => order.crewIds.includes(s.id));
  const leader = crew.find((c) => c.role === 'TEAM_LEADER') ?? crew[0];
  const running = ['ASSIGNED', 'IN_TRANSIT', 'PAUSED_WAITING_CUSTOMER'].includes(order.status);

  return (
    <Card>
      <CardHeader
        title="Tiến độ hiện trường"
        icon={Radio}
        subtitle={last ? `Đội xe cập nhật ${agoText(last, now)}` : 'Đội xe chưa cập nhật bước nào'}
        action={<FieldStageBadge order={order} />}
      />

      {(order.flags.isVehicleIncident || order.flags.hasFieldDiscrepancyLock || order.status === 'PAUSED_WAITING_CUSTOMER') && (
        <div className="mb-4 space-y-2">
          {order.flags.isVehicleIncident && (
            <p className="flex items-center gap-2 rounded-xl bg-rose-50 px-3 py-2 text-sm font-medium text-rose-800">
              <Siren className="size-4 shrink-0" /> Xe báo sự cố — cần chuyển giao phương tiện (mục Sự cố).
            </p>
          )}
          {order.flags.hasFieldDiscrepancyLock && (
            <p className="flex items-center gap-2 rounded-xl bg-amber-50 px-3 py-2 text-sm font-medium text-amber-800">
              <Lock className="size-4 shrink-0" /> Phát sinh hiện trường — đội xe bị khóa bước tiếp theo cho tới khi khách xác nhận.
            </p>
          )}
          {order.status === 'PAUSED_WAITING_CUSTOMER' && (
            <p className="flex items-center gap-2 rounded-xl bg-amber-50 px-3 py-2 text-sm font-medium text-amber-800">
              <Timer className="size-4 shrink-0" /> Đội xe đang chờ khách{order.flags.waitingMinutes > 0 ? ` · ${order.flags.waitingMinutes} phút` : ''}.
            </p>
          )}
        </div>
      )}

      {/* Các bước theo chiều ngang — mỗi bước kèm giờ đội xe bấm. */}
      <ol className="grid gap-1" style={{ gridTemplateColumns: `repeat(${fieldStagesOf(order).length}, minmax(0, 1fr))` }}>
        {fieldStagesOf(order).map((s) => {
          const done = isFieldStageDone(order, s.stage);
          const current = running && s.stage === nextFieldStage(order)?.stage;
          const at = times[s.stage];
          return (
            <li key={s.stage} className="min-w-0">
              <div className={cx('h-1.5 rounded-full', done ? 'bg-brand' : current ? 'bg-brand/30' : 'bg-slate-200')} />
              <p className={cx('mt-2 flex items-center gap-1 text-xs font-semibold', done ? 'text-slate-900' : current ? 'text-brand' : 'text-slate-400')}>
                {done && <CheckCircle2 className="size-3.5 shrink-0 text-brand" />}
                <span className="truncate">{FIELD_STAGE_LABEL[s.stage]}</span>
              </p>
              <p className="text-[11px] text-slate-500 tabular-nums">{at ? hhmm(at) : current ? 'Bước kế tiếp' : '—'}</p>
            </li>
          );
        })}
      </ol>

      <div className="mt-4 grid gap-2 border-t border-slate-100 pt-4 text-sm sm:grid-cols-2">
        <div>
          <p className="text-xs text-slate-500">Xe</p>
          <p className="font-semibold tabular-nums">{vehicles.length ? vehicles.map((v) => v.plateNumber).join(', ') : 'Chưa gán'}</p>
        </div>
        <div>
          <p className="text-xs text-slate-500">Đội xe ({crew.length} người)</p>
          <p className="truncate font-semibold">{leader ? `${leader.name} · ${STAFF_ROLE_LABEL[leader.role]}` : 'Chưa gán'}</p>
        </div>
      </div>
    </Card>
  );
};
