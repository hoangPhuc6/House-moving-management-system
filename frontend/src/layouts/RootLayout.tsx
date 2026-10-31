import { useEffect } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { DevToolbar } from '@/features/demo/components/DevToolbar';
import { Toaster } from '@/layouts/Toaster';
import { ROUTES } from '@/routes/paths';

/** Khi chuyển trang, luôn cuộn về đầu. */
const ScrollToTop = () => {
  const { pathname } = useLocation();
  useEffect(() => {
    window.scrollTo({ top: 0 });
  }, [pathname]);
  return null;
};

/** Khung ngoài cùng: nền theo trang, công cụ demo và toast. Các trang hiển thị qua <Outlet />. */
export const RootLayout = () => {
  const { pathname } = useLocation();
  return (
    <div className={pathname === ROUTES.home ? 'bg-white' : 'min-h-screen bg-canvas'}>
      <ScrollToTop />
      <Outlet />
      <DevToolbar />
      <Toaster />
    </div>
  );
};
