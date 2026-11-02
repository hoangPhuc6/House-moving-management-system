import { useEffect } from 'react';
import { X } from 'lucide-react';
import { ALERT_STYLE, cx } from '@/components/ui';
import { useApp } from '@/services/store/AppStore';
import type { ToastTone } from '@/types';

export const ToastItem = ({ id, tone, message }: { id: string; tone: ToastTone; message: string }) => {
  const { dispatch } = useApp();
  useEffect(() => {
    const t = window.setTimeout(() => dispatch({ type: 'DISMISS_TOAST', id }), 5200);
    return () => window.clearTimeout(t);
  }, [id, dispatch]);
  const s = ALERT_STYLE[tone];
  return (
    <div className="anim-toast pointer-events-auto flex w-full items-start gap-3 rounded-xl border border-slate-200 bg-white p-3.5 shadow-lg shadow-slate-900/10">
      <s.icon className={cx('mt-0.5 size-[18px] shrink-0', s.iconColor)} />
      <p className="flex-1 text-sm font-medium leading-snug text-slate-800">{message}</p>
      <button type="button" className="rounded p-0.5 text-slate-400 hover:text-slate-700" onClick={() => dispatch({ type: 'DISMISS_TOAST', id })} aria-label="Đóng thông báo">
        <X className="size-3.5" />
      </button>
    </div>
  );
};

export const Toaster = () => {
  const { state } = useApp();
  return (
    <div className="pointer-events-none fixed top-3 right-3 left-3 z-[70] flex flex-col items-center gap-2 sm:top-auto sm:bottom-20 sm:left-auto sm:w-96 sm:items-end">
      {state.toasts.map((t) => (
        <ToastItem key={t.id} {...t} />
      ))}
    </div>
  );
};
