import { type Dispatch } from 'react';

export type ServiceGroup = 'TRANSPORT' | 'LABOR' | 'PACKING' | 'ASSEMBLY' | 'SURCHARGE';

export interface ServiceItem {
  id: string;
  group: ServiceGroup;
  name: string;
  description: string;
  unit: string;
  basePrice: number;
  active: boolean;
}

export type UserRole = 'CUSTOMER' | 'STAFF' | 'COORDINATOR' | 'ADMIN';

export interface UserAccount {
  id: string;
  name: string;
  email: string;
  phone: string;
  role: UserRole;
  locked: boolean;
  emailVerified: boolean;
}

export interface InventoryItem {
  id: string;
  name: string;
  unit: string;
  stock: number;
  minStock: number;
}

export type InventoryTxnType = 'IN' | 'OUT' | 'RETURN' | 'ADJUST';

export interface InventoryTxn {
  id: string;
  itemId: string;
  type: InventoryTxnType;
  /** ADJUST: giá trị tồn mới. Các loại khác: số lượng dương. */
  quantity: number;
  /** Chênh lệch tồn kho thực tế đã áp dụng (có dấu). */
  delta: number;
  orderId: string | null;
  by: string;
  note: string;
  at: number;
}

export type PaymentKind = 'DEPOSIT' | 'FINAL';

export type PaymentMethod = 'TRANSFER';

export type PaymentStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'CANCELLED';

export interface PaymentRecord {
  id: string;
  orderId: string;
  kind: PaymentKind;
  method: PaymentMethod;
  amount: number;
  status: PaymentStatus;
  receivedBy: string;
  confirmedBy: string | null;
  confirmedAt: number | null;
  createdAt: number;
}

export interface FeedbackEntry {
  id: string;
  orderId: string;
  customerName: string;
  stars: number;
  comment: string;
  createdAt: number;
}

/** Dữ liệu lịch sử giả lập (đơn đã hoàn tất trước đây) để dashboard có chuỗi thời gian. */
export interface HistoricRevenue {
  id: string;
  date: string;
  revenue: number;
  services: string[];
}

export interface CatalogState {
  services: ServiceItem[];
  users: UserAccount[];
  inventory: InventoryItem[];
  txns: InventoryTxn[];
  payments: PaymentRecord[];
  feedbacks: FeedbackEntry[];
  history: HistoricRevenue[];
}

export type CatalogAction =
  | { type: 'UPSERT_SERVICE'; service: Omit<ServiceItem, 'id'> & { id?: string } }
  | { type: 'TOGGLE_SERVICE'; id: string }
  | { type: 'SET_USER_ROLE'; id: string; role: UserRole }
  | { type: 'TOGGLE_USER_LOCK'; id: string }
  | { type: 'ADD_USER'; user: Omit<UserAccount, 'id' | 'locked' | 'emailVerified'> }
  | { type: 'ADD_INVENTORY_ITEM'; item: Omit<InventoryItem, 'id'> }
  | { type: 'INVENTORY_TXN'; itemId: string; txnType: InventoryTxnType; quantity: number; orderId: string | null; note: string }
  | { type: 'RECORD_PAYMENT'; payment: Pick<PaymentRecord, 'orderId' | 'kind' | 'method' | 'amount' | 'receivedBy'> }
  | { type: 'SET_PAYMENT_STATUS'; id: string; status: PaymentStatus; by: string }
  | { type: 'SUBMIT_FEEDBACK'; orderId: string; customerName: string; stars: number; comment: string };

export interface CatalogContextValue {
  catalog: CatalogState;
  dispatchCatalog: Dispatch<CatalogAction>;
}
