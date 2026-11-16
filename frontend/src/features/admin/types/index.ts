import type { Period } from '@/features/admin/period';

export interface Draft {
  id: string | null;
  plateNumber: string;
  capacityTons: number;
}

/** Dữ liệu chia sẻ giữa layout Admin và các trang con (qua <Outlet context>). */
export interface AdminOutletContext {
  query: string;
  /** Kỳ báo cáo chọn chung cho các tab Admin (Dashboard, Quyết toán, Thanh toán, Hoàn tiền, Khiếu nại). */
  period: Period;
  setPeriod: (p: Period) => void;
}
