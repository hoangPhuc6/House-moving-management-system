import { Badge, cx } from '@/components/ui';
import { PhotoStrip } from '@/features/order/components/SitePhotoGallery';
import type { SurveyItem } from '@/types';

/** Danh sách đồ đạc đã khảo sát — dùng chung cho Điều phối và khách. */
export const SurveyItemList = ({ items, className }: { items: SurveyItem[]; className?: string }) => {
  if (items.length === 0) return <p className="text-sm text-slate-500">—</p>;
  return (
    <ul className={cx('divide-y divide-slate-100', className)}>
      {items.map((it, i) => (
        <li key={`${it.name}-${i}`} className="flex items-center gap-3 py-2 text-sm">
          <span className="min-w-0 flex-1 text-slate-800">
            {it.name}
            {it.note && <span className="block text-xs text-slate-500">{it.note}</span>}
            <PhotoStrip photos={it.photos ?? []} className="mt-1.5" />
          </span>
          {it.fragile && <Badge tone="warning">Dễ vỡ</Badge>}
          {it.disassemble && <Badge tone="info">Tháo lắp</Badge>}
          <span className="w-10 shrink-0 text-right font-semibold text-slate-900 tabular-nums">×{it.qty}</span>
        </li>
      ))}
    </ul>
  );
};
