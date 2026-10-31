import { useState } from 'react';
import { CalendarClock, Lock, ShieldCheck, XCircle } from 'lucide-react';
import { Button, Card, cx, Field, Input, Modal, Select } from '@/components/ui';
import { RESCHEDULE_LOCK_HOURS } from '@/constants/pricing';
import { TIME_SLOTS } from '@/constants/resources';
import { formatVND } from '@/services/format';
import { useApp } from '@/services/store/AppStore';
import type { Order } from '@/types';

export const ScheduleCard = ({ order }: { order: Order }) => {
  const { dispatch } = useApp();
  const [rescheduleOpen, setRescheduleOpen] = useState(false);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [cancelReason, setCancelReason] = useState("");
  const reasonOk = cancelReason.trim().length >= 5;
  const [newDate, setNewDate] = useState("2026-11-03");
  const [newSlot, setNewSlot] = useState("07:00 - 09:00");
  const late = order.flags.hoursUntilDeparture < RESCHEDULE_LOCK_HOURS;
  const preDeparture =
    [
      "NEW_PENDING_SURVEY",
      "SURVEYED_PENDING_CONFIRM",
      "QUOTED_PENDING_SCHEDULE",
      "ASSIGNED",
    ].includes(order.status) && order.fieldStage === "NOT_STARTED";

  if (order.status === "CANCELLED_EXPIRED") {
    return (
      <Card>
        <h3 className="text-[15px] font-semibold">Đơn đã hủy</h3>
        <p className="mt-1 text-sm text-slate-500">
          {order.depositReservedUntil
            ? `Khoản cọc được bảo lưu tới ${order.depositReservedUntil} — tự động trừ vào lần đặt tiếp theo.`
            : "Đơn hàng không còn hiệu lực."}
        </p>
      </Card>
    );
  }
  if (!preDeparture) return null;

  return (
    <Card>
      <h3 className="text-[15px] font-semibold">Thay đổi lịch</h3>
      <div
        className={cx(
          "mt-3 flex items-center justify-between rounded-xl px-3.5 py-3",
          late ? "bg-rose-50" : "bg-emerald-50",
        )}
      >
        <span
          className={cx(
            "text-sm font-medium",
            late ? "text-rose-700" : "text-emerald-700",
          )}
        >
          Còn tới giờ khởi hành
        </span>
        <span
          className={cx(
            "text-lg font-bold tabular-nums",
            late ? "text-rose-700" : "text-emerald-700",
          )}
        >
          {order.flags.hoursUntilDeparture} giờ
        </span>
      </div>
      <div className="mt-3 grid grid-cols-2 gap-2">
        <Button
          variant="secondary"
          icon={late ? Lock : CalendarClock}
          disabled={late}
          onClick={() => setRescheduleOpen(true)}
        >
          Xin dời lịch
        </Button>
        <Button
          variant="secondary"
          icon={XCircle}
          className="text-rose-600 hover:border-rose-200 hover:bg-rose-50"
          onClick={() => setCancelOpen(true)}
        >
          Hủy đơn
        </Button>
      </div>
      {late && (
        <p className="mt-3 flex gap-2 rounded-lg bg-slate-50 p-3 text-xs leading-relaxed text-slate-600">
          <Lock className="mt-0.5 size-3.5 shrink-0" />
          Dưới 12h trước giờ khởi hành, vui lòng gọi Hotline để điều phối viên
          hỗ trợ (có áp dụng phí đổi lịch).
        </p>
      )}

      <Modal
        open={rescheduleOpen}
        onClose={() => setRescheduleOpen(false)}
        title="Dời lịch chuyển nhà"
        footer={
          <>
            <Button
              variant="secondary"
              onClick={() => setRescheduleOpen(false)}
            >
              Hủy bỏ
            </Button>
            <Button
              onClick={() => {
                dispatch({
                  type: "RESCHEDULE",
                  orderId: order.id,
                  newDate,
                  newSlot,
                });
                setRescheduleOpen(false);
              }}
            >
              Xác nhận dời lịch
            </Button>
          </>
        }
      >
        <p className="mb-4">
          Miễn phí đổi lịch khi còn trên 12 giờ trước giờ khởi hành.
        </p>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Ngày mới">
            <Input
              type="date"
              value={newDate}
              onChange={(e) => setNewDate(e.target.value)}
            />
          </Field>
          <Field label="Khung giờ">
            <Select
              value={newSlot}
              onChange={(e) => setNewSlot(e.target.value)}
            >
              {TIME_SLOTS.map((s) => (
                <option
                  key={s.id}
                  value={s.label}
                >
                  {s.label}
                </option>
              ))}
            </Select>
          </Field>
        </div>
      </Modal>

      {late ? (
        <Modal
          open={cancelOpen}
          onClose={() => setCancelOpen(false)}
          tone="danger"
          title="Hủy đơn sát giờ vận chuyển"
          footer={
            <>
              <Button
                variant="ghost"
                className="text-rose-600 hover:bg-rose-50"
                disabled={!reasonOk}
                onClick={() => {
                  dispatch({
                    type: "CANCEL_ORDER",
                    orderId: order.id,
                    mode: "FORFEIT",
                    reason: cancelReason.trim(),
                  });
                  setCancelReason("");
                  setCancelOpen(false);
                }}
              >
                Vẫn hủy (mất cọc)
              </Button>
              <Button variant="secondary" onClick={() => setCancelOpen(false)}>
                Giữ đơn
              </Button>
              <Button
                icon={ShieldCheck}
                disabled={!reasonOk}
                onClick={() => {
                  dispatch({
                    type: "CANCEL_ORDER",
                    orderId: order.id,
                    mode: "RESERVE_3_MONTHS",
                    reason: cancelReason.trim(),
                  });
                  setCancelReason("");
                  setCancelOpen(false);
                }}
              >
                Bảo lưu cọc 3 tháng
              </Button>
            </>
          }
        >
          <p className="text-[15px] text-slate-800">
            Bạn đang hủy đơn sát giờ vận chuyển (
            <b className="text-rose-600">&lt;12h</b>). Khoản cọc{" "}
            <b className="tabular-nums">
              {formatVND(order.depositPaid || order.depositRequired)}
            </b>{" "}
            sẽ <b>không được hoàn lại</b> theo chính sách.
          </p>
          <p className="mt-3">
            Bạn có muốn chuyển sang phương án{" "}
            <b className="text-brand">Bảo lưu cọc 3 tháng</b> không? Khoản cọc
            sẽ được trừ vào lần đặt lịch tiếp theo.
          </p>
          <Field
            label="Lý do hủy đơn (bắt buộc)"
            className="mt-4"
            error={
              cancelReason.length > 0 && !reasonOk
                ? "Vui lòng nhập ít nhất 5 ký tự"
                : null
            }
          >
            <textarea
              value={cancelReason}
              onChange={(e) => setCancelReason(e.target.value)}
              rows={3}
              placeholder="VD: Thay đổi kế hoạch, dời lịch chuyển nhà…"
              className="w-full rounded-xl border border-slate-200 p-3 text-base focus:border-brand focus:ring-4 focus:ring-brand/10 focus:outline-none sm:text-sm"
            />
          </Field>
        </Modal>
      ) : (
        <Modal
          open={cancelOpen}
          onClose={() => setCancelOpen(false)}
          tone="warning"
          title="Xác nhận hủy đơn hàng"
          footer={
            <>
              <Button variant="secondary" onClick={() => setCancelOpen(false)}>
                Giữ đơn
              </Button>
              <Button
                variant="danger"
                disabled={!reasonOk}
                onClick={() => {
                  dispatch({
                    type: "CANCEL_ORDER",
                    orderId: order.id,
                    mode: "REFUND_FULL",
                    reason: cancelReason.trim(),
                  });
                  setCancelReason("");
                  setCancelOpen(false);
                }}
              >
                {order.depositPaid > 0 ? "Hủy & hoàn cọc 100%" : "Xác nhận hủy"}
              </Button>
            </>
          }
        >
          Bạn đang hủy trước{" "}
          <b className="tabular-nums">{order.flags.hoursUntilDeparture} giờ</b>{" "}
          so với giờ khởi hành.{" "}
          {order.depositPaid > 0 ? (
            <>
              Khoản cọc <b>{formatVND(order.depositPaid)}</b> sẽ được hoàn 100%
              về tài khoản của bạn trong 1–3 ngày làm việc.
            </>
          ) : (
            "Bạn chưa đặt cọc nên không phát sinh chi phí."
          )}
          <Field
            label="Lý do hủy đơn (bắt buộc)"
            className="mt-4"
            error={
              cancelReason.length > 0 && !reasonOk
                ? "Vui lòng nhập ít nhất 5 ký tự"
                : null
            }
          >
            <textarea
              value={cancelReason}
              onChange={(e) => setCancelReason(e.target.value)}
              rows={3}
              placeholder="VD: Thay đổi kế hoạch, dời lịch chuyển nhà…"
              className="w-full rounded-xl border border-slate-200 p-3 text-base focus:border-brand focus:ring-4 focus:ring-brand/10 focus:outline-none sm:text-sm"
            />
          </Field>
        </Modal>
      )}
    </Card>
  );
};
