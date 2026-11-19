import { useState } from 'react';
import { ArrowUpFromLine, PackageCheck, Plus, X } from 'lucide-react';
import { Alert, Badge, Button, cx, Field, Input, Modal, Select } from '@/components/ui';
import {
  canReturnMaterials,
  discrepancyOf,
  expectedReturnOf,
  issueStockError,
  MATERIAL_STATUS_META,
  maxReturnOf,
  MIN_NOTE_LENGTH,
  newMaterialLine,
  usedOf,
  validateIssue,
  validateReturn,
  withAddedLines,
} from '@/services/materials';
import { useApp } from '@/services/store/AppStore';
import { useCatalog } from '@/services/store/CatalogStore';
import type { MaterialLine, MaterialQuantities, MaterialSlip, Order } from '@/types';

const KIND_LABEL = { CONSUMABLE: 'Tiêu hao', TOOL: 'Dụng cụ' } as const;

const dash = (n: number | null) => (n === null ? '—' : n);

const toQty = (raw: string, max: number) => Math.min(max, Math.max(0, Math.floor(Number(raw) || 0)));

export const MaterialStatusBadge = ({ slip }: { slip: MaterialSlip }) => {
  const meta = MATERIAL_STATUS_META[slip.status];
  const lost = slip.lines.reduce((s, l) => s + l.lost, 0);
  if (slip.status === 'RETURNED' && lost > 0) return <Badge tone="warning">Đã quyết toán · chênh lệch {lost}</Badge>;
  return <Badge tone={meta.tone}>{meta.label}</Badge>;
};

/** Chênh lệch theo dòng: sau quyết toán = thất thoát; trước đó = số đang nằm ngoài kho chưa trả. */
const gapOf = (slip: MaterialSlip, l: MaterialLine): number =>
  slip.status === 'RETURNED' ? l.lost : slip.status === 'DRAFT' ? 0 : l.issued - usedOf(l) - (l.returned ?? 0);

/** Tóm tắt một phiếu cho dòng thu gọn: số dòng, tổng kế hoạch / đã xuất, tổng chênh lệch (hoặc còn ngoài kho). */
export const slipSummary = (slip: MaterialSlip) => ({
  lines: slip.lines.length,
  planned: slip.lines.reduce((s, l) => s + l.planned, 0),
  issued: slip.status === 'DRAFT' ? null : slip.lines.reduce((s, l) => s + l.issued, 0),
  gap: slip.lines.reduce((s, l) => s + gapOf(slip, l), 0),
  settled: slip.status === 'RETURNED',
});

/** Bảng đối chiếu theo dòng: kế hoạch → xuất → nhận → dùng → trả → chênh lệch. */
export const MaterialLinesTable = ({ slip }: { slip: MaterialSlip }) => {
  const settled = slip.status === 'RETURNED';
  const gap = (l: MaterialLine) => gapOf(slip, l);
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[720px] text-sm">
        <thead>
          <tr className="border-b border-slate-100 text-left text-xs font-semibold tracking-wide text-slate-500 uppercase">
            <th className="py-2 pr-3">Vật tư</th>
            <th className="px-3 py-2 text-right">Kế hoạch</th>
            <th className="px-3 py-2 text-right">Đã xuất</th>
            <th className="px-3 py-2 text-right">Đã nhận</th>
            <th className="px-3 py-2 text-right">Đã dùng</th>
            <th className="px-3 py-2 text-right">Đã trả</th>
            <th className="py-2 pl-3 text-right">{settled ? 'Chênh lệch' : 'Còn ngoài kho'}</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {slip.lines.map((l) => {
            const g = gap(l);
            return (
              <tr key={l.id}>
                <td className="py-2.5 pr-3">
                  <p className="font-medium">{l.name}</p>
                  <p className="text-xs text-slate-500">
                    {KIND_LABEL[l.kind]} · {l.unit}
                  </p>
                </td>
                <td className="px-3 py-2.5 text-right tabular-nums text-slate-500">{l.planned}</td>
                <td className="px-3 py-2.5 text-right tabular-nums">{slip.status === 'DRAFT' ? '—' : l.issued}</td>
                <td className={cx('px-3 py-2.5 text-right tabular-nums', l.received !== null && l.received < l.issued && 'font-semibold text-amber-700')}>
                  {dash(l.received)}
                </td>
                <td className="px-3 py-2.5 text-right tabular-nums">{l.kind === 'TOOL' ? '—' : dash(l.used)}</td>
                <td className="px-3 py-2.5 text-right tabular-nums">{dash(l.returned)}</td>
                <td className={cx('py-2.5 pl-3 text-right font-semibold tabular-nums', g > 0 ? (settled ? 'text-rose-600' : 'text-amber-600') : 'text-emerald-600')}>{g}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
};

/** Thủ kho duyệt phiếu nháp, chỉnh số lượng và xuất kho (trừ tồn ngay). */
export const IssueMaterialsModal = ({ order, onClose }: { order: Order; onClose: () => void }) => {
  const { dispatch } = useApp();
  const { catalog, dispatchCatalog } = useCatalog();
  const [added, setAdded] = useState<MaterialLine[]>([]);
  const [pick, setPick] = useState('');
  const slip = withAddedLines(order.materials, added);
  const [issued, setIssued] = useState<MaterialQuantities>(() => Object.fromEntries(order.materials.lines.map((l) => [l.id, l.planned])));
  const addable = catalog.inventory.filter((i) => !slip.lines.some((l) => l.inventoryItemId === i.id));
  const isAdded = (l: MaterialLine) => added.some((a) => a.id === l.id);
  const error =
    (order.status !== 'ASSIGNED' ? 'Đơn không còn ở trạng thái chờ thực hiện.' : null) ??
    validateIssue(slip, issued) ??
    issueStockError(slip, issued, catalog.inventory);

  const addLine = () => {
    const item = catalog.inventory.find((i) => i.id === pick);
    if (!item) return;
    const line = newMaterialLine(item, 0);
    setAdded((a) => [...a, line]);
    setIssued((q) => ({ ...q, [line.id]: 1 }));
    setPick('');
  };

  const removeLine = (id: string) => {
    setAdded((a) => a.filter((l) => l.id !== id));
    setIssued(({ [id]: _removed, ...rest }) => rest);
  };

  const submit = () => {
    if (error) return;
    slip.lines
      .filter((l) => (issued[l.id] ?? 0) > 0)
      .forEach((l) =>
        dispatchCatalog({
          type: 'INVENTORY_TXN',
          itemId: l.inventoryItemId,
          txnType: 'OUT',
          quantity: issued[l.id]!,
          orderId: order.id,
          note: isAdded(l) ? 'Xuất bổ sung ngoài kế hoạch' : 'Xuất theo phiếu vật tư',
        }),
      );
    dispatch({ type: 'ISSUE_MATERIALS', orderId: order.id, issued, addedLines: added });
    onClose();
  };

  return (
    <Modal
      open
      size="lg"
      onClose={onClose}
      title={`Xuất kho vật tư — #${order.id}`}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Hủy</Button>
          <Button icon={ArrowUpFromLine} disabled={!!error} onClick={submit}>Xuất kho</Button>
        </>
      }
    >
      <p className="mb-3 text-sm text-slate-500">
        Kế hoạch: vật tư tiêu hao theo báo giá, dụng cụ bốc xếp theo số người bốc vác. Dòng xuất 0 sẽ bỏ khỏi phiếu.
        Trang bị theo xe như dây chằng, bạt không xuất theo đơn.
      </p>
      <ul className="divide-y divide-slate-100">
        {slip.lines.map((l) => {
          const stock = catalog.inventory.find((i) => i.id === l.inventoryItemId)?.stock ?? 0;
          return (
            <li key={l.id} className="flex items-center gap-3 py-2.5">
              <div className="min-w-0 flex-1">
                <p className="flex flex-wrap items-center gap-1.5 font-medium">
                  {l.name}
                  {isAdded(l) && <Badge tone="brand">Thêm tay</Badge>}
                </p>
                <p className="text-xs text-slate-500">
                  {KIND_LABEL[l.kind]} · {isAdded(l) ? 'ngoài kế hoạch' : `kế hoạch ${l.planned}`} · tồn {stock} {l.unit}
                </p>
              </div>
              {isAdded(l) && (
                <button
                  type="button"
                  onClick={() => removeLine(l.id)}
                  className="grid size-9 shrink-0 place-items-center rounded-lg text-slate-400 hover:bg-rose-50 hover:text-rose-600"
                  aria-label={`Bỏ dòng ${l.name}`}
                >
                  <X className="size-4" />
                </button>
              )}
              {/* Input mặc định w-full — khung cố định để cột tên vật tư giữ đủ chỗ. */}
              <div className="w-24 shrink-0">
                <Input
                  type="number"
                  min={0}
                  max={stock}
                  className="h-10 text-right"
                  value={issued[l.id] ?? 0}
                  invalid={(issued[l.id] ?? 0) > stock}
                  onChange={(e) => setIssued((q) => ({ ...q, [l.id]: toQty(e.target.value, 9999) }))}
                  aria-label={`Số xuất ${l.name}`}
                />
              </div>
            </li>
          );
        })}
      </ul>
      {addable.length > 0 && (
        <div className="mt-3 flex items-center gap-2 rounded-xl bg-slate-50 p-3">
          <div className="min-w-0 flex-1">
            <Select value={pick} onChange={(e) => setPick(e.target.value)} className="h-10" aria-label="Chọn vật tư bổ sung">
              <option value="">Thêm vật tư / dụng cụ ngoài kế hoạch…</option>
              {addable.map((i) => (
                <option key={i.id} value={i.id}>
                  {i.name} · {KIND_LABEL[i.kind]} · tồn {i.stock} {i.unit}
                </option>
              ))}
            </Select>
          </div>
          <Button size="sm" variant="secondary" icon={Plus} disabled={!pick} onClick={addLine}>
            Thêm dòng
          </Button>
        </div>
      )}
      {error && <Alert tone="danger" className="mt-3">{error}</Alert>}
    </Modal>
  );
};

/** Thủ kho kiểm nhận vật tư trả về; chênh lệch phải lập biên bản trước khi quyết toán. */
export const ReturnMaterialsModal = ({ order, onClose }: { order: Order; onClose: () => void }) => {
  const { dispatch } = useApp();
  const { dispatchCatalog } = useCatalog();
  const slip = order.materials;
  const cancelled = order.status === 'CANCELLED_EXPIRED';
  const [returned, setReturned] = useState<MaterialQuantities>(() => Object.fromEntries(slip.lines.map((l) => [l.id, expectedReturnOf(l)])));
  const [note, setNote] = useState('');
  const lostTotal = slip.lines.reduce((s, l) => s + Math.max(0, discrepancyOf(l, returned[l.id] ?? 0)), 0);
  const error = validateReturn(order, returned, note);

  const submit = () => {
    if (error) return;
    slip.lines
      .filter((l) => (returned[l.id] ?? 0) > 0)
      .forEach((l) =>
        dispatchCatalog({
          type: 'INVENTORY_TXN',
          itemId: l.inventoryItemId,
          txnType: 'RETURN',
          quantity: returned[l.id]!,
          orderId: order.id,
          note: cancelled ? 'Thu hồi do hủy đơn' : l.kind === 'TOOL' ? 'Dụng cụ trả về' : 'Vật tư dư nhập lại',
        }),
      );
    dispatch({ type: 'RETURN_MATERIALS', orderId: order.id, returned, note });
    onClose();
  };

  return (
    <Modal
      open
      size="lg"
      onClose={onClose}
      title={`${cancelled ? 'Thu hồi vật tư đơn đã hủy' : 'Kiểm nhận vật tư trả về'} — #${order.id}`}
      tone={lostTotal > 0 ? 'warning' : undefined}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Hủy</Button>
          <Button icon={PackageCheck} disabled={!!error} onClick={submit}>
            {lostTotal > 0 ? 'Quyết toán kèm biên bản' : 'Nhập kho & quyết toán'}
          </Button>
        </>
      }
    >
      {slip.receiveNote && <Alert tone="warning" className="mb-3">Đội xe báo nhận thiếu khi xuất kho: {slip.receiveNote}</Alert>}
      <div className="overflow-x-auto">
        <table className="w-full min-w-[560px] text-sm">
          <thead>
            <tr className="border-b border-slate-100 text-left text-xs font-semibold tracking-wide text-slate-500 uppercase">
              <th className="py-2 pr-3">Vật tư</th>
              <th className="px-2 py-2 text-right">Xuất</th>
              <th className="px-2 py-2 text-right">Nhận</th>
              <th className="px-2 py-2 text-right">Dùng</th>
              <th className="px-2 py-2 text-right">Phải trả</th>
              <th className="px-2 py-2 text-right">Thực trả</th>
              <th className="py-2 pl-2 text-right">Chênh lệch</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {slip.lines.map((l) => {
              const r = returned[l.id] ?? 0;
              const gap = discrepancyOf(l, r);
              return (
                <tr key={l.id}>
                  <td className="py-2 pr-3">
                    <p className="font-medium">{l.name}</p>
                    <p className="text-xs text-slate-500">{KIND_LABEL[l.kind]} · {l.unit}</p>
                  </td>
                  <td className="px-2 py-2 text-right tabular-nums">{l.issued}</td>
                  <td className="px-2 py-2 text-right tabular-nums">{dash(l.received)}</td>
                  <td className="px-2 py-2 text-right tabular-nums">{l.kind === 'TOOL' ? '—' : usedOf(l)}</td>
                  <td className="px-2 py-2 text-right font-semibold tabular-nums">{expectedReturnOf(l)}</td>
                  <td className="px-2 py-2 text-right">
                    <div className="ml-auto w-20">
                      <Input
                        type="number"
                        min={0}
                        max={maxReturnOf(l)}
                        className="h-9 text-right"
                        value={r}
                        onChange={(e) => setReturned((q) => ({ ...q, [l.id]: toQty(e.target.value, maxReturnOf(l)) }))}
                        aria-label={`Số trả ${l.name}`}
                      />
                    </div>
                  </td>
                  <td className={cx('py-2 pl-2 text-right font-semibold tabular-nums', gap > 0 ? 'text-rose-600' : 'text-emerald-600')}>{gap}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      {lostTotal > 0 && (
        <Field
          label="Biên bản xử lý chênh lệch *"
          className="mt-4"
          hint="Ghi lý do mất, hỏng, hao hụt hoặc kho giao thiếu và người chịu trách nhiệm."
          error={note.trim().length < MIN_NOTE_LENGTH ? `Tối thiểu ${MIN_NOTE_LENGTH} ký tự` : null}
        >
          <textarea
            rows={3}
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="VD: Mất 1 dây chằng tại nhà khách — trưởng nhóm NV-014 chịu trách nhiệm, trừ lương theo quy định."
            className="w-full rounded-xl border border-slate-200 p-3 text-base focus:border-brand focus:ring-4 focus:ring-brand/10 focus:outline-none sm:text-sm"
          />
        </Field>
      )}
    </Modal>
  );
};

/** Nút thao tác theo trạng thái phiếu — dùng chung cho Kho và Quyết toán. */
export const MaterialSlipActions = ({ order, onIssue, onReturn }: { order: Order; onIssue: () => void; onReturn: () => void }) => {
  const s = order.materials.status;
  if (s === 'DRAFT' && order.status === 'ASSIGNED') return <Button size="sm" icon={ArrowUpFromLine} onClick={onIssue}>Xuất kho</Button>;
  if (canReturnMaterials(order)) return <Button size="sm" icon={PackageCheck} onClick={onReturn}>{order.status === 'CANCELLED_EXPIRED' ? 'Thu hồi vật tư' : 'Kiểm nhận trả'}</Button>;
  if (s === 'RECEIVED') return <span className="text-xs text-slate-500">Chờ đội xe khai vật tư đã dùng</span>;
  if (s === 'ISSUED') return <span className="text-xs text-slate-500">Chờ đội xe xác nhận nhận</span>;
  return null;
};
