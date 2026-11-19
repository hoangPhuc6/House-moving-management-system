import { useMemo } from 'react';
import { useOutletContext } from 'react-router-dom';
import {
  currentPeriod,
  inWindow,
  type Period,
  type PeriodKind,
  periodOptions,
  periodRange,
  periodWindows,
  todayISO,
} from '@/features/admin/period';
import type { AdminOutletContext } from '@/features/admin/types';
import { isoDate } from '@/features/admin/utils';
import { useApp } from '@/services/store/AppStore';
import { useCatalog } from '@/services/store/CatalogStore';

/** Từ khóa tìm kiếm trên thanh header, chia sẻ cho các trang con của Admin. */
export const useAdminQuery = (): string => useOutletContext<AdminOutletContext>().query;

/**
 * Kỳ báo cáo chọn chung cho các tab Admin (giữ khi chuyển tab).
 * - `windows`: kỳ hiện tại (tới hôm nay nếu đang chạy) + kỳ so sánh — dùng cho chỉ số / Dashboard.
 * - `range` / `inPeriod`: trọn khoảng ngày của kỳ — dùng để lọc danh sách.
 */
export const useAdminPeriod = () => {
  const { period, setPeriod } = useOutletContext<AdminOutletContext>();
  const { state } = useApp();
  const { catalog } = useCatalog();
  const today = todayISO();

  // Kỳ sớm nhất có dữ liệu: lịch sử đơn, đơn hiện có, giao dịch, phiếu chi, khiếu nại.
  const earliest = useMemo(
    () =>
      [
        today,
        ...catalog.history.map((h) => h.date),
        ...state.orders.map((o) => isoDate(o.createdAt)),
        ...catalog.payments.map((p) => isoDate(p.createdAt)),
        ...state.refunds.map((r) => isoDate(r.createdAt)),
        ...state.claims.map((c) => isoDate(c.createdAt)),
      ].sort()[0]!,
    [today, catalog.history, catalog.payments, state.orders, state.refunds, state.claims],
  );
  const options = useMemo(() => periodOptions(period.kind, earliest, today), [period.kind, earliest, today]);
  const windows = useMemo(() => periodWindows(period, today), [period, today]);
  const range = useMemo(() => periodRange(period), [period]);

  return {
    period,
    setPeriod,
    changeKind: (k: PeriodKind) => setPeriod(currentPeriod(k)),
    options,
    windows,
    range,
    /** Ngày ISO thuộc kỳ đang chọn. */
    inPeriod: (iso: string) => inWindow(iso, range),
    /** Mốc thời gian (epoch ms) thuộc kỳ đang chọn. */
    inPeriodTs: (t: number) => inWindow(isoDate(t), range),
  };
};

export type AdminPeriod = ReturnType<typeof useAdminPeriod>;

/** Trạng thái kỳ đặt ở AdminLayout — mặc định tháng hiện tại. */
export const initialAdminPeriod = (): Period => currentPeriod('month');
