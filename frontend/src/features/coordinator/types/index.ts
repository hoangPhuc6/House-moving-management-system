import type { Order } from '@/types';

export type CoordTab = 'queue' | 'editor' | 'dispatch' | 'incident';

/** Trạng thái dùng chung giữa các trang con của Điều phối (qua <Outlet context>). */
export interface CoordinatorOutletContext {
  /** Đơn đang được chọn để báo giá / điều phối / xử lý sự cố. */
  selected: Order | null;
  setSelectedId: (id: string) => void;
  query: string;
  /** Mở một đơn ở tab chỉ định. */
  open: (id: string, tab: CoordTab) => void;
}
