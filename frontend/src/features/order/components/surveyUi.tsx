import type { ReactNode } from 'react';
import { Check, Plus } from 'lucide-react';
import { cx } from '@/components/ui';

/**
 * Bảng màu dùng chung cho form khảo sát — một tông trung tính + màu thương hiệu cho tương tác,
 * xanh lá chỉ cho trạng thái "đã đủ", đỏ chỉ cho lỗi.
 */

/** Gợi ý "+ …" để thêm một món / trường / hạng mục. */
export const SuggestChip = ({ children, onClick, size = 'md' }: { children: ReactNode; onClick: () => void; size?: 'sm' | 'md' }) => (
  <button
    type="button"
    onClick={onClick}
    className={cx(
      'inline-flex items-center gap-1 rounded-full bg-slate-100 font-medium text-slate-600 transition-colors hover:bg-brand/10 hover:text-brand active:bg-brand/15',
      size === 'sm' ? 'h-7 px-2.5 text-[11px]' : 'h-8 px-3 text-xs',
    )}
  >
    <Plus className={size === 'sm' ? 'size-3' : 'size-3.5'} strokeWidth={2.5} /> {children}
  </button>
);

/** Nút bật / tắt thuộc tính của một món (Dễ vỡ, Tháo lắp, Ghi chú). */
export const ToggleChip = ({ on, children, onClick }: { on: boolean; children: ReactNode; onClick: () => void }) => (
  <button
    type="button"
    onClick={onClick}
    aria-pressed={on}
    className={cx(
      'inline-flex h-6 items-center gap-1 rounded-full px-2 text-[11px] font-semibold ring-1 transition-colors ring-inset',
      on ? 'bg-brand/10 text-brand ring-brand/25' : 'bg-white text-slate-500 ring-slate-200 hover:text-slate-700 hover:ring-slate-300',
    )}
  >
    {on ? <Check className="size-3" strokeWidth={3} /> : <Plus className="size-3" strokeWidth={2.5} />}
    {children}
  </button>
);

/** Nút xoá / bỏ dòng — nhạt, chỉ đậm khi rê chuột. */
export const removeBtnCls = 'grid size-9 shrink-0 place-items-center rounded-lg text-slate-300 transition-colors hover:bg-slate-100 hover:text-slate-600';
