import { ChevronRight, Siren, Truck } from 'lucide-react';
import { Badge, Card, CardHeader, EmptyState, PageHeader } from '@/components/ui';
import { isRunning } from '@/features/coordinator/orderSections';
import { routeLabelOf } from '@/features/order/route';
import { FieldStageBadge } from '@/features/order/components/FieldProgress';
import type { OrderSection } from '@/features/coordinator/types';
import { useApp } from '@/services/store/AppStore';
import type { Order } from '@/types';
import { VEHICLE_INCIDENT_KINDS } from '@/constants/resources';

const Row = ({ order: o, onOpen }: { order: Order; onOpen: () => void }) => {
  const { state } = useApp();
  const vehicle = state.resources.vehicles.find((v) => v.id === o.vehicleId);
  return (
    <li>
      <button type="button" onClick={onOpen} className="flex w-full flex-wrap items-center gap-x-4 gap-y-1 px-4 py-3 text-left hover:bg-slate-50">
        <div className="min-w-0 flex-1">
          <p className="text-sm font-semibold text-slate-900">
            <span className="tabular-nums">#{o.id.replace('ORD-2026-', 'ORD-')}</span> · {o.customerName}
          </p>
          <p className="truncate text-xs text-slate-500">
            Xe <span className="font-medium tabular-nums">{vehicle?.plateNumber ?? '—'}</span> · {routeLabelOf(o)}
          </p>
        </div>
        {o.flags.isVehicleIncident ? (
          <Badge tone="danger" dot pulse>
            {o.vehicleIncident ? VEHICLE_INCIDENT_KINDS[o.vehicleIncident.kind] : 'Sự cố xe'}
          </Badge>
        ) : (
          <FieldStageBadge order={o} />
        )}
        <ChevronRight className="size-4 text-slate-300" />
      </button>
    </li>
  );
};

/** Trang Sự cố: đơn đang báo sự cố lên đầu, sau đó là các đơn đang chạy để chủ động theo dõi. */
export const IncidentList = ({ onOpen }: { onOpen: (id: string, s?: OrderSection) => void }) => {
  const { state } = useApp();
  const incidents = state.orders.filter((o) => o.flags.isVehicleIncident);
  const running = state.orders.filter((o) => isRunning(o) && !o.flags.isVehicleIncident);

  return (
    <>
      <PageHeader eyebrow="Sự cố" title="Sự cố & đơn đang chạy" subtitle="Chuyển giao phương tiện (tăng bo) khi xe gặp sự cố giữa đường." />
      <div className="space-y-4">
        <Card padded={false} className={incidents.length ? 'border-rose-200' : undefined}>
          <div className="p-5 pb-2">
            <CardHeader title={`Đang báo sự cố (${incidents.length})`} icon={Siren} />
          </div>
          {incidents.length === 0 ? (
            <p className="px-5 pb-5 text-sm text-emerald-700">Không có sự cố nào.</p>
          ) : (
            <ul className="divide-y divide-slate-100">
              {incidents.map((o) => (
                <Row key={o.id} order={o} onOpen={() => onOpen(o.id, 'incident')} />
              ))}
            </ul>
          )}
        </Card>

        <Card padded={false}>
          <div className="p-5 pb-2">
            <CardHeader title={`Đang thực hiện (${running.length})`} icon={Truck} subtitle="Có thể chủ động tăng bo khi cần" />
          </div>
          {running.length === 0 ? (
            <div className="pb-4">
              <EmptyState icon={Truck} title="Không có đơn đang chạy" />
            </div>
          ) : (
            <ul className="divide-y divide-slate-100">
              {running.map((o) => (
                <Row key={o.id} order={o} onOpen={() => onOpen(o.id, 'incident')} />
              ))}
            </ul>
          )}
        </Card>
      </div>
    </>
  );
};
