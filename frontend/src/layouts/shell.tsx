import { useNavigate } from 'react-router-dom';
import { type RefObject, useEffect, useMemo, useRef, useState } from 'react';
import {
  Bell,
  ChevronDown,
  CircleHelp,
  LogOut,
  type LucideIcon,
  Settings,
  UserRound,
} from 'lucide-react';
import { cx } from '@/components/ui';
import { ROUTES } from '@/routes/paths';
import { formatTime } from '@/services/format';
import { useApp } from '@/services/store/AppStore';
import type { AuditLogEntry } from '@/types';

export const useClickOutside = (ref: RefObject<HTMLElement | null>, open: boolean, onClose: () => void) => {
  useEffect(() => {
    if (!open) return undefined;
    const h = (e: MouseEvent | TouchEvent) => {
      if (ref.current && e.target instanceof Node && !ref.current.contains(e.target)) onClose();
    };
    document.addEventListener('mousedown', h);
    document.addEventListener('touchstart', h);
    return () => {
      document.removeEventListener('mousedown', h);
      document.removeEventListener('touchstart', h);
    };
  }, [ref, open, onClose]);
};

export interface AppUser {
  name: string;
  title: string;
  initials: string;
}

export const ROLE_DOT: Record<AuditLogEntry['actorRole'], string> = {
  CUSTOMER: 'bg-blue-500',
  STAFF: 'bg-brand',
  COORDINATOR: 'bg-emerald-500',
  ADMIN: 'bg-violet-500',
  SYSTEM: 'bg-slate-400',
};

export const NotificationBell = ({ orderIds, dark = false }: { orderIds?: string[]; dark?: boolean }) => {
  const { state } = useApp();
  const [open, setOpen] = useState(false);
  const [lastSeen, setLastSeen] = useState(0);
  const ref = useRef<HTMLDivElement>(null);
  useClickOutside(ref, open, () => setOpen(false));

  const items = useMemo(() => {
    const scope = orderIds ? state.orders.filter((o) => orderIds.includes(o.id)) : state.orders;
    return scope
      .flatMap((o) => o.auditLog.map((l) => ({ ...l, orderId: o.id })))
      .sort((a, b) => b.timestamp - a.timestamp)
      .slice(0, 12);
  }, [state.orders, orderIds]);
  const unread = items.filter((i) => i.timestamp > lastSeen).length;

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => {
          setOpen((v) => !v);
          setLastSeen(Date.now());
        }}
        className={cx(
          'relative grid size-10 place-items-center rounded-xl transition-colors',
          dark ? 'text-white/80 hover:bg-white/10' : 'border border-slate-200 bg-white text-slate-600 hover:bg-slate-50',
        )}
        aria-label={`Thông báo${unread ? ` (${unread} chưa đọc)` : ''}`}
      >
        <Bell className="size-[18px]" />
        {unread > 0 && (
          <span className={cx('absolute -top-1 -right-1 grid h-5 min-w-5 place-items-center rounded-full bg-brand px-1 text-[10px] font-bold text-white tabular-nums ring-2', dark ? 'ring-[#0F172A]' : 'ring-white')}>
            {unread > 9 ? '9+' : unread}
          </span>
        )}
      </button>
      {open && (
        <div className="anim-pop fixed inset-x-3 top-16 z-50 overflow-hidden rounded-2xl border border-slate-200 bg-white text-slate-900 shadow-2xl sm:absolute sm:inset-x-auto sm:top-12 sm:right-0 sm:w-96">
          <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
            <p className="text-sm font-semibold">Thông báo</p>
            <span className="text-xs text-slate-400">{items.length} hoạt động gần đây</span>
          </div>
          <ul className="max-h-[60vh] divide-y divide-slate-50 overflow-y-auto">
            {items.map((n) => (
              <li key={n.id} className="flex gap-3 px-4 py-3 hover:bg-slate-50">
                <span className={cx('mt-1.5 size-2 shrink-0 rounded-full', ROLE_DOT[n.actorRole])} />
                <div className="min-w-0">
                  <p className="text-sm font-medium text-slate-900">{n.action}</p>
                  <p className="line-clamp-2 text-xs text-slate-500">{n.detail}</p>
                  <p className="mt-1 text-[11px] text-slate-400 tabular-nums">#{n.orderId} · {formatTime(n.timestamp)}</p>
                </div>
              </li>
            ))}
            {items.length === 0 && <li className="px-4 py-8 text-center text-sm text-slate-400">Chưa có thông báo.</li>}
          </ul>
        </div>
      )}
    </div>
  );
};

export interface UserMenuItem {
  label: string;
  icon: LucideIcon;
  onClick?: () => void;
  danger?: boolean;
}

export const UserMenu = ({ user, dark = false, showName = false, items: customItems }: { user: AppUser; dark?: boolean; showName?: boolean; items?: UserMenuItem[] }) => {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  useClickOutside(ref, open, () => setOpen(false));

  const items: UserMenuItem[] = customItems ?? [
    { label: 'Hồ sơ cá nhân', icon: UserRound },
    { label: 'Cài đặt', icon: Settings },
    { label: 'Trung tâm trợ giúp', icon: CircleHelp },
    { label: 'Đăng xuất', icon: LogOut, danger: true, onClick: () => navigate(ROUTES.login) },
  ];

  return (
    <div ref={ref} className="relative">
      <button type="button" onClick={() => setOpen((v) => !v)} className={cx('flex items-center gap-2 rounded-xl p-1 pr-1.5 transition-colors', dark ? 'hover:bg-white/10' : 'hover:bg-slate-100')} aria-label="Tài khoản">
        <span className="grid size-8 place-items-center rounded-full bg-gradient-to-br from-brand to-orange-400 text-xs font-bold text-white">{user.initials}</span>
        {showName && (
          <span className="hidden text-left md:block">
            <span className={cx('block text-sm leading-tight font-semibold', dark ? 'text-white' : 'text-slate-900')}>{user.name}</span>
            <span className={cx('block text-[11px] leading-tight', dark ? 'text-white/50' : 'text-slate-500')}>{user.title}</span>
          </span>
        )}
        <ChevronDown className={cx('hidden size-4 sm:block', dark ? 'text-white/50' : 'text-slate-400')} />
      </button>
      {open && (
        <div className="anim-pop absolute top-12 right-0 z-50 w-60 overflow-hidden rounded-2xl border border-slate-200 bg-white p-1.5 text-slate-900 shadow-2xl">
          <div className="px-3 py-2.5">
            <p className="text-sm font-semibold">{user.name}</p>
            <p className="text-xs text-slate-500">{user.title}</p>
          </div>
          <div className="my-1 h-px bg-slate-100" />
          {items.map((it) => (
            <button
              key={it.label}
              type="button"
              onClick={() => {
                setOpen(false);
                it.onClick?.();
              }}
              className={cx('flex h-10 w-full items-center gap-3 rounded-lg px-3 text-sm font-medium', it.danger ? 'text-rose-600 hover:bg-rose-50' : 'text-slate-700 hover:bg-slate-50')}
            >
              <it.icon className="size-4" /> {it.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
};
