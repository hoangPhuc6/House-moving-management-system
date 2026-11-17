import { ChevronDown, ClipboardList } from 'lucide-react';
import { Badge, Card } from '@/components/ui';
import { PACKING_LABEL, SERVICE_MODES, needsFloor } from '@/features/customer/booking/constants';
import { useCatalog } from '@/services/store/CatalogStore';
import type { AddressDetail, Order } from '@/types';

const placeLine = (a: AddressDetail) => [a.propertyType, needsFloor(a.propertyType) ? a.floor : '', a.note].filter(Boolean).join(' • ');

/**
 * "Chi tiết yêu cầu" phía khách — chỉ gồm thông tin chưa hiện ở chỗ khác trên trang đơn
 * (địa chỉ & lịch chuyển nằm ở phần đầu, lịch khảo sát nằm ở thẻ khảo sát). Mặc định thu gọn.
 */
export const RequestSummaryCard = ({ order }: { order: Order }) => {
  const { catalog } = useCatalog();
  const d = order.bookingDetails;
  const packingNames = new Set(catalog.services.filter((s) => s.group === 'PACKING').map((s) => s.name));
  const truckOnly = d?.serviceMode === 'TRUCK_ONLY';

  // Vật tư đóng gói gộp thành một mục như lúc đặt đơn; bốc vác (chỉ thuê xe) kèm số người.
  const services = order.requestedServices.filter((s) => !packingNames.has(s)).map((s) => (truckOnly && /bốc/i.test(s) && d && d.laborCount > 0 ? `${s} ×${d.laborCount}` : s));
  if (order.requestedServices.some((s) => packingNames.has(s))) services.push(PACKING_LABEL);

  const rows: [string, string][] = [
    ['Người liên hệ', [order.customerName, order.phone, d?.email].filter(Boolean).join(' · ')],
    ...(d ? ([['Gói dịch vụ', SERVICE_MODES[d.serviceMode].title]] as [string, string][]) : []),
    ...(d ? ([['Điểm đi', placeLine(d.from)], ['Điểm đến', placeLine(d.to)]] as [string, string][]) : []),
    ...(d && (d.flexibleTime || d.scheduleNote)
      ? ([['Ghi chú lịch', [d.flexibleTime ? 'Chưa cố định giờ, cần tư vấn' : '', d.scheduleNote].filter(Boolean).join('. ')]] as [string, string][])
      : []),
    ...(d?.customerNote ? ([['Ghi chú của bạn', d.customerNote]] as [string, string][]) : []),
  ];

  return (
    <Card padded={false} className="lg:col-span-2">
      <details className="group">
        <summary className="flex cursor-pointer list-none items-center gap-3 px-5 py-4 [&::-webkit-details-marker]:hidden">
          <ClipboardList className="size-4 shrink-0 text-slate-500" />
          <span className="min-w-0 flex-1">
            <span className="block text-[15px] font-semibold text-slate-900">Chi tiết yêu cầu</span>
            <span className="block truncate text-xs text-slate-500">
              {[d ? SERVICE_MODES[d.serviceMode].title : null, services.length > 0 ? `${services.length} dịch vụ` : null].filter(Boolean).join(' · ')}
            </span>
          </span>
          <ChevronDown className="size-4 shrink-0 text-slate-400 transition-transform group-open:rotate-180" />
        </summary>
        <div className="space-y-4 border-t border-slate-100 px-5 py-4 text-sm">
          <dl className="divide-y divide-slate-100 rounded-xl border border-slate-200">
            {rows.map(([k, v]) => (
              <div key={k} className="grid gap-1 px-3 py-2 sm:grid-cols-[140px_1fr]">
                <dt className="text-xs font-medium text-slate-500">{k}</dt>
                <dd className="text-slate-800">{v}</dd>
              </div>
            ))}
          </dl>
          <div>
            <p className="mb-2 text-xs font-medium text-slate-500">Dịch vụ đã chọn</p>
            {services.length === 0 ? (
              <p className="text-slate-500">Không chọn dịch vụ bổ sung.</p>
            ) : (
              <div className="flex flex-wrap gap-1.5">
                {services.map((s) => (
                  <Badge key={s} tone="brand">
                    {s}
                  </Badge>
                ))}
              </div>
            )}
          </div>
        </div>
      </details>
    </Card>
  );
};
