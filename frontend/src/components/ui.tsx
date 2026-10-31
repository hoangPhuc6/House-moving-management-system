import {
  type ButtonHTMLAttributes,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
  useEffect,
} from 'react';
import { AlertTriangle, CheckCircle2, Info, type LucideIcon, X, XCircle } from 'lucide-react';
import { STATUS_META } from '@/constants/order';
import type { OrderStatus, ToastTone } from '@/types';

export const cx = (...classes: (string | false | null | undefined)[]): string => classes.filter(Boolean).join(' ');

export type ButtonVariant = 'primary' | 'secondary' | 'dark' | 'ghost' | 'danger' | 'success';

export type ButtonSize = 'sm' | 'md' | 'lg';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  icon?: LucideIcon;
  block?: boolean;
}

export const VARIANTS: Record<ButtonVariant, string> = {
  primary: 'bg-brand text-white hover:bg-brand-hover shadow-sm shadow-brand/20',
  secondary: 'bg-white text-slate-800 border border-slate-200 hover:bg-slate-50 hover:border-slate-300',
  dark: 'bg-slate-900 text-white hover:bg-slate-800',
  ghost: 'text-slate-600 hover:bg-slate-100 hover:text-slate-900',
  danger: 'bg-rose-600 text-white hover:bg-rose-700',
  success: 'bg-emerald-600 text-white hover:bg-emerald-700',
};

export const SIZES: Record<ButtonSize, string> = {
  sm: 'h-8 px-3 text-xs gap-1.5 rounded-lg',
  md: 'h-10 px-4 text-sm gap-2 rounded-xl',
  lg: 'h-12 px-5 text-[15px] gap-2 rounded-xl',
};

export const Button = ({ variant = 'primary', size = 'md', icon: Icon, block, className, children, ...rest }: ButtonProps) => (
  <button
    type="button"
    {...rest}
    className={cx(
      'inline-flex items-center justify-center font-semibold whitespace-nowrap transition-all duration-150 active:scale-[0.98]',
      'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand/40 focus-visible:ring-offset-2',
      'disabled:cursor-not-allowed disabled:opacity-100 disabled:bg-slate-100 disabled:text-slate-400 disabled:border-slate-200 disabled:shadow-none disabled:active:scale-100',
      VARIANTS[variant],
      SIZES[size],
      block && 'w-full',
      className,
    )}
  >
    {Icon && <Icon className={size === 'sm' ? 'size-3.5' : 'size-4'} strokeWidth={2.25} />}
    {children}
  </button>
);

export type Tone = ToastTone | 'neutral' | 'brand';

export const TONES: Record<Tone, string> = {
  info: 'bg-blue-50 text-blue-700 ring-blue-600/15',
  warning: 'bg-amber-50 text-amber-700 ring-amber-600/20',
  success: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
  danger: 'bg-rose-50 text-rose-700 ring-rose-600/20',
  neutral: 'bg-slate-100 text-slate-600 ring-slate-500/15',
  brand: 'bg-orange-50 text-brand ring-brand/20',
};

export const DOTS: Record<Tone, string> = {
  info: 'bg-blue-600',
  warning: 'bg-amber-500',
  success: 'bg-emerald-500',
  danger: 'bg-rose-600',
  neutral: 'bg-slate-400',
  brand: 'bg-brand',
};

export const Badge = ({ tone = 'neutral', children, dot, pulse, icon: Icon, className }: { tone?: Tone; children: ReactNode; dot?: boolean; pulse?: boolean; icon?: LucideIcon; className?: string }) => (
  <span className={cx('inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold ring-1 ring-inset whitespace-nowrap max-w-full', TONES[tone], className)}>
    {dot && (
      <span className="relative flex size-1.5 shrink-0">
        {pulse && <span className={cx('absolute inline-flex size-full animate-ping rounded-full opacity-75', DOTS[tone])} />}
        <span className={cx('relative inline-flex size-1.5 rounded-full', DOTS[tone])} />
      </span>
    )}
    {Icon && <Icon className="size-3.5 shrink-0" strokeWidth={2.25} />}
    <span className="min-w-0 truncate">{children}</span>
  </span>
);

export const StatusBadge = ({ status }: { status: OrderStatus }) => {
  const meta = STATUS_META[status];
  return (
    <Badge tone={meta.tone} dot pulse={status === 'IN_TRANSIT' || status === 'PAUSED_WAITING_CUSTOMER'}>
      {meta.label}
    </Badge>
  );
};

export const Card = ({ children, className, padded = true }: { children: ReactNode; className?: string; padded?: boolean }) => (
  <div className={cx('rounded-2xl border border-slate-200/80 bg-white shadow-[0_1px_2px_rgba(15,23,42,0.04)]', padded && 'p-6', className)}>{children}</div>
);

export const CardHeader = ({ title, subtitle, icon: Icon, action, eyebrow }: { title: ReactNode; subtitle?: ReactNode; icon?: LucideIcon; action?: ReactNode; eyebrow?: string }) => (
  <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
    <div className="flex min-w-0 items-start gap-3">
      {Icon && (
        <div className="grid size-9 shrink-0 place-items-center rounded-xl border border-slate-200 bg-slate-50 text-slate-700">
          <Icon className="size-[18px]" strokeWidth={2} />
        </div>
      )}
      <div className="min-w-0">
        {eyebrow && <p className="mb-0.5 text-[11px] font-semibold uppercase tracking-wider text-brand">{eyebrow}</p>}
        <h3 className="text-[15px] font-semibold tracking-tight text-slate-900">{title}</h3>
        {subtitle && <p className="mt-0.5 text-sm text-slate-500">{subtitle}</p>}
      </div>
    </div>
    {action}
  </div>
);

export const PageHeader = ({ eyebrow, title, subtitle, actions }: { eyebrow?: string; title: string; subtitle?: ReactNode; actions?: ReactNode }) => (
  <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
    <div>
      {eyebrow && <p className="mb-1 text-xs font-semibold uppercase tracking-wider text-slate-500">{eyebrow}</p>}
      <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-[28px]">{title}</h1>
      {subtitle && <p className="mt-1 text-sm text-slate-500">{subtitle}</p>}
    </div>
    {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
  </div>
);

export const Stat = ({ label, value, hint, tone = 'neutral', icon: Icon }: { label: string; value: ReactNode; hint?: ReactNode; tone?: Tone; icon?: LucideIcon }) => (
  <Card className="flex flex-col gap-3">
    <div className="flex items-center justify-between">
      <span className="text-sm font-medium text-slate-500">{label}</span>
      {Icon && (
        <span className={cx('grid size-8 place-items-center rounded-lg ring-1 ring-inset', TONES[tone])}>
          <Icon className="size-4" />
        </span>
      )}
    </div>
    <div className="text-2xl font-bold tracking-tight text-slate-900 tabular-nums">{value}</div>
    {hint && <div className="text-xs text-slate-500">{hint}</div>}
  </Card>
);

export const ALERT_STYLE: Record<ToastTone, { box: string; icon: LucideIcon; iconColor: string }> = {
  info: { box: 'border-blue-200 bg-blue-50/70 text-blue-900', icon: Info, iconColor: 'text-blue-600' },
  warning: { box: 'border-amber-200 bg-amber-50/80 text-amber-950', icon: AlertTriangle, iconColor: 'text-amber-600' },
  success: { box: 'border-emerald-200 bg-emerald-50/70 text-emerald-950', icon: CheckCircle2, iconColor: 'text-emerald-600' },
  danger: { box: 'border-rose-200 bg-rose-50/80 text-rose-950', icon: XCircle, iconColor: 'text-rose-600' },
};

export const Alert = ({ tone, title, children, action, icon, className }: { tone: ToastTone; title?: ReactNode; children?: ReactNode; action?: ReactNode; icon?: LucideIcon; className?: string }) => {
  const s = ALERT_STYLE[tone];
  const Icon = icon ?? s.icon;
  return (
    <div className={cx('flex flex-col gap-3 rounded-xl border p-4 sm:flex-row sm:items-start', s.box, className)} role={tone === 'danger' ? 'alert' : 'status'}>
      <Icon className={cx('mt-0.5 size-5 shrink-0', s.iconColor)} strokeWidth={2.25} />
      <div className="min-w-0 flex-1 text-sm leading-relaxed">
        {title && <p className="font-semibold">{title}</p>}
        {children && <div className={cx(Boolean(title) && 'mt-0.5', 'opacity-90')}>{children}</div>}
      </div>
      {action && <div className="shrink-0">{action}</div>}
    </div>
  );
};

/** Tooltip bọc ngoài cả nút disabled (nút disabled không nhận hover). */
export const Tooltip = ({ content, children, enabled = true, side = 'top' }: { content: ReactNode; children: ReactNode; enabled?: boolean; side?: 'top' | 'bottom' }) => (
  <span className="group/tt relative inline-flex">
    {children}
    {enabled && (
      <span
        role="tooltip"
        className={cx(
          'pointer-events-none absolute left-1/2 z-40 w-64 -translate-x-1/2 rounded-lg bg-slate-900 px-3 py-2 text-xs leading-relaxed font-medium text-white opacity-0 shadow-xl transition-opacity duration-150 group-hover/tt:opacity-100',
          side === 'top' ? 'bottom-full mb-2' : 'top-full mt-2',
        )}
      >
        {content}
      </span>
    )}
  </span>
);

export interface TabDef<T extends string> {
  id: T;
  label: string;
  icon?: LucideIcon;
  badge?: ReactNode;
}

export const Tabs = <T extends string>({ tabs, value, onChange, className }: { tabs: TabDef<T>[]; value: T; onChange: (v: T) => void; className?: string }) => (
  <div className={cx('scrollbar-none -mx-1 flex gap-1 overflow-x-auto px-1', className)}>
    <div className="inline-flex gap-1 rounded-xl border border-slate-200 bg-white p-1">
      {tabs.map((t) => {
        const active = t.id === value;
        const Icon = t.icon;
        return (
          <button
            key={t.id}
            type="button"
            onClick={() => onChange(t.id)}
            className={cx(
              'inline-flex h-9 items-center gap-2 rounded-lg px-3.5 text-sm font-semibold whitespace-nowrap transition-colors',
              active ? 'bg-slate-900 text-white shadow-sm' : 'text-slate-500 hover:bg-slate-50 hover:text-slate-900',
            )}
          >
            {Icon && <Icon className="size-4" />}
            {t.label}
            {t.badge}
          </button>
        );
      })}
    </div>
  </div>
);

export const Field = ({ label, hint, error, children, className }: { label: string; hint?: ReactNode; error?: string | null; children: ReactNode; className?: string }) => (
  <label className={cx('flex flex-col gap-1.5', className)}>
    <span className="text-[13px] font-semibold text-slate-700">{label}</span>
    {children}
    {error ? <span className="text-xs font-medium text-rose-600">{error}</span> : hint ? <span className="text-xs text-slate-500">{hint}</span> : null}
  </label>
);

export const controlBase =
  'h-11 w-full rounded-xl border bg-white px-3.5 text-sm text-slate-900 placeholder:text-slate-400 transition-colors focus:outline-none focus:ring-4 focus:ring-brand/10 focus:border-brand disabled:bg-slate-50 disabled:text-slate-400';

export const Input = ({ invalid, className, ...rest }: InputHTMLAttributes<HTMLInputElement> & { invalid?: boolean }) => (
  <input {...rest} className={cx(controlBase, invalid ? 'border-rose-400' : 'border-slate-200', className)} />
);

export const Select = ({ className, children, ...rest }: SelectHTMLAttributes<HTMLSelectElement>) => (
  <select {...rest} className={cx(controlBase, 'border-slate-200 pr-8', className)}>
    {children}
  </select>
);

export const NumberStepper = ({ value, onChange, min = 0, max = 999, step = 1, suffix }: { value: number; onChange: (n: number) => void; min?: number; max?: number; step?: number; suffix?: string }) => (
  <div className="flex h-11 items-stretch overflow-hidden rounded-xl border border-slate-200 bg-white">
    <button type="button" className="w-11 shrink-0 text-lg font-semibold text-slate-600 hover:bg-slate-50 disabled:text-slate-300" onClick={() => onChange(Math.max(min, value - step))} disabled={value <= min} aria-label="Giảm">
      −
    </button>
    <div className="flex flex-1 items-center justify-center gap-1 border-x border-slate-200 text-sm font-semibold tabular-nums text-slate-900">
      {value}
      {suffix && <span className="font-medium text-slate-400">{suffix}</span>}
    </div>
    <button type="button" className="w-11 shrink-0 text-lg font-semibold text-slate-600 hover:bg-slate-50 disabled:text-slate-300" onClick={() => onChange(Math.min(max, value + step))} disabled={value >= max} aria-label="Tăng">
      +
    </button>
  </div>
);

export const Toggle = ({ checked, onChange, label, tone = 'brand' }: { checked: boolean; onChange: (v: boolean) => void; label?: ReactNode; tone?: 'brand' | 'danger' }) => (
  <button type="button" role="switch" aria-checked={checked} onClick={() => onChange(!checked)} className="group inline-flex items-center gap-2.5 text-left">
    <span className={cx('relative inline-flex h-5 w-9 shrink-0 rounded-full transition-colors', checked ? (tone === 'danger' ? 'bg-rose-500' : 'bg-brand') : 'bg-slate-600')}>
      <span className={cx('absolute top-0.5 size-4 rounded-full bg-white shadow transition-transform', checked ? 'translate-x-[18px]' : 'translate-x-0.5')} />
    </span>
    {label && <span className="text-xs font-medium">{label}</span>}
  </button>
);

export const useEscape = (open: boolean, onClose: () => void) => {
  useEffect(() => {
    if (!open) return undefined;
    const h = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', h);
    return () => window.removeEventListener('keydown', h);
  }, [open, onClose]);
};

export const Modal = ({ open, onClose, title, children, footer, tone, size = 'md' }: { open: boolean; onClose: () => void; title: ReactNode; children: ReactNode; footer?: ReactNode; tone?: ToastTone; size?: 'md' | 'lg' }) => {
  useEscape(open, onClose);
  if (!open) return null;
  const s = tone ? ALERT_STYLE[tone] : null;
  return (
    <div className="fixed inset-0 z-[60] flex items-end justify-center p-0 sm:items-center sm:p-6" role="dialog" aria-modal="true">
      <div className="anim-fade absolute inset-0 bg-slate-950/50 backdrop-blur-[2px]" onClick={onClose} />
      <div className={cx('anim-pop relative w-full overflow-hidden rounded-t-3xl bg-white pb-[env(safe-area-inset-bottom)] shadow-2xl sm:rounded-2xl sm:pb-0', size === 'lg' ? 'sm:max-w-2xl' : 'sm:max-w-lg')}>
        <div className="mx-auto mt-2.5 h-1 w-10 rounded-full bg-slate-200 sm:hidden" />
        <div className="flex items-start gap-3 border-b border-slate-100 px-5 py-4 sm:px-6 sm:py-5">
          {s && (
            <div className={cx('grid size-10 shrink-0 place-items-center rounded-xl', tone === 'danger' ? 'bg-rose-50' : tone === 'warning' ? 'bg-amber-50' : tone === 'success' ? 'bg-emerald-50' : 'bg-blue-50')}>
              <s.icon className={cx('size-5', s.iconColor)} />
            </div>
          )}
          <h3 className="flex-1 pt-1.5 text-base font-semibold tracking-tight text-slate-900">{title}</h3>
          <button type="button" onClick={onClose} className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700" aria-label="Đóng">
            <X className="size-4" />
          </button>
        </div>
        <div className="max-h-[70vh] overflow-y-auto px-6 py-5 text-sm leading-relaxed text-slate-600">{children}</div>
        {footer && <div className="flex flex-col-reverse gap-2 border-t border-slate-100 bg-slate-50/60 px-6 py-4 sm:flex-row sm:justify-end">{footer}</div>}
      </div>
    </div>
  );
};

export const Drawer = ({ open, onClose, title, subtitle, children, footer }: { open: boolean; onClose: () => void; title: ReactNode; subtitle?: ReactNode; children: ReactNode; footer?: ReactNode }) => {
  useEscape(open, onClose);
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-[60]" role="dialog" aria-modal="true">
      <div className="anim-fade absolute inset-0 bg-slate-950/50 backdrop-blur-[2px]" onClick={onClose} />
      <aside className="anim-slide absolute inset-y-0 right-0 flex w-full max-w-md flex-col bg-white shadow-2xl">
        <div className="flex items-start justify-between gap-3 border-b border-slate-100 px-6 py-5">
          <div>
            <h3 className="text-base font-semibold tracking-tight text-slate-900">{title}</h3>
            {subtitle && <p className="mt-0.5 text-sm text-slate-500">{subtitle}</p>}
          </div>
          <button type="button" onClick={onClose} className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700" aria-label="Đóng">
            <X className="size-4" />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto px-6 py-5">{children}</div>
        {footer && <div className="flex gap-2 border-t border-slate-100 px-6 py-4">{footer}</div>}
      </aside>
    </div>
  );
};

/** QR minh họa (deterministic) — không phải mã thanh toán thật. */
export const QrMock = ({ seed, size = 168 }: { seed: string; size?: number }) => {
  const n = 25;
  let h = 0;
  for (const ch of seed) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
  const cells: boolean[] = [];
  for (let i = 0; i < n * n; i += 1) {
    h = (h * 1103515245 + 12345) >>> 0;
    cells.push(((h >>> 16) & 1) === 1);
  }
  const inFinder = (x: number, y: number) => (x < 8 && y < 8) || (x >= n - 8 && y < 8) || (x < 8 && y >= n - 8);
  const finder = (ox: number, oy: number) => (
    <g key={`${ox}-${oy}`}>
      <rect x={ox} y={oy} width={7} height={7} fill="#0F172A" />
      <rect x={ox + 1} y={oy + 1} width={5} height={5} fill="#fff" />
      <rect x={ox + 2} y={oy + 2} width={3} height={3} fill="#0F172A" />
    </g>
  );
  return (
    <svg viewBox={`-1 -1 ${n + 2} ${n + 2}`} width={size} height={size} className="rounded-lg bg-white" shapeRendering="crispEdges" aria-label="Mã QR minh họa">
      {cells.map((on, i) => {
        const x = i % n;
        const y = Math.floor(i / n);
        return on && !inFinder(x, y) ? <rect key={i} x={x} y={y} width={1} height={1} fill="#0F172A" /> : null;
      })}
      {finder(0, 0)}
      {finder(n - 7, 0)}
      {finder(0, n - 7)}
      <rect x={n / 2 - 2.5} y={n / 2 - 2.5} width={5} height={5} rx={1} fill="#FF5A1F" />
    </svg>
  );
};

export const Divider = ({ className }: { className?: string }) => <div className={cx('h-px bg-slate-100', className)} />;

export const KeyValue = ({ label, value, strong, tone }: { label: ReactNode; value: ReactNode; strong?: boolean; tone?: 'brand' | 'danger' | 'success' }) => (
  <div className="flex items-center justify-between gap-4 py-1.5 text-sm">
    <span className={cx(strong ? 'font-semibold text-slate-900' : 'text-slate-500')}>{label}</span>
    <span
      className={cx(
        'text-right tabular-nums',
        strong ? 'text-base font-bold text-slate-900' : 'font-semibold text-slate-800',
        tone === 'brand' && 'text-brand',
        tone === 'danger' && 'text-rose-600',
        tone === 'success' && 'text-emerald-600',
      )}
    >
      {value}
    </span>
  </div>
);

export const EmptyState = ({ icon: Icon, title, children }: { icon: LucideIcon; title: string; children?: ReactNode }) => (
  <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-slate-200 bg-slate-50/50 px-6 py-10 text-center">
    <div className="mb-3 grid size-11 place-items-center rounded-xl bg-white text-slate-400 ring-1 ring-slate-200">
      <Icon className="size-5" />
    </div>
    <p className="text-sm font-semibold text-slate-800">{title}</p>
    {children && <div className="mt-1 max-w-sm text-sm text-slate-500">{children}</div>}
  </div>
);
