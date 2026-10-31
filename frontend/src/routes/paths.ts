import type { RoleType } from '@/types';

/** Toàn bộ đường dẫn của ứng dụng — dùng cho Link, NavLink và navigate(). */
export const ROUTES = {
  home: '/',
  login: '/login',
  customer: {
    root: '/customer',
    orders: '/customer/orders',
    create: '/customer/create',
    history: '/customer/history',
  },
  staff: {
    root: '/staff',
    jobs: '/staff/jobs',
    job: (orderId: string) => `/staff/jobs/${orderId}`,
    survey: '/staff/survey',
  },
  coordinator: {
    root: '/coordinator',
    queue: '/coordinator/queue',
    editor: '/coordinator/editor',
    dispatch: '/coordinator/dispatch',
    incident: '/coordinator/incident',
  },
  admin: {
    root: '/admin',
    dashboard: '/admin/dashboard',
    settlement: '/admin/settlement',
    payments: '/admin/payments',
    finance: '/admin/finance',
    claims: '/admin/claims',
    inventory: '/admin/inventory',
    vehicles: '/admin/vehicles',
    services: '/admin/services',
    users: '/admin/users',
  },
} as const;

/** Trang đầu tiên của từng vai trò. */
export const ROLE_HOME: Record<RoleType, string> = {
  LANDING: ROUTES.home,
  AUTH: ROUTES.login,
  CUSTOMER: ROUTES.customer.orders,
  STAFF: ROUTES.staff.jobs,
  COORDINATOR: ROUTES.coordinator.queue,
  ADMIN: ROUTES.admin.dashboard,
};

/** Suy ra vai trò đang xem từ đường dẫn hiện tại. */
export const roleFromPath = (pathname: string): RoleType => {
  const first = pathname.split('/')[1] ?? '';
  switch (first) {
    case 'login':
      return 'AUTH';
    case 'customer':
      return 'CUSTOMER';
    case 'staff':
      return 'STAFF';
    case 'coordinator':
      return 'COORDINATOR';
    case 'admin':
      return 'ADMIN';
    default:
      return 'LANDING';
  }
};
