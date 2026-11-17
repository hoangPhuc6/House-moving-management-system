import { Check } from 'lucide-react';
import { cx } from '@/components/ui';
import { INCLUDED_KM, VEHICLE_PRICING } from '@/constants/pricing';
import { SERVICE_MODES } from '@/features/customer/booking/constants';
import { formatVND } from '@/services/format';
import type { ServiceMode } from '@/types';

const MODES: ServiceMode[] = ['FULL_PACKAGE', 'TRUCK_ONLY'];

/** Bảng giá xe lấy từ đơn giá hệ thống dùng để lập báo giá. */
const VEHICLES = Object.entries(VEHICLE_PRICING)
  .map(([tons, v]) => ({ tons: Number(tons), ...v }))
  .sort((a, b) => a.tons - b.tons);

export const Pricing = ({ onBook }: { onBook: () => void }) => (
  <section id="pricing" className="scroll-mt-20 bg-white py-20 sm:py-28">
    <div className="mx-auto max-w-7xl px-4 sm:px-6">
      <div className="mx-auto mb-12 max-w-2xl text-center">
        <p className="text-sm font-semibold text-brand">Dịch vụ & bảng giá</p>
        <h2 className="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">Hai gói dịch vụ, giá tính từng dòng</h2>
        <p className="mt-4 text-slate-500">Giá cuối cùng được chốt sau khảo sát. Mọi phát sinh đều cần bạn xác nhận trước.</p>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        {MODES.map((mode, i) => {
          const m = SERVICE_MODES[mode];
          const Icon = m.icon;
          const dark = i === 0;
          return (
            <div key={mode} className={cx('relative flex flex-col rounded-3xl border p-6 sm:p-8', dark ? 'border-slate-900 bg-obsidian text-white shadow-2xl shadow-slate-900/20' : 'border-slate-200 bg-canvas')}>
              <span className="absolute -top-3 left-6 rounded-full bg-brand px-3 py-1 text-xs font-semibold text-white">{m.badge}</span>
              <span className={cx('grid size-12 place-items-center rounded-2xl', dark ? 'bg-white/10' : 'bg-white ring-1 ring-slate-200')}>
                <Icon className="size-6 text-brand" />
              </span>
              <p className="mt-5 text-xl font-semibold">{m.title}</p>
              <p className={cx('mt-2 text-sm leading-relaxed', dark ? 'text-white/60' : 'text-slate-500')}>{m.desc}</p>
              <ul className="mt-6 flex-1 space-y-3 text-sm">
                {m.perks.map((perk) => (
                  <li key={perk} className="flex gap-2.5"><Check className="size-4 shrink-0 translate-y-0.5 text-brand" /> <span className={dark ? 'text-white/80' : 'text-slate-600'}>{perk}</span></li>
                ))}
              </ul>
              <button type="button" onClick={onBook} className={cx('mt-8 h-12 rounded-xl font-semibold transition-colors', dark ? 'bg-brand text-white hover:bg-brand-hover' : 'border border-slate-200 bg-white text-slate-900 hover:bg-slate-50')}>
                Đặt lịch {mode === 'FULL_PACKAGE' ? 'trọn gói' : 'thuê xe'}
              </button>
            </div>
          );
        })}
      </div>

      <div className="mt-4 overflow-hidden rounded-3xl border border-slate-200">
        <div className="border-b border-slate-200 bg-canvas px-6 py-4">
          <p className="font-semibold">Bảng giá xe tham khảo</p>
          <p className="text-sm text-slate-500">Giá chuyến đã gồm {INCLUDED_KM} km đầu tiên.</p>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[520px] text-sm">
            <thead className="text-left text-xs text-slate-500">
              <tr>
                <th className="px-6 py-3 font-medium">Loại xe</th>
                <th className="px-6 py-3 text-right font-medium">Giá chuyến</th>
                <th className="px-6 py-3 text-right font-medium">Km vượt</th>
                <th className="px-6 py-3 text-right font-medium">Bốc xếp / người</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {VEHICLES.map((v) => (
                <tr key={v.tons}>
                  <td className="px-6 py-3 font-semibold">{v.label}</td>
                  <td className="px-6 py-3 text-right tabular-nums">{formatVND(v.perTrip)}</td>
                  <td className="px-6 py-3 text-right tabular-nums">{formatVND(v.perKm)} / km</td>
                  <td className="px-6 py-3 text-right tabular-nums">{formatVND(v.laborPerPerson)} <span className="text-slate-400">(tối đa {v.maxLabor})</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </section>
);
