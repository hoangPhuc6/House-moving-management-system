import { useEffect, useState } from 'react';
import { ArrowRight, Menu, Phone, X } from 'lucide-react';
import { Logo } from '@/components/Logo';
import { cx } from '@/components/ui';
import { LINKS } from '@/features/landing/constants';

/** Thanh điều hướng dính: trong suốt trên hero, nền đặc khi cuộn; menu toàn màn hình trên mobile. */
export const SiteHeader = ({ onLogin, onBook }: { onLogin: () => void; onBook: () => void }) => {
  const [scrolled, setScrolled] = useState(false);
  const [menu, setMenu] = useState(false);

  useEffect(() => {
    const h = () => setScrolled(window.scrollY > 24);
    h();
    window.addEventListener('scroll', h, { passive: true });
    return () => window.removeEventListener('scroll', h);
  }, []);
  useEffect(() => {
    document.body.style.overflow = menu ? 'hidden' : '';
    return () => {
      document.body.style.overflow = '';
    };
  }, [menu]);

  return (
    <header className={cx('fixed inset-x-0 top-0 z-50 transition-colors duration-200', scrolled || menu ? 'border-b border-white/10 bg-obsidian/90 backdrop-blur-xl' : 'bg-transparent')}>
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6">
        <Logo dark />
        <nav className="hidden items-center gap-8 text-sm font-medium text-white/60 md:flex">
          {LINKS.map((l) => <a key={l.href} href={l.href} className="transition-colors hover:text-white">{l.label}</a>)}
        </nav>
        <div className="flex items-center gap-2">
          <button type="button" onClick={onLogin} className="hidden h-9 rounded-xl px-3 text-sm font-semibold text-white/80 hover:text-white sm:block">Đăng nhập</button>
          <button type="button" onClick={onBook} className="h-9 rounded-xl bg-white px-4 text-sm font-semibold text-slate-900 hover:bg-white/90">Đặt lịch</button>
          <button type="button" onClick={() => setMenu((v) => !v)} className="grid size-10 place-items-center rounded-xl text-white md:hidden" aria-label="Menu" aria-expanded={menu}>
            {menu ? <X className="size-5" /> : <Menu className="size-5" />}
          </button>
        </div>
      </div>
      {menu && (
        <div className="anim-fade h-[calc(100dvh-64px)] overflow-y-auto bg-obsidian px-4 pt-4 pb-10 md:hidden">
          <nav className="flex flex-col">
            {LINKS.map((l) => (
              <a key={l.href} href={l.href} onClick={() => setMenu(false)} className="flex h-14 items-center justify-between border-b border-white/10 text-lg font-semibold text-white">
                {l.label} <ArrowRight className="size-4 text-white/40" />
              </a>
            ))}
          </nav>
          <div className="mt-8 grid gap-3">
            <button type="button" onClick={onBook} className="h-12 rounded-xl bg-brand font-semibold text-white">Đặt lịch chuyển nhà</button>
            <button type="button" onClick={onLogin} className="h-12 rounded-xl border border-white/15 font-semibold text-white">Đăng nhập</button>
          </div>
          <a href="tel:19006868" className="mt-8 flex items-center gap-2 text-sm text-white/60"><Phone className="size-4" /> Hotline 1900 6868 (24/7)</a>
        </div>
      )}
    </header>
  );
};
