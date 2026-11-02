import {
  CalendarClock,
  Camera,
  CheckCircle2,
  Headphones,
  type LucideIcon,
  Phone,
  QrCode,
  ReceiptText,
  Star,
  Truck,
} from 'lucide-react';
import { Alert, Badge, Button, Card, Divider, KeyValue } from '@/components/ui';
import {
  CUSTOMER_STATUS_COPY,
  HOUSE_TYPE_META,
  ONSITE_SURVEY_CUSTOMER_COPY,
} from '@/constants/order';
import { CompactProgress } from '@/features/customer/components/CompactProgress';
import { FieldAdjustmentBanner } from '@/features/customer/components/FieldAdjustmentBanner';
import { LiveProgressCard } from '@/features/customer/components/LiveProgressCard';
import { ScheduleCard } from '@/features/customer/components/ScheduleCard';
import { WaitTimerCard } from '@/features/customer/components/WaitTimerCard';
import { scrollToSection } from '@/features/customer/utils';
import { AutoCancelCountdown } from '@/features/order/components/AutoCancelCountdown';
import { OrderRequestDetails } from '@/features/order/components/OrderRequestDetails';
import { ProcessStepper } from '@/features/order/components/ProcessStepper';
import { ReferenceQuoteCard } from '@/features/order/components/ReferenceQuoteCard';
import { formatDateVN, formatVND } from '@/services/format';
import { computeTotals } from '@/services/pricing';
import { useApp } from '@/services/store/AppStore';
import type { Order } from '@/types';

export const OrderOverview = ({ order }: { order: Order }) => {
  const { state } = useApp();
  const copy =
    order.status === "NEW_PENDING_SURVEY" && order.onSiteSurveyRequest
      ? ONSITE_SURVEY_CUSTOMER_COPY
      : CUSTOMER_STATUS_COPY[order.status];
  const vehicle = state.resources.vehicles.find(
    (v) => v.id === order.vehicleId,
  );
  const crew = state.resources.staff.filter((s) =>
    order.crewIds.includes(s.id),
  );
  const totals = computeTotals(order);
  const executing = [
    "ASSIGNED",
    "IN_TRANSIT",
    "PAUSED_WAITING_CUSTOMER",
    "PENDING_PAYMENT_OR_CLAIM",
  ].includes(order.status);

  const cta: { label: string; section: string; icon: LucideIcon } | null =
    order.status === "SURVEYED_PENDING_CONFIRM"
      ? {
          label: "Xem báo giá & đặt cọc",
          section: "quote-section",
          icon: ReceiptText,
        }
      : order.status === "PENDING_PAYMENT_OR_CLAIM" && totals.remaining > 0
        ? {
            label: `Thanh toán ${formatVND(totals.remaining)}`,
            section: "payment-section",
            icon: QrCode,
          }
        : order.status === "COMPLETED"
          ? {
              label: "Đánh giá & hỗ trợ sau chuyển",
              section: "payment-section",
              icon: Star,
            }
          : null;

  return (
    <div className="space-y-4">
      {order.flags.hasFieldDiscrepancyLock && (
        <FieldAdjustmentBanner order={order} />
      )}
      {order.flags.isVehicleIncident && (
        <Alert
          tone="info"
          icon={Truck}
          title="Đội xe đang xử lý sự cố phương tiện"
        >
          Xe gặp sự cố kỹ thuật, trung tâm điều phối đang điều xe thay thế. Đồ
          đạc của bạn vẫn an toàn và được bảo hiểm.
        </Alert>
      )}

      {/* Status hero */}
      <Card className="overflow-hidden !p-0">
        <div className="relative bg-[#0F172A] p-5 text-white sm:p-6">
          <div className="pointer-events-none absolute -top-16 -right-10 size-48 rounded-full bg-brand/30 blur-3xl" />
          <div className="relative flex items-start justify-between gap-3">
            <div>
              <p className="text-xs text-white/50 tabular-nums">
                Đơn #{order.id}
              </p>
              <h1 className="mt-1 text-xl font-bold sm:text-2xl">
                {copy.title}
              </h1>
              <p className="mt-1 text-sm text-white/60">{copy.desc}</p>
            </div>
            <span className="grid size-11 shrink-0 place-items-center rounded-2xl bg-white/10">
              {order.status === "COMPLETED" ? (
                <CheckCircle2 className="size-5 text-emerald-400" />
              ) : (
                <Truck className="size-5 text-brand" />
              )}
            </span>
          </div>
          {order.status === "SURVEYED_PENDING_CONFIRM" &&
            order.autoCancelDeadline && (
              <div className="relative mt-4">
                <AutoCancelCountdown deadline={order.autoCancelDeadline} />
              </div>
            )}
        </div>
        <div className="space-y-4 p-5 sm:p-6">
          <CompactProgress order={order} />
          <div className="hidden md:block">
            <ProcessStepper status={order.status} />
          </div>
          <div className="grid gap-4 md:grid-cols-[1fr_auto]">
            <div className="relative space-y-3 pl-6">
              <span className="absolute top-2 bottom-2 left-[7px] w-px border-l border-dashed border-slate-300" />
              <div className="relative">
                <span className="absolute top-1 -left-6 size-3.5 rounded-full border-2 border-slate-400 bg-white" />
                <p className="text-xs text-slate-500">Điểm đi</p>
                <p className="text-sm font-medium text-slate-900">
                  {order.fromAddress}
                </p>
              </div>
              <div className="relative">
                <span className="absolute top-1 -left-6 size-3.5 rounded-full border-2 border-brand bg-brand" />
                <p className="text-xs text-slate-500">Điểm đến</p>
                <p className="text-sm font-medium text-slate-900">
                  {order.toAddress}
                </p>
              </div>
            </div>
            <div className="flex items-center gap-3 rounded-xl bg-slate-50 p-3 md:flex-col md:items-end md:justify-center md:bg-transparent md:p-0 md:text-right">
              <CalendarClock className="size-5 text-slate-400 md:hidden" />
              <div>
                <p className="text-xs text-slate-500">Lịch chuyển</p>
                <p className="text-sm font-semibold tabular-nums">
                  {formatDateVN(order.scheduledDate)} · {order.timeSlot}
                </p>
              </div>
            </div>
          </div>
          {cta && (
            <Button
              block
              size="lg"
              icon={cta.icon}
              onClick={() => scrollToSection(cta.section)}
            >
              {cta.label}
            </Button>
          )}
        </div>
      </Card>

      <div className="grid gap-4 lg:grid-cols-2 lg:items-start">
        {executing && (
          <LiveProgressCard
            order={order}
            vehicle={vehicle?.plateNumber}
            capacity={vehicle?.capacityTons}
            crew={crew}
          />
        )}
        {(order.status === "PAUSED_WAITING_CUSTOMER" ||
          order.flags.waitingMinutes > 0) && <WaitTimerCard order={order} />}
        {order.referenceQuote && order.quoteItems.length === 0 && (
          <ReferenceQuoteCard quote={order.referenceQuote} />
        )}
        <ScheduleCard order={order} />
        <Card className="lg:col-span-2">
          <h3 className="mb-3 text-[15px] font-semibold text-slate-900">
            Thông tin bạn đã đặt
          </h3>
          <div className="grid gap-x-8 sm:grid-cols-2">
            <KeyValue label="Họ tên" value={order.customerName} />
            <KeyValue label="Số điện thoại" value={order.phone} />
            <KeyValue label="Loại nhà" value={HOUSE_TYPE_META[order.houseType].label} />
            <KeyValue
              label="Lịch chuyển"
              value={`${formatDateVN(order.scheduledDate)} · ${order.timeSlot}`}
            />
            <KeyValue label="Địa chỉ đi" value={order.fromAddress} />
            <KeyValue label="Địa chỉ đến" value={order.toAddress} />
          </div>
          <Divider className="my-4" />
          <OrderRequestDetails order={order} />
          <Divider className="my-4" />
          <p className="mb-2 flex items-center gap-2 text-sm font-semibold text-slate-900">
            <Camera className="size-4 text-slate-500" /> Ảnh đồ đạc đã gửi (
            {order.customerPhotos.length})
          </p>
          {order.customerPhotos.length === 0 ? (
            <p className="text-sm text-slate-500">Bạn chưa gửi ảnh nào.</p>
          ) : (
            <div className="flex flex-wrap gap-1.5">
              {order.customerPhotos.map((n, i) => (
                <Badge key={`${n}-${i}`} tone="neutral">
                  {n}
                </Badge>
              ))}
            </div>
          )}
        </Card>
        <Card className="flex items-center gap-4">
          <span className="grid size-11 shrink-0 place-items-center rounded-2xl bg-orange-50 text-brand">
            <Headphones className="size-5" />
          </span>
          <div className="min-w-0 flex-1">
            <p className="text-sm font-semibold">Cần hỗ trợ?</p>
            <p className="text-xs text-slate-500">
              Tổng đài 24/7 · phản hồi trong 2 phút
            </p>
          </div>
          <a
            href="tel:19006868"
            className="inline-flex h-10 items-center gap-2 rounded-xl bg-slate-900 px-4 text-sm font-semibold text-white"
          >
            <Phone className="size-4" /> Gọi
          </a>
        </Card>
      </div>
    </div>
  );
};
