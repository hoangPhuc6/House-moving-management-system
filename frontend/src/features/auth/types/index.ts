import type { RoleType } from '@/types';

export type AuthTab = "login" | "register";

export type AppRole = Exclude<RoleType, "LANDING" | "AUTH">;

export interface Credential {
  password: string;
  role: AppRole;
}

export interface RegisterForm {
  fullName: string;
  email: string;
  password: string;
  confirm: string;
  phone: string;
  birthDate: string;
}

export type FormErrors = Partial<Record<keyof RegisterForm, string>>;
