export type Range = '7' | '30' | '90' | 'all';

export type Group = 'day' | 'week' | 'month';

export interface RevenueRow {
  date: string;
  revenue: number;
  services: string[];
}

export interface Draft {
  id: string | null;
  plateNumber: string;
  capacityTons: number;
}

/** Dữ liệu chia sẻ giữa layout Admin và các trang con (qua <Outlet context>). */
export interface AdminOutletContext {
  query: string;
}
