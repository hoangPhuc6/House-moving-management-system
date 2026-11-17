import { ClipboardList, ExternalLink, Footprints, Route, Truck, Wallet } from 'lucide-react';
import { Card, CardHeader, KeyValue } from '@/components/ui';
import { SURVEY_METHOD_META } from '@/constants/order';
import { ReportSummary } from '@/features/coordinator/components/SurveyPanel';
import { needsFloor } from '@/features/customer/booking/constants';
import { OrderRequestDetails } from '@/features/order/components/OrderRequestDetails';
import { FieldProgressCard } from '@/features/order/components/FieldProgress';
import { hasFieldProgress } from '@/features/order/fieldProgress';
import { distanceKmOf, mapsRouteUrl } from '@/features/order/route';
import { appointmentLabel, coordinatorSurveyBadge, isMediaSubmitted, SURVEY_STAGE_META, surveyStageOf } from '@/features/order/surveyStage';
import { formatTime, formatVND } from '@/services/format';
import { computeTotals } from '@/services/pricing';
import { useApp } from '@/services/store/AppStore';
import type { Order } from '@/types';

/**
 * Mục "Tổng quan" của trang chi tiết đơn — chỉ những gì phần đầu trang chưa có
 * (khách, lộ trình, lịch chuyển, trạng thái đã ở phần đầu).
 */
export const OrderOverviewPanel = ({ order }: { order: Order }) => {
  const { state } = useApp();
  const t = computeTotals(order);
  const kind = surveyStageOf(order);
  const surveyor = state.resources.staff.find((s) => s.id === order.surveyAssignment?.surveyorId);
  const vehicles = state.resources.vehicles.filter((v) => order.vehicleIds.includes(v.id));
  const crew = state.resources.staff.filter((s) => order.crewIds.includes(s.id));
  const hasQuote = order.quoteItems.length > 0;

  return (
    <div className="space-y-4">
    {hasFieldProgress(order) && <FieldProgressCard order={order} />}
    <div className="grid gap-4 xl:grid-cols-[1fr_360px] xl:items-start">
      <div className="space-y-4">
        <Card>
          <CardHeader
            title="Lộ trình"
            icon={Route}
            subtitle={distanceKmOf(order) !== null ? `Khoảng ${distanceKmOf(order)} km` : undefined}
            action={
              <a href={mapsRouteUrl(order)} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 text-sm font-semibold text-brand hover:underline">
                Bản đồ <ExternalLink className="size-3.5" />
              </a>
            }
          />
          <ol className="relative space-y-3 pl-5">
            <span aria-hidden className="absolute top-1.5 bottom-1.5 left-[5px] w-px bg-slate-200" />
            {[
              { label: 'Điểm đi', address: order.fromAddress, detail: order.bookingDetails?.from, dot: 'bg-slate-900' },
              { label: 'Điểm đến', address: order.toAddress, detail: order.bookingDetails?.to, dot: 'bg-brand' },
            ].map((p) => (
              <li key={p.label} className="relative">
                <span className={`absolute top-1 -left-5 size-2.5 rounded-full ring-4 ring-white ${p.dot}`} />
                <p className="text-[11px] font-semibold tracking-wider text-slate-400 uppercase">{p.label}</p>
                <p className="text-sm font-semibold text-slate-900">{p.address}</p>
                {p.detail && (
                  <p className="text-xs text-slate-500">
                    {[p.detail.propertyType, needsFloor(p.detail.propertyType) ? p.detail.floor : '', p.detail.note].filter(Boolean).join(' • ')}
                  </p>
                )}
              </li>
            ))}
          </ol>
        </Card>

        <Card>
          <CardHeader title="Yêu cầu của khách" icon={ClipboardList} />
          <OrderRequestDetails order={order} />
        </Card>

        <Card>
          <CardHeader title="Khảo sát" icon={Footprints} />
          <KeyValue
            label="Hình thức"
            value={order.surveyMethod ? SURVEY_METHOD_META[order.surveyMethod].short : SURVEY_STAGE_META[kind].label}
          />
          {order.status === 'NEW_PENDING_SURVEY' && <KeyValue label="Tình trạng" value={coordinatorSurveyBadge(order).label} />}
          {appointmentLabel(order) && <KeyValue label="Lịch khách hẹn" value={appointmentLabel(order)} />}
          {kind !== 'TRUCK_CONFIRM' && (
            <KeyValue label="Người khảo sát" value={surveyor ? `${surveyor.name} (${surveyor.code})` : 'Chưa phân'} />
          )}
          {kind === 'PHOTOS' && (
            <KeyValue
              label="Ảnh / video khách gửi"
              value={`${order.customerPhotos.length} tệp${order.customerPhotos.length > 0 && !isMediaSubmitted(order) ? ' (chưa bấm gửi)' : ''}`}
            />
          )}
          {order.surveyReport && (
            <div className="mt-3">
              <ReportSummary order={order} />
            </div>
          )}
        </Card>
      </div>

      <div className="space-y-4">
        <Card>
          <CardHeader title="Nguồn lực" icon={Truck} />
          <KeyValue label="Xe" value={vehicles.length ? vehicles.map((v) => v.plateNumber).join(', ') : 'Chưa gán'} />
          <KeyValue label="Nhân sự" value={crew.length ? crew.map((s) => s.name).join(', ') : 'Chưa gán'} />
        </Card>

        <Card>
          <CardHeader title="Thanh toán" icon={Wallet} />
          {hasQuote ? (
            <>
              <KeyValue label="Báo giá gốc" value={formatVND(t.baseQuote)} />
              {t.fieldAdjustments !== 0 && <KeyValue label="Phát sinh hiện trường" value={formatVND(t.fieldAdjustments)} />}
              {t.discount > 0 && <KeyValue label={`Giảm giá (${order.discountPercent}%)`} value={`−${formatVND(t.discount)}`} tone="success" />}
              {t.waitFee > 0 && <KeyValue label="Phí chờ" value={formatVND(t.waitFee)} />}
              <KeyValue label="Tổng cộng" value={formatVND(t.grandTotal)} strong />
              <KeyValue label="Đặt cọc yêu cầu" value={formatVND(order.depositRequired)} />
              <KeyValue label="Đã thanh toán" value={formatVND(t.paid)} tone="success" />
              <KeyValue label="Còn lại" value={formatVND(t.remaining)} tone={t.remaining > 0 ? 'brand' : undefined} />
            </>
          ) : (
            <p className="text-sm text-slate-500">Chưa có báo giá.</p>
          )}
        </Card>

        <p className="px-1 text-xs text-slate-400">
          Tạo lúc <span className="tabular-nums">{formatTime(order.createdAt)}</span>
        </p>
      </div>
    </div>
    </div>
  );
};
