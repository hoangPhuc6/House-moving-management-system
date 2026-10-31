import { FileText, History, Navigation, ScanLine, ShieldCheck, Truck, Video } from 'lucide-react';
import { cx } from '@/components/ui';
import { BentoCard } from '@/features/landing/components/BentoCard';

export const BentoFeatures = () => (
  <section id="features" className="scroll-mt-20 bg-canvas py-20 sm:py-28">
    <div className="mx-auto max-w-7xl px-4 sm:px-6">
      <div className="mx-auto mb-12 max-w-2xl text-center">
        <p className="text-sm font-semibold text-brand">Nền tảng vận hành</p>
        <h2 className="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">
          Một hệ thống. Bốn lớp minh bạch.
        </h2>
        <p className="mt-4 text-slate-500">
          Mỗi con số trong báo giá đều có nguồn gốc, mỗi thay đổi đều có dấu
          vết, mỗi chiếc xe đều có vị trí.
        </p>
      </div>
      <div className="grid gap-4 md:grid-cols-3">
        <BentoCard
          className="md:col-span-2"
          icon={Video}
          eyebrow="Khảo sát AI / Video"
          title="Quay video 2 phút, AI nhận diện đồ đạc"
          desc="Khách quay nhanh căn nhà, hệ thống đếm đồ đạc, ước tính khối lượng và đề xuất loại xe — đội khảo sát chỉ cần xác nhận."
        >
          <div className="relative h-48 overflow-hidden rounded-2xl bg-slate-900">
            <div className="bg-grid-dark absolute inset-0" />
            <div className="absolute top-4 left-4 flex items-center gap-2 rounded-full bg-black/40 px-2.5 py-1 text-[11px] font-semibold text-white backdrop-blur">
              <span className="size-1.5 animate-pulse rounded-full bg-rose-500" />{" "}
              REC 01:42
            </div>
            {[
              { l: "Tủ lạnh 2 cánh", c: "left-[12%] top-[38%] h-24 w-20" },
              { l: "Sofa 3 chỗ", c: "left-[38%] top-[52%] h-16 w-36" },
              { l: "12 thùng carton", c: "right-[10%] top-[30%] h-20 w-24" },
            ].map((b) => (
              <div
                key={b.l}
                className={cx(
                  "absolute rounded-md border-2 border-brand/90 bg-brand/10",
                  b.c,
                )}
              >
                <span className="absolute -top-6 left-0 rounded bg-brand px-1.5 py-0.5 text-[10px] font-semibold whitespace-nowrap text-white">
                  {b.l}
                </span>
              </div>
            ))}
            <ScanLine className="absolute right-4 bottom-4 size-6 text-white/40" />
          </div>
        </BentoCard>

        <BentoCard
          icon={FileText}
          eyebrow="Single Source of Truth"
          title="Báo giá một nguồn dữ liệu"
          desc="Khách, điều phối và kế toán cùng nhìn một bảng giá — mọi chỉnh sửa được ghi nhật ký."
        >
          <div className="space-y-2 rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm">
            {[
              ["Xe tải 2 tấn", "1.282.500 đ"],
              ["4 nhân công", "1.400.000 đ"],
              ["Phụ phí hẻm", "160.000 đ"],
            ].map(([a, b]) => (
              <div key={a} className="flex justify-between">
                <span className="text-slate-500">{a}</span>
                <span className="font-semibold tabular-nums">{b}</span>
              </div>
            ))}
            <div className="flex items-center gap-1.5 border-t border-slate-200 pt-2 text-xs text-blue-700">
              <History className="size-3.5" /> CSKH cập nhật chiết khấu −5%
            </div>
          </div>
        </BentoCard>

        <BentoCard
          icon={Navigation}
          eyebrow="Real-time"
          title="Theo dõi tiến độ từng phút"
          desc="Từ lúc nhận vật tư tại kho đến khi ký biên bản — cập nhật ngay trên điện thoại của bạn."
        >
          <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
            <div className="mb-3 flex justify-between text-xs font-semibold">
              <span className="text-slate-500">Q.3</span>
              <span className="text-brand">Đang di chuyển · 62%</span>
              <span className="text-slate-500">Thủ Đức</span>
            </div>
            <div className="relative h-2 rounded-full bg-slate-200">
              <div className="h-2 w-[62%] rounded-full bg-brand" />
              <div className="absolute top-1/2 left-[62%] grid size-7 -translate-x-1/2 -translate-y-1/2 place-items-center rounded-full bg-white shadow ring-1 ring-slate-200">
                <Truck className="size-3.5 text-brand" />
              </div>
            </div>
            <p className="mt-3 text-xs text-slate-500 tabular-nums">
              51C-889.92 · ETA 10:45
            </p>
          </div>
        </BentoCard>

        <BentoCard
          className="md:col-span-2"
          icon={ShieldCheck}
          eyebrow="Bảo hiểm & Cam kết"
          title="Đền bù minh bạch, xử lý khiếu nại độc lập"
          desc="Ảnh/video đóng gói được lưu trữ làm bằng chứng. Khiếu nại được xử lý trên phiếu riêng, không chỉnh sửa hồ sơ doanh thu gốc."
        >
          <div className="grid grid-cols-3 gap-3">
            {[
              ["50 triệu", "Hạn mức bảo hiểm / đơn"],
              ["48 giờ", "Phản hồi khiếu nại"],
              ["100%", "Đồ đạc được chụp trước khi bốc"],
            ].map(([v, l]) => (
              <div
                key={l}
                className="rounded-2xl border border-slate-200 bg-slate-50 p-4"
              >
                <p className="text-xl font-bold tracking-tight tabular-nums sm:text-2xl">
                  {v}
                </p>
                <p className="mt-1 text-xs text-slate-500">{l}</p>
              </div>
            ))}
          </div>
        </BentoCard>
      </div>
    </div>
  </section>
);

// ---------------------------------------------------------------------------
