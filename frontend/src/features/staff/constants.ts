import {
  ClipboardCheck,
  type LucideIcon,
  MapPin,
  PackageCheck,
  PlayCircle,
  Truck,
} from 'lucide-react';
import { isMaterialsSettled } from '@/services/materials';
import type { Order, FieldStage, StaffMember } from '@/types';

/** Nhân viên mặc định khi mở khu vực Nhân viên (đổi được qua thanh công cụ demo). */
export const STAFF_ID = "st-1";

/** Thông tin hiển thị trên thanh tài khoản. */
export const staffUserOf = (m: Pick<StaffMember, "name" | "code"> | undefined) => {
  const name = m?.name ?? "Nhân viên";
  const words = name.trim().split(/\s+/);
  const initials = ((words[0]?.[0] ?? "") + (words.length > 1 ? (words[words.length - 1]?.[0] ?? "") : "")).toUpperCase();
  return { name, title: m ? `${m.code} · Ca sáng 07:00 – 15:00` : "", initials };
};

export const JOB_STATUSES = [
  "ASSIGNED",
  "IN_TRANSIT",
  "PAUSED_WAITING_CUSTOMER",
  "PENDING_PAYMENT_OR_CLAIM",
  "COMPLETED",
];

export const RUNNING = ["ASSIGNED", "IN_TRANSIT", "PAUSED_WAITING_CUSTOMER"];

/** Đã bàn giao cho khách nhưng phiếu vật tư chưa quyết toán (khai đã dùng / trả kho) — lệnh vẫn còn việc. */
export const needsToolReturn = (o: Order) => o.status === "PENDING_PAYMENT_OR_CLAIM" && !isMaterialsSettled(o.materials);

/** Lệnh còn việc cho đội xe: đang chạy hoặc còn chờ trả dụng cụ. */
export const isOpenJob = (o: Order) => RUNNING.includes(o.status) || needsToolReturn(o);

export const STAGE_ICONS: Record<Exclude<FieldStage, "NOT_STARTED">, LucideIcon> = {
  MATERIALS_PICKED: PackageCheck,
  WORKING: PlayCircle,
  MOVING: Truck,
  ARRIVED: MapPin,
  HANDED_OVER: ClipboardCheck,
};
