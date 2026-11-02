import { useEffect, useState, type ReactNode } from 'react';
import { Link, NavLink, useLocation } from 'react-router-dom';
import { Menu, PanelLeftClose, PanelLeftOpen, Search, X, type LucideIcon } from 'lucide-react';
import { Logo } from '@/components/Logo';
import { cx } from '@/components/ui';
import { NotificationBell, UserMenu, type AppUser } from '@/layouts/shell';
import { ROUTES } from '@/routes/paths';

export interface NavItem {
  /** Đường dẫn đích của mục menu. */
  to: string;
  label: string;
  icon: LucideIcon;
  badge?: number | string;
  badgeTone?: 'brand' | 'danger';
}

export interface AppShellProps {
  nav: NavItem[];
  workspaceLabel: string;
  user: AppUser;
  search?: {
    value: string;
    onChange: (v: string) => void;
    placeholder: string;
  };
  /** Nội dung trang (thường là <Outlet />). */
  children: ReactNode;
}

const SidebarNav = ({ nav, collapsed = false, onNavigate }: { nav: NavItem[]; collapsed?: boolean; onNavigate?: () => void }) => (
  <nav className="flex flex-col gap-1">
    {nav.map((n) => (
      <NavLink
        key={n.to}
        to={n.to}
        onClick={onNavigate}
        title={collapsed ? n.label : undefined}
        aria-label={n.label}
        className={({ isActive }) =>
          cx(
            'relative flex h-11 items-center gap-3 rounded-xl px-3 text-left text-sm font-medium transition-colors',
            collapsed && 'justify-center px-0',
            isActive ? 'bg-white/10 text-white' : 'text-white/55 hover:bg-white/5 hover:text-white',
          )
        }
      >
        {({ isActive }) => (
          <>
            {isActive && <span className="absolute top-2.5 bottom-2.5 left-0 w-[3px] rounded-r-full bg-brand" />}
            <n.icon className={cx('size-[18px] shrink-0', isActive && 'text-brand')} />
            {!collapsed && <span className="flex-1 truncate">{n.label}</span>}
            {n.badge !== undefined && (
              <span
                className={cx(
                  collapsed && 'absolute top-1 right-1 min-w-4 px-1 text-[10px]',
                  'min-w-5 rounded-full px-1.5 py-0.5 text-center text-[11px] font-bold tabular-nums text-white',
                  n.badgeTone === 'danger' ? 'bg-rose-500' : 'bg-brand',
                )}
              >
                {n.badge}
              </span>
            )}
          </>
        )}
      </NavLink>
    ))}
  </nav>
);

/** Shell cho không gian làm việc nội bộ (Điều phối, Admin) — ưu tiên desktop. */
export const AppShell = ({ nav, workspaceLabel, user, search, children }: AppShellProps) => {
  const { pathname } = useLocation();
  const [drawer, setDrawer] = useState(false);
  const [collapsed, setCollapsed] = useState(() => {
    try {
      return localStorage.getItem('sidebar-collapsed') === '1';
    } catch {
      return false;
    }
  });
  const toggleCollapsed = () =>
    setCollapsed((c) => {
      try {
        localStorage.setItem('sidebar-collapsed', c ? '0' : '1');
      } catch {
        /* bỏ qua: chỉ là tiện ích ghi nhớ */
      }
      return !c;
    });
  const current = nav.find((n) => pathname === n.to || pathname.startsWith(`${n.to}/`));

  useEffect(() => {
    document.body.style.overflow = drawer ? 'hidden' : '';
    return () => {
      document.body.style.overflow = '';
    };
  }, [drawer]);

  return (
    <div className={cx('transition-[padding] duration-200', collapsed ? 'lg:pl-[76px]' : 'lg:pl-64')}>
      <div className="mx-auto flex max-w-[1520px] gap-4 p-0 lg:p-3">
        <aside
          className={cx(
            'fixed top-0 left-0 z-30 hidden h-screen flex-col bg-[#0F172A] p-4 text-white transition-[width] duration-200 lg:flex',
            collapsed ? 'w-[76px]' : 'w-64',
          )}
        >
          <div className={cx('py-2', collapsed ? 'flex justify-center' : 'px-2')}>
            <Link to={ROUTES.home} aria-label="Về trang chủ" className="inline-block">
              <Logo dark iconOnly={collapsed} />
            </Link>
            {!collapsed && <p className="mt-1 text-xs font-medium text-white/40">{workspaceLabel}</p>}
          </div>
          <div className="mt-6 flex-1">
            <SidebarNav nav={nav} collapsed={collapsed} />
          </div>
          <button
            type="button"
            onClick={toggleCollapsed}
            title={collapsed ? 'Mở rộng menu' : 'Thu gọn menu'}
            aria-label={collapsed ? 'Mở rộng menu' : 'Thu gọn menu'}
            className={cx(
              'flex h-10 items-center gap-3 rounded-xl text-sm font-medium text-white/55 transition-colors hover:bg-white/5 hover:text-white',
              collapsed ? 'justify-center' : 'px-3',
            )}
          >
            {collapsed ? <PanelLeftOpen className="size-[18px]" /> : <PanelLeftClose className="size-[18px]" />}
            {!collapsed && 'Thu gọn'}
          </button>
        </aside>

        {drawer && (
          <div className="fixed inset-0 z-[60] lg:hidden">
            <div className="anim-fade absolute inset-0 bg-slate-950/60" onClick={() => setDrawer(false)} />
            <aside className="anim-slide-left absolute inset-y-0 left-0 flex w-72 flex-col bg-[#0F172A] p-4 text-white">
              <div className="mb-6 flex items-center justify-between px-2 py-2">
                <div>
                  <Link to={ROUTES.home} aria-label="Về trang chủ" onClick={() => setDrawer(false)}>
                    <Logo dark />
                  </Link>
                  <p className="mt-1 text-xs text-white/40">{workspaceLabel}</p>
                </div>
                <button type="button" onClick={() => setDrawer(false)} className="rounded-lg p-2 text-white/60 hover:bg-white/10" aria-label="Đóng menu">
                  <X className="size-5" />
                </button>
              </div>
              <SidebarNav nav={nav} onNavigate={() => setDrawer(false)} />
            </aside>
          </div>
        )}

        <div className="min-w-0 flex-1">
          <header className="sticky top-0 z-40 mb-4 flex items-center gap-3 border-b border-slate-200/80 bg-white/90 px-4 py-3 backdrop-blur lg:relative lg:rounded-2xl lg:border">
            <button
              type="button"
              onClick={() => setDrawer(true)}
              className="-ml-1 grid size-10 place-items-center rounded-xl text-slate-700 hover:bg-slate-100 lg:hidden"
              aria-label="Mở menu"
            >
              <Menu className="size-5" />
            </button>
            <p className="truncate text-[15px] font-semibold lg:hidden">{current?.label}</p>
            {search ? (
              <div className="relative ml-auto hidden flex-1 sm:block lg:ml-0">
                <Search className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-slate-400" />
                <input
                  value={search.value}
                  onChange={(e) => search.onChange(e.target.value)}
                  placeholder={search.placeholder}
                  className="h-10 w-full max-w-md rounded-xl border border-slate-200 bg-slate-50 pr-3 pl-9 text-sm placeholder:text-slate-400 focus:border-brand focus:bg-white focus:ring-4 focus:ring-brand/10 focus:outline-none"
                />
              </div>
            ) : (
              <div className="hidden flex-1 lg:block" />
            )}
            <div className="ml-auto flex items-center gap-2">
              <NotificationBell />
              <UserMenu user={user} showName />
            </div>
          </header>
          <main key={pathname} className="anim-enter px-3 pb-10 sm:px-4 lg:px-0">
            {children}
          </main>
        </div>
      </div>
    </div>
  );
};
