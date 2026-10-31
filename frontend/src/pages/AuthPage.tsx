import { useNavigate } from 'react-router-dom';
import { useState } from 'react';
import {
  ArrowLeft,
  ArrowRight,
  CalendarDays,
  Eye,
  EyeOff,
  Lock,
  Mail,
  MapPin,
  Phone,
} from 'lucide-react';
import { Logo } from '@/components/Logo';
import { Alert, Button, Field, Input, Modal, Tabs } from '@/components/ui';
import { DEMO_ACCOUNTS, DEMO_PASSWORD, EMPTY_FORM, MIN_PASSWORD } from '@/features/auth/constants';
import type { AuthTab, FormErrors, RegisterForm } from '@/features/auth/types';
import { accounts, normalizeEmail, validateEmail, validateRegister } from '@/features/auth/utils';
import { ROLE_HOME } from '@/routes/paths';
import type { RoleType } from '@/types';

export const AuthPage = () => {
  const navigate = useNavigate();
  const [tab, setTab] = useState<AuthTab>("login");
  const [form, setForm] = useState<RegisterForm>(EMPTY_FORM);
  const [errors, setErrors] = useState<FormErrors>({});
  const [loginError, setLoginError] = useState<string | null>(null);
  const [showPw, setShowPw] = useState(false);
  const [forgotOpen, setForgotOpen] = useState(false);
  const [forgotEmail, setForgotEmail] = useState("");
  const [forgotSent, setForgotSent] = useState(false);
  const forgotError = forgotEmail ? validateEmail(forgotEmail) : undefined;

  const enter = (r: RoleType) => navigate(ROLE_HOME[r]);

  const switchTab = (t: AuthTab) => {
    setTab(t);
    setErrors({});
    setLoginError(null);
  };
  const setField = <K extends keyof RegisterForm>(k: K, v: RegisterForm[K]) => {
    setForm((f) => ({ ...f, [k]: v }));
    if (errors[k]) setErrors((e) => ({ ...e, [k]: undefined }));
    setLoginError(null);
  };

  const submit = () => {
    if (tab === "login") {
      const errs: FormErrors = {
        email: validateEmail(form.email),
        password: form.password ? undefined : "Vui lòng nhập mật khẩu",
      };
      setErrors(errs);
      if (errs.email || errs.password) return;
      const found = accounts.get(normalizeEmail(form.email));
      if (!found || found.password !== form.password) {
        setLoginError("Email hoặc mật khẩu không đúng.");
        return;
      }
      enter(found.role);
      return;
    }
    const errs = validateRegister(form);
    setErrors(errs);
    if (Object.keys(errs).length) return;
    accounts.set(normalizeEmail(form.email), {
      password: form.password,
      role: "CUSTOMER",
    });
    enter("CUSTOMER");
  };

  return (
    <div className="mx-auto grid min-h-screen max-w-[1440px] gap-3 p-0 sm:p-3 lg:grid-cols-2">
      {/* Branding side */}
      <div className="relative hidden overflow-hidden rounded-3xl bg-obsidian p-10 text-white lg:flex lg:flex-col">
        <div className="bg-grid-dark absolute inset-0 [mask-image:radial-gradient(ellipse_at_bottom_left,black_30%,transparent_75%)]" />
        <div className="pointer-events-none absolute -bottom-40 -left-20 h-[420px] w-[520px] rounded-full bg-brand/25 blur-[120px]" />
        <button
          type="button"
          onClick={() => enter("LANDING")}
          className="relative w-fit"
        >
          <Logo dark />
        </button>
        <div className="relative mt-auto">
          <h2 className="max-w-md text-4xl leading-tight font-bold tracking-tight">
            Mọi chuyến xe, mọi đồng tiền — đều nằm trong tầm mắt.
          </h2>
          <p className="mt-4 max-w-md text-white/55">
            Đăng nhập để theo dõi đơn hàng, điều phối đội xe hoặc quyết toán hồ
            sơ trên cùng một nền tảng.
          </p>

          {/* Live tracking preview */}
          <div className="mt-10 max-w-md rounded-2xl border border-white/10 bg-white/[0.04] p-5 backdrop-blur">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-xs text-white/50">Đơn hàng</p>
                <p className="font-semibold tabular-nums">#ORD-2026-089</p>
              </div>
              <span className="inline-flex items-center gap-1.5 rounded-full bg-brand/15 px-2.5 py-1 text-xs font-semibold text-orange-300">
                <span className="size-1.5 animate-pulse rounded-full bg-brand" />{" "}
                Đang di chuyển
              </span>
            </div>
            <div className="mt-5 flex items-center gap-3 text-sm">
              <MapPin className="size-4 shrink-0 text-white/40" />
              <span className="truncate text-white/70">
                128/45 Lê Văn Sỹ, Q.3
              </span>
            </div>
            <div className="my-2 ml-2 h-6 border-l border-dashed border-white/20" />
            <div className="flex items-center gap-3 text-sm">
              <MapPin className="size-4 shrink-0 text-brand" />
              <span className="truncate text-white/90">
                Vinhomes Grand Park, TP. Thủ Đức
              </span>
            </div>
            <div className="relative mt-5 h-1.5 rounded-full bg-white/10">
              <div className="h-1.5 w-[64%] rounded-full bg-brand" />
            </div>
            <div className="mt-3 flex justify-between text-xs text-white/50 tabular-nums">
              <span>51C-889.92 · 4 nhân sự</span>
              <span>ETA 10:45</span>
            </div>
          </div>
        </div>
      </div>

      {/* Form side */}
      <div className="flex items-center justify-center bg-white px-5 py-10 sm:rounded-3xl sm:border sm:border-slate-200/80 sm:px-10">
        <div className="w-full max-w-md">
          <button
            type="button"
            onClick={() => enter("LANDING")}
            className="mb-8 flex items-center gap-1.5 text-sm font-semibold text-slate-500 hover:text-slate-900"
          >
            <ArrowLeft className="size-4" /> Về trang chủ
          </button>
          <h1 className="text-2xl font-bold tracking-tight">
            {tab === "login" ? "Chào mừng trở lại" : "Tạo tài khoản MoveFlow"}
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            {tab === "login"
              ? "Đăng nhập để tiếp tục quản lý đơn hàng của bạn."
              : "Chỉ mất 30 giây để đặt lịch chuyển nhà đầu tiên. Các mục có dấu * là bắt buộc."}
          </p>

          <Tabs
            className="mt-6"
            value={tab}
            onChange={switchTab}
            tabs={[
              { id: "login", label: "Đăng nhập" },
              { id: "register", label: "Đăng ký" },
            ]}
          />

          <form
            className="mt-6 space-y-4"
            noValidate
            onSubmit={(e) => {
              e.preventDefault();
              submit();
            }}
          >
            {loginError && (
              <Alert tone="danger" title={loginError}>
                Dùng tài khoản dùng thử bên dưới nếu bạn chỉ muốn xem thử.
              </Alert>
            )}
            {tab === "register" && (
              <Field label="Họ và tên *" error={errors.fullName}>
                <Input
                  invalid={!!errors.fullName}
                  placeholder="Nguyễn Thị Lan"
                  autoComplete="name"
                  value={form.fullName}
                  onChange={(e) => setField("fullName", e.target.value)}
                />
              </Field>
            )}
            <Field
              label={tab === "register" ? "Email *" : "Email"}
              error={errors.email}
            >
              <div className="relative">
                <Mail className="absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-slate-400" />
                <Input
                  invalid={!!errors.email}
                  className="pl-10"
                  type="email"
                  placeholder="ban@email.com"
                  autoComplete="email"
                  value={form.email}
                  onChange={(e) => setField("email", e.target.value)}
                />
              </div>
            </Field>
            <Field
              label={tab === "register" ? "Mật khẩu *" : "Mật khẩu"}
              error={errors.password}
              hint={
                tab === "register"
                  ? `Tối thiểu ${MIN_PASSWORD} ký tự`
                  : undefined
              }
            >
              <div className="relative">
                <Lock className="absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-slate-400" />
                <Input
                  invalid={!!errors.password}
                  className="pr-10 pl-10"
                  type={showPw ? "text" : "password"}
                  placeholder="••••••••"
                  autoComplete={
                    tab === "login" ? "current-password" : "new-password"
                  }
                  value={form.password}
                  onChange={(e) => setField("password", e.target.value)}
                />
                <button
                  type="button"
                  onClick={() => setShowPw((v) => !v)}
                  className="absolute top-1/2 right-3 -translate-y-1/2 text-slate-400 hover:text-slate-700"
                  aria-label={showPw ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
                >
                  {showPw ? (
                    <EyeOff className="size-4" />
                  ) : (
                    <Eye className="size-4" />
                  )}
                </button>
              </div>
            </Field>
            {tab === "register" && (
              <>
                <Field label="Xác nhận mật khẩu *" error={errors.confirm}>
                  <div className="relative">
                    <Lock className="absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-slate-400" />
                    <Input
                      invalid={!!errors.confirm}
                      className="pl-10"
                      type={showPw ? "text" : "password"}
                      placeholder="Nhập lại mật khẩu"
                      autoComplete="new-password"
                      value={form.confirm}
                      onChange={(e) => setField("confirm", e.target.value)}
                    />
                  </div>
                </Field>
                <div className="grid gap-4 sm:grid-cols-2">
                  <Field label="Số điện thoại" error={errors.phone}>
                    <div className="relative">
                      <Phone className="absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-slate-400" />
                      <Input
                        invalid={!!errors.phone}
                        className="pl-10"
                        placeholder="0912 345 678"
                        inputMode="tel"
                        autoComplete="tel"
                        value={form.phone}
                        onChange={(e) => setField("phone", e.target.value)}
                      />
                    </div>
                  </Field>
                  <Field label="Ngày sinh" error={errors.birthDate}>
                    <div className="relative">
                      <CalendarDays className="pointer-events-none absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-slate-400" />
                      <Input
                        invalid={!!errors.birthDate}
                        className="pl-10"
                        type="date"
                        autoComplete="bday"
                        max={new Date().toISOString().slice(0, 10)}
                        value={form.birthDate}
                        onChange={(e) => setField("birthDate", e.target.value)}
                      />
                    </div>
                  </Field>
                </div>
              </>
            )}
            {tab === "login" && (
              <div className="flex items-center justify-between text-sm">
                <label className="flex items-center gap-2 text-slate-600">
                  <input
                    type="checkbox"
                    defaultChecked
                    className="size-4 rounded accent-[#FF5A1F]"
                  />{" "}
                  Ghi nhớ đăng nhập
                </label>
                <button
                  type="button"
                  onClick={() => {
                    setForgotSent(false);
                    setForgotOpen(true);
                  }}
                  className="font-semibold text-brand hover:underline"
                >
                  Quên mật khẩu?
                </button>
              </div>
            )}
            <Button type="submit" size="lg" block>
              {tab === "login" ? "Đăng nhập" : "Tạo tài khoản"}{" "}
              <ArrowRight className="size-4" />
            </Button>
          </form>

          <div className="my-7 flex items-center gap-3 text-xs font-medium text-slate-400">
            <div className="h-px flex-1 bg-slate-200" /> Tài khoản dùng thử{" "}
            <div className="h-px flex-1 bg-slate-200" />
          </div>
          <p className="-mt-3 mb-3 text-center text-xs text-slate-500">
            Mật khẩu chung cho tài khoản dùng thử:{" "}
            <span className="font-mono font-semibold text-slate-700">
              {DEMO_PASSWORD}
            </span>
          </p>
          <div className="grid grid-cols-2 gap-2">
            {DEMO_ACCOUNTS.map((a) => (
              <button
                key={a.role}
                type="button"
                onClick={() => enter(a.role)}
                className="group flex items-center gap-3 rounded-xl border border-slate-200 p-3 text-left transition-colors hover:border-slate-900 hover:bg-slate-900 hover:text-white"
              >
                <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-slate-100 text-slate-700 group-hover:bg-white/10 group-hover:text-brand">
                  <a.icon className="size-4" />
                </span>
                <span className="min-w-0">
                  <span className="block text-sm font-semibold">{a.label}</span>
                  <span className="block truncate text-[11px] text-slate-400 group-hover:text-white/50">
                    {a.email}
                  </span>
                </span>
              </button>
            ))}
          </div>
        </div>
      </div>
      <Modal
        open={forgotOpen}
        onClose={() => setForgotOpen(false)}
        title="Đặt lại mật khẩu"
        footer={
          forgotSent ? (
            <Button onClick={() => setForgotOpen(false)}>Đóng</Button>
          ) : (
            <>
              <Button variant="secondary" onClick={() => setForgotOpen(false)}>
                Hủy
              </Button>
              <Button disabled={!forgotEmail || !!forgotError} onClick={() => setForgotSent(true)}>
                Gửi liên kết
              </Button>
            </>
          )
        }
      >
        {forgotSent ? (
          <Alert tone="success" title="Đã gửi hướng dẫn">
            Nếu email {forgotEmail.trim()} tồn tại trong hệ thống, liên kết đặt lại mật khẩu (hiệu lực 15 phút) đã được gửi.
          </Alert>
        ) : (
          <Field label="Email đăng ký" error={forgotError}>
            <Input type="email" value={forgotEmail} onChange={(e) => setForgotEmail(e.target.value)} placeholder="ban@email.com" invalid={!!forgotError} />
          </Field>
        )}
      </Modal>
    </div>
  );
};
