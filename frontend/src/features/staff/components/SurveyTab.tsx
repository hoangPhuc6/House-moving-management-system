import { type ReactNode, useMemo, useState } from 'react';
import {
  CheckCircle2,
  Footprints,
  MapPin,
  Navigation,
  PackageCheck,
  Phone,
  Truck,
} from 'lucide-react';
import { Badge, Button, cx, EmptyState, NumberStepper, Select } from '@/components/ui';
import { HOUSE_TYPE_META } from '@/constants/order';
import { MIN_SURVEY_INPUT, VEHICLE_PRICING } from '@/constants/pricing';
import { Block } from '@/features/staff/components/Block';
import { CustomerInfo } from '@/features/staff/components/CustomerInfo';
import { StickyActionBar } from '@/layouts/MobileShell';
import { formatDateVN, formatTime, formatVND } from '@/services/format';
import { buildSurveyQuote, quoteSubtotal } from '@/services/pricing';
import { useApp } from '@/services/store/AppStore';
import type { SurveyInput } from '@/types';

export const SurveyTab = () => {
  const { state, dispatch } = useApp();
  // Nhân viên vận chuyển chỉ khảo sát trực tiếp — đơn khảo sát qua ảnh do Điều phối lập báo giá.
  const candidates = state.orders.filter(
    (o) => o.status === "NEW_PENDING_SURVEY" && o.onSiteSurveyRequest,
  );
  const [targetId, setTargetId] = useState(candidates[0]?.id ?? "");
  const [s, setS] = useState<SurveyInput>(MIN_SURVEY_INPUT);

  const set = <K extends keyof SurveyInput>(k: K, v: SurveyInput[K]) =>
    setS((x) => ({ ...x, [k]: v }));
  const items = useMemo(() => buildSurveyQuote(s), [s]);
  const total = quoteSubtotal(items);
  const target = candidates.find((o) => o.id === targetId) ?? candidates[0];
  const request = target?.onSiteSurveyRequest;

  if (!target || !request) {
    return (
      <EmptyState icon={Footprints} title="Chưa có yêu cầu khảo sát trực tiếp">
        Bạn chỉ đến khảo sát tại nhà khách khi Điều phối gửi yêu cầu.
      </EmptyState>
    );
  }

  const row = (label: string, node: ReactNode) => (
    <div className="flex items-center justify-between gap-3 py-2.5">
      <span className="text-sm font-medium text-slate-700">{label}</span>
      <div className="w-40 shrink-0">{node}</div>
    </div>
  );

  return (
    <div className="space-y-3 pb-24 md:pb-0">
      <Block title="Đơn khảo sát trực tiếp" icon={MapPin}>
        <Select
          value={target.id}
          onChange={(e) => setTargetId(e.target.value)}
          className="text-base sm:text-sm"
        >
          {candidates.map((o) => (
            <option key={o.id} value={o.id}>
              #{o.id} · {o.customerName}
            </option>
          ))}
        </Select>
        <div className="mt-3"><CustomerInfo order={target} /></div>
        <p className="mt-2.5 text-sm text-slate-600">
          {target.fromAddress} → {target.toAddress}
        </p>
        <div className="mt-2 flex flex-wrap gap-1.5">
          <Badge tone="info">{HOUSE_TYPE_META[target.houseType].label}</Badge>
          <Badge tone="neutral">
            {formatDateVN(target.scheduledDate)} · {target.timeSlot}
          </Badge>
        </div>
      </Block>

      <Block
        title="Yêu cầu từ Điều phối"
        icon={Footprints}
        right={
          <span className="text-xs text-slate-400 tabular-nums">
            {formatTime(request.requestedAt)}
          </span>
        }
      >
        <p className="rounded-xl bg-amber-50 p-3 text-sm text-amber-900">
          {request.note || "Cần đến tận nơi để khảo sát."}
        </p>
        <p className="mt-2 text-xs font-semibold text-slate-700">
          Nhân viên được cử:{" "}
          {state.resources.staff.find((s) => s.id === request.surveyorId)?.name ?? "—"}
        </p>
        <p className="mt-2 text-xs text-slate-500">
          Khách đã gửi kèm{" "}
          <span className="tabular-nums">{target.customerPhotos.length}</span>{" "}
          ảnh đồ đạc.
        </p>
        <div className="mt-3 grid grid-cols-2 gap-2">
          <a
            href={`tel:${target.phone.replace(/\s/g, "")}`}
            className="flex h-12 items-center justify-center gap-2 rounded-xl border border-slate-200 text-sm font-semibold text-slate-700 active:bg-slate-50"
          >
            <Phone className="size-4" /> Hẹn giờ với khách
          </a>
          <a
            href={`https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(target.fromAddress)}`}
            target="_blank"
            rel="noreferrer"
            className="flex h-12 items-center justify-center gap-2 rounded-xl border border-slate-200 text-sm font-semibold text-slate-700 active:bg-slate-50"
          >
            <Navigation className="size-4" /> Đến nhà khách
          </a>
        </div>
      </Block>

      <Block title="Xe & nhân viên bốc xếp" icon={Truck}>
        <div className="divide-y divide-slate-100">
          {row(
            "Quãng đường",
            <NumberStepper
              value={s.distanceKm}
              onChange={(v) => set("distanceKm", v)}
              min={1}
              max={200}
              suffix="km"
            />,
          )}
          <div className="py-2.5">
            <span className="text-sm font-medium text-slate-700">Loại xe</span>
            <div className="mt-2 grid grid-cols-3 gap-1.5">
              {Object.keys(VEHICLE_PRICING)
                .map(Number)
                .sort((a, b) => a - b)
                .map((cap) => (
                  <button
                    key={cap}
                    type="button"
                    onClick={() => set("vehicleCapacity", cap)}
                    className={cx(
                      "h-12 rounded-xl border text-sm font-semibold tabular-nums",
                      s.vehicleCapacity === cap
                        ? "border-slate-900 bg-slate-900 text-white"
                        : "border-slate-200 text-slate-600",
                    )}
                  >
                    {String(cap).replace(".", ",")} tấn
                  </button>
                ))}
            </div>
          </div>
          {row(
            "Số lượng xe",
            <NumberStepper
              value={s.vehicleQty}
              onChange={(v) => set("vehicleQty", v)}
              min={1}
              max={5}
            />,
          )}
          {row(
            "Nhân viên bốc xếp",
            <NumberStepper
              value={s.workers}
              onChange={(v) => set("workers", v)}
              min={0}
              max={12}
              suffix="người"
            />,
          )}
        </div>
      </Block>

      <Block title="Vật tư & phụ phí" icon={PackageCheck}>
        <div className="divide-y divide-slate-100">
          {row(
            "Thùng carton",
            <NumberStepper
              value={s.cartons}
              onChange={(v) => set("cartons", v)}
              max={200}
              step={5}
            />,
          )}
          {row(
            "Băng keo",
            <NumberStepper
              value={s.tapeRolls}
              onChange={(v) => set("tapeRolls", v)}
              max={50}
              suffix="cuộn"
            />,
          )}
          {row(
            "Màng PE",
            <NumberStepper
              value={s.peFilmRolls}
              onChange={(v) => set("peFilmRolls", v)}
              max={20}
              suffix="cuộn"
            />,
          )}
          {row(
            "Số lầu (thang bộ)",
            <NumberStepper
              value={s.floors}
              onChange={(v) => set("floors", v)}
              max={20}
            />,
          )}
          {row(
            "Độ sâu hẻm",
            <NumberStepper
              value={s.alleyMeters}
              onChange={(v) => set("alleyMeters", v)}
              max={500}
              step={50}
              suffix="m"
            />,
          )}
        </div>
      </Block>

      <StickyActionBar className="md:!sticky md:bottom-4">
        <div className="flex items-center gap-3">
          <div className="min-w-0 flex-1">
            <p className="text-xs text-slate-500">
              Khảo sát trực tiếp · {items.length} hạng mục
            </p>
            <p className="text-xl font-bold tracking-tight tabular-nums">
              {formatVND(total)}
            </p>
          </div>
          <Button
            size="lg"
            icon={CheckCircle2}
            onClick={() =>
              dispatch({
                type: "SUBMIT_SURVEY",
                orderId: target.id,
                items,
                by: "STAFF",
              })
            }
          >
            Gửi báo giá
          </Button>
        </div>
      </StickyActionBar>
    </div>
  );
};
