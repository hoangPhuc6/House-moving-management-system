import { useMemo, useState } from 'react';
import { ArrowUpFromLine, History, PackagePlus } from 'lucide-react';
import {
  Badge,
  Button,
  Card,
  CardHeader,
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
import { formatTime } from '@/services/format';
import { useApp } from '@/services/store/AppStore';
import { useCatalog } from '@/services/store/CatalogStore';
import { inventoryError } from '@/services/store/catalogReducer';
import type { InventoryItem, InventoryTxnType } from '@/types';

export const InventoryModule = ({ query }: { query: string }) => {
  const { state, dispatch } = useApp();
  const { catalog, dispatchCatalog } = useCatalog();
  const [tab, setTab] = useState<'stock' | 'history' | 'usage'>('stock');
  const [txn, setTxn] = useState<{ item: InventoryItem; type: InventoryTxnType } | null>(null);
  const [qty, setQty] = useState(1);
  const [orderId, setOrderId] = useState('');
  const [note, setNote] = useState('');
  const [adding, setAdding] = useState(false);
  const [draft, setDraft] = useState({ name: '', unit: '', stock: 0, minStock: 0 });

  const items = catalog.inventory.filter((i) => !query.trim() || i.name.toLowerCase().includes(query.trim().toLowerCase()));
  const low = catalog.inventory.filter((i) => i.stock <= i.minStock);
  const itemName = (id: string) => catalog.inventory.find((i) => i.id === id)?.name ?? id;
  const needsOrder = txn?.type === 'OUT' || txn?.type === 'RETURN';
  const error = txn ? inventoryError(txn.item, txn.type, qty) : null;

  const usage = useMemo(() => {
    const map = new Map<string, Map<string, { used: number; returned: number }>>();
    catalog.txns.forEach((t) => {
      if (!t.orderId || (t.type !== 'OUT' && t.type !== 'RETURN')) return;
      const byItem = map.get(t.orderId) ?? new Map();
      const cur = byItem.get(t.itemId) ?? { used: 0, returned: 0 };
      if (t.type === 'OUT') cur.used += t.quantity; else cur.returned += t.quantity;
      byItem.set(t.itemId, cur);
      map.set(t.orderId, byItem);
    });
    return [...map.entries()];
  }, [catalog.txns]);

  const open = (item: InventoryItem, type: InventoryTxnType) => {
    setTxn({ item, type });
    setQty(type === 'ADJUST' ? item.stock : 1);
    setOrderId('');
    setNote('');
  };

  const submit = () => {
    if (!txn || error || (needsOrder && !orderId)) return;
    dispatchCatalog({ type: 'INVENTORY_TXN', itemId: txn.item.id, txnType: txn.type, quantity: qty, orderId: needsOrder ? orderId : null, note });
    dispatch({ type: 'PUSH_TOAST', tone: 'success', message: `${TXN_META[txn.type].label}: ${txn.item.name}.` });
    setTxn(null);
  };

  return (
    <>
      <PageHeader eyebrow="Vận hành" title="Kho & dụng cụ" subtitle="Mọi lần nhập, xuất, hoàn kho đều lưu lịch sử; không cho xuất vượt tồn." actions={<Button icon={PackagePlus} onClick={() => setAdding(true)}>Thêm vật tư</Button>} />
      <div className="mb-4 grid gap-4 sm:grid-cols-3">
        <Stat label="Danh mục vật tư" value={catalog.inventory.length} icon={PackagePlus} />
        <Stat label="Sắp hết hàng" value={low.length} icon={ArrowUpFromLine} tone={low.length ? 'warning' : 'success'} hint={low.map((i) => i.name).join(', ') || 'Tồn kho ổn định'} />
        <Stat label="Giao dịch kho" value={catalog.txns.length} icon={History} tone="info" />
      </div>
      <Tabs className="mb-4" value={tab} onChange={setTab} tabs={[{ id: 'stock', label: 'Tồn kho' }, { id: 'history', label: 'Lịch sử giao dịch' }, { id: 'usage', label: 'Vật tư theo booking' }]} />

      {tab === 'stock' && (
        <Card padded={false}>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[820px] text-sm">
              <thead>
                <tr className="border-b border-slate-100 bg-slate-50/60 text-left text-xs font-semibold tracking-wide text-slate-500 uppercase">
                  <th className="px-6 py-3">Vật tư</th><th className="px-3 py-3 text-right">Tồn</th><th className="px-3 py-3 text-right">Mức tối thiểu</th><th className="px-3 py-3">Tình trạng</th><th className="px-6 py-3 text-right">Giao dịch</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {items.length === 0 && <tr><td colSpan={5} className="p-6"><EmptyState icon={PackagePlus} title="Không có vật tư phù hợp" /></td></tr>}
                {items.map((i) => (
                  <tr key={i.id}>
                    <td className="px-6 py-3.5 font-semibold">{i.name}</td>
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

      {tab === 'usage' && (
        <div className="space-y-4">
          {usage.length === 0 && <Card><EmptyState icon={History} title="Chưa xuất vật tư cho booking nào" /></Card>}
          {usage.map(([oid, byItem]) => (
            <Card key={oid}>
              <CardHeader title={`Đơn #${oid}`} subtitle={state.orders.find((o) => o.id === oid)?.customerName} />
              <ul className="divide-y divide-slate-100 text-sm">
                {[...byItem.entries()].map(([iid, u]) => (
                  <li key={iid} className="flex items-center justify-between gap-3 py-2.5">
                    <span className="font-medium">{itemName(iid)}</span>
                    <span className="text-slate-500 tabular-nums">Đã xuất {u.used} · Hoàn {u.returned} · <b className={u.used - u.returned > 0 ? 'text-amber-600' : 'text-emerald-600'}>Cần thu hồi {Math.max(0, u.used - u.returned)}</b></span>
                  </li>
                ))}
              </ul>
            </Card>
          ))}
        </div>
      )}

      <Modal
        open={!!txn}
        onClose={() => setTxn(null)}
        title={txn ? `${TXN_META[txn.type].label} — ${txn.item.name}` : ''}
        footer={<><Button variant="secondary" onClick={() => setTxn(null)}>Hủy</Button><Button disabled={!!error || (needsOrder && !orderId)} onClick={submit}>Xác nhận</Button></>}
      >
        {txn && (
          <div className="space-y-4">
            <p>Tồn hiện tại: <b className="tabular-nums">{txn.item.stock} {txn.item.unit}</b></p>
            <Field label={txn.type === 'ADJUST' ? 'Tồn kho thực tế' : 'Số lượng'} error={error}>
              <Input type="number" min={0} value={qty} onChange={(e) => setQty(Math.floor(Number(e.target.value)))} invalid={!!error} />
            </Field>
            {needsOrder && (
              <Field label="Gắn với booking" error={!orderId ? 'Chọn booking để theo dõi vật tư' : null}>
                <Select value={orderId} onChange={(e) => setOrderId(e.target.value)}>
                  <option value="">— Chọn đơn —</option>
                  {state.orders.map((o) => <option key={o.id} value={o.id}>#{o.id} · {o.customerName}</option>)}
                </Select>
              </Field>
            )}
            <Field label="Ghi chú"><Input value={note} onChange={(e) => setNote(e.target.value)} /></Field>
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
            <Button disabled={!draft.name.trim() || !draft.unit.trim() || draft.stock < 0} onClick={() => { dispatchCatalog({ type: 'ADD_INVENTORY_ITEM', item: { ...draft, name: draft.name.trim(), unit: draft.unit.trim() } }); setAdding(false); setDraft({ name: '', unit: '', stock: 0, minStock: 0 }); }}>Thêm</Button>
          </>
        }
      >
        <div className="space-y-4">
          <Field label="Tên vật tư"><Input value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} /></Field>
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
