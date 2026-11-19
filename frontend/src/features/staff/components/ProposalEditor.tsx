import { type ReactNode, useMemo, useState } from 'react';
import { Truck, X } from 'lucide-react';
import { cx, NumberStepper } from '@/components/ui';
import { capacityLabel, qtyOf, setVehicleQty, VEHICLE_CAPACITIES } from '@/constants/pricing';
import { PROPOSAL_FIELDS, type ProposalField } from '@/features/order/proposal';
import { Block } from '@/features/staff/components/Block';
import { removeBtnCls, SuggestChip } from '@/features/order/components/surveyUi';
import { isQuoteExtraService } from '@/services/pricing';
import { useCatalog } from '@/services/store/CatalogStore';
import type { SurveyExtra, SurveyInput } from '@/types';

const inputCls = 'h-9 min-w-0 rounded-lg border border-slate-200 bg-white px-2.5 text-base focus:border-brand focus:outline-none sm:text-sm';

const Chip = SuggestChip;

const RemoveBtn = ({ onClick, label }: { onClick: () => void; label: string }) => (
  <button type="button" onClick={onClick} aria-label={label} className={removeBtnCls}>
    <X className="size-4" />
  </button>
);

const Row = ({ label, hint, children, onRemove }: { label: ReactNode; hint?: string; children: ReactNode; onRemove?: () => void }) => (
  <div className="flex items-center gap-2 py-2.5">
    <div className="min-w-0 flex-1">
      <p className="text-sm font-medium text-slate-700">{label}</p>
      {hint && <p className="text-xs text-slate-500">{hint}</p>}
    </div>
    <div className="w-40 shrink-0">{children}</div>
    {onRemove ? <RemoveBtn onClick={onRemove} label="Bỏ mục này" /> : <span className="w-9 shrink-0" />}
  </div>
);

const GroupTitle = ({ children }: { children: ReactNode }) => <p className="pt-3 pb-1 text-xs font-semibold tracking-wide text-slate-500 uppercase">{children}</p>;

/** Gợi ý hạng mục nhập tay hay gặp khi khảo sát (ngoài bảng giá — Điều phối định giá). */
const COMMON_CUSTOM_EXTRAS: Pick<SurveyExtra, 'name' | 'unit'>[] = [
  { name: 'Cẩu / kéo đồ qua ban công', unit: 'lần' },
  { name: 'Đăng ký thang máy hàng', unit: 'lần' },
  { name: 'Đóng thùng gỗ đồ dễ vỡ', unit: 'thùng' },
  { name: 'Vận chuyển đàn piano', unit: 'chiếc' },
];

/**
 * Đề xuất xe, nhân sự, vật tư & điều kiện lối đi của người khảo sát.
 * Chỉ các mục bắt buộc hiện sẵn; mục khác thêm từng cái qua gợi ý, bỏ đi thì về 0 và ẩn.
 */
export const ProposalEditor = ({
  value: p,
  onChange,
  errors,
  distanceLocked = false,
}: {
  value: SurveyInput;
  onChange: (next: SurveyInput) => void;
  errors: { vehicles: boolean; hours: boolean };
  /** Quãng đường do hệ thống tính — chỉ hiển thị. */
  distanceLocked?: boolean;
}) => {
  const { catalog } = useCatalog();
  const set = <K extends keyof SurveyInput>(k: K, v: SurveyInput[K]) => onChange({ ...p, [k]: v });

  // Ô tùy chọn đang hiện: có sẵn giá trị (điền từ thông tin khách) hoặc người khảo sát vừa thêm.
  const [shown, setShown] = useState<string[]>(() => PROPOSAL_FIELDS.filter((f) => f.get(p) > 0).map((f) => f.key));
  const addField = (f: ProposalField) => {
    setShown((xs) => [...xs, f.key]);
    if (f.get(p) === 0) onChange(f.set(p, f.initial));
  };
  const removeField = (f: ProposalField) => {
    setShown((xs) => xs.filter((k) => k !== f.key));
    onChange(f.set(p, 0));
  };

  const vehicleLines = p.vehicles.filter((l) => l.qty > 0).sort((a, b) => b.capacity - a.capacity);
  const missingCaps = VEHICLE_CAPACITIES.filter((cap) => qtyOf(p.vehicles, cap) === 0);

  const extras = p.extras ?? [];
  const setExtras = (xs: SurveyExtra[]) => set('extras', xs);
  const patchExtra = (i: number, patch: Partial<SurveyExtra>) => setExtras(extras.map((x, j) => (j === i ? { ...x, ...patch } : x)));
  const services = useMemo(() => catalog.services.filter(isQuoteExtraService), [catalog.services]);
  // Tháo lắp đã đánh dấu theo từng món đồ — không gợi ý lại ở đây.
  const serviceSuggestions = services.filter((s) => s.group !== 'ASSEMBLY' && !extras.some((x) => x.serviceId === s.id));
  const customSuggestions = COMMON_CUSTOM_EXTRAS.filter((c) => !extras.some((x) => x.name.toLowerCase() === c.name.toLowerCase()));

  const fieldsOf = (group: ProposalField['group']) => PROPOSAL_FIELDS.filter((f) => f.group === group);
  const renderGroup = (group: ProposalField['group'], title: string) => {
    const all = fieldsOf(group);
    const active = all.filter((f) => shown.includes(f.key));
    const rest = all.filter((f) => !shown.includes(f.key));
    return (
      <div>
        <GroupTitle>{title}</GroupTitle>
        {active.length > 0 && (
          <div className="divide-y divide-slate-100">
            {active.map((f) => (
              <Row key={f.key} label={f.label} hint={f.hint} onRemove={() => removeField(f)}>
                <NumberStepper value={f.get(p)} onChange={(v) => onChange(f.set(p, v))} max={f.max} step={f.step} suffix={f.unit} />
              </Row>
            ))}
          </div>
        )}
        {rest.length > 0 && (
          <div className="mt-1.5 flex flex-wrap gap-1.5">
            {rest.map((f) => (
              <Chip key={f.key} onClick={() => addField(f)}>
                {f.label}
              </Chip>
            ))}
          </div>
        )}
      </div>
    );
  };

  return (
    <Block title="Đề xuất xe & nhân sự" icon={Truck}>
      <div className="divide-y divide-slate-100">
        {distanceLocked ? (
          <Row label="Quãng đường" hint="Hệ thống tự tính từ địa chỉ điểm đi & điểm đến">
            <div className="flex h-11 items-center justify-center rounded-xl bg-slate-50 text-sm font-semibold text-slate-700 tabular-nums">
              {p.distanceKm} <span className="ml-1 font-medium text-slate-400">km</span>
            </div>
          </Row>
        ) : (
          <Row label="Quãng đường" hint="Đơn chưa có quãng đường tự tính — ước lượng giúp Điều phối">
            <NumberStepper value={p.distanceKm} onChange={(v) => set('distanceKm', v)} min={1} max={200} suffix="km" />
          </Row>
        )}
        <div>
          <Row label="Thời gian dự kiến *">
            <NumberStepper value={p.estimatedHours} onChange={(v) => set('estimatedHours', v)} max={24} step={0.5} suffix="giờ" />
          </Row>
          <p className={cx('-mt-1 pb-2 text-xs', errors.hours ? 'font-medium text-rose-600' : 'text-slate-500')}>
            {errors.hours ? 'Bắt buộc — ' : ''}Tổng thời gian bốc xếp + di chuyển + dỡ đồ. Xe & người được giữ lịch trong khoảng này.
          </p>
        </div>
        <Row label="Nhân viên bốc xếp">
          <NumberStepper value={p.workers} onChange={(v) => set('workers', v)} min={0} max={12} suffix="người" />
        </Row>
      </div>

      <GroupTitle>Xe *</GroupTitle>
      {vehicleLines.length > 0 && (
        <div className="divide-y divide-slate-100">
          {vehicleLines.map((l) => (
            <Row key={l.capacity} label={`Xe ${String(l.capacity).replace('.', ',')} tấn`} onRemove={() => set('vehicles', setVehicleQty(p.vehicles, l.capacity, 0))}>
              <NumberStepper value={l.qty} onChange={(v) => set('vehicles', setVehicleQty(p.vehicles, l.capacity, v))} min={1} max={5} suffix="xe" />
            </Row>
          ))}
        </div>
      )}
      {missingCaps.length > 0 && (
        <div className="mt-1.5 flex flex-wrap gap-1.5">
          {missingCaps.map((cap) => (
            <Chip key={cap} onClick={() => set('vehicles', setVehicleQty(p.vehicles, cap, 1))}>
              {capacityLabel(cap).replace(/^xe/, 'Xe')}
            </Chip>
          ))}
        </div>
      )}
      {errors.vehicles && <p className="mt-1 text-xs font-medium text-rose-600">Đề xuất ít nhất một xe</p>}

      {renderGroup('MATERIAL', 'Vật tư đóng gói')}

      <GroupTitle>Hạng mục đề xuất thêm</GroupTitle>
      {extras.length > 0 && (
        <ul className="divide-y divide-slate-100 rounded-xl border border-slate-200">
          {extras.map((x, i) => (
            <li key={i} className="space-y-1.5 px-2.5 py-2">
              <div className="flex items-center gap-1.5">
                {x.serviceId ? (
                  <span className="min-w-0 flex-1 truncate text-sm font-medium text-slate-800">{x.name}</span>
                ) : (
                  <input
                    value={x.name}
                    onChange={(e) => patchExtra(i, { name: e.target.value })}
                    placeholder="Tên hạng mục"
                    className={cx(inputCls, 'flex-1')}
                  />
                )}
                <div className="w-32 shrink-0">
                  <NumberStepper value={x.qty} onChange={(v) => patchExtra(i, { qty: v })} min={1} max={50} suffix={x.unit} />
                </div>
                <RemoveBtn onClick={() => setExtras(extras.filter((_, j) => j !== i))} label="Bỏ hạng mục này" />
              </div>
              <div className="flex gap-1.5">
                {!x.serviceId && (
                  <input value={x.unit} onChange={(e) => patchExtra(i, { unit: e.target.value })} placeholder="Đơn vị" className={cx(inputCls, 'w-24')} />
                )}
                <input
                  value={x.note}
                  onChange={(e) => patchExtra(i, { note: e.target.value })}
                  placeholder={x.serviceId ? 'Ghi chú (VD: tủ 3 cánh, 2 máy lạnh treo tường)' : 'Lý do / mô tả cho Điều phối định giá'}
                  className={cx(inputCls, 'flex-1')}
                />
              </div>
              {!x.serviceId && <p className="text-[11px] text-slate-500">Ngoài bảng giá — Điều phối định giá khi lập báo giá.</p>}
            </li>
          ))}
        </ul>
      )}
      <div className="mt-1.5 flex flex-wrap gap-1.5">
        {serviceSuggestions.map((s) => (
          <Chip key={s.id} onClick={() => setExtras([...extras, { serviceId: s.id, name: s.name, qty: 1, unit: s.unit, note: '' }])}>
            {s.name}
          </Chip>
        ))}
        {customSuggestions.map((c) => (
          <Chip key={c.name} onClick={() => setExtras([...extras, { serviceId: null, name: c.name, qty: 1, unit: c.unit, note: '' }])}>
            {c.name}
          </Chip>
        ))}
        <Chip onClick={() => setExtras([...extras, { serviceId: null, name: '', qty: 1, unit: 'gói', note: '' }])}>Khác…</Chip>
      </div>
    </Block>
  );
};
