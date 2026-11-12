import { ClipboardList, Package, Wrench } from 'lucide-react';
import { Badge } from '@/components/ui';
import { formatDateVN } from '@/services/format';
import type { BookingDetails, Order, ServiceMode, SurveyPreference } from '@/types';

const MODE_LABEL: Record<ServiceMode, string> = { FULL_PACKAGE: 'Chuyển nhà trọn gói', TRUCK_ONLY: 'Chỉ thuê xe tải' };
const SURVEY_LABEL: Record<SurveyPreference, string> = { VIDEO_CALL: 'Video Call', ON_SITE: 'Trực tiếp tận nơi', PHOTOS_ONLY: 'Khách gửi ảnh / video' };

/** Thông tin khách khai báo thêm qua wizard đặt đơn — giúp Điều phối khảo sát & báo giá. */
const BookingDetailsBlock = ({ details: d }: { details: BookingDetails }) => {
  const rows: [string, string][] = [
    ['Hình thức', MODE_LABEL[d.serviceMode]],
    ...(d.homeScale ? ([['Quy mô nhà', d.homeScale]] as [string, string][]) : []),
    ['Điểm đi', [d.from.propertyType, d.from.floor, d.from.note].filter(Boolean).join(' • ')],
    ['Điểm đến', [d.to.propertyType, d.to.floor, d.to.note].filter(Boolean).join(' • ')],
    ...(d.surveyPreference
      ? ([['Khảo sát mong muốn', SURVEY_LABEL[d.surveyPreference] + (d.surveyDate ? ` — ${formatDateVN(d.surveyDate)}` : '') + (d.surveyContactSlot ? ` (${d.surveyContactSlot})` : '')]] as [string, string][])
      : ([['Bốc vác thuê thêm', d.laborCount > 0 ? `${d.laborCount} người` : 'Không thuê (khách tự khiêng vác)']] as [string, string][])),
    ...(d.flexibleTime || d.scheduleNote ? ([['Ghi chú lịch', [d.flexibleTime ? 'Chưa cố định giờ, cần tư vấn' : '', d.scheduleNote].filter(Boolean).join('. ')]] as [string, string][]) : []),
    ...(d.email ? ([['Email', d.email]] as [string, string][]) : []),
    ...(d.customerNote ? ([['Ghi chú khách', d.customerNote]] as [string, string][]) : []),
  ];
  return (
    <div>
      <p className="mb-2 flex items-center gap-2 font-semibold text-slate-900"><ClipboardList className="size-4 text-slate-500" /> Thông tin khai báo khi đặt đơn</p>
      <dl className="divide-y divide-slate-100 rounded-xl border border-slate-200">
        {rows.map(([k, v]) => (
          <div key={k} className="grid gap-1 px-3 py-2 sm:grid-cols-[140px_1fr]">
            <dt className="text-xs font-medium text-slate-500">{k}</dt>
            <dd className="text-slate-800">{v}</dd>
          </div>
        ))}
      </dl>
    </div>
  );
};

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
          <p className="text-slate-500">Danh mục đồ đạc sẽ được chốt khi khảo sát.</p>
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
      {order.bookingDetails && <BookingDetailsBlock details={order.bookingDetails} />}
    </div>
  );
};
