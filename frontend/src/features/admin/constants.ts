import { ArrowDownToLine, ArrowUpFromLine, Plus, Repeat2, SlidersHorizontal } from 'lucide-react';
import { VEHICLE_PRICING } from '@/constants/pricing';
import type {
  ClaimTicket,
  InventoryTxnType,
  PaymentKind,
  PaymentMethod,
  PaymentStatus,
  ServiceItem,
  UserRole,
} from '@/types';

export const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

// ===========================================================================
// Module 2 — Dịch vụ & bảng giá
// ===========================================================================

export const EMPTY_SERVICE: Omit<ServiceItem, 'id'> = { group: 'TRANSPORT', name: '', description: '', unit: '', basePrice: 0, active: true };

export const TXN_META: Record<InventoryTxnType, { label: string; icon: typeof Plus; tone: 'success' | 'danger' | 'info' | 'warning' }> = {
  IN: { label: 'Nhập kho', icon: ArrowDownToLine, tone: 'success' },
  OUT: { label: 'Xuất kho', icon: ArrowUpFromLine, tone: 'danger' },
  RETURN: { label: 'Hoàn kho', icon: Repeat2, tone: 'info' },
  ADJUST: { label: 'Điều chỉnh tồn', icon: SlidersHorizontal, tone: 'warning' },
};

export const ROLE_LABEL: Record<UserRole, string> = { CUSTOMER: 'Khách hàng', STAFF: 'Nhân viên', COORDINATOR: 'Điều phối viên', ADMIN: 'Admin' };

export const DAY = 86_400_000;

export const KIND_LABEL: Record<PaymentKind, string> = { DEPOSIT: 'Đặt cọc', FINAL: 'Thanh toán cuối' };

export const METHOD_LABEL: Record<PaymentMethod, string> = { TRANSFER: 'Chuyển khoản / VietQR' };

export const PAYMENT_STATUS_META: Record<PaymentStatus, { label: string; tone: 'warning' | 'success' | 'danger' | 'neutral' }> = {
  PENDING: { label: 'Chờ xác nhận', tone: 'warning' },
  SUCCESS: { label: 'Thành công', tone: 'success' },
  FAILED: { label: 'Thất bại', tone: 'danger' },
  CANCELLED: { label: 'Đã hủy', tone: 'neutral' },
};

export const ADMIN_NAME = 'Phạm Thu Trang';

export const CAPACITIES = Object.keys(VEHICLE_PRICING).map(Number);

export const CLAIM_STATUS: Record<
  ClaimTicket["status"],
  { label: string; tone: "warning" | "info" | "success" | "danger" }
> = {
  OPEN: { label: "Mới tiếp nhận", tone: "warning" },
  INVESTIGATING: { label: "Đang đối chiếu", tone: "info" },
  APPROVED: { label: "Đã duyệt bồi thường", tone: "success" },
  REJECTED: { label: "Từ chối", tone: "danger" },
};
