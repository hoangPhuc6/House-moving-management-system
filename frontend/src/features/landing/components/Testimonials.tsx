import { Quote, Star } from 'lucide-react';
import { REVIEWS } from '@/features/landing/constants';

export const Testimonials = () => (
  <section className="bg-white py-20 sm:py-28">
    <div className="mx-auto max-w-7xl px-4 sm:px-6">
      <div className="mb-12 flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-sm font-semibold text-brand">Khách hàng nói gì</p>
          <h2 className="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">4,9/5 từ hơn 6.200 đánh giá</h2>
        </div>
        <div className="flex gap-0.5">{[0, 1, 2, 3, 4].map((i) => <Star key={i} className="size-6 fill-amber-400 text-amber-400" />)}</div>
      </div>
      <div className="scrollbar-none -mx-4 flex snap-x snap-mandatory gap-4 overflow-x-auto px-4 md:mx-0 md:grid md:grid-cols-3 md:overflow-visible md:px-0">
        {REVIEWS.map((r) => (
          <figure key={r.name} className="w-[85%] shrink-0 snap-start rounded-3xl border border-slate-200 bg-canvas p-6 md:w-auto">
            <Quote className="size-6 text-brand" />
            <blockquote className="mt-4 text-[15px] leading-relaxed text-slate-700">“{r.text}”</blockquote>
            <figcaption className="mt-6 flex items-center gap-3">
              <span className="grid size-10 place-items-center rounded-full bg-slate-900 text-sm font-bold text-white">{r.name.split(' ').slice(-1)[0]?.[0]}</span>
              <span>
                <span className="block text-sm font-semibold">{r.name}</span>
                <span className="block text-xs text-slate-500">{r.place}</span>
              </span>
            </figcaption>
          </figure>
        ))}
      </div>
    </div>
  </section>
);
