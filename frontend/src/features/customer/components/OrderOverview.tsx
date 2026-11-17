import {
  CalendarClock,
  CheckCircle2,
  Headphones,
  Phone,
  Truck,
} from 'lucide-react';
import { Alert, Button, Card } from '@/components/ui';
import { CUSTOMER_STATUS_COPY } from '@/constants/order';
import { CompactProgress } from '@/features/customer/components/CompactProgress';
import { FieldAdjustmentBanner } from '@/features/customer/components/FieldAdjustmentBanner';
import { LiveProgressCard } from '@/features/customer/components/LiveProgressCard';
import { RequestSummaryCard } from '@/features/customer/components/RequestSummaryCard';
import { ScheduleCard } from '@/features/customer/components/ScheduleCard';
import { SurveyedItemsCard } from '@/features/customer/components/SurveyedItemsCard';
import { SurveyStageCard } from '@/features/customer/components/SurveyStageCard';
import { WaitTimerCard } from '@/features/customer/components/WaitTimerCard';
import { customerStepMeta, surveyHeroCopy } from '@/features/customer/surveyStage';
import { customerActionOf, scrollToSection } from '@/features/customer/utils';
import { AutoCancelCountdown } from '@/features/order/components/AutoCancelCountdown';
import { ProcessStepper } from '@/features/order/components/ProcessStepper';
import { ReferenceQuoteCard } from '@/features/order/components/ReferenceQuoteCard';
import { formatDateVN } from '@/services/format';
import { useApp } from '@/services/store/AppStore';
import type { Order } from '@/types';

export const OrderOverview = ({ order }: { order: Order }) => {
  const { state } = useApp();
  const inSurveyStage = order.status === "NEW_PENDING_SURVEY";
  const copy = inSurveyStage ? surveyHeroCopy(order) : CUSTOMER_STATUS_COPY[order.status];
  const vehicle = state.resources.vehicles.find(
    (v) => v.id === order.vehicleId,
  );
  const crew = state.resources.staff.filter((s) =>
    order.crewIds.includes(s.id),
  );
  const executing = [
    "ASSIGNED",
    "IN_TRANSIT",
    "PAUSED_WAITING_CUSTOMER",
    "PENDING_PAYMENT_OR_CLAIM",
  ].includes(order.status);

  // Hành động có khối đích trên trang mới thành CTA; phát sinh hiện trường đã có banner riêng ở đầu trang.
  const action = customerActionOf(order);
  const cta = action?.section ? { ...action, section: action.section } : null;

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
            <ProcessStepper status={order.status} meta={customerStepMeta(order)} />
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

      {inSurveyStage && <SurveyStageCard order={order} />}

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
        {/* Trọn gói: chưa khảo sát thì chưa có giá — chỉ gói chỉ thuê xe (giá theo xe & km khách chọn) mới hiện giá tham khảo. */}
        {order.bookingDetails?.serviceMode === "TRUCK_ONLY" &&
          order.referenceQuote &&
          order.quoteItems.length === 0 && (
            <ReferenceQuoteCard quote={order.referenceQuote} />
          )}
        <ScheduleCard order={order} />
        <SurveyedItemsCard order={order} />
        <RequestSummaryCard order={order} />
      </div>

      <p className="flex items-center justify-center gap-1.5 text-xs text-slate-500">
        <Headphones className="size-3.5" /> Cần hỗ trợ? Tổng đài 24/7{" "}
        <a href="tel:19006868" className="inline-flex items-center gap-1 font-semibold text-slate-900 hover:text-brand">
          <Phone className="size-3.5" /> 1900 6868
        </a>
      </p>
    </div>
  );
};
