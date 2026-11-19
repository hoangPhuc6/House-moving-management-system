import { Outlet, useNavigate } from 'react-router-dom';
import { CalendarDays, CircleHelp, ClipboardList, LogOut, Ruler, UserRound } from 'lucide-react';
import { isOpenJob, staffUserOf } from '@/features/staff/constants';
import { canAccessSurvey, pendingSurveysOf, useCurrentStaff, useCurrentStaffId } from '@/features/staff/session';
import { selectStaffJobs } from '@/features/staff/utils';
import { type MobileNavItem, MobileShell } from '@/layouts/MobileShell';
import type { UserMenuItem } from '@/layouts/shell';
import { ROUTES } from '@/routes/paths';
import { useApp } from '@/services/store/AppStore';

/** Khung của khu vực Nhân viên vận chuyển. */
export const StaffLayout = () => {
  const { state } = useApp();
  const navigate = useNavigate();
  const staffId = useCurrentStaffId();
  const me = useCurrentStaff();
  const jobs = selectStaffJobs(state.orders, staffId);
  const surveyCount = pendingSurveysOf(state.orders, staffId).length;
  const runningCount = jobs.filter(isOpenJob).length;

  const nav: MobileNavItem[] = [
    { to: ROUTES.staff.jobs, label: 'Lệnh việc', icon: ClipboardList, badge: runningCount || undefined },
    // Mục Khảo sát chỉ hiện với người được Điều phối phân khảo sát.
    ...(canAccessSurvey(state.orders, staffId) ? [{ to: ROUTES.staff.survey, label: 'Khảo sát', icon: Ruler, badge: surveyCount || undefined }] : []),
  ];
  const accountMenu: UserMenuItem[] = [
    { label: 'Hồ sơ cá nhân', icon: UserRound },
    { label: 'Lịch làm việc', icon: CalendarDays },
    { label: 'Trung tâm trợ giúp', icon: CircleHelp },
    { label: 'Đăng xuất', icon: LogOut, danger: true, onClick: () => navigate(ROUTES.login) },
  ];

  return (
    <MobileShell nav={nav} user={staffUserOf(me)} userMenuItems={accountMenu} maxWidth="max-w-3xl" notificationOrderIds={jobs.map((j) => j.id)}>
      <Outlet />
    </MobileShell>
  );
};
