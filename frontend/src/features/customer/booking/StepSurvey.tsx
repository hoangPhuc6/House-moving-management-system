import { CalendarDays, CloudUpload, Headset, Home, Info } from "lucide-react";
import { Badge, Field, Input, cx } from "@/components/ui";
import type { SurveyPreference } from "@/types";
import {
  HOME_SCALES,
  SURVEY_PREFERENCES,
  SURVEY_TIME_SLOTS,
  needsSurveyAppointment,
} from "./constants";
import { FieldError, SectionCard, choiceClass } from "./parts";
import {
  type BookingWizard,
  type StepErrors,
  todayISO,
} from "./useBookingWizard";

/** Quy mô nhà — chỉ để ước lượng giá tham khảo; danh mục chính thức được chốt khi khảo sát. */
export const HomeScaleSection = ({
  wizard,
  errors,
}: {
  wizard: BookingWizard;
  errors: StepErrors;
}) => {
  const { draft, update, volumeM3 } = wizard;
  return (
    <SectionCard
      id="home-scale"
      icon={Home}
      tone="brand"
      title="Quy mô nhà hiện tại"
      subtitle="Chọn mức gần đúng nhất — danh mục đồ chi tiết sẽ được chốt khi khảo sát"
      action={
        volumeM3 > 0 ? (
          <Badge tone="brand">≈ {volumeM3.toFixed(1)} m³</Badge>
        ) : (
          <Badge tone="neutral">Bắt buộc</Badge>
        )
      }
    >
      <div
        className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-5"
        role="radiogroup"
        aria-label="Quy mô nhà ở"
      >
        {HOME_SCALES.map((s) => {
          const sel = draft.homeScale === s.label;
          return (
            <button
              key={s.label}
              type="button"
              role="radio"
              aria-checked={sel}
              onClick={() => update("homeScale", s.label)}
              className={cx(choiceClass(sel), "p-3")}
            >
              <span
                className={cx(
                  "block text-sm font-semibold",
                  sel ? "text-slate-900" : "text-slate-800",
                )}
              >
                {s.label}
              </span>
              <span className="block text-xs text-slate-500">{s.hint}</span>
            </button>
          );
        })}
      </div>
      <FieldError message={errors.homeScale} />
      <p className="flex items-start gap-1.5 text-xs text-slate-500">
        <Info className="mt-0.5 size-3.5 shrink-0" /> Có đồ đặc biệt (đàn piano,
        két sắt, gương lớn…)? Ghi chú ở bước Xác nhận hoặc báo chuyên viên khi
        khảo sát.
      </p>
    </SectionCard>
  );
};

const prevDay = (iso: string): string => {
  const [y, m, d] = iso.split("-").map(Number);
  if (!y || !m || !d) return "";
  const dt = new Date(y, m - 1, d - 1);
  const p = (n: number) => String(n).padStart(2, "0");
  return `${dt.getFullYear()}-${p(dt.getMonth() + 1)}-${p(dt.getDate())}`;
};

export const SurveySection = ({
  wizard,
  errors,
}: {
  wizard: BookingWizard;
  errors: StepErrors;
}) => {
  const { draft, update } = wizard;
  const appointment = needsSurveyAppointment(draft.surveyPreference);
  return (
    <SectionCard
      icon={Headset}
      title="Hình thức khảo sát"
      subtitle="Miễn phí 100% — không phát sinh chi phí dù không sử dụng dịch vụ"
      action={<Badge tone="success">0 đ</Badge>}
    >
      <div
        className="grid gap-2 md:grid-cols-3"
        role="radiogroup"
        aria-label="Hình thức khảo sát"
      >
        {(Object.keys(SURVEY_PREFERENCES) as SurveyPreference[]).map((k) => {
          const meta = SURVEY_PREFERENCES[k];
          const sel = draft.surveyPreference === k;
          return (
            <button
              key={k}
              type="button"
              role="radio"
              aria-checked={sel}
              onClick={() => update("surveyPreference", k)}
              className={cx(
                choiceClass(sel),
                "flex w-full items-start gap-3 p-4",
              )}
            >
              <span
                className={cx(
                  "mt-0.5 grid size-5 shrink-0 place-items-center rounded-full border-2",
                  sel ? "border-brand" : "border-slate-300",
                )}
              >
                {sel && <span className="size-2.5 rounded-full bg-brand" />}
              </span>
              <span className="min-w-0 flex-1">
                <span className="flex flex-wrap items-center gap-2 text-sm font-semibold text-slate-900">
                  {meta.title}
                  {meta.badge && (
                    <Badge tone={k === "VIDEO_CALL" ? "brand" : "neutral"}>
                      {meta.badge}
                    </Badge>
                  )}
                </span>
                <span className="mt-1 block text-[13px] leading-relaxed text-slate-500">
                  {meta.desc}
                </span>
                <span className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs font-medium text-slate-500">
                  {meta.meta.map((m) => (
                    <span key={m} className="inline-flex items-center gap-1">
                      <meta.icon className="size-3.5 text-slate-400" /> {m}
                    </span>
                  ))}
                </span>
              </span>
            </button>
          );
        })}
      </div>

      {appointment ? (
        <div className="space-y-3 rounded-2xl bg-slate-50 p-4">
          <p className="flex items-center gap-2 text-[13px] font-semibold text-slate-700">
            <CalendarDays className="size-4 text-slate-500" />{" "}
            {draft.surveyPreference === "ON_SITE"
              ? "Lịch hẹn chuyên viên đến khảo sát"
              : "Lịch hẹn gọi video khảo sát"}
          </p>
          <Field
            label="Ngày khảo sát *"
            hint="Trước ngày chuyển nhà ít nhất 1 ngày"
            error={errors.surveyDate ?? null}
            className="sm:max-w-xs"
          >
            <Input
              type="date"
              min={todayISO()}
              max={
                draft.scheduledDate ? prevDay(draft.scheduledDate) : undefined
              }
              value={draft.surveyDate}
              invalid={Boolean(errors.surveyDate)}
              onChange={(e) => update("surveyDate", e.target.value)}
              className="text-base sm:text-sm"
            />
          </Field>
          <div>
            <p className="mb-2 text-[13px] font-medium text-slate-700">
              Khung giờ *
            </p>
            <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
              {SURVEY_TIME_SLOTS.map((s) => {
                const sel = draft.surveyContactSlot === s;
                return (
                  <button
                    key={s}
                    type="button"
                    aria-pressed={sel}
                    onClick={() => update("surveyContactSlot", s)}
                    className={cx(
                      "min-h-11 rounded-xl border px-3 py-2 text-xs font-semibold transition-colors",
                      sel
                        ? "border-slate-900 bg-slate-900 text-white"
                        : "border-slate-200 bg-white text-slate-700 hover:border-slate-300",
                    )}
                  >
                    {s}
                  </button>
                );
              })}
            </div>
            <FieldError message={errors.surveyContactSlot} />
          </div>
          <p className="flex items-center gap-1.5 text-xs text-slate-500">
            <Info className="size-3.5 shrink-0" /> Chuyên viên gọi xác nhận lịch
            hẹn qua số điện thoại bạn khai báo.
          </p>
        </div>
      ) : (
        <p className="flex items-start gap-2 rounded-xl border border-blue-200 bg-blue-50/60 px-3 py-2.5 text-xs text-blue-900">
          <CloudUpload className="mt-0.5 size-4 shrink-0 text-blue-600" /> Sau
          khi gửi yêu cầu, vào trang chi tiết đơn để tải ảnh / video phòng ốc,
          đồ lớn và lối đi. Chuyên viên lập báo giá dựa trên nội dung bạn gửi.
        </p>
      )}
    </SectionCard>
  );
};
