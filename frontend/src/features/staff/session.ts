import { useSyncExternalStore } from 'react';
import { STAFF_ID } from '@/features/staff/constants';
import { useApp } from '@/services/store/AppStore';
import type { Order, StaffMember } from '@/types';

/**
 * Nhân viên đang đăng nhập ở khu vực Nhân viên (bản demo chưa có phiên đăng nhập thật).
 * Đổi qua thanh công cụ demo; nhớ theo trình duyệt nếu được.
 */
const KEY = 'demo.staffId';
const read = (): string => {
  try {
    return localStorage.getItem(KEY) || STAFF_ID;
  } catch {
    return STAFF_ID;
  }
};

let current = read();
const listeners = new Set<() => void>();

export const setCurrentStaffId = (id: string) => {
  current = id;
  try {
    localStorage.setItem(KEY, id);
  } catch {
    /* bỏ qua: chế độ riêng tư / chặn lưu trữ */
  }
  listeners.forEach((l) => l());
};

const subscribe = (l: () => void) => {
  listeners.add(l);
  return () => listeners.delete(l);
};

export const useCurrentStaffId = (): string => useSyncExternalStore(subscribe, () => current);

export const useCurrentStaff = (): StaffMember | undefined => {
  const id = useCurrentStaffId();
  const { state } = useApp();
  return state.resources.staff.find((s) => s.id === id);
};

/** Đơn đang chờ chính người này khảo sát (đã được phân, chưa gửi kết quả). */
export const pendingSurveysOf = (orders: Order[], staffId: string): Order[] =>
  orders.filter((o) => o.status === 'NEW_PENDING_SURVEY' && o.surveyAssignment?.surveyorId === staffId && !o.surveyReport);

/** Đơn người này đã gửi kết quả khảo sát. */
export const submittedSurveysOf = (orders: Order[], staffId: string): Order[] =>
  orders.filter((o) => o.surveyReport?.surveyorId === staffId);

/** Chỉ người được phân khảo sát (đang có việc hoặc đã từng gửi kết quả) mới thấy mục Khảo sát. */
export const canAccessSurvey = (orders: Order[], staffId: string): boolean =>
  pendingSurveysOf(orders, staffId).length > 0 || submittedSurveysOf(orders, staffId).length > 0;
