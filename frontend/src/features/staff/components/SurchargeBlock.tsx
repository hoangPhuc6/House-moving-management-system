import { type ChangeEvent, useState } from 'react';
import { AlertOctagon, Camera, Plus, Send, Trash2, Undo2 } from 'lucide-react';
import { Badge, Button, controlBase, cx, Field, Input, Modal, NumberStepper, Select } from '@/components/ui';
import {
  groupSurchargesByReport,
  isSurchargeBlocking,
  SURCHARGE_TYPE_META,
  SURCHARGE_TYPES,
  surchargeAmount,
  surveyedFromRecord,
  systemUnitPrice,
} from '@/constants/surcharge';
import { SurchargeDetails } from '@/features/order/components/SurchargeDetails';
import { photosFromFiles, SitePhotoGrid } from '@/features/order/components/SitePhotoGallery';
import { Block } from '@/features/staff/components/Block';
import { formatTime, formatVND } from '@/services/format';
import { useApp } from '@/services/store/AppStore';
import { useCatalog } from '@/services/store/CatalogStore';
import type { EvidencePhoto, Order, ServiceItem, SurchargeDraft, SurchargeType, Vehicle } from '@/types';

/** Danh sách phát sinh của lệnh (gom theo lần báo) + nút báo sai lệch. */
export const SurchargeBlock = ({ order, canReport }: { order: Order; canReport: boolean }) => {
  const { dispatch } = useApp();
  const [open, setOpen] = useState(false);
  const reports = groupSurchargesByReport(order.surcharges).reverse();

  return (
    <Block title="Phát sinh hiện trường" icon={AlertOctagon}>
      {reports.length === 0 ? (
        <p className="text-sm text-slate-500">Chưa có sai lệch nào so với khảo sát.</p>
      ) : (
        <ul className="space-y-3">
          {reports.map((r) => (
            <li key={r.reportId} className="space-y-3 rounded-xl border border-slate-200 p-3">
              <div>
                <p className="flex items-center justify-between gap-2 text-xs text-slate-500">
                  <span className="tabular-nums">Báo lúc {formatTime(r.proposedAt)}</span>
                  <Badge tone="neutral">{r.lines.length} dòng</Badge>
                </p>
                {r.note && <p className="mt-1 text-sm text-slate-700">{r.note}</p>}
              </div>
              <ul className="space-y-3 border-t border-slate-100 pt-3">
                {r.lines.map((s) => (
                  <li key={s.id} className="[&:not(:first-child)]:border-t [&:not(:first-child)]:border-slate-100 [&:not(:first-child)]:pt-3">
                    <SurchargeDetails s={s} compact priceLabel={s.status === 'PROPOSED' ? `Ước tính · ${s.quantity} ${s.unit}` : undefined}>
                      {s.status === 'PROPOSED' && (
                        <Button size="sm" variant="secondary" icon={Undo2} onClick={() => dispatch({ type: 'STAFF_CANCEL_SURCHARGE', orderId: order.id, surchargeId: s.id })}>
                          Rút dòng này
                        </Button>
                      )}
                    </SurchargeDetails>
                  </li>
                ))}
              </ul>
            </li>
          ))}
        </ul>
      )}
      {isSurchargeBlocking(order) && (
        <p className="mt-3 rounded-lg bg-rose-50 p-2.5 text-xs font-medium text-rose-800">
          Đang chờ Điều phối duyệt / khách xác nhận. Không tự báo giá hay thu tiền phát sinh với khách.
        </p>
      )}
      {canReport && (
        <Button
          variant="secondary"
          size="lg"
          block
          icon={AlertOctagon}
          className="mt-3 h-14 border-amber-300 text-amber-800 hover:bg-amber-50"
          onClick={() => setOpen(true)}
        >
          Báo cáo sai lệch hiện trường
        </Button>
      )}
      {open && <SurchargeForm order={order} onClose={() => setOpen(false)} />}
    </Block>
  );
};

interface LineState {
  key: string;
  type: SurchargeType;
  serviceId: string;
  surveyed: string;
  actual: string;
  description: string;
  quantity: number;
  manualPrice: string;
  photos: EvidencePhoto[];
}

let lineSeq = 0;
const newLine = (type: SurchargeType, surveyed = ''): LineState => ({
  key: `ln-${++lineSeq}`,
  type,
  serviceId: '',
  surveyed,
  actual: '',
  description: '',
  quantity: 1,
  manualPrice: '',
  photos: [],
});

interface LinePricing {
  name: string;
  unit: string;
  unitPrice: number;
  service: ServiceItem | null;
  options: ServiceItem[];
}

/** Đơn giá & tên dòng theo cách tính của loại phụ phí. */
const priceLine = (l: LineState, order: Order, vehicles: Vehicle[], services: ServiceItem[]): LinePricing => {
  const meta = SURCHARGE_TYPE_META[l.type];
  if (meta.mode === 'SYSTEM') return { name: meta.label, unit: meta.unit, unitPrice: systemUnitPrice(l.type, order, vehicles), service: null, options: [] };
  if (meta.mode === 'CATALOG') {
    const options = services.filter((s) => s.active && meta.catalogGroups?.includes(s.group));
    const service = options.find((s) => s.id === l.serviceId) ?? options[0] ?? null;
    return { name: service ? `${meta.label}: ${service.name}` : meta.label, unit: service?.unit ?? '', unitPrice: service?.basePrice ?? 0, service, options };
  }
  return { name: l.description.trim().split(/[.,\n]/)[0]?.slice(0, 60) || meta.label, unit: meta.unit, unitPrice: Number(l.manualPrice) || 0, service: null, options: [] };
};

const revoke = (photos: EvidencePhoto[]) => photos.forEach((p) => URL.revokeObjectURL(p.url));

/** Lỗi của một dòng — mỗi dòng có thể bị bác / từ chối riêng nên phải tự đủ mô tả & bằng chứng. */
const lineErrors = (l: LineState, unitPrice: number) => ({
  actual: !l.actual.trim(),
  description: !l.description.trim(),
  photos: l.photos.length === 0,
  price: unitPrice <= 0,
});
const hasLineError = (e: ReturnType<typeof lineErrors>) => Object.values(e).some(Boolean);

/**
 * Một lần báo gồm một hoặc nhiều dòng sai lệch, mỗi dòng có mô tả & ảnh riêng.
 * Ghi chú chung (tùy chọn) chỉ hiện khi báo từ 2 dòng — với 1 dòng thì trùng với mô tả của dòng.
 */
const SurchargeForm = ({ order, onClose }: { order: Order; onClose: () => void }) => {
  const { state, dispatch } = useApp();
  const { catalog } = useCatalog();
  const [note, setNote] = useState('');
  // "Khảo sát ghi" điền sẵn từ biên bản khảo sát của đơn — NV chỉ cần ghi thực tế.
  const [lines, setLines] = useState<LineState[]>(() => [newLine('LONG_CARRY', surveyedFromRecord('LONG_CARRY', order))]);
  const [submitted, setSubmitted] = useState(false);

  const priced = lines.map((l) => ({ l, p: priceLine(l, order, state.resources.vehicles, catalog.services) }));
  const total = priced.reduce((sum, { l, p }) => sum + surchargeAmount({ quantity: l.quantity, unitPrice: p.unitPrice }), 0);
  const invalid = priced.some(({ l, p }) => hasLineError(lineErrors(l, p.unitPrice)));
  const multi = lines.length > 1;

  const patch = (key: string, p: Partial<LineState>) => setLines((ls) => ls.map((l) => (l.key === key ? { ...l, ...p } : l)));
  const removeLine = (key: string) =>
    setLines((ls) => {
      const l = ls.find((x) => x.key === key);
      if (l) revoke(l.photos);
      return ls.filter((x) => x.key !== key);
    });
  const addPhotos = (e: ChangeEvent<HTMLInputElement>, key: string) => {
    const next = photosFromFiles(e.target.files);
    e.target.value = '';
    setLines((ls) => ls.map((l) => (l.key === key ? { ...l, photos: [...l.photos, ...next] } : l)));
  };
  const removePhoto = (id: string, key: string) =>
    setLines((ls) =>
      ls.map((l) => {
        if (l.key !== key) return l;
        revoke(l.photos.filter((p) => p.id === id));
        return { ...l, photos: l.photos.filter((p) => p.id !== id) };
      }),
    );

  const submit = () => {
    setSubmitted(true);
    if (invalid) return;
    const drafts: SurchargeDraft[] = priced.map(({ l, p }) => ({
      type: l.type,
      name: p.name,
      unit: p.unit,
      unitPrice: p.unitPrice,
      surveyed: l.surveyed,
      actual: l.actual,
      description: l.description,
      quantity: l.quantity,
      photos: l.photos,
    }));
    dispatch({ type: 'STAFF_PROPOSE_SURCHARGE', orderId: order.id, report: { note: multi ? note : '', lines: drafts } });
    onClose();
  };

  const addTile = (key: string, danger: boolean) => (
    <label
      className={cx(
        'relative flex size-full cursor-pointer flex-col items-center justify-center gap-1 rounded-xl border border-dashed text-[11px] font-semibold active:bg-slate-50',
        danger ? 'border-rose-400 text-rose-600' : 'border-slate-300 text-slate-600 hover:bg-slate-50',
      )}
    >
      <Camera className="size-5" />
      Thêm ảnh
      <input type="file" accept="image/*" capture="environment" multiple className="sr-only" onChange={(e) => addPhotos(e, key)} />
    </label>
  );

  return (
    <Modal
      open
      size="lg"
      onClose={onClose}
      title="Báo cáo sai lệch hiện trường"
      tone="warning"
      footer={
        <div className="flex w-full items-center gap-2 sm:w-auto">
          <span className="mr-3 hidden text-sm text-slate-600 sm:block">
            {lines.length} dòng · ước tính <b className="text-slate-900 tabular-nums">+{formatVND(total)}</b>
          </span>
          <Button variant="secondary" className="shrink-0" onClick={onClose}>
            Hủy
          </Button>
          <Button icon={Send} className="flex-1 sm:flex-none" onClick={submit}>
            Gửi Điều phối ({lines.length})
          </Button>
        </div>
      }
    >
      <div className="space-y-5">
        {multi && (
          <Field label="Ghi chú chung (tùy chọn)" hint="Điều liên quan nhiều dòng — VD: xe phải đỗ ngoài đường lớn nên phát sinh cả hẻm lẫn khiêng xa.">
            <textarea
              value={note}
              onChange={(e) => setNote(e.target.value)}
              rows={2}
              maxLength={2000}
              placeholder="Bỏ trống nếu mô tả từng dòng đã đủ rõ"
              className={cx(controlBase, 'h-auto min-h-[64px] py-2.5')}
            />
          </Field>
        )}

        <div>
          {multi && <p className="mb-2 text-sm font-medium text-slate-700">Các dòng sai lệch ({lines.length})</p>}
          <ol className="space-y-3">
            {priced.map(({ l, p }, i) => {
              const meta = SURCHARGE_TYPE_META[l.type];
              const e = lineErrors(l, p.unitPrice);
              const err = (k: keyof typeof e) => submitted && e[k];
              return (
                <li key={l.key} className={cx('rounded-xl border p-3 sm:p-4', submitted && hasLineError(e) ? 'border-rose-300' : 'border-slate-200')}>
                  <div className="mb-3 flex items-center gap-2">
                    <span className="grid size-6 shrink-0 place-items-center rounded-full bg-slate-900 text-xs font-bold text-white tabular-nums">{i + 1}</span>
                    <Select
                      value={l.type}
                      onChange={(ev) => {
                        const type = ev.target.value as SurchargeType;
                        // Chỉ thay "khảo sát ghi" khi NV chưa sửa giá trị điền sẵn.
                        const untouched = !l.surveyed || l.surveyed === surveyedFromRecord(l.type, order);
                        patch(l.key, { type, serviceId: '', ...(untouched ? { surveyed: surveyedFromRecord(type, order) } : {}) });
                      }}
                      className="h-10 flex-1 font-semibold"
                      aria-label={`Loại sai lệch dòng ${i + 1}`}
                    >
                      {SURCHARGE_TYPES.map((t) => (
                        <option key={t} value={t}>
                          {SURCHARGE_TYPE_META[t].label}
                        </option>
                      ))}
                    </Select>
                    {lines.length > 1 && (
                      <button
                        type="button"
                        onClick={() => removeLine(l.key)}
                        className="grid size-10 shrink-0 place-items-center rounded-lg text-slate-400 hover:bg-rose-50 hover:text-rose-600"
                        aria-label={`Xóa dòng ${i + 1}`}
                      >
                        <Trash2 className="size-4" />
                      </button>
                    )}
                  </div>

                  <div className="space-y-3">
                    {meta.mode === 'CATALOG' && (
                      <Field label="Dịch vụ trong bảng giá" error={err('price') ? 'Chưa có dịch vụ phù hợp' : null}>
                        <Select value={p.service?.id ?? ''} onChange={(e) => patch(l.key, { serviceId: e.target.value })}>
                          {p.options.map((s) => (
                            <option key={s.id} value={s.id}>
                              {s.name} — {formatVND(s.basePrice)}/{s.unit}
                            </option>
                          ))}
                        </Select>
                      </Field>
                    )}
                    <div className="grid gap-3 sm:grid-cols-2">
                      <Field
                        label="Khảo sát / khách khai"
                        hint={l.surveyed && l.surveyed === surveyedFromRecord(l.type, order) ? 'Lấy từ biên bản khảo sát' : undefined}
                      >
                        <Input
                          value={l.surveyed}
                          onChange={(e) => patch(l.key, { surveyed: e.target.value })}
                          placeholder={meta.example.surveyed ? `VD: ${meta.example.surveyed}` : 'Không rõ / không ghi'}
                        />
                      </Field>
                      <Field label="Thực tế *" error={err('actual') ? 'Bắt buộc ghi thực tế' : null}>
                        <Input
                          value={l.actual}
                          invalid={err('actual')}
                          onChange={(e) => patch(l.key, { actual: e.target.value })}
                          placeholder={meta.example.actual ? `VD: ${meta.example.actual}` : 'Thực tế khác thế nào'}
                        />
                      </Field>
                    </div>
                    <div className={cx('grid gap-3', meta.mode === 'MANUAL' && 'sm:grid-cols-2')}>
                      <Field label={meta.qtyLabel}>
                        <div className="max-w-56">
                          <NumberStepper value={l.quantity} onChange={(v) => patch(l.key, { quantity: v })} min={1} max={100} suffix={p.unit || undefined} />
                        </div>
                      </Field>
                      {meta.mode === 'MANUAL' && (
                        <Field label="Đơn giá đề xuất (đ)" error={err('price') ? 'Nhập đơn giá' : null}>
                          <Input
                            inputMode="numeric"
                            value={l.manualPrice}
                            onChange={(e) => patch(l.key, { manualPrice: e.target.value.replace(/\D/g, '') })}
                            placeholder="VD: 200000"
                          />
                        </Field>
                      )}
                    </div>
                    <Field
                      label="Mô tả sai lệch *"
                      hint={meta.mode === 'MANUAL' ? 'Câu đầu tiên dùng làm tên dòng phí' : undefined}
                      error={err('description') ? 'Bắt buộc mô tả sai lệch' : null}
                    >
                      <textarea
                        value={l.description}
                        onChange={(ev) => patch(l.key, { description: ev.target.value })}
                        rows={2}
                        maxLength={2000}
                        placeholder="Sai lệch cụ thể là gì, ảnh hưởng thế nào tới công việc…"
                        className={cx(controlBase, 'h-auto min-h-[64px] py-2.5', err('description') && 'border-rose-400')}
                      />
                    </Field>
                    <Field label={`Ảnh bằng chứng * (${l.photos.length})`} error={err('photos') ? 'Cần ít nhất 1 ảnh' : null}>
                      <SitePhotoGrid photos={l.photos} onRemove={(id) => removePhoto(id, l.key)} addTile={addTile(l.key, Boolean(err('photos')))} />
                    </Field>
                    {multi && (
                    <p className="flex items-baseline justify-between rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-600">
                      <span className="tabular-nums">
                        Ước tính · {l.quantity} {p.unit} × {formatVND(p.unitPrice)}
                      </span>
                      <b className="text-sm text-slate-900 tabular-nums">+{formatVND(surchargeAmount({ quantity: l.quantity, unitPrice: p.unitPrice }))}</b>
                    </p>
                    )}
                  </div>
                </li>
              );
            })}
          </ol>
          <Button variant="soft" block icon={Plus} className="mt-3" onClick={() => setLines((ls) => [...ls, newLine('EXTRA_FLOOR', surveyedFromRecord('EXTRA_FLOOR', order))])}>
            Thêm sai lệch khác
          </Button>
        </div>

        <div className="rounded-xl bg-blue-50 px-4 py-3 text-blue-950">
          <div className="flex items-baseline justify-between gap-3">
            <span className="text-xs font-medium tabular-nums">
              {multi
                ? `Tổng ước tính · ${lines.length} dòng`
                : `Ước tính · ${priced[0]?.l.quantity ?? 0} ${priced[0]?.p.unit ?? ''} × ${formatVND(priced[0]?.p.unitPrice ?? 0)}`}
            </span>
            <span className="text-base font-bold tabular-nums">+{formatVND(total)}</span>
          </div>
          <p className="mt-1 text-xs leading-snug text-blue-800">
            Đơn giá theo bảng giá (loại "Khác" là giá đề xuất). Điều phối duyệt từng dòng rồi gửi khách xác nhận một lần — đội tạm dừng bước bắt đầu
            làm / di chuyển tới khi có kết quả.
          </p>
        </div>
      </div>
    </Modal>
  );
};
