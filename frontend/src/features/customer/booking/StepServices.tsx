import { CheckCircle2, Info, Layers, ShieldCheck, Sparkles, Truck } from 'lucide-react';
import { Badge, Toggle, cx } from '@/components/ui';
import { SERVICE_GROUP_LABEL } from '@/constants/catalog';
import { formatVND } from '@/services/format';
import type { ServiceGroup, ServiceMode } from '@/types';
import { SERVICE_MODES } from './constants';
import { SectionCard, choiceClass } from './parts';
import type { BookingWizard } from './useBookingWizard';

const ADD_ON_GROUPS: ServiceGroup[] = ['PACKING', 'ASSEMBLY', 'SURCHARGE'];

export const StepServices = ({ wizard }: { wizard: BookingWizard }) => {
  const { draft, update, activeServices } = wizard;
  const vehicles = activeServices.filter((s) => s.group === 'TRANSPORT');
  const toggleAddOn = (name: string, on: boolean) =>
    update('addOns', on ? [...draft.addOns, name] : draft.addOns.filter((n) => n !== name));

  return (
    <div className="space-y-4">
      <SectionCard
        icon={Layers}
        tone="brand"
        title="Nhóm dịch vụ cốt lõi"
        subtitle="Chọn 1 hình thức chính phù hợp với nhu cầu của bạn"
        action={<Badge tone="neutral">Chọn 1</Badge>}
      >
        <div className="grid gap-3 md:grid-cols-2" role="radiogroup" aria-label="Hình thức chuyển nhà">
          {(Object.keys(SERVICE_MODES) as ServiceMode[]).map((m) => {
            const meta = SERVICE_MODES[m];
            const sel = draft.serviceMode === m;
            return (
              <button key={m} type="button" role="radio" aria-checked={sel} onClick={() => update('serviceMode', m)} className={cx(choiceClass(sel), 'flex flex-col p-5')}>
                <div className="flex w-full items-start justify-between gap-3">
                  <span className={cx('grid size-11 place-items-center rounded-xl', sel ? 'bg-brand text-white' : 'bg-slate-100 text-slate-500')}>
                    <meta.icon className="size-5" />
                  </span>
                  <span className="flex items-center gap-2">
                    <Badge tone={m === 'FULL_PACKAGE' ? 'brand' : 'neutral'}>{meta.badge}</Badge>
                    <span className={cx('grid size-5 place-items-center rounded-full border-2', sel ? 'border-brand' : 'border-slate-300')}>
                      {sel && <span className="size-2.5 rounded-full bg-brand" />}
                    </span>
                  </span>
                </div>
                <p className="mt-4 text-base font-semibold text-slate-900">{meta.title}</p>
                <p className="mt-1 text-sm leading-relaxed text-slate-500">{meta.desc}</p>
                <ul className="mt-3 space-y-1.5">
                  {meta.perks.map((p) => (
                    <li key={p} className="flex items-start gap-2 text-[13px] text-slate-600">
                      <CheckCircle2 className={cx('mt-0.5 size-4 shrink-0', sel ? 'text-brand' : 'text-slate-400')} /> {p}
                    </li>
                  ))}
                </ul>
                <p className="mt-4 w-full rounded-xl bg-slate-50 px-3 py-2 text-xs text-slate-500">
                  Chi phí dịch vụ: <span className="font-semibold text-slate-800">Báo giá sau khảo sát thực tế</span>
                </p>
              </button>
            );
          })}
        </div>

        {vehicles.length > 0 && (
          <div>
            <p className="mb-2 flex items-center gap-2 text-[13px] font-semibold text-slate-700">
              <Truck className="size-4 text-slate-500" /> Loại xe mong muốn
            </p>
            <div className="flex flex-wrap gap-2">
              {[{ id: 'auto', name: '', label: 'Để MoveFlow đề xuất' }, ...vehicles.map((v) => ({ id: v.id, name: v.name, label: v.name }))].map((v) => {
                const sel = draft.vehicleService === v.name;
                return (
                  <button
                    key={v.id}
                    type="button"
                    aria-pressed={sel}
                    onClick={() => update('vehicleService', v.name)}
                    className={cx(
                      'rounded-full border px-3.5 py-1.5 text-xs font-semibold transition-colors',
                      sel ? 'border-slate-900 bg-slate-900 text-white' : 'border-slate-200 text-slate-600 hover:border-slate-300 hover:text-slate-900',
                    )}
                  >
                    {v.label}
                  </button>
                );
              })}
            </div>
            <p className="mt-2 text-xs text-slate-500">Nếu để hệ thống đề xuất, tải trọng xe được chọn theo khối lượng đồ đạc bạn khai báo ở Bước 3.</p>
          </div>
        )}
      </SectionCard>

      <SectionCard icon={Sparkles} title="Nhóm tiện ích bổ sung chuyên sâu" subtitle="Có thể chọn nhiều mục — giá tham khảo theo bảng giá hiện hành" action={<Badge tone="neutral">Chọn nhiều</Badge>}>
        {ADD_ON_GROUPS.map((g) => {
          const list = activeServices.filter((s) => s.group === g);
          if (list.length === 0) return null;
          return (
            <div key={g}>
              <p className="mb-2 text-[11px] font-semibold uppercase tracking-wider text-slate-400">{SERVICE_GROUP_LABEL[g]}</p>
              <ul className="space-y-2">
                {list.map((s) => {
                  const on = draft.addOns.includes(s.name);
                  return (
                    <li key={s.id} className={cx('flex items-start gap-3 rounded-xl border p-3.5 transition-colors', on ? 'border-brand/40 bg-orange-50/40' : 'border-slate-200')}>
                      <div className="min-w-0 flex-1">
                        <p className="text-sm font-semibold text-slate-900">{s.name}</p>
                        <p className="mt-0.5 text-xs leading-relaxed text-slate-500">{s.description}</p>
                        <p className="mt-1.5 flex items-center gap-1 text-[11px] font-medium text-slate-400">
                          <Info className="size-3" /> Từ {formatVND(s.basePrice)} / {s.unit} — chốt sau khảo sát
                        </p>
                      </div>
                      <Toggle checked={on} onChange={(v) => toggleAddOn(s.name, v)} />
                    </li>
                  );
                })}
              </ul>
            </div>
          );
        })}
        {draft.serviceMode === 'FULL_PACKAGE' && (
          <p className="flex items-center gap-2 rounded-xl bg-slate-50 px-3 py-2 text-xs text-slate-600">
            <CheckCircle2 className="size-4 shrink-0 text-emerald-600" /> Gói trọn gói đã gồm nhân viên bốc xếp — không cần chọn thêm.
          </p>
        )}
      </SectionCard>

      <div className="flex items-start gap-4 rounded-2xl border border-emerald-200 bg-emerald-50/70 p-5">
        <span className="grid size-11 shrink-0 place-items-center rounded-xl bg-white text-emerald-600 ring-1 ring-emerald-200">
          <ShieldCheck className="size-5" />
        </span>
        <div className="text-sm">
          <p className="font-semibold text-emerald-950">Bảo hiểm tài sản 100% khi sử dụng đóng gói trọn gói</p>
          <p className="mt-0.5 text-emerald-900/80">Mọi đồ dễ vỡ, linh kiện máy móc được đóng gói bởi kỹ thuật viên MoveFlow đều nằm trong danh mục bảo vệ, bồi thường theo biên bản nếu xảy ra sự cố.</p>
        </div>
      </div>
    </div>
  );
};
