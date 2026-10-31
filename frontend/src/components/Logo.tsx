import { cx } from '@/components/ui';

export const Logo = ({ dark = false, compact = false, iconOnly = false }: { dark?: boolean; compact?: boolean; iconOnly?: boolean }) => (
  <div className="flex items-center gap-2">
    <svg viewBox="0 0 32 32" className={compact ? 'size-6' : 'size-8'} aria-hidden>
      <rect width="32" height="32" rx="9" fill="#FF5A1F" />
      <path d="M8 21V11l5 5 3-3 3 3 5-5v10" fill="none" stroke="#fff" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
    {!iconOnly && <span className={cx('font-bold tracking-tight', compact ? 'text-sm' : 'text-lg', dark ? 'text-white' : 'text-slate-900')}>MoveFlow</span>}
  </div>
);
