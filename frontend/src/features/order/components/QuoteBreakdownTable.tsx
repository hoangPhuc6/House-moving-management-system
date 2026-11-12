import { cx } from '@/components/ui';
import { formatVND } from '@/services/format';
import { lineTotal, quoteSubtotal } from '@/services/pricing';
import type { QuoteItem, QuoteItemCategory } from '@/types';

const GROUPS: { label: string; categories: QuoteItemCategory[] }[] = [
  { label: 'Vận chuyển', categories: ['VEHICLE'] },
  { label: 'Nhân công', categories: ['LABOR'] },
  { label: 'Vật tư', categories: ['MATERIAL'] },
  { label: 'Phụ phí', categories: ['SURCHARGE', 'FIELD_ADJUSTMENT', 'WAIT_FEE'] },
];

/**
 * Bảng phân rã báo giá: từng hạng mục kèm cách tính, cộng theo nhóm và tổng.
 * `reference` (tùy chọn) để so với báo giá tham khảo khách đã xem lúc đặt đơn.
 */
export const QuoteBreakdownTable = ({ items, reference }: { items: QuoteItem[]; reference?: number | null }) => {
  const total = quoteSubtotal(items);
  const groups = GROUPS.map((g) => ({ ...g, items: items.filter((i) => g.categories.includes(i.category)) })).filter((g) => g.items.length > 0);
  const diff = reference != null ? total - reference : null;

  return (
    <div className="overflow-hidden rounded-xl border border-slate-200">
      <div className="overflow-x-auto">
        <table className="w-full min-w-[560px] text-sm">
          <thead>
            <tr className="bg-slate-50 text-left text-xs font-semibold tracking-wide text-slate-500 uppercase">
              <th className="px-4 py-2.5">Hạng mục & cách tính</th>
              <th className="px-3 py-2.5 text-right">SL × Đơn giá</th>
              <th className="px-4 py-2.5 text-right">Thành tiền</th>
            </tr>
          </thead>
          {groups.map((g) => (
            <tbody key={g.label} className="border-t border-slate-100">
              <tr>
                <td colSpan={2} className="px-4 pt-3 pb-1 text-xs font-semibold text-slate-500">
                  {g.label}
                </td>
                <td className="px-4 pt-3 pb-1 text-right text-xs font-semibold text-slate-500 tabular-nums">{formatVND(quoteSubtotal(g.items))}</td>
              </tr>
              {g.items.map((i) => (
                <tr key={i.id} className="align-top">
                  <td className="px-4 py-2">
                    <p className="font-medium text-slate-900">{i.name}</p>
                    {i.note && <p className="mt-0.5 text-xs text-slate-500">{i.note}</p>}
                  </td>
                  <td className="px-3 py-2 text-right whitespace-nowrap text-slate-600 tabular-nums">
                    {i.quantity} {i.unit} × {formatVND(i.unitPrice)}
                  </td>
                  <td className="px-4 py-2 text-right font-semibold whitespace-nowrap tabular-nums">{formatVND(lineTotal(i))}</td>
                </tr>
              ))}
            </tbody>
          ))}
          {groups.length === 0 && (
            <tbody>
              <tr>
                <td colSpan={3} className="px-4 py-6 text-center text-slate-400">
                  Chưa có hạng mục nào.
                </td>
              </tr>
            </tbody>
          )}
          <tfoot className="border-t border-slate-200 bg-slate-50/60">
            <tr>
              <td colSpan={2} className="px-4 py-3 font-semibold text-slate-900">
                Tổng báo giá ({items.length} hạng mục)
              </td>
              <td className="px-4 py-3 text-right text-lg font-bold tabular-nums">{formatVND(total)}</td>
            </tr>
            {reference != null && diff !== null && (
              <tr>
                <td colSpan={2} className="px-4 pb-3 text-xs text-slate-500">
                  So với báo giá tham khảo khách đã xem ({formatVND(reference)})
                </td>
                <td className={cx('px-4 pb-3 text-right text-xs font-semibold tabular-nums', diff > 0 ? 'text-amber-600' : diff < 0 ? 'text-emerald-600' : 'text-slate-500')}>
                  {diff === 0 ? 'Bằng nhau' : `${diff > 0 ? '+' : '−'}${formatVND(Math.abs(diff))}`}
                </td>
              </tr>
            )}
          </tfoot>
        </table>
      </div>
    </div>
  );
};
