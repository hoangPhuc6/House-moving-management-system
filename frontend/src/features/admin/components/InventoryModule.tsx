import { useMemo, useState } from 'react';
import { ArrowUpFromLine, ChevronRight, ClipboardList, History, PackagePlus } from 'lucide-react';
import {
  Badge,
  Button,
  Card,
  cx,
  EmptyState,
  Field,
  Input,
  Modal,
  PageHeader,
  Select,
  Stat,
  Tabs,
} from '@/components/ui';
import { TXN_META } from '@/features/admin/constants';
import {
  IssueMaterialsModal,
  MaterialLinesTable,
  MaterialSlipActions,
  MaterialStatusBadge,
  ReturnMaterialsModal,
  slipSummary,
} from '@/features/admin/components/MaterialSlip';
import { matches } from '@/features/admin/utils';
import { formatDateVN, formatTime } from '@/services/format';
import { hasMaterialsOutside, isAwaitingIssue } from '@/services/materials';
import { useApp } from '@/services/store/AppStore';
import { useCatalog } from '@/services/store/CatalogStore';
import { inventoryError } from '@/services/store/catalogReducer';
import type { InventoryItem, InventoryTxnType, MaterialKind, MaterialSlipStatus } from '@/types';

const KIND_LABEL: Record<MaterialKind, string> = { CONSUMABLE: 'Tiêu hao', TOOL: 'Dụng cụ' };

/** Thứ tự hiển thị phiếu: việc kho cần làm lên trước. */
const SLIP_PRIORITY: Record<MaterialSlipStatus, number> = { DRAFT: 0, USAGE_REPORTED: 1, ISSUED: 2, RECEIVED: 3, RETURNED: 4, NONE: 5 };

export const InventoryModule = ({ query }: { query: string }) => {
  const { state, dispatch } = useApp();
  const { catalog, dispatchCatalog } = useCatalog();
  const [tab, setTab] = useState<'stock' | 'history' | 'slips'>('stock');
  const [txn, setTxn] = useState<{ item: InventoryItem; type: InventoryTxnType } | null>(null);
  const [qty, setQty] = useState(1);
  const [note, setNote] = useState('');
  const [adding, setAdding] = useState(false);
  const [draft, setDraft] = useState<Omit<InventoryItem, 'id'>>({ name: '', unit: '', kind: 'CONSUMABLE', stock: 0, minStock: 0 });
  const [issuing, setIssuing] = useState<string | null>(null);
  const [returning, setReturning] = useState<string | null>(null);
  // Phiếu mặc định thu gọn — chỉ xổ chi tiết khi chọn.
  const [expanded, setExpanded] = useState<Set<string>>(() => new Set());
  const toggle = (id: string) =>
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  const items = catalog.inventory.filter((i) => !query.trim() || i.name.toLowerCase().includes(query.trim().toLowerCase()));
  const low = catalog.inventory.filter((i) => i.stock <= i.minStock);
  const itemName = (id: string) => catalog.inventory.find((i) => i.id === id)?.name ?? id;
  // Xuất / hoàn kho theo đơn đi qua phiếu vật tư; ở đây chỉ còn giao dịch nội bộ, bắt buộc ghi lý do.
  const needsReason = txn?.type === 'OUT' || txn?.type === 'RETURN';
  const error = txn ? inventoryError(txn.item, txn.type, qty) : null;

  const slips = useMemo(
    () =>
      state.orders
        .filter((o) => o.materials.lines.length > 0 && matches(query, o.id, o.customerName))
        .sort((a, b) => SLIP_PRIORITY[a.materials.status] - SLIP_PRIORITY[b.materials.status] || a.scheduledDate.localeCompare(b.scheduledDate)),
    [state.orders, query],
  );
  // Đếm trên toàn bộ đơn (không theo ô tìm kiếm) để khớp badge ở menu bên trái.
  const toIssue = state.orders.filter(isAwaitingIssue).length;
  const outside = slips.filter((o) => hasMaterialsOutside(o.materials)).length;
  const issuingOrder = state.orders.find((o) => o.id === issuing);
  const returningOrder = state.orders.find((o) => o.id === returning);

  const open = (item: InventoryItem, type: InventoryTxnType) => {
    setTxn({ item, type });
    setQty(type === 'ADJUST' ? item.stock : 1);
    setNote('');
  };

  const submit = () => {
    if (!txn || error || (needsReason && !note.trim())) return;
    dispatchCatalog({ type: 'INVENTORY_TXN', itemId: txn.item.id, txnType: txn.type, quantity: qty, orderId: null, note: note.trim() });
    dispatch({ type: 'PUSH_TOAST', tone: 'success', message: `${TXN_META[txn.type].label}: ${txn.item.name}.` });
    setTxn(null);
  };

  return (
    <>
      <PageHeader eyebrow="Vận hành" title="Kho & dụng cụ" actions={<Button icon={PackagePlus} onClick={() => setAdding(true)}>Thêm vật tư</Button>} />
      <div className="mb-4 grid grid-cols-2 gap-4 xl:grid-cols-4">
        <Stat label="Danh mục vật tư" value={catalog.inventory.length} icon={PackagePlus} />
        <Stat label="Sắp hết hàng" value={low.length} icon={ArrowUpFromLine} tone={low.length ? 'warning' : 'success'} hint={low.map((i) => i.name).join(', ') || 'Tồn kho ổn định'} />
        <Stat label="Phiếu chờ xuất kho" value={toIssue} icon={ClipboardList} tone={toIssue ? 'brand' : 'neutral'} hint="Đơn đã gán đội, chưa xuất vật tư" />
        <Stat label="Phiếu còn ngoài kho" value={outside} icon={History} tone={outside ? 'warning' : 'success'} hint="Đã xuất, chưa kiểm nhận trả" />
      </div>
      <Tabs
        className="mb-4"
        value={tab}
        onChange={setTab}
        tabs={[
          { id: 'stock', label: 'Tồn kho' },
          {
            id: 'slips',
            label: 'Phiếu vật tư theo đơn',
            // Cùng kiểu badge với menu bên trái (AppShell) — số phiếu chờ xuất kho.
            badge: toIssue ? (
              <span className="min-w-5 rounded-full bg-brand px-1.5 py-0.5 text-center text-[11px] font-bold text-white tabular-nums">
                {toIssue}
              </span>
            ) : undefined,
          },
          { id: 'history', label: 'Lịch sử giao dịch' },
        ]}
      />

      {tab === 'stock' && (
        <Card padded={false}>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[820px] text-sm">
              <thead>
                <tr className="border-b border-slate-100 bg-slate-50/60 text-left text-xs font-semibold tracking-wide text-slate-500 uppercase">
                  <th className="px-6 py-3">Vật tư</th><th className="px-3 py-3">Loại</th><th className="px-3 py-3 text-right">Tồn</th><th className="px-3 py-3 text-right">Mức tối thiểu</th><th className="px-3 py-3">Tình trạng</th><th className="px-6 py-3 text-right">Giao dịch</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {items.length === 0 && <tr><td colSpan={6} className="p-6"><EmptyState icon={PackagePlus} title="Không có vật tư phù hợp" /></td></tr>}
                {items.map((i) => (
                  <tr key={i.id}>
                    <td className="px-6 py-3.5 font-semibold">{i.name}</td>
                    <td className="px-3 py-3.5"><Badge tone={i.kind === 'TOOL' ? 'info' : 'neutral'}>{KIND_LABEL[i.kind]}</Badge></td>
                    <td className="px-3 py-3.5 text-right font-semibold tabular-nums">{i.stock} <span className="text-xs font-normal text-slate-400">{i.unit}</span></td>
                    <td className="px-3 py-3.5 text-right tabular-nums text-slate-500">{i.minStock}</td>
                    <td className="px-3 py-3.5">{i.stock <= i.minStock ? <Badge tone="warning" dot>Sắp hết</Badge> : <Badge tone="success" dot>Đủ hàng</Badge>}</td>
                    <td className="px-6 py-3.5">
                      <div className="flex justify-end gap-1.5">
                        {(Object.keys(TXN_META) as InventoryTxnType[]).map((t) => (
                          <Button key={t} size="sm" variant="secondary" icon={TXN_META[t].icon} onClick={() => open(i, t)} title={TXN_META[t].label}><span className="hidden xl:inline">{TXN_META[t].label}</span></Button>
                        ))}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      {tab === 'history' && (
        <Card padded={false}>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] text-sm">
              <thead>
                <tr className="border-b border-slate-100 bg-slate-50/60 text-left text-xs font-semibold tracking-wide text-slate-500 uppercase">
                  <th className="px-6 py-3">Thời điểm</th><th className="px-3 py-3">Loại</th><th className="px-3 py-3">Vật tư</th><th className="px-3 py-3 text-right">Thay đổi</th><th className="px-3 py-3">Đơn</th><th className="px-6 py-3">Người thực hiện</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {catalog.txns.map((t) => (
                  <tr key={t.id}>
                    <td className="px-6 py-3.5 whitespace-nowrap text-slate-500 tabular-nums">{formatTime(t.at)}</td>
                    <td className="px-3 py-3.5"><Badge tone={TXN_META[t.type].tone}>{TXN_META[t.type].label}</Badge></td>
                    <td className="px-3 py-3.5"><p className="font-medium">{itemName(t.itemId)}</p>{t.note && <p className="text-xs text-slate-500">{t.note}</p>}</td>
                    <td className={cx('px-3 py-3.5 text-right font-semibold tabular-nums', t.delta < 0 ? 'text-rose-600' : 'text-emerald-600')}>{t.delta > 0 ? '+' : ''}{t.delta}</td>
                    <td className="px-3 py-3.5 tabular-nums">{t.orderId ? `#${t.orderId}` : '—'}</td>
                    <td className="px-6 py-3.5 text-slate-600">{t.by}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      {tab === 'slips' && (
        <div className="space-y-4">
          {slips.length === 0 && (
            <Card>
              <EmptyState icon={ClipboardList} title="Chưa có phiếu vật tư nào">
                Phiếu được lập tự động khi Điều phối gán đội xe cho đơn.
              </EmptyState>
            </Card>
          )}
          {slips.length > 0 && (
            <Card padded={false} className="overflow-hidden">
              <div className="flex items-center justify-between gap-3 border-b border-slate-100 bg-slate-50/60 px-4 py-2.5 text-xs text-slate-500 sm:px-6">
                <span>
                  <span className="font-semibold text-slate-700 tabular-nums">{slips.length}</span> phiếu · bấm vào phiếu để xem chi tiết từng dòng
                </span>
                <button
                  type="button"
                  className="font-semibold text-brand hover:underline"
                  onClick={() => setExpanded(expanded.size === slips.length ? new Set() : new Set(slips.map((o) => o.id)))}
                >
                  {expanded.size === slips.length ? 'Thu gọn tất cả' : 'Mở tất cả'}
                </button>
              </div>
              <ul className="divide-y divide-slate-100">
                {slips.map((o) => {
                  const open = expanded.has(o.id);
                  const sum = slipSummary(o.materials);
                  return (
                    <li key={o.id} className={cx(open && 'bg-slate-50/40')}>
                      <div className="flex flex-wrap items-center gap-x-4 gap-y-2 px-4 py-3 sm:px-6">
                        <button
                          type="button"
                          aria-expanded={open}
                          aria-controls={`slip-${o.id}`}
                          onClick={() => toggle(o.id)}
                          className="flex min-w-0 flex-1 items-center gap-3 text-left"
                        >
                          <ChevronRight className={cx('size-4 shrink-0 text-slate-400 transition-transform', open && 'rotate-90')} />
                          <span className="min-w-0">
                            <span className="flex flex-wrap items-center gap-2">
                              <span className="text-sm font-semibold tabular-nums">Đơn #{o.id}</span>
                              <MaterialStatusBadge slip={o.materials} />
                              {o.status === 'CANCELLED_EXPIRED' && <Badge tone="danger">Đơn đã hủy</Badge>}
                            </span>
                            <span className="mt-0.5 block truncate text-xs text-slate-500">
                              {o.customerName} · chuyển ngày {formatDateVN(o.scheduledDate)}
                            </span>
                          </span>
                        </button>
                        <span className="hidden text-xs text-slate-500 tabular-nums md:block">
                          {sum.lines} dòng · KH {sum.planned}
                          {sum.issued !== null && <> · xuất {sum.issued}</>}
                          {sum.gap > 0 && (
                            <span className={cx('ml-1 font-semibold', sum.settled ? 'text-rose-600' : 'text-amber-600')}>
                              · {sum.settled ? 'chênh lệch' : 'ngoài kho'} {sum.gap}
                            </span>
                          )}
                        </span>
                        <div className="shrink-0">
                          <MaterialSlipActions order={o} onIssue={() => setIssuing(o.id)} onReturn={() => setReturning(o.id)} />
                        </div>
                      </div>
                      {open && (
                        <div id={`slip-${o.id}`} className="border-t border-slate-100 px-4 pt-1 pb-4 sm:px-6">
                          <MaterialLinesTable slip={o.materials} />
                          {o.materials.resolutionNote && o.materials.lines.some((l) => l.lost > 0) && (
                            <p className="mt-3 rounded-lg bg-amber-50 p-3 text-xs text-amber-800">Biên bản chênh lệch: {o.materials.resolutionNote}</p>
                          )}
                        </div>
                      )}
                    </li>
                  );
                })}
              </ul>
            </Card>
          )}
        </div>
      )}

      {issuingOrder && <IssueMaterialsModal order={issuingOrder} onClose={() => setIssuing(null)} />}
      {returningOrder && <ReturnMaterialsModal order={returningOrder} onClose={() => setReturning(null)} />}

      <Modal
        open={!!txn}
        onClose={() => setTxn(null)}
        title={txn ? `${TXN_META[txn.type].label} — ${txn.item.name}` : ''}
        footer={<><Button variant="secondary" onClick={() => setTxn(null)}>Hủy</Button><Button disabled={!!error || (needsReason && !note.trim())} onClick={submit}>Xác nhận</Button></>}
      >
        {txn && (
          <div className="space-y-4">
            <p>Tồn hiện tại: <b className="tabular-nums">{txn.item.stock} {txn.item.unit}</b></p>
            {needsReason && (
              <p className="rounded-lg bg-slate-50 p-3 text-xs text-slate-600">
                Giao dịch nội bộ, không gắn với đơn. Xuất / thu hồi vật tư cho đơn hàng thực hiện ở tab “Phiếu vật tư theo đơn”.
              </p>
            )}
            <Field label={txn.type === 'ADJUST' ? 'Tồn kho thực tế' : 'Số lượng'} error={error}>
              <Input type="number" min={0} value={qty} onChange={(e) => setQty(Math.floor(Number(e.target.value)))} invalid={!!error} />
            </Field>
            <Field label={needsReason ? 'Lý do *' : 'Ghi chú'} error={needsReason && !note.trim() ? 'Nhập lý do xuất / hoàn kho' : null}>
              <Input value={note} onChange={(e) => setNote(e.target.value)} placeholder={needsReason ? 'VD: Xuất bảo dưỡng xe đẩy, thanh lý hàng hỏng…' : ''} />
            </Field>
          </div>
        )}
      </Modal>

      <Modal
        open={adding}
        onClose={() => setAdding(false)}
        title="Thêm vật tư / dụng cụ"
        footer={
          <>
            <Button variant="secondary" onClick={() => setAdding(false)}>Hủy</Button>
            <Button disabled={!draft.name.trim() || !draft.unit.trim() || draft.stock < 0} onClick={() => { dispatchCatalog({ type: 'ADD_INVENTORY_ITEM', item: { ...draft, name: draft.name.trim(), unit: draft.unit.trim() } }); setAdding(false); setDraft({ name: '', unit: '', kind: 'CONSUMABLE', stock: 0, minStock: 0 }); }}>Thêm</Button>
          </>
        }
      >
        <div className="space-y-4">
          <Field label="Tên vật tư"><Input value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} /></Field>
          <Field label="Loại" hint={draft.kind === 'TOOL' ? 'Xuất cho đội mượn, phải trả đủ khi quyết toán.' : 'Dùng cho khách, phần dư nhập lại kho.'}>
            <Select value={draft.kind} onChange={(e) => setDraft({ ...draft, kind: e.target.value as MaterialKind })}>
              <option value="CONSUMABLE">Vật tư tiêu hao</option>
              <option value="TOOL">Dụng cụ dùng lại</option>
            </Select>
          </Field>
          <div className="grid grid-cols-3 gap-3">
            <Field label="Đơn vị"><Input value={draft.unit} onChange={(e) => setDraft({ ...draft, unit: e.target.value })} /></Field>
            <Field label="Tồn ban đầu"><Input type="number" min={0} value={draft.stock} onChange={(e) => setDraft({ ...draft, stock: Math.max(0, Number(e.target.value)) })} /></Field>
            <Field label="Mức tối thiểu"><Input type="number" min={0} value={draft.minStock} onChange={(e) => setDraft({ ...draft, minStock: Math.max(0, Number(e.target.value)) })} /></Field>
          </div>
        </div>
      </Modal>
    </>
  );
};

// ===========================================================================
// Module 1 — Tài khoản & phân quyền
// ===========================================================================
