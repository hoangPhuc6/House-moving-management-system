import { useState } from 'react';
import { Outlet } from 'react-router-dom';
import { Banknote, BarChart3, Boxes, FileWarning, Tags, Truck, Users, Wallet, Warehouse } from 'lucide-react';
import type { AdminOutletContext } from '@/features/admin/types';
import { AppShell, type NavItem } from '@/layouts/AppShell';
import { ROUTES } from '@/routes/paths';
import { useApp } from '@/services/store/AppStore';

/** Khung của khu vực Admin: sidebar + ô tìm kiếm chung, các trang con hiển thị qua <Outlet />. */
export const AdminLayout = () => {
  const { state } = useApp();
  const [query, setQuery] = useState('');

  const nav: NavItem[] = [
    { to: ROUTES.admin.dashboard, label: 'Dashboard & báo cáo', icon: BarChart3 },
    {
      to: ROUTES.admin.settlement,
      label: 'Vật tư & Đóng hồ sơ',
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
      label: 'Quản lý khiếu nại',
      icon: FileWarning,
      badge: state.claims.filter((c) => c.status === 'OPEN' || c.status === 'INVESTIGATING').length || undefined,
      badgeTone: 'danger',
    },
    { to: ROUTES.admin.inventory, label: 'Kho & dụng cụ', icon: Boxes },
    { to: ROUTES.admin.vehicles, label: 'Quản lý xe', icon: Truck },
    { to: ROUTES.admin.services, label: 'Dịch vụ & bảng giá', icon: Tags },
    { to: ROUTES.admin.users, label: 'Tài khoản & phân quyền', icon: Users },
  ];
  const context: AdminOutletContext = { query };

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
