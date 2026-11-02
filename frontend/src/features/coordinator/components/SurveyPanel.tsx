import { useMemo, useState } from 'react';
import { Camera, CheckCircle2, Footprints, Image as ImageIcon, Undo2 } from 'lucide-react';
import { Alert, Badge, Button, cx, Field, Input, NumberStepper, Select } from '@/components/ui';
import { SURVEY_METHOD_META } from '@/constants/order';
import { MIN_SURVEY_INPUT, VEHICLE_PRICING } from '@/constants/pricing';
import { formatTime, formatVND } from '@/services/format';
import { buildSurveyQuote, quoteSubtotal } from '@/services/pricing';
import { useApp } from '@/services/store/AppStore';
import type { Order, SurveyInput } from '@/types';

/**
 * Khảo sát qua ảnh do Điều phối thực hiện (mặc định). Khi ảnh không đủ căn cứ,
 * Điều phối cử nhân viên vận chuyển khảo sát trực tiếp — lúc đó chỉ nhân viên đó gửi báo giá.
 */
export const SurveyPanel = ({ order }: { order: Order }) => {
  const { state, dispatch } = useApp();
  const [note, setNote] = useState("");
  const [surveyorId, setSurveyorId] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const [s, setS] = useState<SurveyInput>(MIN_SURVEY_INPUT);
  const set = <K extends keyof SurveyInput>(k: K, v: SurveyInput[K]) =>
    setS((x) => ({ ...x, [k]: v }));
  const items = useMemo(() => buildSurveyQuote(s), [s]);
  const req = order.onSiteSurveyRequest;

  if (req) {
    return (
      <div className="mx-6 mb-6 rounded-xl border border-amber-200 bg-amber-50/60 p-4">
        <p className="flex items-center gap-2 text-sm font-semibold text-slate-900">
          <Footprints className="size-4 text-brand" />{" "}
          {SURVEY_METHOD_META.ON_SITE.label}
        </p>
        <p className="mt-2 text-sm text-slate-600">
          Đã yêu cầu lúc{" "}
          <span className="tabular-nums">{formatTime(req.requestedAt)}</span>
          {req.note && <> — {req.note}</>}. Đang chờ{" "}
          <b>
            {state.resources.staff.find((x) => x.id === req.surveyorId)?.name ??
              "nhân viên"}
          </b>{" "}
          khảo sát và gửi báo giá.
        </p>
        <Button
          className="mt-3"
          size="sm"
          variant="secondary"
          icon={Undo2}
          onClick={() =>
            dispatch({ type: "CANCEL_ONSITE_SURVEY", orderId: order.id })
          }
        >
          Hủy yêu cầu, tự khảo sát qua ảnh
        </Button>
      </div>
    );
  }

  const stepper = (
    label: string,
    k: Exclude<keyof SurveyInput, "vehicleCapacity">,
    opts: { min?: number; max: number; step?: number; suffix?: string },
  ) => (
    <Field label={label}>
      <NumberStepper value={s[k]} onChange={(v) => set(k, v)} {...opts} />
    </Field>
  );

  return (
    <div className="space-y-5 border-t border-slate-100 p-6">
      <div>
        <div className="mb-3 flex items-center justify-between">
          <p className="flex items-center gap-2 text-sm font-semibold text-slate-900">
            <Camera className="size-4 text-slate-500" />{" "}
            {SURVEY_METHOD_META.PHOTO.label}
          </p>
          <Badge tone={order.customerPhotos.length ? "neutral" : "warning"}>
            {order.customerPhotos.length} ảnh khách gửi
          </Badge>
        </div>
        {order.customerPhotos.length > 0 ? (
          <ul className="grid grid-cols-4 gap-2 sm:grid-cols-6">
            {order.customerPhotos.map((name) => (
              <li
                key={name}
                className="flex aspect-square flex-col items-center justify-center gap-1 rounded-xl bg-slate-100 p-1.5 text-slate-400"
              >
                <ImageIcon className="size-5" />
                <span className="w-full truncate text-center text-[10px] font-medium">
                  {name}
                </span>
              </li>
            ))}
          </ul>
        ) : (
          <Alert tone="warning" title="Khách chưa gửi ảnh đồ đạc">
            Gọi khách bổ sung ảnh, hoặc yêu cầu nhân viên vận chuyển khảo sát
            trực tiếp.
          </Alert>
        )}
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        {stepper("Quãng đường", "distanceKm", {
          min: 1,
          max: 200,
          suffix: "km",
        })}
        <Field label="Loại xe">
          <Select
            value={s.vehicleCapacity}
            onChange={(e) => set("vehicleCapacity", Number(e.target.value))}
            className="h-11"
          >
            {Object.keys(VEHICLE_PRICING)
              .map(Number)
              .sort((a, b) => a - b)
              .map((cap) => (
                <option key={cap} value={cap}>
                  {VEHICLE_PRICING[cap]?.label}
                </option>
              ))}
          </Select>
        </Field>
        {stepper("Số lượng xe", "vehicleQty", { min: 1, max: 5 })}
        {stepper("Nhân viên bốc xếp", "workers", { min: 0, max: 12, suffix: "người" })}
        {stepper("Thùng carton", "cartons", { max: 200, step: 5 })}
        {stepper("Băng keo", "tapeRolls", { max: 50, suffix: "cuộn" })}
        {stepper("Màng PE", "peFilmRolls", { max: 20, suffix: "cuộn" })}
        {stepper("Số lầu (thang bộ)", "floors", { max: 20 })}
        {stepper("Độ sâu hẻm", "alleyMeters", {
          max: 500,
          step: 50,
          suffix: "m",
        })}
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-slate-50 p-4">
        <div>
          <p className="text-xs text-slate-500">
            Báo giá tự động · {items.length} hạng mục
          </p>
          <p className="text-xl font-bold tabular-nums">
            {formatVND(quoteSubtotal(items))}
          </p>
        </div>
        <Button
          icon={CheckCircle2}
          onClick={() =>
            dispatch({
              type: "SUBMIT_SURVEY",
              orderId: order.id,
              items,
              by: "COORDINATOR",
            })
          }
        >
          Gửi báo giá cho khách
        </Button>
      </div>

      <div className="flex flex-col gap-3 border-t border-slate-100 pt-5 sm:flex-row sm:items-end">
        <Field
          label="Ảnh chưa đủ căn cứ? Lý do cần khảo sát trực tiếp"
          className="flex-1"
        >
          <Input
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="VD: Ảnh thiếu, nhiều đồ cồng kềnh cần tháo lắp"
          />
        </Field>
        <Field
          label="Nhân viên khảo sát *"
          error={
            submitted && !surveyorId ? "Bắt buộc chọn nhân viên khảo sát" : null
          }
          className="sm:w-60"
        >
          <Select
            value={surveyorId}
            onChange={(e) => setSurveyorId(e.target.value)}
            className={cx(submitted && !surveyorId && "border-rose-400")}
          >
            <option value="">— Chọn nhân viên —</option>
            {state.resources.staff
              .filter((s) => s.isAvailable)
              .map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name} ({s.code})
                </option>
              ))}
          </Select>
        </Field>
        <Button
          variant="secondary"
          icon={Footprints}
          onClick={() => {
            setSubmitted(true);
            if (surveyorId)
              dispatch({
                type: "REQUEST_ONSITE_SURVEY",
                orderId: order.id,
                note,
                surveyorId,
              });
          }}
        >
          Cử nhân viên khảo sát trực tiếp
        </Button>
      </div>
    </div>
  );
};
