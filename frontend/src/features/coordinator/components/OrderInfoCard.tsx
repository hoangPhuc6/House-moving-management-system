import { ReceiptText } from 'lucide-react';
import { Card, CardHeader, KeyValue, StatusBadge } from '@/components/ui';
import { HOUSE_TYPE_META, SURVEY_METHOD_META } from '@/constants/order';
import { AutoCancelCountdown } from '@/features/order/components/AutoCancelCountdown';
import { OrderRequestDetails } from '@/features/order/components/OrderRequestDetails';
import { formatTime, formatVND } from '@/services/format';
import { computeTotals } from '@/services/pricing';
import { useApp } from '@/services/store/AppStore';
import type { Order } from '@/types';

/** Toàn bộ thông tin đơn hàng hiển thị cạnh báo giá để Điều phối đối chiếu. */
export const OrderInfoCard = ({ order }: { order: Order }) => {
  const { state } = useApp();
  const t = computeTotals(order);
  const vehicle = state.resources.vehicles.find(
    (v) => v.id === order.vehicleId,
  );
  const crew = state.resources.staff.filter((s) =>
    order.crewIds.includes(s.id),
  );
  return (
    <Card>
      <CardHeader
        title="Thông tin đơn hàng"
        subtitle={`#${order.id}`}
        icon={ReceiptText}
      />
      <KeyValue label="Khách hàng" value={order.customerName} />
      <KeyValue label="Số điện thoại" value={order.phone} />
      <KeyValue
        label="Địa chỉ lấy hàng"
        value={<span className="font-semibold">{order.fromAddress}</span>}
      />
      <KeyValue
        label="Địa chỉ giao hàng"
        value={<span className="font-semibold">{order.toAddress}</span>}
      />
      <KeyValue
        label="Loại nhà"
        value={HOUSE_TYPE_META[order.houseType].label}
      />
      <KeyValue label="Ngày thực hiện" value={order.scheduledDate} />
      <KeyValue label="Khung giờ" value={order.timeSlot} />
      <div className="my-3 rounded-xl bg-slate-50/70 p-3">
        <OrderRequestDetails order={order} />
      </div>
      <KeyValue
        label="Trạng thái"
        value={<StatusBadge status={order.status} />}
      />
      <KeyValue
        label="Khảo sát"
        value={
          order.surveyMethod
            ? SURVEY_METHOD_META[order.surveyMethod].short
            : order.onSiteSurveyRequest
              ? SURVEY_METHOD_META.ON_SITE.short
              : "Chưa khảo sát"
        }
      />
      <KeyValue
        label="Ảnh khách gửi"
        value={`${order.customerPhotos.length} ảnh`}
      />
      <KeyValue label="Xe" value={vehicle?.plateNumber ?? "Chưa gán"} />
      <KeyValue
        label="Nhân sự"
        value={crew.length ? crew.map((s) => s.name).join(", ") : "Chưa gán"}
      />
      <KeyValue label="Tạo lúc" value={formatTime(order.createdAt)} />
      <div className="my-2 border-t border-slate-100" />
      <KeyValue label="Báo giá gốc" value={formatVND(t.baseQuote)} />
      {t.fieldAdjustments !== 0 && (
        <KeyValue
          label="Phát sinh hiện trường"
          value={formatVND(t.fieldAdjustments)}
        />
      )}
      {t.discount > 0 && (
        <KeyValue
          label={`Giảm giá toàn đơn (${order.discountPercent}%)`}
          value={`−${formatVND(t.discount)}`}
          tone="success"
        />
      )}
      {t.waitFee > 0 && (
        <KeyValue label="Phí chờ" value={formatVND(t.waitFee)} />
      )}
      <KeyValue label="Tổng cộng" value={formatVND(t.grandTotal)} strong />
      <KeyValue
        label="Đặt cọc yêu cầu"
        value={formatVND(order.depositRequired)}
      />
      <KeyValue
        label="Đã thanh toán"
        value={formatVND(t.paid)}
        tone="success"
      />
      <KeyValue
        label="Còn lại"
        value={formatVND(t.remaining)}
        tone={t.remaining > 0 ? "brand" : undefined}
      />
      {order.autoCancelDeadline !== null &&
        order.status === "SURVEYED_PENDING_CONFIRM" && (
          <div className="mt-3">
            <AutoCancelCountdown deadline={order.autoCancelDeadline} />
          </div>
        )}
    </Card>
  );
};
