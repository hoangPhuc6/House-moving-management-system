import { ChevronDown, PackageCheck, Phone } from 'lucide-react';
import { Card } from '@/components/ui';
import { SurveyItemList } from '@/features/order/components/SurveyItemList';
import { surveyItemsTotal } from '@/features/order/surveyItems';
import type { Order } from '@/types';

/**
 * Đồ đạc người khảo sát ghi nhận — căn cứ của báo giá và để khách đối chiếu khi nhận đồ.
 * Mở sẵn lúc khách đang xem báo giá để kịp báo thiếu / sai trước khi xác nhận.
 */
export const SurveyedItemsCard = ({ order }: { order: Order }) => {
  const r = order.surveyReport;
  if (!r || r.items.length === 0) return null;
  const reviewing = order.status === 'SURVEYED_PENDING_CONFIRM';
  const fragile = r.items.filter((i) => i.fragile).length;

  return (
    <Card padded={false} className="lg:col-span-2">
      <details className="group" open={reviewing}>
        <summary className="flex cursor-pointer list-none items-center gap-3 px-5 py-4 [&::-webkit-details-marker]:hidden">
          <PackageCheck className="size-4 shrink-0 text-slate-500" />
          <span className="min-w-0 flex-1">
            <span className="block text-[15px] font-semibold text-slate-900">Đồ đạc đã khảo sát</span>
            <span className="block truncate text-xs text-slate-500">
              {[`${surveyItemsTotal(r.items)} món`, fragile > 0 ? `${fragile} loại dễ vỡ` : null].filter(Boolean).join(' · ')}
            </span>
          </span>
          <ChevronDown className="size-4 shrink-0 text-slate-400 transition-transform group-open:rotate-180" />
        </summary>
        <div className="border-t border-slate-100 px-5 py-3">
          <SurveyItemList items={r.items} />
          {reviewing && (
            <p className="mt-3 flex items-start gap-1.5 rounded-xl bg-slate-50 p-3 text-xs text-slate-600">
              <Phone className="mt-0.5 size-3.5 shrink-0" />
              <span>
                Báo giá được lập theo danh sách này. Thiếu hoặc sai món nào, gọi <b>1900 6868</b> trước khi xác nhận báo giá để được cập nhật.
              </span>
            </p>
          )}
        </div>
      </details>
    </Card>
  );
};
