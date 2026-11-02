import { useState } from 'react';
import { ChevronDown } from 'lucide-react';
import { cx } from '@/components/ui';
import { FAQS } from '@/features/landing/constants';

export const Faq = () => {
  const [open, setOpen] = useState<number | null>(0);
  return (
    <section id="faq" className="scroll-mt-20 border-t border-slate-200 bg-canvas py-20 sm:py-28">
      <div className="mx-auto grid max-w-7xl gap-10 px-4 sm:px-6 lg:grid-cols-[1fr_1.4fr]">
        <div>
          <p className="text-sm font-semibold text-brand">Hỏi đáp</p>
          <h2 className="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">Câu hỏi thường gặp</h2>
          <p className="mt-4 text-slate-500">Chưa tìm thấy câu trả lời? Gọi <a href="tel:19006868" className="font-semibold text-slate-900 underline">1900 6868</a>, chúng tôi hỗ trợ 24/7.</p>
        </div>
        <div className="divide-y divide-slate-200 rounded-3xl border border-slate-200 bg-white">
          {FAQS.map(([q, a], i) => (
            <div key={q}>
              <button type="button" onClick={() => setOpen(open === i ? null : i)} className="flex w-full items-center justify-between gap-4 px-5 py-5 text-left sm:px-6" aria-expanded={open === i}>
                <span className="font-semibold text-slate-900">{q}</span>
                <ChevronDown className={cx('size-5 shrink-0 text-slate-400 transition-transform', open === i && 'rotate-180')} />
              </button>
              {open === i && <p className="anim-fade -mt-1 px-5 pb-5 text-sm leading-relaxed text-slate-600 sm:px-6">{a}</p>}
            </div>
          ))}
        </div>
      </div>
    </section>
  );
};
