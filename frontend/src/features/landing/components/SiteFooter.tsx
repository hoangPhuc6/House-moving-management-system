import { Phone } from 'lucide-react';
import { Logo } from '@/components/Logo';
import { FOOTER_COLS } from '@/features/landing/constants';

export const SiteFooter = () => (
  <footer className="bg-obsidian text-white">
    <div className="mx-auto grid max-w-7xl gap-10 px-4 py-14 sm:px-6 md:grid-cols-2 lg:grid-cols-[1.5fr_1fr_1fr_1fr]">
      <div>
        <Logo dark />
        <p className="mt-4 max-w-xs text-sm text-white/50">Hệ thống quản lý & điều phối dịch vụ chuyển nhà — minh bạch từ báo giá đến bàn giao.</p>
        <div className="mt-6 space-y-2 text-sm text-white/60">
          <p className="flex items-center gap-2"><Phone className="size-4" /> 1900 6868</p>
        </div>
      </div>
      {FOOTER_COLS.map(({ title, links }) => (
        <div key={title}>
          <p className="text-sm font-semibold">{title}</p>
          <ul className="mt-4 space-y-2.5 text-sm text-white/50">
            {links.map((l) => <li key={l.label}><a href={l.href} className="hover:text-white">{l.label}</a></li>)}
          </ul>
        </div>
      ))}
    </div>
    <div className="border-t border-white/10">
      <div className="mx-auto flex max-w-7xl flex-col gap-2 px-4 py-6 text-xs text-white/40 sm:flex-row sm:justify-between sm:px-6">
        <p>© 2026 MoveFlow. Bảo lưu mọi quyền.</p>
        <p>Đồ án môn SE100 · Phân tích thiết kế hướng đối tượng.</p>
      </div>
    </div>
  </footer>
);
