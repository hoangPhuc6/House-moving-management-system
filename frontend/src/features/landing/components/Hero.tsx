import { ArrowRight } from 'lucide-react';
import { QuickQuote } from '@/features/landing/components/QuickQuote';

export const Hero = ({ onBook }: { onBook: () => void }) => (
  <section className="relative overflow-hidden bg-obsidian pt-16 text-white">
    <div className="bg-grid-dark absolute inset-0 [mask-image:radial-gradient(ellipse_at_top,black_30%,transparent_75%)]" />
    <div className="pointer-events-none absolute -top-40 left-1/2 h-[520px] w-[900px] -translate-x-1/2 rounded-full bg-brand/20 blur-[120px]" />
    <div className="pointer-events-none absolute right-[-10%] bottom-[-20%] h-[420px] w-[520px] rounded-full bg-blue-500/10 blur-[120px]" />

    <div className="relative mx-auto grid max-w-7xl gap-12 px-4 pt-10 pb-20 sm:px-6 lg:grid-cols-[1.1fr_1fr] lg:gap-16 lg:pt-16 lg:pb-28">
      <div className="flex flex-col justify-center">
        <a
          href="#process"
          className="mb-6 inline-flex w-fit items-center gap-2 rounded-full border border-white/10 bg-white/[0.04] py-1 pr-3 pl-1 text-xs font-medium text-white/70 backdrop-blur hover:border-white/20"
        >
          <span className="rounded-full bg-brand px-2 py-0.5 text-[11px] font-semibold text-white">
            Mới
          </span>
          Điều phối thời gian thực cho 120+ đội xe
          <ArrowRight className="size-3.5" />
        </a>
        <h1 className="text-[40px] leading-[1.05] font-bold tracking-tight sm:text-5xl lg:text-[58px]">
          Chuyển Nhà Minh Bạch.
          <br />
          <span className="text-white/50">Báo Giá Tự Động.</span>
          <br />
          Điều Phối <span className="text-brand">Thời Gian Thực.</span>
        </h1>
        <p className="mt-6 max-w-xl text-base leading-relaxed text-white/60 sm:text-lg">
          MoveFlow kết nối khách hàng, đội vận chuyển, điều phối viên và kế toán
          trên một nguồn dữ liệu duy nhất — từ khảo sát video đến biên bản bàn
          giao.
        </p>
        <div className="mt-8 flex flex-wrap items-center gap-3">
          <button
            type="button"
            onClick={onBook}
            className="inline-flex h-12 items-center gap-2 rounded-xl bg-brand px-5 text-[15px] font-semibold text-white shadow-lg shadow-brand/30 hover:bg-brand-hover"
          >
            Đặt lịch chuyển nhà <ArrowRight className="size-4" />
          </button>
          <a
            href="#process"
            className="inline-flex h-12 items-center gap-2 rounded-xl border border-white/15 px-5 text-[15px] font-semibold text-white/90 hover:bg-white/5"
          >
            Xem quy trình 7 bước
          </a>
        </div>
        <dl className="mt-12 grid max-w-lg grid-cols-3 gap-6 border-t border-white/10 pt-6">
          {[
            ["18.400+", "Đơn hoàn tất"],
            ["4,9/5", "Đánh giá khách hàng"],
            ["< 15′", "Ra báo giá"],
          ].map(([v, l]) => (
            <div key={l}>
              <dt className="text-2xl font-bold tracking-tight tabular-nums">
                {v}
              </dt>
              <dd className="mt-1 text-xs text-white/50">{l}</dd>
            </div>
          ))}
        </dl>
      </div>

      <div className="relative">
        <div className="pointer-events-none absolute inset-0 -m-10 rounded-full bg-[radial-gradient(circle_at_center,rgba(255,90,31,0.28),transparent_65%)]" />
        <QuickQuote onBook={onBook} />
      </div>
    </div>
  </section>
);
