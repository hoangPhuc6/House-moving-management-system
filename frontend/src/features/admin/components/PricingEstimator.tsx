import { useState } from 'react';
import { Calculator } from 'lucide-react';
import { Card, CardHeader, cx, Field, NumberStepper, Select } from '@/components/ui';
import { VEHICLE_PRICING } from '@/constants/pricing';
import { formatVND } from '@/services/format';
import { buildSurveyQuote, lineTotal, quoteSubtotal } from '@/services/pricing';
import { useCatalog } from '@/services/store/CatalogStore';
import type { SurveyInput } from '@/types';

/** Bảng phân rã chi phí — dùng chung cho mô phỏng của Admin và bảng giá tham khảo của khách. */
export const PricingEstimator = ({ reference = false }: { reference?: boolean }) => {
  const { catalog } = useCatalog();
  const [input, setInput] = useState<SurveyInput>({ distanceKm: 10, vehicleCapacity: 2, vehicleQty: 1, workers: 3, cartons: 20, tapeRolls: 4, peFilmRolls: 2, floors: 0, alleyMeters: 0 });
  const [discount, setDiscount] = useState(0);
  const [extraIds, setExtraIds] = useState<string[]>([]);
  const set = <K extends keyof SurveyInput>(k: K, v: SurveyInput[K]) => setInput((p) => ({ ...p, [k]: v }));

  const addOns = catalog.services.filter((s) => s.group === 'ASSEMBLY');
  const base = buildSurveyQuote(input);
  const extras = addOns.filter((s) => s.active && extraIds.includes(s.id));
  const transport = quoteSubtotal(base.filter((i) => i.category === 'VEHICLE'));
  const additional = quoteSubtotal(base.filter((i) => i.category === 'LABOR' || i.category === 'MATERIAL')) + extras.reduce((s, e) => s + e.basePrice, 0);
  const surcharge = quoteSubtotal(base.filter((i) => i.category === 'SURCHARGE'));
  const subtotal = transport + additional + surcharge;
  const discountAmt = Math.round((subtotal * discount) / 100);
  const total = subtotal - discountAmt;

  return (
    <Card>
      <CardHeader
        title={reference ? 'Ước tính giá tham khảo' : 'Mô phỏng tính giá (Pricing Engine)'}
        icon={Calculator}
        subtitle={reference ? 'Giá chính thức được báo sau khi khảo sát thực tế.' : 'Cùng bộ đầu vào luôn cho cùng một kết quả; trả về từng khoản.'}
      />
      <div className="grid gap-5 lg:grid-cols-2">
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <Field label="Loại xe">
              <Select value={input.vehicleCapacity} onChange={(e) => set('vehicleCapacity', Number(e.target.value))}>
                {Object.entries(VEHICLE_PRICING).map(([cap, v]) => <option key={cap} value={cap}>{v.label}</option>)}
              </Select>
            </Field>
            <Field label="Khoảng cách (km)"><NumberStepper value={input.distanceKm} onChange={(n) => set('distanceKm', n)} max={500} /></Field>
            <Field label="Nhân viên bốc xếp"><NumberStepper value={input.workers} onChange={(n) => set('workers', n)} max={20} /></Field>
            <Field label="Thùng carton"><NumberStepper value={input.cartons} onChange={(n) => set('cartons', n)} max={300} /></Field>
            <Field label="Số lầu khiêng bộ"><NumberStepper value={input.floors} onChange={(n) => set('floors', n)} max={20} /></Field>
            <Field label="Quãng hẻm (m)"><NumberStepper value={input.alleyMeters} onChange={(n) => set('alleyMeters', n)} step={50} max={500} /></Field>
          </div>
          <div>
            <p className="mb-2 text-[13px] font-semibold text-slate-700">Dịch vụ bổ sung</p>
            <div className="flex flex-wrap gap-2">
              {addOns.map((s) => (
                <button
                  key={s.id}
                  type="button"
                  disabled={!s.active}
                  onClick={() => setExtraIds((ids) => (ids.includes(s.id) ? ids.filter((x) => x !== s.id) : [...ids, s.id]))}
                  className={cx('rounded-full border px-3 py-2 text-xs font-semibold disabled:cursor-not-allowed disabled:opacity-40', extraIds.includes(s.id) && s.active ? 'border-brand bg-orange-50 text-brand' : 'border-slate-200 text-slate-600 hover:border-slate-300')}
                  title={s.active ? undefined : 'Dịch vụ đã ngừng hoạt động'}
                >
                  {s.name} · {formatVND(s.basePrice)}
                </button>
              ))}
            </div>
          </div>
          {!reference && (
            <Field label="Giảm giá toàn đơn (%)" hint={discount > 0 ? `−${formatVND(discountAmt)}` : undefined}>
              <NumberStepper value={discount} onChange={(n) => setDiscount(Math.min(100, Math.max(0, n)))} max={100} step={5} suffix="%" />
            </Field>
          )}
        </div>
        <div className="rounded-2xl border border-slate-200 bg-slate-50/60 p-4 text-sm">
          <ul className="space-y-1.5">
            {[...base].map((i) => (
              <li key={i.id} className="flex justify-between gap-3"><span className="text-slate-600">{i.name} <span className="text-slate-400">×{i.quantity}</span></span><span className="font-medium tabular-nums">{formatVND(lineTotal(i))}</span></li>
            ))}
            {extras.map((e) => <li key={e.id} className="flex justify-between gap-3"><span className="text-slate-600">{e.name}</span><span className="font-medium tabular-nums">{formatVND(e.basePrice)}</span></li>)}
          </ul>
          <div className="my-3 h-px bg-slate-200" />
          <dl className="space-y-1.5">
            <div className="flex justify-between"><dt className="text-slate-500">Phí vận chuyển</dt><dd className="tabular-nums">{formatVND(transport)}</dd></div>
            <div className="flex justify-between"><dt className="text-slate-500">Dịch vụ bổ sung</dt><dd className="tabular-nums">{formatVND(additional)}</dd></div>
            <div className="flex justify-between"><dt className="text-slate-500">Phụ phí</dt><dd className="tabular-nums">{formatVND(surcharge)}</dd></div>
            {!reference && <div className="flex justify-between"><dt className="text-slate-500">Giảm giá</dt><dd className="tabular-nums text-emerald-600">−{formatVND(discountAmt)}</dd></div>}
            <div className="flex items-baseline justify-between border-t border-slate-200 pt-2.5"><dt className="font-semibold">{reference ? 'Tổng dự kiến' : 'Tổng cuối'}</dt><dd className="text-xl font-bold tabular-nums">{formatVND(total)}</dd></div>
          </dl>
        </div>
      </div>
    </Card>
  );
};
