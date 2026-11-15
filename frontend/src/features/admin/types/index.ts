export interface Draft {
  id: string | null;
  plateNumber: string;
  capacityTons: number;
}

/** Dữ liệu chia sẻ giữa layout Admin và các trang con (qua <Outlet context>). */
export interface AdminOutletContext {
  query: string;
}
