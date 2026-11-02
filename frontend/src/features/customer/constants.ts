import { Building, Building2, Home, type LucideIcon } from 'lucide-react';
import type { HouseType, Order } from '@/types';

export const CUSTOMER = {
  name: "Nguyễn Thị Lan",
  phone: "0912 345 678",
  title: "Thành viên Vàng",
  initials: "NL",
};

export const PAYMENT_STATUSES: Order["status"][] = [
  "PENDING_PAYMENT_OR_CLAIM",
  "COMPLETED",
];

export const HOUSE_ICONS: Record<HouseType, LucideIcon> = {
  GROUND_FLOOR: Home,
  APARTMENT: Building2,
  DEEP_ALLEY: Building,
};

export const CLAIM_PRESETS = [
  "Vỡ / hỏng đồ dễ vỡ",
  "Thiết bị điện không hoạt động",
  "Trầy xước nội thất",
  "Thất lạc đồ đạc",
];
