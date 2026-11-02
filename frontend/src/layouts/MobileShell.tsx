import { type ReactNode } from 'react';
import { Link, NavLink, useLocation } from 'react-router-dom';
import { type LucideIcon } from 'lucide-react';
import { Logo } from '@/components/Logo';
import { cx } from '@/components/ui';
import { type AppUser, NotificationBell, UserMenu, type UserMenuItem } from '@/layouts/shell';
import { ROUTES } from '@/routes/paths';

export interface MobileNavItem {
  /** Đường dẫn đích của mục menu. */
  to: string;
  label: string;
  icon: LucideIcon;
  badge?: number | string;
  /** Nút nổi bật ở giữa thanh điều hướng dưới (VD: "Đặt mới"). */
  primary?: boolean;
}

export interface MobileShellProps {
  nav: MobileNavItem[];
  user: AppUser;
  tone?: 'light' | 'dark';
  /** Khối nội dung phụ dưới thanh tiêu đề (VD: chọn đơn hàng). */
  headerExtra?: ReactNode;
  notificationOrderIds?: string[];
  /** Thay các mục mặc định trong menu avatar (VD: tài khoản khách hàng). */
  userMenuItems?: UserMenuItem[];
  /** Độ rộng vùng nội dung. */
  maxWidth?: string;
  /** Độ rộng thanh tiêu đề — rộng hơn nội dung để menu desktop không bị dồn. */
  headerWidth?: string;
  /** Nội dung trang (thường là <Outlet />). */
  children: ReactNode;
}

/**
 * Shell mobile-first cho Khách hàng & Nhân viên vận chuyển:
 * thanh tiêu đề dính trên, bottom tab bar trên mobile, tab ngang trên desktop.
 */
export const MobileShell = ({ nav, user, tone = 'light', headerExtra, notificationOrderIds, userMenuItems, maxWidth = 'max-w-3xl', headerWidth = 'max-w-6xl', children }: MobileShellProps) => {
  const dark = tone === 'dark';
  const { pathname } = useLocation();

  return (
    <div className="min-h-screen">
      <header className={cx('sticky top-0 z-40 border-b backdrop-blur-xl', dark ? 'border-white/5 bg-[#0F172A]/95 text-white' : 'border-slate-200/80 bg-white/90')}>
        <div className={cx('mx-auto flex h-14 items-center gap-3 px-4 md:h-16 md:gap-6', headerWidth)}>
          <Link to={ROUTES.home} aria-label="Về trang chủ">
            <Logo dark={dark} compact />
          </Link>
          <nav className="mx-auto hidden min-w-0 items-center gap-1 md:flex">
            {nav.map((n) => (
              <NavLink
                key={n.to}
                to={n.to}
                className={({ isActive }) =>
                  cx(
                    'relative inline-flex h-9 shrink-0 items-center gap-2 rounded-lg px-3 text-sm font-semibold whitespace-nowrap transition-colors',
                    isActive ? (dark ? 'bg-white/10 text-white' : 'bg-slate-900 text-white') : dark ? 'text-white/60 hover:text-white' : 'text-slate-500 hover:bg-slate-100 hover:text-slate-900',
                  )
                }
              >
                <n.icon className="size-4" />
                {n.label}
                {n.badge !== undefined && <span className="grid h-4 min-w-4 place-items-center rounded-full bg-rose-500 px-1 text-[10px] text-white">{n.badge}</span>}
              </NavLink>
            ))}
          </nav>
          <div className="ml-auto flex items-center gap-1.5 md:ml-0">
            <NotificationBell orderIds={notificationOrderIds} dark={dark} />
            <UserMenu user={user} dark={dark} items={userMenuItems} />
          </div>
        </div>
        {headerExtra && <div className={cx('mx-auto px-4 pb-3', headerWidth)}>{headerExtra}</div>}
      </header>

      <main key={pathname} className={cx('anim-enter mx-auto px-4 pt-4 pb-28 md:pt-6 md:pb-12', maxWidth)}>
        {children}
      </main>

      {/* Bottom tab bar */}
      <nav className="fixed inset-x-0 bottom-0 z-40 border-t border-slate-200 bg-white/95 pb-[env(safe-area-inset-bottom)] backdrop-blur-xl md:hidden" aria-label="Điều hướng chính">
        <div className="mx-auto flex max-w-md items-stretch justify-around px-2">
          {nav.map((n) =>
            n.primary ? (
              <NavLink key={n.to} to={n.to} className="flex flex-1 flex-col items-center justify-center gap-1 py-2" aria-label={n.label}>
                {({ isActive }) => (
                  <>
                    <span className={cx('-mt-5 grid size-12 place-items-center rounded-2xl shadow-lg ring-4 ring-white transition-colors', isActive ? 'bg-slate-900 text-white' : 'bg-brand text-white shadow-brand/30')}>
                      <n.icon className="size-5" strokeWidth={2.5} />
                    </span>
                    <span className={cx('text-[11px] font-semibold', isActive ? 'text-slate-900' : 'text-slate-500')}>{n.label}</span>
                  </>
                )}
              </NavLink>
            ) : (
              <NavLink key={n.to} to={n.to} className="relative flex min-h-16 flex-1 flex-col items-center justify-center gap-1 py-2">
                {({ isActive }) => (
                  <>
                    {isActive && <span className="absolute top-0 h-0.5 w-8 rounded-full bg-brand" />}
                    <span className="relative">
                      <n.icon className={cx('size-[22px]', isActive ? 'text-brand' : 'text-slate-400')} strokeWidth={isActive ? 2.4 : 2} />
                      {n.badge !== undefined && <span className="absolute -top-1.5 -right-2 grid h-4 min-w-4 place-items-center rounded-full bg-rose-500 px-1 text-[10px] font-bold text-white ring-2 ring-white">{n.badge}</span>}
                    </span>
                    <span className={cx('text-[11px] font-semibold', isActive ? 'text-slate-900' : 'text-slate-500')}>{n.label}</span>
                  </>
                )}
              </NavLink>
            ),
          )}
        </div>
      </nav>
    </div>
  );
};

/** Thanh hành động dính đáy trên mobile (nằm trên bottom tab bar), tĩnh trên desktop. */
export const StickyActionBar = ({ children, className }: { children: ReactNode; className?: string }) => (
  <div className={cx('fixed inset-x-0 bottom-[calc(64px+env(safe-area-inset-bottom))] z-30 border-t border-slate-200 bg-white/95 px-4 py-3 backdrop-blur-xl md:static md:mt-4 md:rounded-2xl md:border md:px-5 md:py-4', className)}>
    <div className="mx-auto max-w-3xl md:max-w-none">{children}</div>
  </div>
);
