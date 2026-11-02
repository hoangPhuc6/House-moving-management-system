import { Outlet, useNavigate } from 'react-router-dom';
import { CalendarDays, CircleHelp, ClipboardList, LogOut, Ruler, UserRound } from 'lucide-react';
import { RUNNING, STAFF_USER } from '@/features/staff/constants';
import { selectStaffJobs } from '@/features/staff/utils';
import { type MobileNavItem, MobileShell } from '@/layouts/MobileShell';
import type { UserMenuItem } from '@/layouts/shell';
import { ROUTES } from '@/routes/paths';
import { useApp } from '@/services/store/AppStore';

/** Khung của khu vực Nhân viên vận chuyển. */
export const StaffLayout = () => {
  const { state } = useApp();
  const navigate = useNavigate();
  const jobs = selectStaffJobs(state.orders);
  const surveyCount = state.orders.filter((o) => o.status === 'NEW_PENDING_SURVEY' && o.onSiteSurveyRequest).length;
  const runningCount = jobs.filter((j) => RUNNING.includes(j.status)).length;

  const nav: MobileNavItem[] = [
    { to: ROUTES.staff.jobs, label: 'Lệnh việc', icon: ClipboardList, badge: runningCount || undefined },
    { to: ROUTES.staff.survey, label: 'Khảo sát', icon: Ruler, badge: surveyCount || undefined },
  ];
  const accountMenu: UserMenuItem[] = [
    { label: 'Hồ sơ cá nhân', icon: UserRound },
    { label: 'Lịch làm việc', icon: CalendarDays },
    { label: 'Trung tâm trợ giúp', icon: CircleHelp },
    { label: 'Đăng xuất', icon: LogOut, danger: true, onClick: () => navigate(ROUTES.login) },
  ];

  return (
    <MobileShell nav={nav} user={STAFF_USER} userMenuItems={accountMenu} maxWidth="max-w-3xl" notificationOrderIds={jobs.map((j) => j.id)}>
      <Outlet />
    </MobileShell>
  );
};
