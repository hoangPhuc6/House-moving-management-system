import { useState } from 'react';
import { ArrowLeftRight, Siren, Truck } from 'lucide-react';
import {
  Alert,
  Button,
  Card,
  CardHeader,
  cx,
  Field,
  Input,
  Modal,
  Select,
  Tooltip,
} from '@/components/ui';
import { FIELD_STAGE_LABEL } from '@/constants/order';
import { PLATE_REGEX, VEHICLE_INCIDENT_KINDS } from '@/constants/resources';
import { formatTime } from '@/services/format';
import { addDaysISO, dayMonth, downtimeOn, todayLocalISO } from '@/services/downtime';
import { hasSkill, isActiveStaff } from '@/services/staff';
import { useApp } from '@/services/store/AppStore';
import type { Order } from '@/types';

/** Mục "Sự cố" của trang chi tiết đơn: chuyển giao phương tiện (tăng bo). */
export const IncidentPanel = ({ order }: { order: Order }) => {
  const { state, dispatch } = useApp();
  const [open, setOpen] = useState(false);
  const [plate, setPlate] = useState("");
  const [driver, setDriver] = useState("");
  const today = todayLocalISO();
  const [repairUntil, setRepairUntil] = useState(() => addDaysISO(todayLocalISO(), 2));
  const [submitted, setSubmitted] = useState(false);
  const vehicle = state.resources.vehicles.find(
    (v) => v.id === order.vehicleId,
  );
  const transferable = [
    "ASSIGNED",
    "IN_TRANSIT",
    "PAUSED_WAITING_CUSTOMER",
  ].includes(order.status);
  // Tài xế thay thế: có kỹ năng lái, đang làm, bằng còn hạn vào ngày chuyển (hạng bằng kiểm khi xác nhận theo xe thay thế).
  const drivers = state.resources.staff.filter(
    (s) =>
      hasSkill(s, "DRIVE") &&
      isActiveStaff(s) &&
      !!s.license &&
      s.license.expiresAt >= order.scheduledDate &&
      !downtimeOn(state.resources.downtimes, "STAFF", s.id, today),
  );
  const plateNorm = plate.trim().toUpperCase();
  const plateError = !plateNorm
    ? "Bắt buộc nhập biển số xe thay thế"
    : !PLATE_REGEX.test(plateNorm)
      ? "Sai định dạng — ví dụ: 51C-456.78"
      : plateNorm === vehicle?.plateNumber
        ? "Biển số trùng xe đang hỏng"
        : null;
  const driverError = !driver.trim() ? "Bắt buộc chọn tài xế mới" : null;
  const repairError = !repairUntil ? "Ghi ngày dự kiến sửa xong" : repairUntil < today ? "Không được trước hôm nay" : null;

  return (
    <>
        <Card
          className={cx(order.flags.isVehicleIncident && "border-rose-300")}
        >
          <CardHeader
            title="Phương tiện đang thực hiện"
            subtitle="Chuyển giao xe & tài xế khi xe gặp sự cố giữa đường"
            icon={order.flags.isVehicleIncident ? Siren : Truck}
          />
          {order.flags.isVehicleIncident ? (
            <Alert
              tone="danger"
              icon={Siren}
              title={`Đội xe báo sự cố: ${order.vehicleIncident ? VEHICLE_INCIDENT_KINDS[order.vehicleIncident.kind].toLowerCase() : "xe hỏng giữa đường"}`}
            >
              <p>
                Xe <b className="tabular-nums">{vehicle?.plateNumber ?? "—"}</b>
                {order.vehicleIncident && <> · báo lúc {formatTime(order.vehicleIncident.reportedAt)}</>}
              </p>
              {order.vehicleIncident && <p className="mt-1 whitespace-pre-line">{order.vehicleIncident.note}</p>}
            </Alert>
          ) : (
            <Alert tone="info">
              Đơn chưa báo sự cố. Bạn vẫn có thể chủ động tăng bo khi đơn đang
              thực hiện.
            </Alert>
          )}
          <div className="mt-5 grid gap-3 sm:grid-cols-3">
            <div className="rounded-xl border border-slate-200 p-4">
              <p className="text-xs text-slate-500">Xe hiện tại</p>
              <p className="mt-1 text-lg font-bold tabular-nums">
                {vehicle?.plateNumber ?? "—"}
              </p>
            </div>
            <div className="rounded-xl border border-slate-200 p-4">
              <p className="text-xs text-slate-500">Tiến độ</p>
              <p className="mt-1 text-lg font-bold">
                {FIELD_STAGE_LABEL[order.fieldStage]}
              </p>
            </div>
            <div className="rounded-xl border border-slate-200 p-4">
              <p className="text-xs text-slate-500">Nhân sự</p>
              <p className="mt-1 text-lg font-bold tabular-nums">
                {order.crewIds.length} người
              </p>
            </div>
          </div>
          <Tooltip
            enabled={!transferable}
            content="Chỉ chuyển giao khi đơn đã gán lệnh hoặc đang vận chuyển."
          >
            <Button
              className="mt-5"
              size="lg"
              variant={order.flags.isVehicleIncident ? "danger" : "dark"}
              icon={ArrowLeftRight}
              disabled={!transferable}
              onClick={() => {
                setOpen(true);
                setSubmitted(false);
              }}
            >
              Chuyển giao phương tiện (Tăng bo)
            </Button>
          </Tooltip>
        </Card>

      <Modal
        open={open}
        onClose={() => setOpen(false)}
        tone="danger"
        title="Chuyển giao phương tiện (Tăng bo)"
        footer={
          <>
            <Button variant="secondary" onClick={() => setOpen(false)}>
              Hủy
            </Button>
            <Button
              variant="danger"
              icon={ArrowLeftRight}
              onClick={() => {
                setSubmitted(true);
                if (plateError || driverError || repairError) return;
                dispatch({
                  type: "TRANSFER_VEHICLE",
                  orderId: order.id,
                  plateNumber: plateNorm,
                  driverName: driver,
                  repairUntil,
                });
                setOpen(false);
                setPlate("");
                setDriver("");
              }}
            >
              Xác nhận tăng bo
            </Button>
          </>
        }
      >
        <p className="mb-4">
          Đơn <b className="tabular-nums">#{order.id}</b> đang{" "}
          <b>{order.status}</b>. Cả hai trường dưới đây là bắt buộc trước khi
          cập nhật trạng thái đơn.
        </p>
        <div className="space-y-4">
          <Field
            label="Biển số xe thay thế *"
            error={submitted ? plateError : null}
            hint="Định dạng: 51C-456.78"
          >
            <Input
              invalid={submitted && !!plateError}
              value={plate}
              onChange={(e) => setPlate(e.target.value)}
              placeholder="51D-456.78"
              className="font-mono uppercase tabular-nums"
            />
          </Field>
          <div className="flex flex-wrap gap-1.5">
            {state.resources.vehicles
              .filter(
                (v) => v.status === "AVAILABLE" && v.id !== order.vehicleId && !downtimeOn(state.resources.downtimes, "VEHICLE", v.id, today),
              )
              .map((v) => (
                <button
                  key={v.id}
                  type="button"
                  onClick={() => setPlate(v.plateNumber)}
                  className="rounded-full border border-slate-200 px-2.5 py-1 font-mono text-xs font-semibold text-slate-600 hover:border-slate-900"
                >
                  {v.plateNumber}
                </button>
              ))}
          </div>
          <Field label="Tài xế mới *" error={submitted ? driverError : null}>
            <Select
              value={driver}
              onChange={(e) => setDriver(e.target.value)}
              className={cx(submitted && driverError && "border-rose-400")}
            >
              <option value="">— Chọn tài xế —</option>
              {drivers.map((d) => (
                <option key={d.id} value={d.name}>
                  {d.name} ({d.code}) · bằng {d.license?.licenseClass}
                </option>
              ))}
            </Select>
          </Field>
          {vehicle && (
            <Field
              label={`Xe ${vehicle.plateNumber} sửa xong dự kiến *`}
              error={submitted ? repairError : null}
              hint={`Xe hỏng nghỉ sửa từ hôm nay (${dayMonth(today)}) tới ngày này — không được xếp vào đơn khác.`}
            >
              <Input type="date" min={today} value={repairUntil} invalid={submitted && !!repairError} onChange={(e) => setRepairUntil(e.target.value)} className="tabular-nums" />
            </Field>
          )}
        </div>
      </Modal>
    </>
  );
};
