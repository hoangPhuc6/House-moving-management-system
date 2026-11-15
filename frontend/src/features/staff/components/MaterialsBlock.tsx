import { useState } from 'react';
import { CheckCircle2, ClipboardList, Send, Wrench } from 'lucide-react';
import { Alert, Badge, Button, cx, Field, Input, NumberStepper } from '@/components/ui';
import { Block } from '@/features/staff/components/Block';
import {
  expectedReturnOf,
  MATERIAL_STATUS_META,
  MIN_NOTE_LENGTH,
  validateReceive,
  validateUsage,
} from '@/services/materials';
import { useApp } from '@/services/store/AppStore';
import type { MaterialLine, MaterialQuantities, Order } from '@/types';

const KIND_LABEL = { CONSUMABLE: 'Tiêu hao', TOOL: 'Dụng cụ' } as const;

const LineName = ({ line }: { line: MaterialLine }) => (
  <div className="min-w-0 flex-1">
    <p className="truncate text-sm font-medium text-slate-800">{line.name}</p>
    <p className="text-xs text-slate-500">
      {KIND_LABEL[line.kind]} · {line.unit}
    </p>
  </div>
);

/** Danh sách chỉ đọc: tên dòng + một con số theo bước hiện tại. */
const ReadOnlyLines = ({ lines, value, suffix }: { lines: MaterialLine[]; value: (l: MaterialLine) => number; suffix?: (l: MaterialLine) => string | null }) => (
  <ul className="divide-y divide-slate-100">
    {lines.map((l) => (
      <li key={l.id} className="flex items-center gap-3 py-2.5">
        <LineName line={l} />
        <div className="text-right">
          <p className="text-sm font-semibold tabular-nums">×{value(l)}</p>
          {suffix?.(l) && <p className="text-[11px] text-amber-700">{suffix(l)}</p>}
        </div>
      </li>
    ))}
  </ul>
);

const fill = (lines: MaterialLine[], pick: (l: MaterialLine) => number): MaterialQuantities =>
  Object.fromEntries(lines.map((l) => [l.id, pick(l)]));

/**
 * Form nhận vật tư tại kho. State nằm ở JobDetail vì việc xác nhận dùng chung nút hành động chính của lệnh
 * ("Nhận vật tư tại kho"). Số nhận mặc định = số đã xuất; chỉ lưu các dòng nhân viên sửa,
 * nên khi kho vừa xuất phiếu (DRAFT → ISSUED) form tự lấy đúng số mới.
 */
export const useMaterialReceipt = (order: Order) => {
  const { dispatch } = useApp();
  const slip = order.materials;
  const [overrides, setOverrides] = useState<MaterialQuantities>({});
  const [note, setNote] = useState('');
  const [checked, setChecked] = useState(false);
  const [attempted, setAttempted] = useState(false);

  const active = slip.status === 'ISSUED' && order.status === 'ASSIGNED' && order.fieldStage === 'NOT_STARTED';
  const received: MaterialQuantities = fill(slip.lines, (l) => overrides[l.id] ?? l.issued);
  const shortage = slip.lines.some((l) => (received[l.id] ?? 0) < l.issued);
  const error = active ? validateReceive(slip, received, note) : null;

  /** Trả về false nếu chưa đủ điều kiện (chưa tick kiểm tra / thiếu ghi chú) — lỗi hiện đỏ trên khối phiếu. */
  const submit = (): boolean => {
    setAttempted(true);
    if (!active || !checked || error) return false;
    dispatch({ type: 'CONFIRM_MATERIALS_RECEIVED', orderId: order.id, received, note });
    return true;
  };

  return {
    active,
    received,
    setQty: (lineId: string, n: number) => setOverrides((o) => ({ ...o, [lineId]: n })),
    note,
    setNote,
    checked,
    setChecked,
    attempted,
    shortage,
    submit,
  };
};

export type MaterialReceipt = ReturnType<typeof useMaterialReceipt>;

/**
 * Phiếu vật tư trên lệnh việc của đội xe: nhận tại kho (kiểm từng dòng, xác nhận bằng nút chính của lệnh)
 * → khai đã dùng sau bàn giao.
 */
export const MaterialsBlock = ({ order, receipt }: { order: Order; receipt: MaterialReceipt }) => {
  const { dispatch } = useApp();
  const slip = order.materials;
  const [used, setUsed] = useState<MaterialQuantities>(() => fill(slip.lines, (l) => (l.kind === 'TOOL' ? 0 : (l.received ?? 0))));

  if (slip.lines.length === 0) return null;

  const meta = MATERIAL_STATUS_META[slip.status];
  const canReceive = receipt.active;
  const canReport = slip.status === 'RECEIVED' && order.fieldStage === 'HANDED_OVER';
  const { received, shortage, note, checked, attempted } = receipt;
  const noteMissing = shortage && note.trim().length < MIN_NOTE_LENGTH;
  const uncheckedError = attempted && !checked;
  const usageError = canReport ? validateUsage(slip, used) : null;

  return (
    <Block title="Phiếu vật tư & dụng cụ" icon={Wrench} right={<Badge tone={meta.tone}>{meta.label}</Badge>}>
      {slip.status === 'DRAFT' && (
        <>
          <p className="mb-2 rounded-lg bg-amber-50 p-2.5 text-xs text-amber-800">
            Kho đang chuẩn bị phiếu xuất. Khi kho xuất xong, kiểm hàng và xác nhận số thực nhận tại đây.
          </p>
          <ReadOnlyLines lines={slip.lines} value={(l) => l.planned} />
        </>
      )}

      {slip.status === 'ISSUED' && !canReceive && <ReadOnlyLines lines={slip.lines} value={(l) => l.issued} />}

      {canReceive && (
        <>
          <p className="mb-2 text-xs text-slate-500">Đếm thực tế tại kho. Chỉ giảm số lượng nếu kho giao thiếu.</p>
          <ul className="divide-y divide-slate-100">
            {slip.lines.map((l) => (
              <li key={l.id} className="flex items-center gap-3 py-2.5">
                <LineName line={l} />
                <div className="w-36 shrink-0">
                  <NumberStepper
                    value={received[l.id] ?? 0}
                    min={0}
                    max={l.issued}
                    suffix={`/${l.issued}`}
                    onChange={(n) => receipt.setQty(l.id, n)}
                  />
                </div>
              </li>
            ))}
          </ul>
          {shortage && (
            <Field
              label="Ghi chú nhận thiếu (bắt buộc)"
              className="mt-3"
              error={attempted && noteMissing ? `Ghi rõ thiếu gì, vì sao (tối thiểu ${MIN_NOTE_LENGTH} ký tự)` : null}
            >
              <Input
                value={note}
                invalid={attempted && noteMissing}
                onChange={(e) => receipt.setNote(e.target.value)}
                placeholder="VD: Kho hết chăn bọc, giao thiếu 2 tấm"
              />
            </Field>
          )}
          <label
            className={cx(
              'mt-3 flex cursor-pointer items-start gap-3 rounded-xl border p-3 transition-colors',
              uncheckedError ? 'border-rose-400 bg-rose-50' : checked ? 'border-emerald-200 bg-emerald-50' : 'border-slate-200',
            )}
          >
            <input
              type="checkbox"
              checked={checked}
              onChange={(e) => receipt.setChecked(e.target.checked)}
              aria-invalid={uncheckedError}
              className={cx('mt-0.5 size-5 shrink-0 rounded accent-[#FF5A1F]', uncheckedError && 'outline-2 outline-rose-500')}
            />
            <span className="text-sm">
              <span className={cx('font-semibold', uncheckedError ? 'text-rose-700' : 'text-slate-800')}>
                Tôi đã kiểm tra phiếu vật tư
              </span>
              <span className={cx('block text-xs', uncheckedError ? 'text-rose-600' : 'text-slate-500')}>
                {uncheckedError
                  ? 'Bắt buộc: hãy đếm và tick xác nhận trước khi nhận vật tư.'
                  : 'Đã đếm thực tế từng dòng, số lượng ở trên đúng với hàng nhận.'}
              </span>
            </span>
          </label>
          <p className="mt-2 text-xs text-slate-500">Bấm nút “Nhận vật tư tại kho” bên dưới để hoàn tất.</p>
        </>
      )}

      {slip.status === 'RECEIVED' && !canReport && (
        <>
          {slip.receiveNote && <Alert tone="warning" className="mb-2">Nhận thiếu: {slip.receiveNote}</Alert>}
          <ReadOnlyLines lines={slip.lines} value={(l) => l.received ?? 0} />
          <p className="mt-2 text-xs text-slate-500">Đội xe đang giữ. Sau khi bàn giao, khai số vật tư đã dùng để mang phần còn lại về kho.</p>
        </>
      )}

      {canReport && (
        <>
          <p className="mb-2 text-xs text-slate-500">Khai số vật tư tiêu hao đã dùng cho khách. Dụng cụ phải mang về đủ.</p>
          <ul className="divide-y divide-slate-100">
            {slip.lines.map((l) => (
              <li key={l.id} className="flex items-center gap-3 py-2.5">
                <LineName line={l} />
                {l.kind === 'CONSUMABLE' ? (
                  <div className="w-36 shrink-0">
                    <NumberStepper
                      value={used[l.id] ?? 0}
                      min={0}
                      max={l.received ?? 0}
                      suffix={`/${l.received ?? 0}`}
                      onChange={(n) => setUsed((u) => ({ ...u, [l.id]: n }))}
                    />
                  </div>
                ) : (
                  <span className="text-xs font-semibold text-slate-600 tabular-nums">Mang về ×{l.received ?? 0}</span>
                )}
              </li>
            ))}
          </ul>
          <div className="mt-3 rounded-xl bg-slate-50 p-3 text-xs text-slate-600">
            <p className="mb-1 font-semibold text-slate-800">Cần mang về kho</p>
            {slip.lines
              .map((l) => ({ l, back: (l.received ?? 0) - (l.kind === 'TOOL' ? 0 : (used[l.id] ?? 0)) }))
              .filter((x) => x.back > 0)
              .map(({ l, back }) => (
                <p key={l.id} className="tabular-nums">
                  {l.name}: {back} {l.unit}
                </p>
              ))}
          </div>
          <Button
            className="mt-3"
            block
            size="lg"
            icon={Send}
            disabled={!!usageError}
            onClick={() => dispatch({ type: 'REPORT_MATERIAL_USAGE', orderId: order.id, used })}
          >
            Gửi báo cáo vật tư
          </Button>
        </>
      )}

      {slip.status === 'USAGE_REPORTED' && (
        <>
          <ReadOnlyLines lines={slip.lines} value={expectedReturnOf} />
          <p className="mt-2 flex items-center gap-1.5 text-xs text-slate-500">
            <ClipboardList className="size-3.5" /> Số trên là lượng phải nộp về kho. Kho sẽ kiểm nhận và đối chiếu.
          </p>
        </>
      )}

      {slip.status === 'RETURNED' && (
        <>
          <ReadOnlyLines lines={slip.lines} value={(l) => l.returned ?? 0} suffix={(l) => (l.lost > 0 ? `Chênh lệch ${l.lost}` : null)} />
          <p className={cx('mt-2 flex items-center gap-1.5 text-xs', slip.lines.some((l) => l.lost > 0) ? 'text-amber-700' : 'text-emerald-700')}>
            <CheckCircle2 className="size-3.5" />
            {slip.lines.some((l) => l.lost > 0) ? `Kho đã quyết toán, có chênh lệch: ${slip.resolutionNote}` : 'Kho đã nhận đủ vật tư trả về.'}
          </p>
        </>
      )}
    </Block>
  );
};
