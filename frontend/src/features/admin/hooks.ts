import { useOutletContext } from 'react-router-dom';
import type { AdminOutletContext } from '@/features/admin/types';

/** Từ khóa tìm kiếm trên thanh header, chia sẻ cho các trang con của Admin. */
export const useAdminQuery = (): string => useOutletContext<AdminOutletContext>().query;
