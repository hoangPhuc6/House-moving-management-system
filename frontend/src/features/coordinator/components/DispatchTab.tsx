import { useMemo, useState } from 'react';
import {
  AlertTriangle,
  CalendarRange,
  CheckCircle2,
  ClipboardCheck,
  Clock,
  Truck,
  Users,
  Wand2,
} from 'lucide-react';
import {
  Alert,
  Badge,
  Button,
  Card,
  CardHeader,
  cx,
  KeyValue,
  PageHeader,
  StatusBadge,
  Tooltip,
} from '@/components/ui';
import { VEHICLE_STATUS_META } from '@/constants/resources';
import { OrderPicker } from '@/features/coordinator/components/OrderPicker';
import { HOURS } from '@/features/coordinator/constants';
import { parseShift } from '@/features/coordinator/utils';
import { getAssignmentRequirement, validateAssignment } from '@/services/dispatch';
import { buildMaterialPlan } from '@/services/materials';
import {
  driveBlockReason,
  hasSkill,
  isActiveStaff,
  leaderOf,
  licenseExpiringSoon,
  pairDrivers,
  SKILL_LABEL,
  SKILL_ORDER,
  validateCrew,
  WORK_STATUS_META,
} from '@/services/staff';
import { useApp } from '@/services/store/AppStore';
import { useCatalog } from '@/services/store/CatalogStore';
import type { CrewAssignment, Order, StaffMember, Vehicle } from '@/types';

export const DispatchTab = ({
  order,
  onSelect,
}: {
  order: Order;
  onSelect: (id: string) => void;
}) => {
  const { state, dispatch } = useApp();
  const { catalog } = useCatalog();
  const req = useMemo(() => getAssignmentRequirement(order), [order]);
  const ownTag = order.id.replace("ORD-2026-", "ORD-");
  const [vehicleIds, setVehicleIds] = useState<string[]>(order.vehicleIds);
  /** Tài xế đã chọn — hệ thống tự ghép với xe (pairDrivers). */
  const [driverIds, setDriverIds] = useState<string[]>(() => order.crew.filter((c) => c.role === "DRIVER").map((c) => c.staffId));
  const [loaderIds, setLoaderIds] = useState<string[]>(() => order.crew.filter((c) => c.role === "LOADER").map((c) => c.staffId));
  const staff = state.resources.staff;
  const byId = (id: string) => staff.find((s) => s.id === id);
  const onDate = order.scheduledDate;
  const selectedVehicles = state.resources.vehicles.filter((v) =>
    vehicleIds.includes(v.id),
  );
  const isOwn = (v: Vehicle) => v.busyOrderId === ownTag;
  const conflict = selectedVehicles.find((v) => v.status === "BUSY" && !isOwn(v));
  const maintenance = selectedVehicles.some((v) => v.status === "MAINTENANCE");
  const assignable =
    order.status === "QUOTED_PENDING_SCHEDULE" || order.status === "ASSIGNED";
  const lateShift = state.resources.vehicles.find(
    (v) => v.id === "veh-2" && v.status === "BUSY",
  );

  // Đội theo chuyến: tài xế tự ghép với xe (xe lớn trước) + nhân viên bốc xếp. Tài xế đầu tiên là trưởng nhóm.
  const pairing = pairDrivers(selectedVehicles, driverIds, staff, onDate);
  const drivers = pairing.drivers;
  const crew: CrewAssignment[] = [...drivers, ...loaderIds.map((id) => ({ staffId: id, role: "LOADER" as const, vehicleId: null }))];
  const leaderId = leaderOf(crew);
  const plateOf = (vehicleId: string | null) => state.resources.vehicles.find((v) => v.id === vehicleId)?.plateNumber;
  const mismatch =
    validateAssignment(req, selectedVehicles.map((v) => v.capacityTons)) ??
    (driverIds.length !== selectedVehicles.length
      ? `Cần chọn đúng ${selectedVehicles.length} tài xế — mỗi xe một tài xế (đang chọn ${driverIds.length}).`
      : pairing.error) ??
    validateCrew(req, selectedVehicles, crew, staff, onDate);
  const driversOk = req.vehicleTotal === 0 || (driverIds.length === req.vehicleTotal && !pairing.error);
  const moversOk = loaderIds.length === req.workers;
  const assemblyOk = !req.needsAssembly || crew.some((c) => hasSkill(byId(c.staffId)!, "ASSEMBLY"));
  const vehicle = conflict;
  const smallestTons = selectedVehicles.length ? Math.min(...selectedVehicles.map((v) => v.capacityTons)) : 0;

  /** Lý do một người không thể bốc xếp trong chuyến này (null = chọn được). */
  const loaderBlockReason = (s: StaffMember): string | null => {
    if (!isActiveStaff(s)) return WORK_STATUS_META[s.status].label;
    if (driverIds.includes(s.id)) return "Đang là tài xế";
    if (!loaderIds.includes(s.id) && loaderIds.length >= req.workers) return "Đủ người";
    return null;
  };

  /** Lý do một người không thể chọn làm tài xế chuyến này (null = chọn được). */
  const driverBlockReason = (s: StaffMember): string | null => {
    if (!isActiveStaff(s)) return WORK_STATUS_META[s.status].label;
    if (loaderIds.includes(s.id)) return "Đang bốc xếp";
    if (!driverIds.includes(s.id) && driverIds.length >= req.vehicleTotal) return "Đủ người";
    // Không lái được cả xe nhỏ nhất đang chọn (bằng hết hạn / sai hạng).
    return driveBlockReason(s, smallestTons, onDate);
  };

  /** Lý do một xe không thể chọn (null = chọn được). */
  const vehicleBlockReason = (v: Vehicle): string | null => {
    if (vehicleIds.includes(v.id)) return null;
    if (v.status === "MAINTENANCE") return "Đang bảo dưỡng";
    if (v.status === "BUSY" && !isOwn(v)) return "Đang kẹt ca khác";
    const types = req.vehicles.filter((r) => r.capacity !== null);
    if (types.length > 0) {
      const t = types.find((r) => r.capacity === v.capacityTons);
      if (!t) return `Sai loại xe (cần ${types.map((r) => `${String(r.capacity).replace(".", ",")} tấn`).join(" / ")})`;
      const got = selectedVehicles.filter((x) => x.capacityTons === t.capacity).length;
      if (got >= t.qty) return "Đã đủ số xe loại này";
    } else if (req.vehicleTotal > 0 && vehicleIds.length >= req.vehicleTotal) {
      return "Đã đủ số xe";
    }
    return null;
  };

  const toggleVehicle = (id: string) =>
    setVehicleIds((c) =>
      c.includes(id) ? c.filter((x) => x !== id) : [...c, id],
    );
  const toggleLoader = (id: string) =>
    setLoaderIds((c) =>
      c.includes(id) ? c.filter((x) => x !== id) : [...c, id],
    );
  const toggleDriver = (id: string) =>
    setDriverIds((c) =>
      c.includes(id) ? c.filter((x) => x !== id) : [...c, id],
    );

  /**
   * Chọn sẵn theo báo giá: xe đúng loại; mỗi xe một tài xế lái được xe đó; đủ người bốc xếp
   * (ưu tiên người biết tháo lắp nếu đơn cần). Tài xế mặc định là trưởng nhóm.
   */
  const autoPick = () => {
    const usable = state.resources.vehicles.filter(
      (v) => v.status === "AVAILABLE" || isOwn(v),
    );
    const picked: Vehicle[] = [];
    req.vehicles.forEach((r) => {
      usable
        .filter((v) => !picked.includes(v) && (r.capacity === null || v.capacityTons === r.capacity))
        .slice(0, r.qty)
        .forEach((v) => picked.push(v));
    });
    const active = staff.filter(isActiveStaff);
    const used = new Set<string>();
    const nextDrivers: Record<string, string> = {};
    // Xe lớn chọn trước — ít người đủ hạng bằng hơn.
    [...picked].sort((a, b) => b.capacityTons - a.capacityTons).forEach((v) => {
      const d = active.find((s) => !used.has(s.id) && !driveBlockReason(s, v.capacityTons, onDate));
      if (d) {
        nextDrivers[v.id] = d.id;
        used.add(d.id);
      }
    });
    const loaderPool = active
      .filter((s) => !used.has(s.id) && hasSkill(s, "LOAD"))
      .sort((a, b) => Number(req.needsAssembly && hasSkill(b, "ASSEMBLY")) - Number(req.needsAssembly && hasSkill(a, "ASSEMBLY")));
    const nextLoaders = loaderPool.slice(0, req.workers).map((s) => s.id);
    setVehicleIds(picked.map((v) => v.id));
    setDriverIds(Object.values(nextDrivers));
    setLoaderIds(nextLoaders);
  };

  const blocked =
    !!conflict ||
    maintenance ||
    !assignable ||
    vehicleIds.length === 0 ||
    !!mismatch;

  const slotBlocks = useMemo(
    () =>
      state.resources.vehicles.map((v) => ({
        v,
        range: parseShift(v.currentShiftTime),
      })),
    [state.resources.vehicles],
  );

  return (
    <>
      <PageHeader
        eyebrow="Điều phối"
        title="Bảng điều phối & gán nguồn lực"
        subtitle="Cross-check trùng lịch xe và công suất khung giờ trước khi gán."
        actions={
          <OrderPicker
            value={order.id}
            onChange={onSelect}
            filter={(o) =>
              o.status === "QUOTED_PENDING_SCHEDULE" || o.status === "ASSIGNED"
            }
          />
        }
      />

      {/* Schedule board */}
      <Card className="mb-4" padded={false}>
        <div className="flex flex-wrap items-center justify-between gap-3 p-6 pb-4">
          <h3 className="flex items-center gap-2 text-[15px] font-semibold">
            <CalendarRange className="size-4" /> Lịch xe hôm nay
          </h3>
        </div>
        <div className="overflow-x-auto px-6 pb-6">
          <div className="min-w-[760px]">
            <div className="grid grid-cols-[140px_repeat(14,1fr)] border-b border-slate-100 pb-2 text-[11px] font-semibold text-slate-400 tabular-nums">
              <span>Xe</span>
              {HOURS.map((h) => (
                <span key={h}>{String(h).padStart(2, "0")}:00</span>
              ))}
            </div>
            {slotBlocks.map(({ v, range }) => (
              <div
                key={v.id}
                className={cx(
                  "grid grid-cols-[140px_repeat(14,1fr)] items-center border-b border-slate-50 py-2",
                  vehicleIds.includes(v.id) && "bg-orange-50/40",
                )}
              >
                <div>
                  <p className="text-sm font-semibold tabular-nums">
                    {v.plateNumber}
                  </p>
                  <p className="text-[11px] text-slate-400">
                    {String(v.capacityTons).replace(".", ",")} tấn
                  </p>
                </div>
                <div className="relative col-span-14 h-8 rounded-lg bg-slate-50">
                  {v.status === "MAINTENANCE" && (
                    <div className="absolute inset-0 rounded-lg bg-[repeating-linear-gradient(45deg,#fef3c7,#fef3c7_6px,#fffbeb_6px,#fffbeb_12px)] px-2 py-1.5 text-[11px] font-semibold text-amber-700">
                      Bảo dưỡng
                    </div>
                  )}
                  {range && (
                    <div
                      className={cx(
                        "absolute top-0.5 bottom-0.5 flex items-center gap-1 rounded-md px-2 text-[11px] font-semibold text-white",
                        v.busyOrderId?.includes("089")
                          ? "bg-brand"
                          : "bg-slate-800",
                      )}
                      style={{
                        left: `${((range[0] - 7) / 14) * 100}%`,
                        width: `${((range[1] - range[0]) / 14) * 100}%`,
                      }}
                    >
                      <span className="truncate">#{v.busyOrderId}</span>
                    </div>
                  )}
                  {v.id === "veh-2" && range && (
                    <div
                      className="absolute top-0.5 bottom-0.5 rounded-r-md border-2 border-dashed border-rose-400 bg-rose-100/60"
                      style={{
                        left: `${((range[1] - 7) / 14) * 100}%`,
                        width: `${(1 / 14) * 100}%`,
                      }}
                      title="Ca trước đang trễ ~1h"
                    />
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
      </Card>

      <div className="grid gap-4 xl:grid-cols-[1fr_360px]">
        <div className="space-y-4">
          {conflict && vehicle && (
            <Alert
              tone="danger"
              icon={AlertTriangle}
              title="CẢNH BÁO TRÙNG LỊCH"
              className="anim-pop"
            >
              Xe <b className="tabular-nums">{vehicle.plateNumber}</b> đang kẹt
              ca <b className="tabular-nums">#{vehicle.busyOrderId}</b> trong
              khung giờ{" "}
              <b className="tabular-nums">{vehicle.currentShiftTime}</b>. Hệ
              thống chặn thao tác gán!
            </Alert>
          )}
          {maintenance && (
            <Alert tone="warning" title="Xe đang bảo dưỡng">
              Không thể gán xe đang trong lịch bảo dưỡng định kỳ.
            </Alert>
          )}
          {lateShift && (
            <Alert
              tone="warning"
              icon={Clock}
              title={`Ca trước đang trễ: xe ${lateShift.plateNumber}`}
            >
              #{lateShift.busyOrderId} dự kiến kết thúc trễ ~60 phút so với
              khung {lateShift.currentShiftTime}.
            </Alert>
          )}

          <Card>
            <CardHeader
              title="Yêu cầu theo báo giá"
              icon={ClipboardCheck}
              subtitle="Phải chọn đúng loại xe và đủ số lượng đã báo cho khách"
              action={
                <Button size="sm" variant="secondary" icon={Wand2} onClick={autoPick}>
                  Gợi ý tự động
                </Button>
              }
            />
            <div className="grid gap-3 sm:grid-cols-2">
              {req.vehicles.map((r) => {
                const got = selectedVehicles.filter((v) => r.capacity === null || v.capacityTons === r.capacity).length;
                const ok = got === r.qty;
                return (
                  <div key={r.label} className={cx("flex items-center gap-3 rounded-xl border p-3", ok ? "border-emerald-200 bg-emerald-50/50" : "border-slate-200 bg-slate-50/60")}>
                    <span className={cx("grid size-10 shrink-0 place-items-center rounded-lg", ok ? "bg-emerald-600 text-white" : "bg-slate-900 text-white")}>
                      <Truck className="size-5" />
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-semibold">{r.label}</p>
                      <p className="text-xs text-slate-500">Cần <b className="tabular-nums">{r.qty}</b> xe{r.capacity !== null && ` · ${String(r.capacity).replace(".", ",")} tấn`}</p>
                    </div>
                    <Badge tone={ok ? "success" : "warning"} className="tabular-nums">{got}/{r.qty}</Badge>
                  </div>
                );
              })}
              {req.vehicles.length === 0 && <p className="rounded-xl bg-slate-50 p-3 text-sm text-slate-500 sm:col-span-2">Báo giá không có hạng mục xe — chọn xe phù hợp.</p>}
              {[
                { label: "Tài xế", need: req.vehicleTotal, got: driverIds.length, ok: driversOk, hint: "mỗi xe một tài xế · kiêm trưởng nhóm" },
                { label: "Nhân viên bốc xếp", need: req.workers, got: loaderIds.length, ok: moversOk, hint: "theo báo giá" },
                { label: "Biết tháo lắp", need: req.needsAssembly ? 1 : 0, got: assemblyOk ? 1 : 0, ok: assemblyOk, hint: "đơn có dịch vụ tháo lắp" },
              ]
                .filter((t) => t.need > 0)
                .map((t) => (
                <div key={t.label} className={cx("flex items-center gap-3 rounded-xl border p-3", t.ok ? "border-emerald-200 bg-emerald-50/50" : "border-slate-200 bg-slate-50/60")}>
                  <span className={cx("grid size-10 shrink-0 place-items-center rounded-lg", t.ok ? "bg-emerald-600 text-white" : "bg-slate-900 text-white")}>
                    <Users className="size-5" />
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-semibold">{t.label}</p>
                    <p className="text-xs text-slate-500">Cần <b className="tabular-nums">{t.need}</b> người · {t.hint}</p>
                  </div>
                  <Badge tone={t.ok ? "success" : "warning"} className="tabular-nums">{t.got}/{t.need}</Badge>
                </div>
              ))}
            </div>
          </Card>

          <Card>
            <CardHeader
              title="Chọn phương tiện"
              icon={Truck}
              subtitle={req.vehicleTotal > 0 ? `Yêu cầu: ${req.vehicles.map((r) => `${r.qty} × ${r.label}`).join(" + ")}` : "Chọn xe phù hợp"}
              action={
                <Badge tone={vehicleIds.length === req.vehicleTotal ? "success" : "warning"} className="tabular-nums">
                  {vehicleIds.length}/{req.vehicleTotal} đã chọn
                </Badge>
              }
            />
            <div className="grid gap-2 sm:grid-cols-2">
              {state.resources.vehicles.map((v) => {
                const sel = vehicleIds.includes(v.id);
                const meta = VEHICLE_STATUS_META[v.status];
                const reason = vehicleBlockReason(v);
                return (
                  <button
                    key={v.id}
                    type="button"
                    disabled={!!reason}
                    onClick={() => toggleVehicle(v.id)}
                    className={cx(
                      "flex items-center gap-3 rounded-xl border p-3 text-left transition-all",
                      reason && "cursor-not-allowed bg-slate-50 opacity-60",
                      sel ? "border-brand ring-4 ring-brand/10" : "border-slate-200 enabled:hover:border-slate-300",
                    )}
                  >
                    <span className={cx("grid size-10 shrink-0 place-items-center rounded-lg", sel ? "bg-brand text-white" : v.status === "AVAILABLE" ? "bg-slate-900 text-white" : "bg-slate-100 text-slate-400")}>
                      {sel ? <CheckCircle2 className="size-5" /> : <Truck className="size-5" />}
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="font-semibold tabular-nums">{v.plateNumber}</p>
                      <p className="text-xs text-slate-500 tabular-nums">
                        {String(v.capacityTons).replace(".", ",")} tấn {v.currentShiftTime && `· ${v.currentShiftTime}`}
                      </p>
                      {reason && !sel && <p className="mt-0.5 text-[11px] font-medium text-rose-600">{reason}</p>}
                    </div>
                    <Badge tone={meta.tone}>{meta.label}</Badge>
                  </button>
                );
              })}
            </div>
          </Card>

          {req.vehicleTotal > 0 && (
            <Card>
              <CardHeader
                title="Chọn tài xế"
                icon={Users}
                subtitle={`Yêu cầu: ${req.vehicleTotal} tài xế (mỗi xe một tài xế) — tài xế kiêm trưởng nhóm; hệ thống tự ghép xe theo hạng bằng`}
                action={
                  <Badge tone={driversOk ? "success" : "warning"} className="tabular-nums">
                    {driverIds.length}/{req.vehicleTotal} đã chọn
                  </Badge>
                }
              />
              <div className="grid gap-2 sm:grid-cols-2">
                {staff
                  .filter((s) => hasSkill(s, "DRIVE") && s.status !== "RESIGNED")
                  .map((s) => {
                    const sel = driverIds.includes(s.id);
                    const reason = sel ? null : driverBlockReason(s);
                    const paired = drivers.find((d) => d.staffId === s.id);
                    const soon = sel && licenseExpiringSoon(s, onDate);
                    return (
                      <label
                        key={s.id}
                        className={cx(
                          "flex items-center gap-3 rounded-xl border p-3",
                          reason
                            ? "cursor-not-allowed border-slate-100 bg-slate-50 opacity-60"
                            : sel
                              ? "cursor-pointer border-brand bg-orange-50/40"
                              : "cursor-pointer border-slate-200 hover:border-slate-300",
                        )}
                      >
                        <input type="checkbox" className="size-4 accent-[#FF5A1F]" checked={sel} disabled={!!reason} onChange={() => toggleDriver(s.id)} />
                        <div className="min-w-0 flex-1">
                          <p className="truncate text-sm font-semibold">{s.name}</p>
                          <p className="text-xs text-slate-500">
                            <span className="tabular-nums">{s.code}</span> · Bằng {s.license?.licenseClass ?? "—"}
                            {paired && <span className="tabular-nums"> · lái {plateOf(paired.vehicleId)}</span>}
                          </p>
                          {soon && <p className="text-[11px] font-medium text-amber-700">Bằng hết hạn {s.license!.expiresAt.split("-").reverse().join("/")}</p>}
                        </div>
                        {leaderId === s.id && <Badge tone="brand">Trưởng nhóm</Badge>}
                        {reason && <Badge tone="neutral">{reason}</Badge>}
                      </label>
                    );
                  })}
              </div>
              {pairing.error && driverIds.length === selectedVehicles.length && (
                <Alert tone="danger" className="mt-3">{pairing.error}</Alert>
              )}
            </Card>
          )}

          {req.workers > 0 && (
            <Card>
              <CardHeader
                title="Nhân viên bốc xếp"
                icon={Users}
                subtitle={`Yêu cầu: ${req.workers} người có kỹ năng bốc xếp${req.needsAssembly ? " — đơn có tháo lắp, nên chọn người biết tháo lắp" : ""}`}
                action={
                  <Badge tone={moversOk ? "success" : "warning"} className="tabular-nums">
                    {loaderIds.length}/{req.workers} đã chọn
                  </Badge>
                }
              />
              <div className="grid gap-2 sm:grid-cols-2">
                {staff
                  .filter((s) => hasSkill(s, "LOAD") && s.status !== "RESIGNED")
                  .map((s) => {
                    const sel = loaderIds.includes(s.id);
                    const reason = sel ? null : loaderBlockReason(s);
                    return (
                      <label
                        key={s.id}
                        className={cx(
                          "flex items-center gap-3 rounded-xl border p-3",
                          reason
                            ? "cursor-not-allowed border-slate-100 bg-slate-50 opacity-60"
                            : sel
                              ? "cursor-pointer border-brand bg-orange-50/40"
                              : "cursor-pointer border-slate-200 hover:border-slate-300",
                        )}
                      >
                        <input type="checkbox" className="size-4 accent-[#FF5A1F]" checked={sel} disabled={!!reason} onChange={() => toggleLoader(s.id)} />
                        <div className="min-w-0 flex-1">
                          <p className="truncate text-sm font-semibold">{s.name}</p>
                          <p className="text-xs text-slate-500">
                            <span className="tabular-nums">{s.code}</span> · {SKILL_ORDER.filter((k) => hasSkill(s, k)).map((k) => SKILL_LABEL[k]).join(", ")}
                          </p>
                        </div>
                        {reason && <Badge tone="neutral">{reason}</Badge>}
                      </label>
                    );
                  })}
              </div>
            </Card>
          )}

        </div>

        <Card className="h-fit xl:sticky xl:top-[76px]">
          <CardHeader title="Tóm tắt gán lệnh" />
          <KeyValue label="Đơn hàng" value={`#${order.id}`} />
          <KeyValue
            label="Trạng thái"
            value={<StatusBadge status={order.status} />}
          />
          <KeyValue label="Khung giờ" value={order.timeSlot} />
          <KeyValue
            label="Xe"
            value={
              selectedVehicles.length
                ? selectedVehicles.map((v) => v.plateNumber).join(", ")
                : "—"
            }
            tone={conflict ? "danger" : undefined}
          />
          <KeyValue
            label="Số xe"
            value={`${vehicleIds.length}/${req.vehicleTotal}`}
            tone={vehicleIds.length === req.vehicleTotal ? "success" : "danger"}
          />
          {req.vehicleTotal > 0 && (
            <KeyValue
              label="Tài xế"
              value={drivers.length ? drivers.map((d) => `${byId(d.staffId)?.name} (${plateOf(d.vehicleId)})`).join(", ") : `${driverIds.length}/${req.vehicleTotal}`}
              tone={driversOk ? "success" : "danger"}
            />
          )}
          {req.workers > 0 && (
            <KeyValue
              label="Bốc xếp"
              value={`${loaderIds.length}/${req.workers} người`}
              tone={moversOk ? "success" : "danger"}
            />
          )}
          {leaderId && <KeyValue label="Trưởng nhóm" value={`${byId(leaderId)?.name} (tài xế)`} />}
          {mismatch && assignable && vehicleIds.length > 0 && (
            <p className="mt-3 rounded-lg bg-rose-50 p-3 text-xs font-medium text-rose-700">{mismatch}</p>
          )}
          {!assignable && (
            <p className="mt-3 rounded-lg bg-amber-50 p-3 text-xs text-amber-800">
              Đơn này chưa đặt cọc hoặc đã bắt đầu thực hiện. Chọn một đơn ở
              trạng thái “Đã cọc - Chờ điều phối”.
            </p>
          )}
          <Tooltip
            enabled={blocked}
            content={
              conflict
                ? "Xe đang trùng lịch — hệ thống chặn thao tác gán."
                : mismatch
                    ? mismatch
                    : "Đơn chưa sẵn sàng để gán."
            }
          >
            <Button
              className="mt-5 w-full"
              size="lg"
              icon={blocked ? AlertTriangle : CheckCircle2}
              disabled={blocked}
              onClick={() =>
                dispatch({
                  type: "ASSIGN_RESOURCES",
                  orderId: order.id,
                  vehicleIds,
                  crew,
                  materialPlan: buildMaterialPlan(order.quoteItems, catalog.inventory),
                })
              }
            >
              {order.status === "ASSIGNED"
                ? "Cập nhật lệnh công tác"
                : "Gán & gửi lệnh công tác"}
            </Button>
          </Tooltip>
        </Card>
      </div>
    </>
  );
};
