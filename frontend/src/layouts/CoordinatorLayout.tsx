import { useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { AlertOctagon, CalendarRange, ClipboardList, FileWarning, ListTodo, Siren } from 'lucide-react';
import { Alert, Button } from '@/components/ui';
import { surchargesNeedingReview } from '@/constants/surcharge';
import { claimsAwaiting } from '@/features/admin/components/ClaimsModule';
import { defaultSectionOf } from '@/features/coordinator/orderSections';
import type { CoordinatorOutletContext, OrderSection } from '@/features/coordinator/types';
import { surveyPhaseOf } from '@/features/order/surveyStage';
import { AppShell, type NavItem } from '@/layouts/AppShell';
import { ROUTES } from '@/routes/paths';
import { useApp } from '@/services/store/AppStore';

/**
 * Khung của khu vực Điều phối viên. Mỗi mục menu là một việc:
 * Việc cần làm · Đơn hàng (danh sách + chi tiết từng đơn) · Lịch xe & nhân sự · Phát sinh hiện trường · Sự cố · Khiếu nại.
 */
export const CoordinatorLayout = () => {
  const { state } = useApp();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const [query, setQuery] = useState('');
  const incidents = state.orders.filter((o) => o.flags.isVehicleIncident).length;
  // Đơn đang chờ chính Điều phối: phân người khảo sát, lập báo giá, phản hồi thương lượng, xếp xe.
  const myWork = state.orders.filter(
    (o) =>
      (o.status === 'NEW_PENDING_SURVEY' && surveyPhaseOf(o) !== 'SURVEYING') ||
      (o.status === 'SURVEYED_PENDING_CONFIRM' && o.negotiation) ||
      o.status === 'QUOTED_PENDING_SCHEDULE',
  ).length;
  const surchargeReviews = state.orders.reduce((n, o) => n + surchargesNeedingReview(o).length, 0);

  const nav: NavItem[] = [
    { to: ROUTES.coordinator.queue, label: 'Việc cần làm', icon: ListTodo, badge: myWork || undefined },
    { to: ROUTES.coordinator.orders, label: 'Đơn hàng', icon: ClipboardList },
    { to: ROUTES.coordinator.schedule, label: 'Lịch xe & nhân sự', icon: CalendarRange },
    { to: ROUTES.coordinator.surcharge, label: 'Phát sinh hiện trường', icon: AlertOctagon, badge: surchargeReviews || undefined, badgeTone: 'danger' },
    { to: ROUTES.coordinator.incident, label: 'Sự cố', icon: Siren, badge: incidents || undefined, badgeTone: 'danger' },
    { to: ROUTES.coordinator.claims, label: 'Khiếu nại', icon: FileWarning, badge: claimsAwaiting(state.claims, 'COORDINATOR') || undefined, badgeTone: 'danger' },
  ];

  const open = (id: string, section?: OrderSection) => {
    const order = state.orders.find((o) => o.id === id);
    navigate(ROUTES.coordinator.order(id, section ?? (order ? defaultSectionOf(order) : undefined)));
  };
  const context: CoordinatorOutletContext = { query, open };

  return (
    <AppShell
      nav={nav}
      workspaceLabel="Trung tâm điều phối"
      user={{ name: 'Lê Minh Châu', title: 'Điều phối viên ca sáng', initials: 'MC' }}
      search={{
        value: query,
        onChange: (v) => {
          setQuery(v);
          // Tìm kiếm luôn hiển thị ở danh sách đơn (trừ khi đang ở Việc cần làm — trang này cũng lọc theo từ khóa).
          if (pathname !== ROUTES.coordinator.queue && pathname !== ROUTES.coordinator.orders) navigate(ROUTES.coordinator.orders);
        },
        placeholder: 'Tìm mã đơn, tên khách, số điện thoại…',
      }}
    >
      {incidents > 0 && pathname !== ROUTES.coordinator.incident && (
        <Alert
          tone="danger"
          icon={Siren}
          className="mb-4"
          title={`${incidents} đơn đang báo sự cố xe hỏng giữa đường`}
          action={
            <Button size="sm" variant="danger" onClick={() => navigate(ROUTES.coordinator.incident)}>
              Xử lý ngay
            </Button>
          }
        >
          Cần chuyển giao phương tiện (tăng bo) để đội xe tiếp tục hành trình.
        </Alert>
      )}
      {surchargeReviews > 0 && pathname !== ROUTES.coordinator.surcharge && (
        <Alert
          tone="warning"
          icon={AlertOctagon}
          className="mb-4"
          title={`${surchargeReviews} khoản phát sinh hiện trường cần duyệt`}
          action={
            <Button size="sm" onClick={() => navigate(ROUTES.coordinator.surcharge)}>
              Duyệt ngay
            </Button>
          }
        >
          Đội xe đang tạm dừng chờ Điều phối chốt giá và khách xác nhận.
        </Alert>
      )}
      <Outlet context={context} />
    </AppShell>
  );
};
