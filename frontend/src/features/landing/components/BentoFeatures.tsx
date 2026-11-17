import { FileText, History, Navigation, ShieldCheck, Truck, Video } from 'lucide-react';
import { SURVEY_PREFERENCES } from '@/features/customer/booking/constants';
import { BentoCard } from '@/features/landing/components/BentoCard';
import type { SurveyPreference } from '@/types';

const SURVEY_ORDER: SurveyPreference[] = ['VIDEO_CALL', 'ON_SITE', 'PHOTOS_ONLY'];

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
          vết, mỗi chuyến xe đều có mốc tiến độ.
        </p>
      </div>
      <div className="grid gap-4 md:grid-cols-3">
        <BentoCard
          className="md:col-span-2"
          icon={Video}
          eyebrow="Khảo sát miễn phí"
          title="Video call, tận nơi hoặc gửi ảnh — bạn chọn"
          desc="Video call ~10 phút qua Zalo / Messenger, chuyên viên đến tận nơi ~30 phút, hoặc tự tải ảnh / video đồ đạc. Điều phối viên lập báo giá từ kết quả khảo sát."
        >
          <div className="grid gap-3 sm:h-48 sm:grid-cols-3">
            {SURVEY_ORDER.map((k) => {
              const m = SURVEY_PREFERENCES[k];
              const Icon = m.icon;
              return (
                <div
                  key={k}
                  className="flex flex-col justify-between gap-4 rounded-2xl border border-slate-200 bg-slate-50 p-4"
                >
                  <div className="flex items-start justify-between gap-2">
                    <span className="grid size-10 place-items-center rounded-xl bg-white ring-1 ring-slate-200">
                      <Icon className="size-5 text-brand" />
                    </span>
                    {m.badge && (
                      <span className="rounded-full bg-brand/10 px-2 py-0.5 text-[10px] font-semibold text-brand">
                        {m.badge}
                      </span>
                    )}
                  </div>
                  <div>
                    <p className="text-sm font-semibold text-slate-900">{m.title}</p>
                    <p className="mt-1 text-xs text-slate-500">{m.meta.join(" · ")}</p>
                  </div>
                </div>
              );
            })}
          </div>
        </BentoCard>

        <BentoCard
          icon={FileText}
          eyebrow="Single Source of Truth"
          title="Báo giá một nguồn dữ liệu"
          desc="Khách, điều phối viên và admin cùng nhìn một bảng giá — mọi chỉnh sửa được ghi vào Audit Log."
        >
          <div className="space-y-2 rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm">
            {[
              ["Xe tải 2 tấn", "900.000 đ"],
              ["3 nhân viên bốc xếp", "1.050.000 đ"],
              ["Phụ phí hẻm sâu", "80.000 đ"],
            ].map(([a, b]) => (
              <div key={a} className="flex justify-between">
                <span className="text-slate-500">{a}</span>
                <span className="font-semibold tabular-nums">{b}</span>
              </div>
            ))}
            <div className="flex items-center gap-1.5 border-t border-slate-200 pt-2 text-xs text-blue-700">
              <History className="size-3.5" /> Điều phối cập nhật giảm giá toàn đơn −5%
            </div>
          </div>
        </BentoCard>

        <BentoCard
          icon={Navigation}
          eyebrow="Tiến độ trực tiếp"
          title="Theo dõi đội xe theo từng mốc"
          desc="Từ lúc nhận vật tư tại kho đến khi ký biên bản — cập nhật ngay trên điện thoại của bạn."
        >
          <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
            <div className="mb-3 flex justify-between text-xs font-semibold">
              <span className="text-slate-500">Q.3</span>
              <span className="text-brand">Đang di chuyển · 3/5</span>
              <span className="text-slate-500">Thủ Đức</span>
            </div>
            <div className="relative h-2 rounded-full bg-slate-200">
              <div className="h-2 w-[60%] rounded-full bg-brand" />
              <div className="absolute top-1/2 left-[60%] grid size-7 -translate-x-1/2 -translate-y-1/2 place-items-center rounded-full bg-white shadow ring-1 ring-slate-200">
                <Truck className="size-3.5 text-brand" />
              </div>
            </div>
            <p className="mt-3 text-xs text-slate-500 tabular-nums">
              Nhận vật tư → Bắt đầu → Đang di chuyển → Đã tới nơi → Bàn giao
            </p>
          </div>
        </BentoCard>

        <BentoCard
          className="md:col-span-2"
          icon={ShieldCheck}
          eyebrow="Phát sinh & Sự cố"
          title="Phát sinh cần bạn duyệt, sự cố có phương án"
          desc="Sai lệch hiện trường được gửi thành báo giá điều chỉnh, đội xe chỉ tiếp tục khi bạn xác nhận. Xe hỏng giữa đường được tăng bo sang xe thay thế. Khiếu nại xử lý trên phiếu riêng, không sửa doanh thu đã khóa."
        >
          <div className="grid grid-cols-3 gap-3">
            {[
              ["30 phút", "Chờ khách đầu tiên miễn phí"],
              ["Tăng bo", "Đổi xe khi xe gặp sự cố"],
              ["100%", "Bồi thường tài sản theo biên bản"],
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
