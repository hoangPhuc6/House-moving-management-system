import { Check } from 'lucide-react';
import { cx } from '@/components/ui';
import { PLANS } from '@/features/landing/constants';
import { formatVND } from '@/services/format';

export const Pricing = ({ onBook }: { onBook: () => void }) => (
  <section id="pricing" className="scroll-mt-20 bg-canvas py-20 sm:py-28">
    <div className="mx-auto max-w-7xl px-4 sm:px-6">
      <div className="mx-auto mb-12 max-w-2xl text-center">
        <p className="text-sm font-semibold text-brand">Bảng giá tham khảo</p>
        <h2 className="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">Giá trọn gói, không phí ẩn</h2>
        <p className="mt-4 text-slate-500">Giá cuối cùng được chốt sau khảo sát. Mọi phát sinh đều cần bạn xác nhận trước.</p>
      </div>
      <div className="grid gap-4 lg:grid-cols-3">
        {PLANS.map((p) => (
          <div key={p.name} className={cx('relative flex flex-col rounded-3xl border p-6 sm:p-8', p.featured ? 'border-slate-900 bg-obsidian text-white shadow-2xl shadow-slate-900/20' : 'border-slate-200 bg-white')}>
            {p.featured && <span className="absolute -top-3 left-6 rounded-full bg-brand px-3 py-1 text-xs font-semibold text-white">Phổ biến nhất</span>}
            <p className="text-lg font-semibold">{p.name}</p>
            <p className={cx('text-sm', p.featured ? 'text-white/50' : 'text-slate-500')}>{p.desc}</p>
            <p className="mt-6 text-sm">Từ</p>
            <p className="text-4xl font-bold tracking-tight tabular-nums">{formatVND(p.price)}</p>
            <ul className="mt-6 flex-1 space-y-3 text-sm">
              {p.items.map((i) => (
                <li key={i} className="flex gap-2.5"><Check className="size-4 shrink-0 translate-y-0.5 text-brand" /> <span className={p.featured ? 'text-white/80' : 'text-slate-600'}>{i}</span></li>
              ))}
            </ul>
            <button type="button" onClick={onBook} className={cx('mt-8 h-12 rounded-xl font-semibold transition-colors', p.featured ? 'bg-brand text-white hover:bg-brand-hover' : 'border border-slate-200 text-slate-900 hover:bg-slate-50')}>
              Nhận báo giá chính xác
            </button>
          </div>
        ))}
      </div>
    </div>
  </section>
);
