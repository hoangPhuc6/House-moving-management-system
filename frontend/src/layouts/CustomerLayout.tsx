import { matchPath, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { CircleHelp, CreditCard, History, Home, LogOut, MapPin, Plus, ShieldCheck } from 'lucide-react';
import { CUSTOMER } from '@/features/customer/constants';
import { isOpenOrder, needsAction, selectMyOrders } from '@/features/customer/utils';
import { type MobileNavItem, MobileShell } from '@/layouts/MobileShell';
import type { UserMenuItem } from '@/layouts/shell';
import { ROUTES } from '@/routes/paths';
import { useApp } from '@/services/store/AppStore';

/** Khung của khu vực Khách hàng: thanh điều hướng + <Outlet /> cho các trang con. */
export const CustomerLayout = () => {
  const { state } = useApp();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const myOrders = selectMyOrders(state.orders, state.activeOrderId);
  const actionCount = myOrders.filter(isOpenOrder).filter(needsAction).length;

  const nav: MobileNavItem[] = [
    { to: ROUTES.customer.orders, label: 'Đơn hàng', icon: Home, badge: actionCount || undefined },
    { to: ROUTES.customer.create, label: 'Đặt mới', icon: Plus, primary: true },
    { to: ROUTES.customer.history, label: 'Lịch sử', icon: History },
  ];
  const accountMenu: UserMenuItem[] = [
    { label: 'Địa chỉ đã lưu', icon: MapPin },
    { label: 'Phương thức thanh toán', icon: CreditCard },
    { label: 'Chính sách bảo hiểm', icon: ShieldCheck },
    { label: 'Trung tâm trợ giúp', icon: CircleHelp },
    { label: 'Đăng xuất', icon: LogOut, danger: true, onClick: () => navigate(ROUTES.login) },
  ];

  // Trang tạo đơn và chi tiết đơn (có thể kèm cột danh sách bên trái) cần vùng nội dung rộng hơn.
  const wide = pathname === ROUTES.customer.create || matchPath('/customer/orders/:orderId', pathname) !== null;
  /** Thông báo dẫn tới chi tiết đơn: đơn đang mở → tab Đơn hàng, đơn đã đóng → Lịch sử. */
  const orderHref = (orderId: string) => {
    const o = myOrders.find((x) => x.id === orderId);
    return o && !isOpenOrder(o) ? ROUTES.customer.historyOrder(orderId) : ROUTES.customer.order(orderId);
  };

  return (
    <MobileShell
      nav={nav}
      user={CUSTOMER}
      userMenuItems={accountMenu}
      notificationOrderIds={myOrders.map((o) => o.id)}
      notificationHref={orderHref}
      maxWidth={wide ? 'max-w-6xl' : 'max-w-5xl'}
    >
      <Outlet />
    </MobileShell>
  );
};
