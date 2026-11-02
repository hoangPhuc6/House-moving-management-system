import { useState } from 'react';
import { ArrowLeftRight, Siren, Truck } from 'lucide-react';
import {
  Alert,
  Badge,
  Button,
  Card,
  CardHeader,
  cx,
  Field,
  Input,
  Modal,
  PageHeader,
  Select,
  StatusBadge,
  Tooltip,
} from '@/components/ui';
import { FIELD_STAGE_LABEL } from '@/constants/order';
import { PLATE_REGEX } from '@/constants/resources';
import { OrderPicker } from '@/features/coordinator/components/OrderPicker';
import { useApp } from '@/services/store/AppStore';
import type { Order } from '@/types';

export const IncidentTab = ({
  order,
  onSelect,
}: {
  order: Order;
  onSelect: (id: string) => void;
}) => {
  const { state, dispatch } = useApp();
  const [open, setOpen] = useState(false);
  const [plate, setPlate] = useState("");
  const [driver, setDriver] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const vehicle = state.resources.vehicles.find(
    (v) => v.id === order.vehicleId,
  );
  const transferable = [
    "ASSIGNED",
    "IN_TRANSIT",
    "PAUSED_WAITING_CUSTOMER",
  ].includes(order.status);
  const drivers = state.resources.staff.filter(
    (s) => (s.role === "DRIVER" || s.role === "TEAM_LEADER") && s.isAvailable,
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
  const incidentOrders = state.orders.filter(
    (o) =>
      o.flags.isVehicleIncident ||
      ["IN_TRANSIT", "PAUSED_WAITING_CUSTOMER"].includes(o.status),
  );

  return (
    <>
      <PageHeader
        eyebrow="Khẩn cấp"
        title="Xử lý sự cố phương tiện"
        subtitle="Chuyển giao phương tiện (tăng bo) cho đơn đang vận chuyển."
        actions={<OrderPicker value={order.id} onChange={onSelect} />}
      />
      <div className="grid gap-4 xl:grid-cols-[1fr_340px]">
        <Card
          className={cx(order.flags.isVehicleIncident && "border-rose-300")}
        >
          <CardHeader
            title={`Đơn #${order.id}`}
            subtitle={`${order.customerName} · ${order.toAddress}`}
            icon={order.flags.isVehicleIncident ? Siren : Truck}
            action={<StatusBadge status={order.status} />}
          />
          {order.flags.isVehicleIncident ? (
            <Alert
              tone="danger"
              icon={Siren}
              title="Đội xe báo sự cố: xe chết máy giữa đường"
            >
              Xe <b className="tabular-nums">{vehicle?.plateNumber ?? "—"}</b>{" "}
              không thể tiếp tục. Đồ đạc vẫn trên thùng xe — cần điều xe thay
              thế và tài xế mới.
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

        <Card>
          <CardHeader title="Đơn cần theo dõi" icon={Siren} />
          <ul className="space-y-2">
            {incidentOrders.map((o) => (
              <li key={o.id}>
                <button
                  type="button"
                  onClick={() => onSelect(o.id)}
                  className={cx(
                    "flex w-full items-center justify-between rounded-xl border p-3 text-left",
                    o.id === order.id
                      ? "border-slate-900"
                      : "border-slate-200 hover:border-slate-300",
                  )}
                >
                  <div>
                    <p className="text-sm font-semibold tabular-nums">
                      #{o.id}
                    </p>
                    <p className="text-xs text-slate-500">{o.customerName}</p>
                  </div>
                  {o.flags.isVehicleIncident ? (
                    <Badge tone="danger" dot pulse>
                      Sự cố
                    </Badge>
                  ) : (
                    <StatusBadge status={o.status} />
                  )}
                </button>
              </li>
            ))}
            {incidentOrders.length === 0 && (
              <li className="text-sm text-slate-400">
                Không có đơn đang chạy.
              </li>
            )}
          </ul>
        </Card>
      </div>

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
                if (plateError || driverError) return;
                dispatch({
                  type: "TRANSFER_VEHICLE",
                  orderId: order.id,
                  plateNumber: plateNorm,
                  driverName: driver,
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
                (v) => v.status === "AVAILABLE" && v.id !== order.vehicleId,
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
                  {d.name} ({d.code})
                </option>
              ))}
            </Select>
          </Field>
        </div>
      </Modal>
    </>
  );
};
