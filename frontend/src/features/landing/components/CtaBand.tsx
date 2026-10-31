import { ArrowRight, Check } from 'lucide-react';

export const CtaBand = ({ onBook }: { onBook: () => void }) => (
  <section id="cta" className="bg-canvas py-20">
    <div className="mx-auto max-w-7xl px-4 sm:px-6">
      <div className="relative overflow-hidden rounded-3xl bg-obsidian px-6 py-14 text-center text-white sm:px-12">
        <div className="bg-grid-dark absolute inset-0 opacity-70" />
        <div className="pointer-events-none absolute -bottom-32 left-1/2 h-72 w-[600px] -translate-x-1/2 rounded-full bg-brand/30 blur-[100px]" />
        <div className="relative">
          <h2 className="text-3xl font-bold tracking-tight sm:text-4xl">
            Sẵn sàng cho lần chuyển nhà nhẹ nhàng nhất?
          </h2>
          <p className="mx-auto mt-4 max-w-xl text-white/60">
            Khảo sát miễn phí. Báo giá rõ ràng từng dòng. Hủy trước 12 giờ hoàn
            cọc 100%.
          </p>
          <div className="mt-8 flex flex-wrap justify-center gap-3">
            <button
              type="button"
              onClick={onBook}
              className="inline-flex h-12 items-center gap-2 rounded-xl bg-brand px-6 font-semibold hover:bg-brand-hover"
            >
              Đặt lịch ngay <ArrowRight className="size-4" />
            </button>
          </div>
          <ul className="mt-8 flex flex-wrap justify-center gap-x-6 gap-y-2 text-sm text-white/60">
            {["Không phí ẩn", "Bảo hiểm đồ đạc", "Hỗ trợ 24/7"].map((t) => (
              <li key={t} className="flex items-center gap-1.5">
                <Check className="size-4 text-brand" /> {t}
              </li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  </section>
);
