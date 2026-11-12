import {
  CalendarDays,
  ClipboardCheck,
  Layers,
  Mail,
  MessageSquareText,
  Phone,
  Route,
  ShieldCheck,
  User,
} from "lucide-react";
import { Badge, Card, Field, Input, cx } from "@/components/ui";
import { formatDateVN } from "@/services/format";
import type { AddressDetail } from "@/types";
import {
  HOME_SCALES,
  SERVICE_MODES,
  SURVEY_PREFERENCES,
  needsSurveyAppointment,
} from "./constants";
import { EditLink, FieldError, SectionCard } from "./parts";
import type { BookingWizard, StepErrors } from "./useBookingWizard";

const WEEKDAYS = [
  "Chủ nhật",
  "Thứ Hai",
  "Thứ Ba",
  "Thứ Tư",
  "Thứ Năm",
  "Thứ Sáu",
  "Thứ Bảy",
];

const weekdayOf = (iso: string): string => {
  const [y, m, d] = iso.split("-").map(Number);
  if (!y || !m || !d) return "";
  return WEEKDAYS[new Date(y, m - 1, d).getDay()] ?? "";
};

const AddressRow = ({
  marker,
  label,
  value,
  address,
}: {
  marker: string;
  label: string;
  value: AddressDetail;
  address: string;
}) => (
  <div className="flex gap-3">
    <span
      className={cx(
        "grid size-7 shrink-0 place-items-center rounded-full text-xs font-bold",
        marker === "A" ? "bg-slate-900 text-white" : "bg-brand text-white",
      )}
    >
      {marker}
    </span>
    <div className="min-w-0 flex-1">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-[11px] font-semibold uppercase tracking-wider text-slate-400">
          {label}
        </p>
        <Badge tone="neutral">{value.floor}</Badge>
      </div>
      <p className="mt-0.5 text-sm font-semibold text-slate-900">{address}</p>
      <p className="mt-0.5 text-xs text-slate-500">
        {value.propertyType}
        {value.note && <> • {value.note}</>}
      </p>
    </div>
  </div>
);

export const StepReview = ({
  wizard,
  errors,
  onEdit,
}: {
  wizard: BookingWizard;
  errors: StepErrors;
  onEdit: (step: number) => void;
}) => {
  const {
    draft,
    update,
    items,
    volumeM3,
    requestedServices,
    fromAddress,
    toAddress,
    distanceKm,
    laborService,
  } = wizard;
  const truckOnly = draft.serviceMode === "TRUCK_ONLY";
  const mode = SERVICE_MODES[draft.serviceMode];
  const survey = SURVEY_PREFERENCES[draft.surveyPreference];

  const scale = HOME_SCALES.find((s) => s.label === draft.homeScale);

  return (
    <div className="space-y-4">
      <SectionCard
        icon={Route}
        title="Lộ trình & Lịch hẹn"
        subtitle="Điểm đón, giao hàng và thời gian thực hiện"
        action={<EditLink onClick={() => onEdit(0)} />}
      >
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-slate-50 px-4 py-3">
          <div className="flex items-center gap-3">
            <CalendarDays className="size-5 text-slate-500" />
            <div>
              <p className="text-xs text-slate-500">Thời gian vận chuyển</p>
              <p className="text-sm font-semibold text-slate-900">
                {weekdayOf(draft.scheduledDate)},{" "}
                {formatDateVN(draft.scheduledDate)} ({draft.timeSlot})
              </p>
            </div>
          </div>
          {distanceKm !== null && (
            <Badge tone="brand" icon={Route}>
              ~{distanceKm} km
            </Badge>
          )}
        </div>
        <div className="space-y-4">
          <AddressRow
            marker="A"
            label="Địa chỉ nhận hàng (điểm đi)"
            value={draft.from}
            address={fromAddress}
          />
          <AddressRow
            marker="B"
            label="Địa chỉ dỡ đồ (điểm đến)"
            value={draft.to}
            address={toAddress}
          />
        </div>
        {(draft.flexibleTime || draft.scheduleNote) && (
          <p className="rounded-xl border border-slate-200 px-3 py-2 text-xs text-slate-600">
            {draft.flexibleTime && (
              <span className="font-semibold text-slate-800">
                Chưa cố định giờ — cần tư vấn thêm.{" "}
              </span>
            )}
            {draft.scheduleNote}
          </p>
        )}
      </SectionCard>

      <SectionCard
        icon={Layers}
        title="Gói dịch vụ đã chọn"
        subtitle="Các tiện ích bao gồm trong yêu cầu chuyển nhà"
        action={<EditLink onClick={() => onEdit(1)} />}
      >
        <div className="flex items-start gap-3 rounded-xl border border-brand/30 bg-orange-50/50 p-4">
          <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-brand text-white">
            <mode.icon className="size-5" />
          </span>
          <div className="min-w-0">
            <p className="flex flex-wrap items-center gap-2 text-sm font-semibold text-slate-900">
              {mode.title} <Badge tone="brand">{mode.badge}</Badge>
            </p>
            <p className="mt-0.5 text-xs leading-relaxed text-slate-500">
              {mode.desc}
            </p>
          </div>
        </div>
        {requestedServices.length === 0 ? (
          <p className="text-sm text-slate-500">Không chọn tiện ích bổ sung.</p>
        ) : (
          <div className="flex flex-wrap gap-1.5">
            {!truckOnly && (
              <Badge tone="neutral">
                Xe tải: chuyên viên đề xuất sau khảo sát
              </Badge>
            )}
            {requestedServices.map((s) => (
              <Badge key={s} tone="brand">
                {truckOnly && s === laborService?.name
                  ? `${s} ×${draft.laborCount}`
                  : s}
              </Badge>
            ))}
          </div>
        )}
        {truckOnly && draft.laborCount === 0 && (
          <p className="text-xs text-slate-500">
            Không thuê nhân viên bốc vác — bạn tự khiêng vác.
          </p>
        )}
      </SectionCard>

      {!truckOnly && (
        <SectionCard
          icon={ClipboardCheck}
          title="Quy mô nhà & Hình thức khảo sát"
          subtitle="Danh mục đồ chi tiết được chốt khi khảo sát"
          action={<EditLink onClick={() => onEdit(1)} />}
        >
          <div className="grid grid-cols-2 gap-2">
            {[
              {
                k: "Quy mô",
                v: scale ? `${scale.label} (${scale.hint})` : "Chưa chọn",
              },
              { k: "Khối lượng ước tính", v: `≈ ${volumeM3.toFixed(1)} m³` },
            ].map((x) => (
              <div
                key={x.k}
                className="rounded-xl border border-slate-200 px-3 py-2.5 text-center"
              >
                <p className="text-[11px] font-medium text-slate-500">{x.k}</p>
                <p className="mt-0.5 text-sm font-semibold text-slate-900 tabular-nums">
                  {x.v}
                </p>
              </div>
            ))}
          </div>
          {items.length > 0 && (
            <p className="text-xs text-slate-500">
              Ước lượng gồm:{" "}
              {items.map((i) => `${i.name} ×${i.quantity}`).join(", ")}.
            </p>
          )}
          <div className="flex items-start gap-3 rounded-xl bg-slate-50 p-3.5">
            <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-white text-slate-700 ring-1 ring-slate-200">
              <survey.icon className="size-[18px]" />
            </span>
            <div className="text-sm">
              <p className="text-[11px] font-semibold uppercase tracking-wider text-slate-400">
                Hình thức khảo sát
              </p>
              <p className="font-semibold text-slate-900">{survey.title}</p>
              <p className="text-xs text-slate-500">
                {needsSurveyAppointment(draft.surveyPreference)
                  ? `Lịch hẹn: ${weekdayOf(draft.surveyDate)}, ${formatDateVN(draft.surveyDate)} (${draft.surveyContactSlot})`
                  : "Tải ảnh / video ở trang chi tiết đơn sau khi gửi yêu cầu"}
              </p>
            </div>
          </div>
        </SectionCard>
      )}

      <SectionCard
        icon={User}
        tone="brand"
        title="Người liên hệ"
        subtitle="Điều phối viên sẽ gọi theo thông tin này để khảo sát & báo giá"
      >
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Họ và tên *" error={errors.customerName ?? null}>
            <div className="relative">
              <User className="pointer-events-none absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-slate-400" />
              <Input
                value={draft.customerName}
                invalid={Boolean(errors.customerName)}
                onChange={(e) => update("customerName", e.target.value)}
                autoComplete="name"
                className="pl-10 text-base sm:text-sm"
              />
            </div>
          </Field>
          <Field label="Số điện thoại *" error={errors.phone ?? null}>
            <div className="relative">
              <Phone className="pointer-events-none absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-slate-400" />
              <Input
                value={draft.phone}
                invalid={Boolean(errors.phone)}
                onChange={(e) => update("phone", e.target.value)}
                inputMode="tel"
                autoComplete="tel"
                className="pl-10 text-base sm:text-sm"
              />
            </div>
          </Field>
          <Field
            label="Hòm thư điện tử"
            hint="Nhận bản sao báo giá qua email (tùy chọn)"
            error={errors.email ?? null}
            className="sm:col-span-2"
          >
            <div className="relative">
              <Mail className="pointer-events-none absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-slate-400" />
              <Input
                type="email"
                value={draft.email}
                invalid={Boolean(errors.email)}
                onChange={(e) => update("email", e.target.value)}
                autoComplete="email"
                placeholder="ban@email.com"
                className="pl-10 text-base sm:text-sm"
              />
            </div>
          </Field>
          <Field
            label="Ghi chú bổ sung cho điều phối viên"
            className="sm:col-span-2"
          >
            <div className="relative">
              <MessageSquareText className="pointer-events-none absolute top-3.5 left-3.5 size-4 text-slate-400" />
              <textarea
                value={draft.customerNote}
                onChange={(e) => update("customerNote", e.target.value)}
                rows={3}
                placeholder="VD: Ưu tiên xe tải vào giờ trưa để tránh kẹt xe"
                className="w-full rounded-xl border border-slate-200 bg-white py-3 pr-3.5 pl-10 text-base text-slate-900 placeholder:text-slate-400 focus:border-brand focus:ring-4 focus:ring-brand/10 focus:outline-none sm:text-sm"
              />
            </div>
          </Field>
        </div>
      </SectionCard>

      <Card className={cx("space-y-3", errors.agreed && "border-rose-300")}>
        <div className="flex items-start gap-3">
          <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-emerald-50 text-emerald-600 ring-1 ring-emerald-200">
            <ShieldCheck className="size-5" />
          </span>
          <div className="text-sm">
            <p className="font-semibold text-slate-900">
              Quy trình điều phối minh bạch
            </p>
            <p className="mt-0.5 text-slate-500">
              Sau khi gửi yêu cầu, điều phối viên MoveFlow sẽ liên hệ khảo sát.
              Bạn sẽ nhận được{" "}
              <span className="font-semibold text-slate-800">
                bảng báo giá chi tiết
              </span>
              , minh bạch từng dòng trước khi quyết định đặt cọc.
            </p>
          </div>
        </div>
        <label className="flex items-start gap-2.5 rounded-xl bg-slate-50 p-3 text-sm text-slate-700">
          <input
            type="checkbox"
            checked={draft.agreed}
            onChange={(e) => update("agreed", e.target.checked)}
            className="mt-0.5 size-4 shrink-0 rounded accent-[#FF5A1F]"
          />
          <span>
            Tôi xác nhận thông tin trên là chính xác và đồng ý với{" "}
            <span className="font-semibold text-brand">Điều khoản dịch vụ</span>{" "}
            của MoveFlow.
          </span>
        </label>
        <FieldError message={errors.agreed} />
      </Card>
    </div>
  );
};
