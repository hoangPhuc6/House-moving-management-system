import {
  ClipboardCheck,
  type LucideIcon,
  MapPin,
  PackageCheck,
  PlayCircle,
  Truck,
} from 'lucide-react';
import type { FieldStage } from '@/types';

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

export const STAGE_ICONS: Record<Exclude<FieldStage, "NOT_STARTED">, LucideIcon> = {
  MATERIALS_PICKED: PackageCheck,
  WORKING: PlayCircle,
  MOVING: Truck,
  ARRIVED: MapPin,
  HANDED_OVER: ClipboardCheck,
};
