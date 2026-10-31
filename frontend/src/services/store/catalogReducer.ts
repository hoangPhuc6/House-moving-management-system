import { uid } from '@/services/format';
import type {
  CatalogAction,
  CatalogState,
  InventoryItem,
  InventoryTxn,
  InventoryTxnType,
  PaymentRecord,
} from '@/types';

/** Số lượng tồn tối đa được xuất cho một yêu cầu; trả null nếu hợp lệ. */
export const inventoryError = (item: InventoryItem | undefined, type: InventoryTxnType, qty: number): string | null => {
  if (!item) return 'Không tìm thấy vật tư.';
  if (!Number.isFinite(qty) || qty < 0 || (type !== 'ADJUST' && qty <= 0)) return 'Số lượng phải lớn hơn 0.';
  if (type === 'OUT' && qty > item.stock) return `Không đủ tồn kho (còn ${item.stock} ${item.unit}).`;
  return null;
};

export const catalogReducer = (state: CatalogState, action: CatalogAction): CatalogState => {
  switch (action.type) {
    case 'UPSERT_SERVICE': {
      const { id, ...rest } = action.service;
      if (id && state.services.some((s) => s.id === id)) {
        return { ...state, services: state.services.map((s) => (s.id === id ? { ...s, ...rest } : s)) };
      }
      return { ...state, services: [...state.services, { ...rest, id: uid('sv') }] };
    }
    case 'TOGGLE_SERVICE':
      return { ...state, services: state.services.map((s) => (s.id === action.id ? { ...s, active: !s.active } : s)) };
    case 'SET_USER_ROLE':
      return { ...state, users: state.users.map((u) => (u.id === action.id ? { ...u, role: action.role } : u)) };
    case 'TOGGLE_USER_LOCK':
      return { ...state, users: state.users.map((u) => (u.id === action.id ? { ...u, locked: !u.locked } : u)) };
    case 'ADD_USER':
      if (state.users.some((u) => u.email.toLowerCase() === action.user.email.toLowerCase())) return state;
      return { ...state, users: [...state.users, { ...action.user, id: uid('u'), locked: false, emailVerified: false }] };
    case 'ADD_INVENTORY_ITEM':
      return { ...state, inventory: [...state.inventory, { ...action.item, id: uid('inv') }] };
    case 'INVENTORY_TXN': {
      const item = state.inventory.find((i) => i.id === action.itemId);
      if (inventoryError(item, action.txnType, action.quantity) || !item) return state;
      const delta =
        action.txnType === 'ADJUST' ? action.quantity - item.stock : action.txnType === 'OUT' ? -action.quantity : action.quantity;
      const txn: InventoryTxn = {
        id: uid('tx'), itemId: item.id, type: action.txnType, quantity: action.quantity, delta,
        orderId: action.orderId, by: 'Phạm Thu Trang', note: action.note, at: Date.now(),
      };
      return {
        ...state,
        inventory: state.inventory.map((i) => (i.id === item.id ? { ...i, stock: i.stock + delta } : i)),
        txns: [txn, ...state.txns],
      };
    }
    case 'RECORD_PAYMENT': {
      const p = action.payment;
      // Chống ghi trùng khi gửi lại cùng yêu cầu: cùng đơn + loại + số tiền đang chờ xác nhận.
      if (state.payments.some((x) => x.orderId === p.orderId && x.kind === p.kind && x.amount === p.amount && x.status === 'PENDING')) return state;
      const rec: PaymentRecord = { ...p, id: uid('pay'), status: 'PENDING', confirmedBy: null, confirmedAt: null, createdAt: Date.now() };
      return { ...state, payments: [rec, ...state.payments] };
    }
    case 'SET_PAYMENT_STATUS':
      return {
        ...state,
        payments: state.payments.map((p) =>
          p.id === action.id && p.status === 'PENDING'
            ? { ...p, status: action.status, confirmedBy: action.status === 'SUCCESS' ? action.by : null, confirmedAt: action.status === 'SUCCESS' ? Date.now() : null }
            : p,
        ),
      };
    case 'SUBMIT_FEEDBACK':
      if (state.feedbacks.some((f) => f.orderId === action.orderId)) return state;
      return { ...state, feedbacks: [{ id: uid('fb'), orderId: action.orderId, customerName: action.customerName, stars: action.stars, comment: action.comment, createdAt: Date.now() }, ...state.feedbacks] };
  }
};
