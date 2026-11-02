import { createContext, type ReactNode, useContext, useEffect, useMemo, useReducer } from 'react';
import { appReducer } from '@/services/store/appReducer';
import { createInitialState } from '@/services/store/initialState';
import type { AppContextValue, Order } from '@/types';

export const AppContext = createContext<AppContextValue | null>(null);

export const AppProvider = ({ children }: { children: ReactNode }) => {
  const [state, dispatch] = useReducer(appReducer, undefined, createInitialState);

  const hasPaused = state.orders.some((o) => o.status === 'PAUSED_WAITING_CUSTOMER');
  useEffect(() => {
    if (!hasPaused) return undefined;
    const t = window.setInterval(() => dispatch({ type: 'TICK_WAIT_TIMERS' }), 1000);
    return () => window.clearInterval(t);
  }, [hasPaused]);

  const value = useMemo(() => ({ state, dispatch }), [state]);
  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
};

export const useApp = (): AppContextValue => {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error('useApp must be used within AppProvider');
  return ctx;
};

/** Đơn đang chọn; null khi hệ thống chưa có đơn nào. */
export const useActiveOrder = (): Order | null => {
  const { state } = useApp();
  return state.orders.find((o) => o.id === state.activeOrderId) ?? state.orders[0] ?? null;
};

export const useNow = (intervalMs = 1000): number => {
  const [now, setNow] = useReducer((_: number, n: number) => n, Date.now());
  useEffect(() => {
    const t = window.setInterval(() => setNow(Date.now()), intervalMs);
    return () => window.clearInterval(t);
  }, [intervalMs]);
  return now;
};
