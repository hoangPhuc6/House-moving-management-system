import {
  CheckCircle2,
  Info,
  Layers,
  ShieldCheck,
  Sparkles,
  Truck,
  Users,
} from "lucide-react";
import { Badge, Toggle, cx } from "@/components/ui";
import { SERVICE_GROUP_LABEL } from "@/constants/catalog";
import { VEHICLE_PRICING } from "@/constants/pricing";
import { parseCapacityTons } from "@/services/dispatch";
import { formatVND } from "@/services/format";
import type { ServiceGroup, ServiceMode } from "@/types";
import { SERVICE_MODES } from "./constants";
import { CompactStepper, FieldError, SectionCard, choiceClass } from "./parts";
import { HomeScaleSection, SurveySection } from "./StepSurvey";
import type { BookingWizard, StepErrors } from "./useBookingWizard";

/** Phụ phí (lầu, hẻm…) không cho khách tự chọn — điều phối tính sau khảo sát. */
const ADD_ON_GROUPS: ServiceGroup[] = ["PACKING", "ASSEMBLY"];

export const StepServices = ({
  wizard,
  errors,
}: {
  wizard: BookingWizard;
  errors: StepErrors;
}) => {
  const {
    draft,
    update,
    activeServices,
    laborService,
    vehiclePricing,
    selectVehicle,
  } = wizard;
  const truckOnly = draft.serviceMode === "TRUCK_ONLY";
  const vehicles = activeServices.filter((s) => s.group === "TRANSPORT");
  const toggleAddOn = (name: string, on: boolean) =>
    update(
      "addOns",
      on ? [...draft.addOns, name] : draft.addOns.filter((n) => n !== name),
    );

  return (
    <div className="space-y-4">
      <SectionCard
        icon={Layers}
        tone="brand"
        title="Nhóm dịch vụ cốt lõi"
        subtitle="Chọn 1 hình thức chính phù hợp với nhu cầu của bạn"
        action={<Badge tone="neutral">Chọn 1</Badge>}
      >
        <div
          className="grid gap-3 md:grid-cols-2"
          role="radiogroup"
          aria-label="Hình thức chuyển nhà"
        >
          {(Object.keys(SERVICE_MODES) as ServiceMode[]).map((m) => {
            const meta = SERVICE_MODES[m];
            const sel = draft.serviceMode === m;
            return (
              <button
                key={m}
                type="button"
                role="radio"
                aria-checked={sel}
                onClick={() => update("serviceMode", m)}
                className={cx(choiceClass(sel), "flex flex-col p-5")}
              >
                <div className="flex w-full items-start justify-between gap-3">
                  <span
                    className={cx(
                      "grid size-11 place-items-center rounded-xl",
                      sel
                        ? "bg-brand text-white"
                        : "bg-slate-100 text-slate-500",
                    )}
                  >
                    <meta.icon className="size-5" />
                  </span>
                  <span className="flex items-center gap-2">
                    <Badge tone={m === "FULL_PACKAGE" ? "brand" : "neutral"}>
                      {meta.badge}
                    </Badge>
                    <span
                      className={cx(
                        "grid size-5 place-items-center rounded-full border-2",
                        sel ? "border-brand" : "border-slate-300",
                      )}
                    >
                      {sel && (
                        <span className="size-2.5 rounded-full bg-brand" />
                      )}
                    </span>
                  </span>
                </div>
                <p className="mt-4 text-base font-semibold text-slate-900">
                  {meta.title}
                </p>
                <p className="mt-1 text-sm leading-relaxed text-slate-500">
                  {meta.desc}
                </p>
                <ul className="mt-3 space-y-1.5">
                  {meta.perks.map((p) => (
                    <li
                      key={p}
                      className="flex items-start gap-2 text-[13px] text-slate-600"
                    >
                      <CheckCircle2
                        className={cx(
                          "mt-0.5 size-4 shrink-0",
                          sel ? "text-brand" : "text-slate-400",
                        )}
                      />{" "}
                      {p}
                    </li>
                  ))}
                </ul>
                <p className="mt-4 w-full rounded-xl bg-slate-50 px-3 py-2 text-xs text-slate-500">
                  Chi phí dịch vụ:{" "}
                  <span className="font-semibold text-slate-800">
                    {m === "FULL_PACKAGE"
                      ? "Báo giá sau khảo sát thực tế"
                      : "Theo loại xe & số nhân viên bạn chọn"}
                  </span>
                </p>
              </button>
            );
          })}
        </div>

        {draft.serviceMode === "FULL_PACKAGE" && (
          <p className="flex items-start gap-2 rounded-xl bg-slate-50 px-3 py-2 text-xs text-slate-600">
            <Truck className="mt-0.5 size-4 shrink-0 text-slate-500" /> Loại xe
            & số nhân sự sẽ do chuyên viên đề xuất sau khi khảo sát.
          </p>
        )}
      </SectionCard>

      {truckOnly && (
        <SectionCard
          id="vehicle"
          icon={Truck}
          tone="brand"
          title="Chọn xe & nhân viên bốc vác"
          subtitle="Giá xe đã gồm 10 km đầu tiên, tài xế hỗ trợ chằng buộc"
          action={<Badge tone="neutral">Bắt buộc</Badge>}
        >
          <div
            className="grid gap-2 sm:grid-cols-3"
            role="radiogroup"
            aria-label="Loại xe"
          >
            {vehicles.map((v) => {
              const cap = parseCapacityTons(v.name);
              const price = cap !== null ? VEHICLE_PRICING[cap] : undefined;
              const sel = draft.vehicleService === v.name;
              return (
                <button
                  key={v.id}
                  type="button"
                  role="radio"
                  aria-checked={sel}
                  onClick={() => selectVehicle(v.name)}
                  className={cx(choiceClass(sel), "flex flex-col p-4")}
                >
                  <span className="text-sm font-semibold text-slate-900">
                    {v.name}
                  </span>
                  <span className="mt-0.5 text-xs leading-relaxed text-slate-500">
                    {v.description}
                  </span>
                  <span className="mt-2 text-sm font-bold tabular-nums text-slate-900">
                    {formatVND(price?.perTrip ?? v.basePrice)}{" "}
                    <span className="text-xs font-medium text-slate-500">
                      / chuyến
                    </span>
                  </span>
                  {price && (
                    <span className="text-[11px] text-slate-500 tabular-nums">
                      +{formatVND(price.perKm)}/km vượt • bốc vác{" "}
                      {formatVND(price.laborPerPerson)}/người
                    </span>
                  )}
                </button>
              );
            })}
          </div>
          <FieldError message={errors.vehicleService} />

          {laborService && (
            <div
              className={cx(
                "flex flex-wrap items-center gap-3 rounded-xl border p-3.5 transition-colors",
                draft.laborCount > 0
                  ? "border-brand/40 bg-orange-50/40"
                  : "border-slate-200",
              )}
            >
              <span
                className={cx(
                  "grid size-9 shrink-0 place-items-center rounded-lg",
                  draft.laborCount > 0
                    ? "bg-brand text-white"
                    : "bg-slate-100 text-slate-500",
                )}
              >
                <Users className="size-[18px]" />
              </span>
              <div className="min-w-0 flex-1">
                <p className="text-sm font-semibold text-slate-900">
                  Thuê thêm nhân viên bốc vác
                </p>
                <p className="mt-0.5 text-xs leading-relaxed text-slate-500">
                  {vehiclePricing
                    ? `Không bắt buộc — ${formatVND(vehiclePricing.laborPerPerson)}/người với ${vehiclePricing.label}, tối đa ${vehiclePricing.maxLabor} người.`
                    : "Chọn loại xe trước — đơn giá bốc vác phụ thuộc loại xe."}
                </p>
              </div>
              {vehiclePricing && (
                <div className="flex items-center gap-3">
                  {draft.laborCount > 0 && (
                    <span className="text-sm font-semibold tabular-nums text-slate-900">
                      {formatVND(
                        vehiclePricing.laborPerPerson * draft.laborCount,
                      )}
                    </span>
                  )}
                  <CompactStepper
                    value={draft.laborCount}
                    onChange={(n) => update("laborCount", n)}
                    label="nhân viên bốc vác"
                    max={vehiclePricing.maxLabor}
                  />
                </div>
              )}
            </div>
          )}
        </SectionCard>
      )}

      {!truckOnly && <HomeScaleSection wizard={wizard} errors={errors} />}

      <SectionCard
        icon={Sparkles}
        title="Nhóm tiện ích bổ sung chuyên sâu"
        subtitle="Có thể chọn nhiều mục — giá tham khảo theo bảng giá hiện hành"
        action={<Badge tone="neutral">Chọn nhiều</Badge>}
      >
        {ADD_ON_GROUPS.map((g) => {
          const list = activeServices.filter((s) => s.group === g);
          if (list.length === 0) return null;
          return (
            <div key={g}>
              <p className="mb-2 text-[11px] font-semibold uppercase tracking-wider text-slate-400">
                {SERVICE_GROUP_LABEL[g]}
              </p>
              <ul className="space-y-2">
                {list.map((s) => {
                  const on = draft.addOns.includes(s.name);
                  return (
                    <li
                      key={s.id}
                      className={cx(
                        "flex items-start gap-3 rounded-xl border p-3.5 transition-colors",
                        on
                          ? "border-brand/40 bg-orange-50/40"
                          : "border-slate-200",
                      )}
                    >
                      <div className="min-w-0 flex-1">
                        <p className="text-sm font-semibold text-slate-900">
                          {s.name}
                        </p>
                        <p className="mt-0.5 text-xs leading-relaxed text-slate-500">
                          {s.description}
                        </p>
                        <p className="mt-1.5 flex items-center gap-1 text-[11px] font-medium text-slate-400">
                          <Info className="size-3" /> Từ{" "}
                          {formatVND(s.basePrice)} / {s.unit} — chốt sau khảo
                          sát
                        </p>
                      </div>
                      <Toggle
                        checked={on}
                        onChange={(v) => toggleAddOn(s.name, v)}
                      />
                    </li>
                  );
                })}
              </ul>
            </div>
          );
        })}
        {draft.serviceMode === "FULL_PACKAGE" && (
          <p className="flex items-center gap-2 rounded-xl bg-slate-50 px-3 py-2 text-xs text-slate-600">
            <CheckCircle2 className="size-4 shrink-0 text-emerald-600" /> Gói
            trọn gói đã gồm nhân viên bốc xếp — không cần chọn thêm.
          </p>
        )}
      </SectionCard>

      {!truckOnly && <SurveySection wizard={wizard} errors={errors} />}

      <div className="flex items-start gap-4 rounded-2xl border border-emerald-200 bg-emerald-50/70 p-5">
        <span className="grid size-11 shrink-0 place-items-center rounded-xl bg-white text-emerald-600 ring-1 ring-emerald-200">
          <ShieldCheck className="size-5" />
        </span>
        <div className="text-sm">
          <p className="font-semibold text-emerald-950">
            Bảo hiểm tài sản 100% khi sử dụng đóng gói trọn gói
          </p>
          <p className="mt-0.5 text-emerald-900/80">
            Mọi đồ dễ vỡ, linh kiện máy móc được đóng gói bởi kỹ thuật viên
            MoveFlow đều nằm trong danh mục bảo vệ, bồi thường theo biên bản nếu
            xảy ra sự cố.
          </p>
        </div>
      </div>
    </div>
  );
};
