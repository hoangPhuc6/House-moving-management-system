import { useEffect } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { ArrowLeft, CalendarClock, MapPin, Phone } from 'lucide-react';
import { Badge, Card, cx, StatusBadge } from '@/components/ui';
import { DispatchPanel } from '@/features/coordinator/components/DispatchPanel';
import { IncidentPanel } from '@/features/coordinator/components/IncidentPanel';
import { OrderLogPanel } from '@/features/coordinator/components/OrderLogPanel';
import { OrderOverviewPanel } from '@/features/coordinator/components/OrderOverviewPanel';
import { QuotePanel } from '@/features/coordinator/components/QuotePanel';
import { defaultSectionOf, sectionsOf } from '@/features/coordinator/orderSections';
import type { OrderSection } from '@/features/coordinator/types';
import { coordinatorSurveyBadge, shortDate, surveyPhaseOf, weekdayOf } from '@/features/order/surveyStage';
import { routeSummaryOf } from '@/features/order/route';
import { settlementBadges } from '@/features/order/settlementStage';
import { FieldStageBadge } from '@/features/order/components/FieldProgress';
import { hasFieldProgress } from '@/features/order/fieldProgress';
import { ROUTES } from '@/routes/paths';
import { useApp } from '@/services/store/AppStore';
import type { Order } from '@/types';

/** Mục nào đang chờ chính Điều phối xử lý — hiện chấm báo trên thanh mục. */
const sectionNeedsMe = (o: Order, s: OrderSection): 'brand' | 'danger' | null => {
  if (s === 'incident' && o.flags.isVehicleIncident) return 'danger';
  if (s === 'quote' && ((o.status === 'NEW_PENDING_SURVEY' && surveyPhaseOf(o) !== 'SURVEYING') || (o.status === 'SURVEYED_PENDING_CONFIRM' && o.negotiation))) return 'brand';
  if (s === 'dispatch' && o.status === 'QUOTED_PENDING_SCHEDULE') return 'brand';
  return null;
};

/**
 * Trang chi tiết một đơn của Điều phối: phần đầu cố định (khách, lộ trình, lịch, trạng thái)
 * + các mục Tổng quan · Khảo sát & Báo giá · Điều phối xe · Sự cố · Nhật ký.
 */
export const CoordinatorOrderDetail = ({ order }: { order: Order }) => {
  const { state, dispatch } = useApp();
  const [params, setParams] = useSearchParams();
  const sections = sectionsOf(order);
  const requested = params.get('tab') as OrderSection | null;
  const fallback = defaultSectionOf(order);
  const section: OrderSection = sections.some((s) => s.id === requested)
    ? requested!
    : sections.some((s) => s.id === fallback)
      ? fallback
      : 'overview';

  // Đồng bộ đơn đang xem với thanh công cụ demo (mô phỏng ngoại lệ cho đúng đơn).
  useEffect(() => {
    if (state.activeOrderId !== order.id) dispatch({ type: 'SET_ACTIVE_ORDER', orderId: order.id });
  }, [order.id]); // eslint-disable-line react-hooks/exhaustive-deps

  const select = (s: OrderSection) => setParams({ tab: s }, { replace: true });
  const surveyBadge = order.status === 'NEW_PENDING_SURVEY' ? coordinatorSurveyBadge(order) : null;

  return (
    <div className="space-y-4">
      <Link to={ROUTES.coordinator.orders} className="inline-flex items-center gap-1.5 text-sm font-semibold text-slate-500 hover:text-slate-900">
        <ArrowLeft className="size-4" /> Đơn hàng
      </Link>

      <Card padded={false} className="overflow-hidden">
        <div className="flex flex-wrap items-start justify-between gap-4 p-5 sm:p-6">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-xl font-bold tracking-tight text-slate-900 tabular-nums sm:text-2xl">#{order.id}</h1>
              <StatusBadge status={order.status} />
              {settlementBadges(order)?.map((b) => (
                <Badge key={b.label} tone={b.tone} dot>
                  {b.label}
                </Badge>
              ))}
              {surveyBadge && <Badge tone={surveyBadge.tone}>{surveyBadge.label}</Badge>}
              {order.negotiation && <Badge tone="warning">Khách thương lượng</Badge>}
              {hasFieldProgress(order) && <FieldStageBadge order={order} />}
              {order.flags.isVehicleIncident && (
                <Badge tone="danger" dot pulse>
                  Sự cố xe
                </Badge>
              )}
            </div>
            <p className="mt-1 text-sm font-semibold text-slate-800">
              {order.customerName} <span className="font-normal text-slate-500 tabular-nums">· {order.phone}</span>
            </p>
            {/* Tuyến rút gọn + lịch chuyển trên một dòng — địa chỉ đầy đủ ở mục Tổng quan. */}
            <p className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-slate-600">
              <span className="inline-flex items-center gap-1.5" title={`${order.fromAddress} → ${order.toAddress}`}>
                <MapPin className="size-4 shrink-0 text-slate-400" /> {routeSummaryOf(order)}
              </span>
              <span className="inline-flex items-center gap-1.5 tabular-nums">
                <CalendarClock className="size-4 shrink-0 text-slate-400" />
                {weekdayOf(order.scheduledDate)} {shortDate(order.scheduledDate)} · {order.timeSlot}
              </span>
            </p>
          </div>
          <a
            href={`tel:${order.phone.replace(/[^\d+]/g, '')}`}
            className="inline-flex h-10 shrink-0 items-center gap-2 rounded-xl bg-slate-900 px-4 text-sm font-semibold text-white hover:bg-slate-800"
          >
            <Phone className="size-4" /> Gọi khách
          </a>
        </div>

        <nav className="scrollbar-none flex gap-1 overflow-x-auto border-t border-slate-100 px-3 sm:px-4" aria-label="Mục của đơn">
          {sections.map((s) => {
            const active = s.id === section;
            const dot = sectionNeedsMe(order, s.id);
            return (
              <button
                key={s.id}
                type="button"
                onClick={() => select(s.id)}
                aria-current={active ? 'page' : undefined}
                className={cx(
                  '-mb-px inline-flex h-12 shrink-0 items-center gap-2 border-b-2 px-3 text-sm font-semibold whitespace-nowrap transition-colors',
                  active ? 'border-brand text-slate-900' : 'border-transparent text-slate-500 hover:text-slate-900',
                )}
              >
                {s.label}
                {dot && <span className={cx('size-2 rounded-full', dot === 'danger' ? 'bg-rose-500' : 'bg-brand')} title="Cần bạn xử lý" />}
              </button>
            );
          })}
        </nav>
      </Card>

      <div key={`${order.id}-${section}`} className="anim-enter">
        {section === 'overview' && <OrderOverviewPanel order={order} />}
        {section === 'quote' && <QuotePanel order={order} />}
        {section === 'dispatch' && <DispatchPanel order={order} />}
        {section === 'incident' && <IncidentPanel order={order} />}
        {section === 'log' && <OrderLogPanel order={order} />}
      </div>
    </div>
  );
};
