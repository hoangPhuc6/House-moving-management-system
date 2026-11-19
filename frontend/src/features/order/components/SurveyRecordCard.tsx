import { ClipboardCheck } from 'lucide-react';
import { Card } from '@/components/ui';
import { SURVEY_METHOD_META } from '@/constants/order';
import { VEHICLE_PRICING } from '@/constants/pricing';
import { hoursLabel } from '@/constants/resources';
import { SitePhotoGallery } from '@/features/order/components/SitePhotoGallery';
import { formatTime } from '@/services/format';
import type { SurveyRecord } from '@/types';

/** Danh sách xe theo biên bản: "2 × Xe tải 2 tấn + 1 × Xe tải 1.25 tấn". */
const fleetLabel = (b: SurveyRecord['baseline']): string =>
  [...b.vehicles]
    .sort((x, y) => y.capacity - x.capacity)
    .filter((v) => v.qty > 0)
    .map((v) => `${v.qty} × ${VEHICLE_PRICING[v.capacity]?.label ?? `xe ${v.capacity} tấn`}`)
    .join(' + ');

/**
 * Căn cứ của báo giá chính thức: số liệu khảo sát, biên bản và ảnh hiện trường.
 * Khách duyệt báo giá = xác nhận các số liệu này; phát sinh tại hiện trường được so với đây.
 */
export const SurveyRecordCard = ({ record, audience = 'customer' }: { record: SurveyRecord; audience?: 'customer' | 'internal' }) => {
  const b = record.baseline;
  const facts: [string, string][] = [
    ['Quãng đường', `${b.distanceKm} km`],
    ['Xe', fleetLabel(b)],
    ['Nhân viên bốc xếp', `${b.workers} người`],
    ...(b.estimatedHours > 0 ? [['Thời gian dự kiến', hoursLabel(b.estimatedHours)] as [string, string]] : []),
    ['Thang bộ — điểm đi', b.floorsFrom > 0 ? `${b.floorsFrom} lầu` : 'Không'],
    ['Thang bộ — điểm đến', b.floorsTo > 0 ? `${b.floorsTo} lầu` : 'Không'],
    ['Hẻm / khiêng bộ — điểm đi', b.alleyFrom > 0 ? `${b.alleyFrom} m` : 'Xe vào tận nơi'],
    ['Hẻm / khiêng bộ — điểm đến', b.alleyTo > 0 ? `${b.alleyTo} m` : 'Xe vào tận nơi'],
  ];
  return (
    <Card className="space-y-4">
      <div className="flex items-start gap-3">
        <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-slate-100 text-slate-600">
          <ClipboardCheck className="size-4" />
        </span>
        <div>
          <p className="text-sm font-semibold text-slate-900">Căn cứ báo giá</p>
          <p className="text-xs text-slate-500">
            {SURVEY_METHOD_META[record.method].label} · <span className="tabular-nums">{formatTime(record.recordedAt)}</span>
          </p>
        </div>
      </div>
      <dl className="grid gap-x-4 gap-y-2 text-sm sm:grid-cols-2">
        {facts.map(([k, v]) => (
          <div key={k} className="flex justify-between gap-3 border-b border-slate-100 pb-1.5 sm:block sm:border-0 sm:pb-0">
            <dt className="text-xs text-slate-500">{k}</dt>
            <dd className="font-medium text-slate-800 tabular-nums">{v}</dd>
          </div>
        ))}
      </dl>
      {record.note && <p className="rounded-xl bg-slate-50 px-3 py-2 text-sm text-slate-700">{record.note}</p>}
      {record.photos.length > 0 && <SitePhotoGallery photos={record.photos} />}
      {audience === 'customer' && (
        <p className="text-xs leading-relaxed text-slate-500">
          Khi duyệt báo giá, bạn xác nhận các thông tin trên là đúng. Nếu thực tế ngày chuyển khác (thêm lầu, hẻm sâu hơn, thêm đồ…), đội xe sẽ báo phát
          sinh kèm ảnh và điều phối viên gửi bạn xác nhận trước khi tính thêm.
        </p>
      )}
    </Card>
  );
};
