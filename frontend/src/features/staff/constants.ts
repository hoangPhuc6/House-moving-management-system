import {
  ClipboardCheck,
  type LucideIcon,
  MapPin,
  PackageCheck,
  PlayCircle,
  Truck,
} from 'lucide-react';
import type { Order, FieldStage } from '@/types';

export const STAFF_ID = "st-1";

export const STAFF_USER = {
  name: "Trần Văn Hùng",
  title: "Trưởng nhóm · NV-014 · Ca sáng 07:00 – 15:00",
  initials: "TH",
};

export const JOB_STATUSES = [
  "ASSIGNED",
  "IN_TRANSIT",
  "PAUSED_WAITING_CUSTOMER",
  "PENDING_PAYMENT_OR_CLAIM",
  "COMPLETED",
];

export const RUNNING = ["ASSIGNED", "IN_TRANSIT", "PAUSED_WAITING_CUSTOMER"];

/** Đã bàn giao cho khách nhưng đội xe chưa trả dụng cụ về kho — lệnh vẫn còn việc. */
export const needsToolReturn = (o: Order) => o.status === "PENDING_PAYMENT_OR_CLAIM" && !o.isWarehouseReturned;

/** Lệnh còn việc cho đội xe: đang chạy hoặc còn chờ trả dụng cụ. */
export const isOpenJob = (o: Order) => RUNNING.includes(o.status) || needsToolReturn(o);

export const STAGE_ICONS: Record<Exclude<FieldStage, "NOT_STARTED">, LucideIcon> = {
  MATERIALS_PICKED: PackageCheck,
  WORKING: PlayCircle,
  MOVING: Truck,
  ARRIVED: MapPin,
  HANDED_OVER: ClipboardCheck,
};
