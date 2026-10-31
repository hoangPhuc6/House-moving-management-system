import {
  DEMO_ACCOUNTS,
  DEMO_PASSWORD,
  EMAIL_REGEX,
  MIN_PASSWORD,
  PHONE_REGEX,
} from '@/features/auth/constants';
import type { Credential, FormErrors, RegisterForm } from '@/features/auth/types';

/** Sổ tài khoản giả lập (không có backend): tài khoản dùng thử + tài khoản đăng ký trong phiên này. */
export const accounts = new Map<string, Credential>(
  DEMO_ACCOUNTS.map((a) => [
    a.email,
    { password: DEMO_PASSWORD, role: a.role },
  ]),
);

export const normalizeEmail = (v: string): string => v.trim().toLowerCase();

export const validateEmail = (email: string): string | undefined => {
  if (!email.trim()) return "Vui lòng nhập email";
  if (!EMAIL_REGEX.test(email.trim())) return "Email không hợp lệ";
  return undefined;
};

export const validatePassword = (password: string): string | undefined => {
  if (!password) return "Vui lòng nhập mật khẩu";
  if (password.length < MIN_PASSWORD)
    return `Mật khẩu tối thiểu ${MIN_PASSWORD} ký tự`;
  return undefined;
};

export const validateRegister = (f: RegisterForm): FormErrors => {
  const errors: FormErrors = {};
  if (!f.fullName.trim()) errors.fullName = "Vui lòng nhập họ và tên";
  errors.email = validateEmail(f.email);
  if (!errors.email && accounts.has(normalizeEmail(f.email)))
    errors.email = "Email này đã được đăng ký";
  errors.password = validatePassword(f.password);
  if (!f.confirm) errors.confirm = "Vui lòng xác nhận mật khẩu";
  else if (f.confirm !== f.password)
    errors.confirm = "Mật khẩu xác nhận không khớp";
  // Không bắt buộc — chỉ kiểm tra định dạng khi khách có nhập.
  if (f.phone.trim() && !PHONE_REGEX.test(f.phone.replace(/\s/g, "")))
    errors.phone = "Số điện thoại không hợp lệ (VD: 0912 345 678)";
  if (f.birthDate && f.birthDate > new Date().toISOString().slice(0, 10))
    errors.birthDate = "Ngày sinh không được ở tương lai";
  return Object.fromEntries(
    Object.entries(errors).filter(([, v]) => v),
  ) as FormErrors;
};
