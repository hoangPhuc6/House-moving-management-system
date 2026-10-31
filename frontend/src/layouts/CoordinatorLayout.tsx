import { useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { CalendarRange, KanbanSquare, ReceiptText, Siren } from 'lucide-react';
import { Alert, Button } from '@/components/ui';
import type { CoordTab, CoordinatorOutletContext } from '@/features/coordinator/types';
import { AppShell, type NavItem } from '@/layouts/AppShell';
import { ROUTES } from '@/routes/paths';
import { useActiveOrder, useApp } from '@/services/store/AppStore';

/** Khung của khu vực Điều phối viên. Giữ đơn đang chọn + từ khóa tìm kiếm cho các trang con. */
export const CoordinatorLayout = () => {
  const { state } = useApp();
  const active = useActiveOrder();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const [selectedId, setSelectedId] = useState(active?.id ?? '');
  const [query, setQuery] = useState('');
  const selected = state.orders.find((o) => o.id === selectedId) ?? active ?? null;
  const incidents = state.orders.filter((o) => o.flags.isVehicleIncident).length;

  const nav: NavItem[] = [
    {
      to: ROUTES.coordinator.queue,
      label: 'Hàng đợi & Kanban',
      icon: KanbanSquare,
      badge: state.orders.filter((o) => o.status === 'SURVEYED_PENDING_CONFIRM').length || undefined,
    },
    {
      to: ROUTES.coordinator.editor,
      label: 'Báo giá & Audit Log',
      icon: ReceiptText,
      badge: state.orders.filter((o) => o.status === 'NEW_PENDING_SURVEY' && !o.onSiteSurveyRequest).length || undefined,
    },
    { to: ROUTES.coordinator.dispatch, label: 'Điều phối nguồn lực', icon: CalendarRange },
    { to: ROUTES.coordinator.incident, label: 'Sự cố khẩn cấp', icon: Siren, badge: incidents || undefined, badgeTone: 'danger' },
  ];

  const open = (id: string, tab: CoordTab) => {
    setSelectedId(id);
    navigate(ROUTES.coordinator[tab]);
  };
  const context: CoordinatorOutletContext = { selected, setSelectedId, query, open };

  return (
    <AppShell
      nav={nav}
      workspaceLabel="Trung tâm điều phối"
      user={{ name: 'Lê Minh Châu', title: 'Điều phối viên ca sáng', initials: 'MC' }}
      search={{
        value: query,
        onChange: (v) => {
          setQuery(v);
          navigate(ROUTES.coordinator.queue);
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
      <Outlet context={context} />
    </AppShell>
  );
};
