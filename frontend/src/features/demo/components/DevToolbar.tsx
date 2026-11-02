import { useLocation, useNavigate } from 'react-router-dom';
import { useRef, useState } from 'react';
import {
  FlaskConical,
  Globe,
  Headset,
  KeyRound,
  type LucideIcon,
  RotateCcw,
  ShieldCheck,
  Truck,
  User,
  X,
} from 'lucide-react';
import { cx, Toggle } from '@/components/ui';
import { STATUS_META, STATUS_ORDER } from '@/constants/order';
import { ROLE_HOME, roleFromPath } from '@/routes/paths';
import { useClickOutside } from '@/layouts/shell';
import { formatVND } from '@/services/format';
import { waitFee } from '@/services/pricing';
import { useActiveOrder, useApp } from '@/services/store/AppStore';
import type { ExceptionFlags, OrderStatus, RoleType } from '@/types';

export const ROLES: { id: RoleType; label: string; icon: LucideIcon }[] = [
  { id: 'LANDING', label: 'Landing', icon: Globe },
  { id: 'AUTH', label: 'Đăng nhập', icon: KeyRound },
  { id: 'CUSTOMER', label: 'Khách hàng', icon: User },
  { id: 'STAFF', label: 'NV vận chuyển', icon: Truck },
  { id: 'COORDINATOR', label: 'Điều phối', icon: Headset },
  { id: 'ADMIN', label: 'Admin', icon: ShieldCheck },
];

export const FLAG_ROWS: { key: keyof Pick<ExceptionFlags, 'hasFieldDiscrepancyLock' | 'isVehicleIncident'>; code: string; label: string }[] = [
  { key: 'hasFieldDiscrepancyLock', code: '1.1.1', label: 'Sai lệch hiện trường' },
  { key: 'isVehicleIncident', code: '1.1.2', label: 'Sự cố xe hỏng' },
];

/** Công cụ demo nổi: chuyển vai trò & mô phỏng ngoại lệ — tách khỏi giao diện sản phẩm. */
export const DevToolbar = () => {
  const { state, dispatch } = useApp();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const order = useActiveOrder();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  useClickOutside(ref, open, () => setOpen(false));

  const setFlags = (patch: Partial<ExceptionFlags>) => order && dispatch({ type: 'SET_FLAGS', orderId: order.id, patch });
  const activeCount = order
    ? Number(order.flags.hasFieldDiscrepancyLock) + Number(order.flags.isVehicleIncident) + Number(order.flags.waitingMinutes > 30) + Number(order.flags.hoursUntilDeparture < 12)
    : 0;
  const currentRole = roleFromPath(pathname);
  const current = ROLES.find((r) => r.id === currentRole);
  const tag = (code: string) => <span className="mr-2 rounded bg-white/10 px-1.5 py-0.5 font-mono text-[10px] text-white/60">{code}</span>;

  return (
    <div ref={ref} className="fixed top-[42%] right-0 z-[55] md:top-auto md:right-5 md:bottom-5">
      {open && (
        <div className="anim-pop fixed inset-x-3 top-16 max-h-[calc(100dvh-160px)] overflow-y-auto rounded-2xl bg-obsidian p-4 text-white shadow-2xl ring-1 ring-white/10 md:absolute md:inset-x-auto md:top-auto md:right-0 md:bottom-full md:mb-2 md:max-h-[75vh] md:w-96">
          <div className="mb-3 flex items-center justify-between">
            <p className="text-[11px] font-semibold tracking-wider text-brand uppercase">Chế độ demo</p>
            <button type="button" onClick={() => setOpen(false)} className="rounded-md p-1 text-white/50 hover:bg-white/10 hover:text-white" aria-label="Đóng">
              <X className="size-4" />
            </button>
          </div>

          <p className="mb-2 text-xs text-white/50">Chuyển vai trò</p>
          <div className="grid grid-cols-3 gap-1.5">
            {ROLES.map((r) => (
              <button
                key={r.id}
                type="button"
                onClick={() => navigate(ROLE_HOME[r.id])}
                className={cx('flex flex-col items-center gap-1 rounded-xl py-2.5 text-[11px] font-semibold transition-colors', currentRole === r.id ? 'bg-white text-slate-900' : 'bg-white/5 text-white/70 hover:bg-white/10')}
              >
                <r.icon className="size-4" /> {r.label}
              </button>
            ))}
          </div>

          {order ? (
          <>
          <div className="mt-4 flex items-center justify-between gap-2">
            <p className="text-xs text-white/50">Mô phỏng ngoại lệ cho đơn</p>
            <select
              value={state.activeOrderId}
              onChange={(e) => dispatch({ type: 'SET_ACTIVE_ORDER', orderId: e.target.value })}
              className="h-8 rounded-lg border border-white/10 bg-white/5 px-2 text-xs font-semibold tabular-nums text-white focus:outline-none"
            >
              {state.orders.map((o) => (
                <option key={o.id} value={o.id} className="text-slate-900">#{o.id}</option>
              ))}
            </select>
          </div>
          <div className="mt-2 space-y-2.5 rounded-xl border border-white/10 bg-white/[0.03] p-3">
            {FLAG_ROWS.map((f) => (
              <div key={f.key} className="flex items-center justify-between gap-3">
                <span className="text-[13px] text-white/80">{tag(f.code)}{f.label}</span>
                <Toggle checked={order.flags[f.key]} onChange={(v) => setFlags({ [f.key]: v })} tone="danger" />
              </div>
            ))}
            <label className="block pt-1">
              <div className="mb-1.5 flex items-center justify-between text-[13px]">
                <span className="text-white/80">{tag('1.1.4')}Phút chờ khách</span>
                <span className="font-semibold tabular-nums">{order.flags.waitingMinutes}′ · <span className="text-brand">{formatVND(waitFee(order.flags.waitingMinutes))}</span></span>
              </div>
              <input type="range" min={0} max={240} step={5} value={order.flags.waitingMinutes} onChange={(e) => setFlags({ waitingMinutes: Number(e.target.value) })} className="w-full accent-[#FF5A1F]" />
            </label>
            <label className="block">
              <div className="mb-1.5 flex items-center justify-between text-[13px]">
                <span className="text-white/80">{tag('1.1.6/7')}Giờ tới khởi hành</span>
                <span className={cx('font-semibold tabular-nums', order.flags.hoursUntilDeparture < 12 ? 'text-rose-400' : 'text-emerald-400')}>{order.flags.hoursUntilDeparture}h</span>
              </div>
              <input type="range" min={0} max={72} step={1} value={order.flags.hoursUntilDeparture} onChange={(e) => setFlags({ hoursUntilDeparture: Number(e.target.value) })} className="w-full accent-[#FF5A1F]" />
            </label>
            <label className="block">
              <p className="mb-1.5 text-[13px] text-white/80">Nhảy trạng thái</p>
              <select
                value={order.status}
                onChange={(e) => dispatch({ type: 'JUMP_STATUS', orderId: order.id, status: e.target.value as OrderStatus })}
                className="h-9 w-full rounded-lg border border-white/10 bg-white/5 px-2 text-xs text-white focus:outline-none"
              >
                {STATUS_ORDER.map((s) => (
                  <option key={s} value={s} className="text-slate-900">{STATUS_META[s].label}</option>
                ))}
              </select>
            </label>
          </div>
          </>
          ) : (
            <p className="mt-4 rounded-xl border border-white/10 bg-white/[0.03] p-3 text-xs text-white/60">Chưa có đơn hàng. Hãy tạo đơn bằng tài khoản khách hàng để mô phỏng ngoại lệ.</p>
          )}
          <button type="button" onClick={() => dispatch({ type: 'RESET_DEMO' })} className="mt-3 inline-flex h-9 w-full items-center justify-center gap-2 rounded-lg border border-white/10 text-[13px] font-semibold text-white/70 hover:bg-white/5 hover:text-white">
            <RotateCcw className="size-4" /> Xóa toàn bộ dữ liệu phiên
          </button>
        </div>
      )}
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className={cx('relative inline-flex h-11 items-center gap-2 rounded-l-xl pr-2 pl-2.5 text-xs font-semibold shadow-xl ring-1 transition-colors md:h-10 md:rounded-full md:pr-3.5 md:pl-3', open ? 'bg-brand text-white ring-brand' : 'bg-obsidian/90 text-white ring-white/15 backdrop-blur hover:bg-obsidian')}
        aria-expanded={open}
      >
        <FlaskConical className="size-4" />
        <span className="hidden md:inline">Demo · {current?.label}</span>
        {activeCount > 0 && <span className="absolute -top-1.5 -left-1.5 grid size-5 place-items-center rounded-full bg-rose-500 text-[10px] tabular-nums md:static">{activeCount}</span>}
      </button>
    </div>
  );
};
