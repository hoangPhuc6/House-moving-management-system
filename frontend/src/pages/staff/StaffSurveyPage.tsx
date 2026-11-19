import { Navigate } from 'react-router-dom';
import { SurveyTab } from '@/features/staff/components/SurveyTab';
import { canAccessSurvey, useCurrentStaffId } from '@/features/staff/session';
import { ROUTES } from '@/routes/paths';
import { useApp } from '@/services/store/AppStore';

/** /staff/survey — chỉ người được Điều phối phân khảo sát mới vào được. */
export const StaffSurveyPage = () => {
  const { state } = useApp();
  const staffId = useCurrentStaffId();
  if (!canAccessSurvey(state.orders, staffId)) return <Navigate to={ROUTES.staff.jobs} replace />;
  return <SurveyTab />;
};
