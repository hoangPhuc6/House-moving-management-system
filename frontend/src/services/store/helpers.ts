import { STATUS_ORDER } from '@/constants/order';
import { uid } from '@/services/format';
import type {
  ActorRole,
  AppState,
  AuditLogEntry,
  Order,
  OrderStatus,
  QuoteItem,
  ToastTone,
  Vehicle,
} from '@/types';

export const ACTORS: Record<ActorRole, string> = {
  CUSTOMER: 'Nguyễn Thị Lan (Khách hàng)',
  STAFF: 'Trần Văn Hùng (Trưởng nhóm)',
  COORDINATOR: 'Lê Minh Châu (Điều phối)',
  ADMIN: 'Phạm Thu Trang (Admin)',
  SYSTEM: 'Hệ thống MoveFlow',
};

export const audit = (actorRole: ActorRole, action: string, detail: string): AuditLogEntry => ({
  id: uid('al'),
  timestamp: Date.now(),
  actor: ACTORS[actorRole],
  actorRole,
  action,
  detail,
});

export const toast = (state: AppState, tone: ToastTone, message: string): AppState => ({
  ...state,
  toasts: [...state.toasts.slice(-3), { id: uid('t'), tone, message }],
});

export const findOrder = (state: AppState, id: string): Order | undefined => state.orders.find((o) => o.id === id);

export const patchOrder = (state: AppState, id: string, fn: (o: Order) => Order): AppState => ({
  ...state,
  orders: state.orders.map((o) => (o.id === id ? fn(o) : o)),
});

// Hoạt động của nhân viên vận chuyển không ghi vào Audit Log.
export const withLog = (o: Order, entry: AuditLogEntry): Order => (entry.actorRole === 'STAFF' ? o : { ...o, auditLog: [...o.auditLog, entry] });

export const statusIndex = (s: OrderStatus): number => STATUS_ORDER.indexOf(s);

export const fieldAdjustmentItem = (): QuoteItem => ({
  id: uid('qi'),
  name: 'Phát sinh hiện trường: Hẻm sâu 200m + Tháo lắp tủ gỗ',
  category: 'FIELD_ADJUSTMENT',
  unit: 'gói',
  unitPrice: 350_000,
  quantity: 1,
});

export const shortId = (orderId: string): string => orderId.replace('ORD-2026-', 'ORD-');

export const nextOrderId = (orders: Order[]): string => {
  const max = orders.reduce((m, o) => Math.max(m, Number(o.id.split('-')[2] ?? 0)), 0);
  return `ORD-2026-${String(max + 1).padStart(3, '0')}`;
};

export const releaseVehicle = (vehicles: Vehicle[], vehicleIds: string[], status: Vehicle['status'] = 'AVAILABLE'): Vehicle[] =>
  vehicles.map((v) => (vehicleIds.includes(v.id) ? { ...v, status, currentShiftTime: null, busyOrderId: null } : v));
