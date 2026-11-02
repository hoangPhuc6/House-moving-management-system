import { Navigate, Route, Routes } from 'react-router-dom';
import { AdminLayout } from '@/layouts/AdminLayout';
import { CoordinatorLayout } from '@/layouts/CoordinatorLayout';
import { CustomerLayout } from '@/layouts/CustomerLayout';
import { RootLayout } from '@/layouts/RootLayout';
import { StaffLayout } from '@/layouts/StaffLayout';
import { AdminClaimsPage } from '@/pages/admin/AdminClaimsPage';
import { AdminDashboardPage } from '@/pages/admin/AdminDashboardPage';
import { AdminFinancePage } from '@/pages/admin/AdminFinancePage';
import { AdminInventoryPage } from '@/pages/admin/AdminInventoryPage';
import { AdminPaymentsPage } from '@/pages/admin/AdminPaymentsPage';
import { AdminServicesPage } from '@/pages/admin/AdminServicesPage';
import { AdminSettlementPage } from '@/pages/admin/AdminSettlementPage';
import { AdminUsersPage } from '@/pages/admin/AdminUsersPage';
import { AdminVehiclesPage } from '@/pages/admin/AdminVehiclesPage';
import { AuthPage } from '@/pages/AuthPage';
import { CoordinatorDispatchPage } from '@/pages/coordinator/CoordinatorDispatchPage';
import { CoordinatorEditorPage } from '@/pages/coordinator/CoordinatorEditorPage';
import { CoordinatorIncidentPage } from '@/pages/coordinator/CoordinatorIncidentPage';
import { CoordinatorQueuePage } from '@/pages/coordinator/CoordinatorQueuePage';
import { CustomerCreatePage } from '@/pages/customer/CustomerCreatePage';
import { CustomerHistoryPage } from '@/pages/customer/CustomerHistoryPage';
import { CustomerOrdersPage } from '@/pages/customer/CustomerOrdersPage';
import { LandingPage } from '@/pages/LandingPage';
import { StaffJobDetailPage } from '@/pages/staff/StaffJobDetailPage';
import { StaffJobsPage } from '@/pages/staff/StaffJobsPage';
import { StaffSurveyPage } from '@/pages/staff/StaffSurveyPage';

/** Bảng định tuyến: mỗi khu vực là một layout route, các trang con hiển thị qua <Outlet />. */
export const AppRoutes = () => (
  <Routes>
    <Route element={<RootLayout />}>
      <Route index element={<LandingPage />} />
      <Route path="login" element={<AuthPage />} />

      <Route path="customer" element={<CustomerLayout />}>
        <Route index element={<Navigate to="orders" replace />} />
        <Route path="orders" element={<CustomerOrdersPage />} />
        <Route path="create" element={<CustomerCreatePage />} />
        <Route path="history" element={<CustomerHistoryPage />} />
      </Route>

      <Route path="staff" element={<StaffLayout />}>
        <Route index element={<Navigate to="jobs" replace />} />
        <Route path="jobs" element={<StaffJobsPage />} />
        <Route path="jobs/:orderId" element={<StaffJobDetailPage />} />
        <Route path="survey" element={<StaffSurveyPage />} />
      </Route>

      <Route path="coordinator" element={<CoordinatorLayout />}>
        <Route index element={<Navigate to="queue" replace />} />
        <Route path="queue" element={<CoordinatorQueuePage />} />
        <Route path="editor" element={<CoordinatorEditorPage />} />
        <Route path="dispatch" element={<CoordinatorDispatchPage />} />
        <Route path="incident" element={<CoordinatorIncidentPage />} />
      </Route>

      <Route path="admin" element={<AdminLayout />}>
        <Route index element={<Navigate to="dashboard" replace />} />
        <Route path="dashboard" element={<AdminDashboardPage />} />
        <Route path="settlement" element={<AdminSettlementPage />} />
        <Route path="payments" element={<AdminPaymentsPage />} />
        <Route path="finance" element={<AdminFinancePage />} />
        <Route path="claims" element={<AdminClaimsPage />} />
        <Route path="inventory" element={<AdminInventoryPage />} />
        <Route path="vehicles" element={<AdminVehiclesPage />} />
        <Route path="services" element={<AdminServicesPage />} />
        <Route path="users" element={<AdminUsersPage />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Route>
  </Routes>
);
