import type { OrderStatus } from '@/types';

export const COLUMNS: { title: string; statuses: OrderStatus[]; accent: string }[] = [
  {
    title: "Chờ khảo sát",
    statuses: ["NEW_PENDING_SURVEY"],
    accent: "bg-blue-500",
  },
  {
    title: "Chờ chốt (24h)",
    statuses: ["SURVEYED_PENDING_CONFIRM"],
    accent: "bg-amber-500",
  },
  {
    title: "Chờ điều phối",
    statuses: ["QUOTED_PENDING_SCHEDULE"],
    accent: "bg-brand",
  },
  {
    title: "Đang thực hiện",
    statuses: ["ASSIGNED", "IN_TRANSIT", "PAUSED_WAITING_CUSTOMER"],
    accent: "bg-blue-600",
  },
  {
    title: "Chờ quyết toán",
    statuses: ["PENDING_PAYMENT_OR_CLAIM"],
    accent: "bg-amber-600",
  },
  {
    title: "Đóng / Hủy",
    statuses: ["COMPLETED", "CANCELLED_EXPIRED"],
    accent: "bg-slate-400",
  },
];

export const HOURS = Array.from({ length: 14 }, (_, i) => 7 + i);
