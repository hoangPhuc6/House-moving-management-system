import type { Tone } from '@/components/ui';
import { uid } from '@/services/format';
import type {
  InventoryItem,
  MaterialLine,
  MaterialQuantities,
  MaterialSlip,
  MaterialSlipStatus,
  Order,
  QuoteItem,
} from '@/types';

/**
 * Bộ dụng cụ bốc xếp tính theo SỐ NGƯỜI BỐC VÁC của công ty (dòng LABOR trên báo giá), không theo số xe:
 * khách chỉ thuê xe và tự bốc thì không có người → không xuất dụng cụ. Tên khớp danh mục kho.
 */
export const LABOR_TOOL_KIT: { name: string; quantity: (workers: number) => number }[] = [
  { name: 'Xe đẩy hàng', quantity: (w) => Math.ceil(w / 2) },
  { name: 'Chăn bọc nội thất', quantity: (w) => w * 3 },
];

/**
 * Trang bị cố định theo xe — luôn nằm trên xe, kiểm khi nhận xe, KHÔNG xuất kho theo đơn.
 * (Đồ an toàn chằng hàng là trách nhiệm của xe, kể cả gói chỉ thuê xe.)
 */
export const VEHICLE_STANDARD_EQUIPMENT: { name: string; quantity: number; unit: string }[] = [
  { name: 'Dây chằng hàng', quantity: 2, unit: 'bộ' },
  { name: 'Bạt che mưa', quantity: 1, unit: 'tấm' },
];

/** Ghi chú giải trình (nhận thiếu, hao hụt) phải đủ dài để có nghĩa. */
export const MIN_NOTE_LENGTH = 5;

export const MATERIAL_STATUS_META: Record<MaterialSlipStatus, { label: string; tone: Tone }> = {
  NONE: { label: 'Không dùng vật tư', tone: 'neutral' },
  DRAFT: { label: 'Chờ kho xuất', tone: 'warning' },
  ISSUED: { label: 'Đã xuất · chờ đội nhận', tone: 'info' },
  RECEIVED: { label: 'Đội xe đang giữ', tone: 'brand' },
  USAGE_REPORTED: { label: 'Chờ kho kiểm nhận trả', tone: 'warning' },
  RETURNED: { label: 'Đã quyết toán', tone: 'success' },
};

export const emptyMaterialSlip = (): MaterialSlip => ({
  status: 'NONE',
  lines: [],
  issuedAt: null,
  receivedAt: null,
  usageReportedAt: null,
  returnedAt: null,
  receiveNote: '',
  resolutionNote: '',
});

/** Thùng carton khớp theo kích thước: dòng báo giá "Thùng nhỏ 40x30x30" ↔ mặt hàng kho "Thùng carton 40x30x30". */
const materialKey = (name: string) => {
  const n = name.trim().toLowerCase();
  const carton = /^thùng .*?(\d+x\d+x\d+)$/.exec(n);
  return carton ? `carton:${carton[1]}` : n;
};
const sameName = (a: string, b: string) => materialKey(a) === materialKey(b);

export const newMaterialLine = (item: InventoryItem, planned: number): MaterialLine => ({
  id: uid('ml'),
  inventoryItemId: item.id,
  name: item.name,
  unit: item.unit,
  kind: item.kind,
  planned,
  issued: 0,
  received: null,
  used: null,
  returned: null,
  lost: 0,
});

/** Số người bốc vác của công ty theo báo giá (0 = khách tự bốc, VD gói chỉ thuê xe không thuê thêm người). */
export const laborCountOf = (quoteItems: QuoteItem[]): number =>
  quoteItems.filter((q) => q.category === 'LABOR').reduce((s, q) => s + q.quantity, 0);

/**
 * Lập phiếu vật tư nháp khi gán đội:
 * - Vật tư tiêu hao: theo các dòng MATERIAL của báo giá (gói chỉ thuê xe không có → không xuất).
 * - Dụng cụ bốc xếp: theo số người bốc vác ({@link LABOR_TOOL_KIT}); không có người → không xuất.
 * - Trang bị theo xe (dây chằng…) không nằm trong phiếu ({@link VEHICLE_STANDARD_EQUIPMENT}).
 * Phiếu rỗng → đơn "không dùng vật tư", không cần xuất kho / quyết toán.
 * Dòng báo giá không có trong danh mục kho thì bỏ qua.
 */
export const buildMaterialPlan = (quoteItems: QuoteItem[], inventory: InventoryItem[]): MaterialLine[] => {
  const planned = new Map<string, number>();
  const add = (item: InventoryItem | undefined, qty: number) => {
    if (item && qty > 0) planned.set(item.id, (planned.get(item.id) ?? 0) + qty);
  };
  quoteItems
    .filter((q) => q.category === 'MATERIAL')
    .forEach((q) => add(inventory.find((i) => i.kind === 'CONSUMABLE' && sameName(i.name, q.name)), q.quantity));
  const workers = laborCountOf(quoteItems);
  LABOR_TOOL_KIT.forEach((t) => add(inventory.find((i) => i.kind === 'TOOL' && sameName(i.name, t.name)), t.quantity(workers)));
  return [...planned.entries()].map(([id, qty]) => newMaterialLine(inventory.find((i) => i.id === id)!, qty));
};

/** Gộp dòng thủ kho thêm tay vào phiếu nháp; bỏ qua dòng trùng vật tư đã có trên phiếu. */
export const withAddedLines = (slip: MaterialSlip, added: MaterialLine[]): MaterialSlip => {
  const seen = new Set(slip.lines.map((l) => l.inventoryItemId));
  const extra = added.filter((l) => !seen.has(l.inventoryItemId) && (seen.add(l.inventoryItemId), true));
  return extra.length ? { ...slip, lines: [...slip.lines, ...extra] } : slip;
};

/** Phiếu nháp của đơn đã gán đội — thủ kho cần duyệt & xuất (dùng cho badge menu và tab Kho). */
export const isAwaitingIssue = (order: Order): boolean => order.materials.status === 'DRAFT' && order.status === 'ASSIGNED';

/** Phiếu tính là đã quyết toán khi đã kiểm nhận trả, hoặc đơn không có dòng vật tư nào. */
export const isMaterialsSettled = (slip: MaterialSlip): boolean => slip.lines.length === 0 || slip.status === 'RETURNED';

/** Vật tư đang nằm ngoài kho (đã xuất, chưa kiểm nhận trả). */
export const hasMaterialsOutside = (slip: MaterialSlip): boolean =>
  slip.status === 'ISSUED' || slip.status === 'RECEIVED' || slip.status === 'USAGE_REPORTED';

/** Số đã dùng được tính khi quyết toán: dụng cụ luôn 0; vật tư tiêu hao theo khai báo của đội (chưa khai = 0). */
export const usedOf = (line: MaterialLine): number => (line.kind === 'TOOL' ? 0 : (line.used ?? 0));

/** Số phải trả về kho theo số đội đã nhận. */
export const expectedReturnOf = (line: MaterialLine): number => Math.max(0, (line.received ?? line.issued) - usedOf(line));

/** Tối đa có thể trả về kho cho một dòng (không vượt số đã xuất trừ đã dùng). */
export const maxReturnOf = (line: MaterialLine): number => Math.max(0, line.issued - usedOf(line));

/** Chênh lệch = đã xuất − đã dùng − đã trả. > 0 là thất thoát cần giải trình. */
export const discrepancyOf = (line: MaterialLine, returned: number): number => line.issued - usedOf(line) - returned;

const isQty = (n: number | undefined): n is number => typeof n === 'number' && Number.isInteger(n) && n >= 0;

/** Kho xuất: phiếu đang nháp, số nguyên ≥ 0 và có ít nhất một dòng xuất. Tồn kho kiểm riêng ở {@link issueStockError}. */
export const validateIssue = (slip: MaterialSlip, issued: MaterialQuantities): string | null => {
  if (slip.status !== 'DRAFT') return 'Phiếu không ở trạng thái chờ xuất kho.';
  if (slip.lines.some((l) => !isQty(issued[l.id]))) return 'Số lượng xuất phải là số nguyên không âm.';
  if (slip.lines.every((l) => issued[l.id] === 0)) return 'Phiếu xuất phải có ít nhất một dòng.';
  return null;
};

export const issueStockError = (slip: MaterialSlip, issued: MaterialQuantities, inventory: InventoryItem[]): string | null => {
  for (const l of slip.lines) {
    const item = inventory.find((i) => i.id === l.inventoryItemId);
    const qty = issued[l.id] ?? 0;
    if (!item) return `“${l.name}” không còn trong danh mục kho.`;
    if (qty > item.stock) return `Không đủ tồn: ${l.name} còn ${item.stock} ${item.unit}, cần xuất ${qty}.`;
  }
  return null;
};

/** Đội xe nhận: không vượt số đã xuất; nhận thiếu phải ghi chú. */
export const validateReceive = (slip: MaterialSlip, received: MaterialQuantities, note: string): string | null => {
  if (slip.status !== 'ISSUED') return 'Phiếu vật tư chưa được kho xuất.';
  for (const l of slip.lines) {
    const r = received[l.id];
    if (!isQty(r) || r > l.issued) return `Số nhận của “${l.name}” phải từ 0 đến ${l.issued}.`;
  }
  const short = slip.lines.some((l) => (received[l.id] ?? 0) < l.issued);
  if (short && note.trim().length < MIN_NOTE_LENGTH) return 'Nhận thiếu so với phiếu xuất — ghi rõ thiếu gì, vì sao.';
  return null;
};

/** Đội xe khai đã dùng: chỉ vật tư tiêu hao, không vượt số đã nhận. */
export const validateUsage = (slip: MaterialSlip, used: MaterialQuantities): string | null => {
  if (slip.status !== 'RECEIVED') return 'Phiếu vật tư không ở trạng thái đội xe đang giữ.';
  for (const l of slip.lines.filter((x) => x.kind === 'CONSUMABLE')) {
    const u = used[l.id];
    if (!isQty(u) || u > (l.received ?? 0)) return `Số đã dùng của “${l.name}” phải từ 0 đến ${l.received ?? 0}.`;
  }
  return null;
};

/** Kho được kiểm nhận trả khi đội đã khai vật tư, hoặc đơn bị hủy khi vật tư đã rời kho. */
export const canReturnMaterials = (order: Order): boolean =>
  order.materials.status === 'USAGE_REPORTED' ||
  (order.status === 'CANCELLED_EXPIRED' && (order.materials.status === 'ISSUED' || order.materials.status === 'RECEIVED'));

export const validateReturn = (order: Order, returned: MaterialQuantities, note: string): string | null => {
  if (!canReturnMaterials(order)) return 'Chưa thể kiểm nhận trả: đội xe chưa khai vật tư đã dùng.';
  for (const l of order.materials.lines) {
    const r = returned[l.id];
    const max = maxReturnOf(l);
    if (!isQty(r) || r > max) return `Số trả của “${l.name}” phải từ 0 đến ${max}.`;
  }
  const missing = order.materials.lines.some((l) => discrepancyOf(l, returned[l.id] ?? 0) > 0);
  if (missing && note.trim().length < MIN_NOTE_LENGTH) return 'Có chênh lệch — lập biên bản: lý do, người chịu trách nhiệm.';
  return null;
};

/** Tóm tắt chênh lệch đã ghi nhận của phiếu đã quyết toán. */
export const totalLost = (slip: MaterialSlip): number => slip.lines.reduce((s, l) => s + l.lost, 0);
