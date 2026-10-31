import { Package, Wrench } from 'lucide-react';
import { Badge } from '@/components/ui';
import type { Order } from '@/types';

/** Đồ đạc cần vận chuyển và dịch vụ khách đã chọn khi đặt đơn. */
export const OrderRequestDetails = ({ order }: { order: Order }) => {
  const volume = order.items.reduce((s, i) => s + (i.volumeM3 ?? 0) * i.quantity, 0);
  return (
    <div className="space-y-4 text-sm">
      <div>
        <p className="mb-2 flex items-center gap-2 font-semibold text-slate-900">
          <Package className="size-4 text-slate-500" /> Đồ đạc cần vận chuyển ({order.items.length})
          {volume > 0 && <span className="ml-auto text-xs font-medium text-slate-500">≈ {volume.toFixed(1)} m³</span>}
        </p>
        {order.items.length === 0 ? (
          <p className="text-slate-500">Khách chưa khai báo đồ đạc.</p>
        ) : (
          <ul className="divide-y divide-slate-100 rounded-xl border border-slate-200">
            {order.items.map((i) => (
              <li key={i.id} className="flex items-center gap-2 px-3 py-2">
                <span className="min-w-0 flex-1 truncate font-medium text-slate-800">{i.name}</span>
                {i.fragile && <Badge tone="warning">Dễ vỡ</Badge>}
                {i.volumeM3 ? <span className="text-xs text-slate-400 tabular-nums">{i.volumeM3} m³</span> : null}
                <span className="font-semibold tabular-nums">×{i.quantity}</span>
              </li>
            ))}
          </ul>
        )}
      </div>
      <div>
        <p className="mb-2 flex items-center gap-2 font-semibold text-slate-900"><Wrench className="size-4 text-slate-500" /> Dịch vụ đã chọn</p>
        {order.requestedServices.length === 0 ? (
          <p className="text-slate-500">Không chọn dịch vụ bổ sung.</p>
        ) : (
          <div className="flex flex-wrap gap-1.5">{order.requestedServices.map((s) => <Badge key={s} tone="brand">{s}</Badge>)}</div>
        )}
      </div>
    </div>
  );
};
