/** Các mục trong trang chi tiết đơn của Điều phối. */
export type OrderSection = 'overview' | 'quote' | 'dispatch' | 'incident' | 'log';

/** @deprecated Giữ tên cũ cho các chỗ gọi `open(id, tab)` — nay là mục của trang chi tiết đơn. */
export type CoordTab = OrderSection;

/** Trạng thái dùng chung giữa các trang con của Điều phối (qua <Outlet context>). */
export interface CoordinatorOutletContext {
  /** Từ khóa ở ô tìm kiếm trên thanh trên cùng. */
  query: string;
  /** Mở trang chi tiết một đơn (mặc định: mục phù hợp với trạng thái đơn). */
  open: (id: string, section?: OrderSection) => void;
}
