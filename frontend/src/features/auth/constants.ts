import { Headset, type LucideIcon, ShieldCheck, Truck, User } from 'lucide-react';
import type { AppRole, RegisterForm } from '@/features/auth/types';

export const MIN_PASSWORD = 6;

export const DEMO_PASSWORD = "moveflow123";

export const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export const PHONE_REGEX = /^(0|\+84)\d{9}$/;

export const DEMO_ACCOUNTS: {
  role: AppRole;
  label: string;
  email: string;
  icon: LucideIcon;
}[] = [
  {
    role: "CUSTOMER",
    label: "Khách hàng",
    email: "lan.nguyen@gmail.com",
    icon: User,
  },
  {
    role: "STAFF",
    label: "NV vận chuyển",
    email: "hung.tv@moveflow.vn",
    icon: Truck,
  },
  {
    role: "COORDINATOR",
    label: "Điều phối viên",
    email: "chau.lm@moveflow.vn",
    icon: Headset,
  },
  {
    role: "ADMIN",
    label: "Admin Kho & TC",
    email: "trang.pt@moveflow.vn",
    icon: ShieldCheck,
  },
];

export const EMPTY_FORM: RegisterForm = {
  fullName: "",
  email: "",
  password: "",
  confirm: "",
  phone: "",
  birthDate: "",
};
