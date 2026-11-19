import { useState } from 'react';
import { Outlet } from 'react-router-dom';
import { Banknote, BarChart3, Boxes, FileSearch, FileWarning, Tags, Truck, Users, Wallet, Warehouse } from 'lucide-react';
import { initialAdminPeriod } from '@/features/admin/hooks';
import type { Period } from '@/features/admin/period';
import type { AdminOutletContext } from '@/features/admin/types';
import { AppShell, type NavItem } from '@/layouts/AppShell';
import { ROUTES } from '@/routes/paths';
import { isAwaitingIssue } from '@/services/materials';
import { useApp } from '@/services/store/AppStore';
import { claimsAwaiting } from '@/features/admin/components/ClaimsModule';

/** Khung của khu vực Admin: sidebar + ô tìm kiếm chung, các trang con hiển thị qua <Outlet />. */
export const AdminLayout = () => {
  const { state } = useApp();
  const [query, setQuery] = useState('');
  // Kỳ báo cáo dùng chung cho các tab (giữ nguyên khi chuyển tab) — mặc định tháng hiện tại.
  const [period, setPeriod] = useState<Period>(initialAdminPeriod);
  // Phiếu vật tư nháp của đơn đã gán đội: thủ kho cần duyệt & xuất trước giờ đội xe tới nhận.
  const slipsToIssue = state.orders.filter(isAwaitingIssue).length;

  const nav: NavItem[] = [
    { to: ROUTES.admin.dashboard, label: 'Dashboard & báo cáo', icon: BarChart3 },
    { to: ROUTES.admin.orders, label: 'Tra cứu đơn hàng', icon: FileSearch },
    {
      to: ROUTES.admin.settlement,
      label: 'Theo dõi quyết toán',
      icon: Warehouse,
      badge: state.orders.filter((o) => o.status === 'PENDING_PAYMENT_OR_CLAIM').length || undefined,
    },
    { to: ROUTES.admin.payments, label: 'Thanh toán & công nợ', icon: Wallet },
    {
      to: ROUTES.admin.finance,
      label: 'Hoàn tiền',
      icon: Banknote,
      badge: state.refunds.filter((r) => r.status === 'PENDING').length || undefined,
    },
    {
      to: ROUTES.admin.claims,
      label: 'Duyệt chi bồi thường',
      icon: FileWarning,
      badge: claimsAwaiting(state.claims, 'ADMIN') || undefined,
      badgeTone: 'danger',
    },
    {
      to: ROUTES.admin.inventory,
      label: 'Kho & dụng cụ',
      icon: Boxes,
      badge: slipsToIssue || undefined,
    },
    { to: ROUTES.admin.vehicles, label: 'Quản lý xe', icon: Truck },
    { to: ROUTES.admin.services, label: 'Dịch vụ & bảng giá', icon: Tags },
    { to: ROUTES.admin.users, label: 'Tài khoản & phân quyền', icon: Users },
  ];
  const context: AdminOutletContext = { query, period, setPeriod };

  return (
    <AppShell
      nav={nav}
      workspaceLabel="Quản trị Kho & Tài chính"
      user={{ name: 'Phạm Thu Trang', title: 'Admin Kho & Tài chính', initials: 'TT' }}
      search={{ value: query, onChange: setQuery, placeholder: 'Tìm mã đơn, mã phiếu, tên khách…' }}
    >
      <Outlet context={context} />
    </AppShell>
  );
};
