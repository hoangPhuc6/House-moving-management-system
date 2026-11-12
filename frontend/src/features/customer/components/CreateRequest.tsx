import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, ArrowRight, Bookmark, RotateCcw, Save, Send } from 'lucide-react';
import { Alert, Badge, Button, cx } from '@/components/ui';
import { WIZARD_STEPS } from '@/features/customer/booking/constants';
import { WizardStepper } from '@/features/customer/booking/parts';
import { StepLocation } from '@/features/customer/booking/StepLocation';
import { StepReview } from '@/features/customer/booking/StepReview';
import { StepServices } from '@/features/customer/booking/StepServices';
import { useBookingWizard, validateStep } from '@/features/customer/booking/useBookingWizard';
import { WizardSidebar } from '@/features/customer/booking/WizardSidebar';
import { StickyActionBar } from '@/layouts/MobileShell';
import { ROUTES } from '@/routes/paths';
import { useApp } from '@/services/store/AppStore';

const STEP_HEADINGS = [
  { eyebrow: 'Quy trình tiếp nhận yêu cầu tiêu chuẩn', title: 'Tạo yêu cầu chuyển nhà', subtitle: 'Điền thông tin để chúng tôi tư vấn khảo sát tận nơi và gửi báo giá minh bạch, trọn gói phù hợp nhất với nhu cầu của bạn.' },
  { eyebrow: 'Bước 2 • Tùy biến giải pháp chuyển dọn', title: 'Chọn dịch vụ', subtitle: 'Trọn gói: chuyên viên khảo sát và đề xuất xe, nhân sự. Chỉ thuê xe: chọn ngay loại xe và số nhân viên bốc vác.' },
  { eyebrow: 'Bước 3 • Xác nhận', title: 'Kiểm tra thông tin & Gửi yêu cầu', subtitle: 'Vui lòng rà soát lại lộ trình, gói dịch vụ và hình thức khảo sát trước khi chuyển tới bộ phận điều phối.' },
];

const scrollTop = () => window.scrollTo({ top: 0, behavior: 'smooth' });

/** Wizard đặt lịch chuyển nhà 3 bước: Địa điểm → Dịch vụ & Khảo sát → Xác nhận. Danh mục đồ & ảnh được chốt ở giai đoạn khảo sát. */
export const CreateRequest = () => {
  const { dispatch } = useApp();
  const navigate = useNavigate();
  const wizard = useBookingWizard();
  const [step, setStep] = useState(0);
  const [maxReached, setMaxReached] = useState(0);
  const [attempted, setAttempted] = useState<Set<number>>(() => new Set());
  const [showRestored, setShowRestored] = useState(wizard.restored);

  const errorsFor = (s: number) => validateStep(wizard.draft, s);
  const errors = attempted.has(step) ? errorsFor(step) : {};
  const last = step === WIZARD_STEPS.length - 1;
  const heading = STEP_HEADINGS[step]!;

  const goTo = (s: number) => {
    setStep(s);
    scrollTop();
  };

  const next = () => {
    setAttempted((a) => new Set(a).add(step));
    if (Object.keys(errorsFor(step)).length > 0) {
      dispatch({ type: 'PUSH_TOAST', tone: 'warning', message: 'Vui lòng hoàn thiện các trường bắt buộc được đánh dấu.' });
      return;
    }
    if (!last) {
      setMaxReached((m) => Math.max(m, step + 1));
      goTo(step + 1);
      return;
    }
    // Kiểm tra lại toàn bộ các bước trước khi gửi (phòng khi khách sửa ngược).
    const broken = WIZARD_STEPS.findIndex((_, i) => Object.keys(errorsFor(i)).length > 0);
    if (broken !== -1) {
      setAttempted((a) => new Set(a).add(broken));
      goTo(broken);
      return;
    }
    dispatch({ type: 'CREATE_ORDER', input: wizard.buildOrderInput() });
    wizard.clearDraft();
    navigate(ROUTES.customer.orders);
  };

  const back = () => step > 0 && goTo(step - 1);

  const saveDraft = () =>
    dispatch(
      wizard.saveDraft()
        ? { type: 'PUSH_TOAST', tone: 'success', message: 'Đã lưu nháp. Bạn có thể quay lại hoàn tất bất cứ lúc nào' }
        : { type: 'PUSH_TOAST', tone: 'warning', message: 'Trình duyệt không cho phép lưu nháp.' },
    );

  const nav = { step, onNext: next, onBack: back, onSaveDraft: saveDraft };

  return (
    <div className="pb-24 md:pb-4">
      <div className="mb-5 flex flex-wrap items-end justify-between gap-4 px-1">
        <div className="min-w-0 max-w-2xl">
          <p className="mb-1 text-[11px] font-semibold uppercase tracking-wider text-brand">{heading.eyebrow}</p>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-[28px]">{heading.title}</h1>
          <p className="mt-1 text-sm text-slate-500">{heading.subtitle}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Badge tone="neutral" icon={Save}>Có thể lưu nháp</Badge>
          <Button variant="secondary" size="sm" icon={Bookmark} onClick={saveDraft}>
            Lưu nháp đơn
          </Button>
        </div>
      </div>

      {showRestored && (
        <Alert
          tone="info"
          title="Đã khôi phục bản nháp trước đó"
          className="mb-4"
          action={
            <Button
              size="sm"
              variant="secondary"
              icon={RotateCcw}
              onClick={() => {
                wizard.resetDraft();
                setStep(0);
                setMaxReached(0);
                setAttempted(new Set());
                setShowRestored(false);
              }}
            >
              Bắt đầu lại
            </Button>
          }
        >
          Thông tin bạn nhập lần trước đã được điền sẵn. Ảnh đồ đạc cần tải lên lại.
        </Alert>
      )}

      <div className="mb-4">
        <WizardStepper step={step} maxReached={maxReached} onJump={goTo} />
      </div>

      <form
        noValidate
        onSubmit={(e) => {
          e.preventDefault();
          next();
        }}
        className="grid items-start gap-4 lg:grid-cols-[minmax(0,1fr)_320px]"
      >
        <div key={step} className="anim-enter min-w-0">
          {step === 0 && <StepLocation wizard={wizard} errors={errors} />}
          {step === 1 && <StepServices wizard={wizard} errors={errors} />}
          {step === 2 && <StepReview wizard={wizard} errors={errors} onEdit={goTo} />}

          <div className="mt-4 hidden items-center justify-between gap-3 rounded-2xl border border-slate-200 bg-white px-5 py-4 md:flex">
            <p className="text-xs text-slate-500">
              {last ? 'Bước cuối cùng: miễn phí hủy lịch trước 24 giờ so với thời điểm vận chuyển.' : `Bước ${step + 1}/${WIZARD_STEPS.length} — thông tin được giữ lại khi chuyển bước.`}
            </p>
            <div className="flex shrink-0 gap-2">
              {step > 0 && (
                <Button variant="secondary" icon={ArrowLeft} onClick={back}>
                  Quay lại Bước {step}
                </Button>
              )}
              <Button type="submit" variant={last ? 'primary' : 'dark'} icon={last ? Send : undefined}>
                {last ? 'Gửi yêu cầu chuyển nhà' : 'Tiếp tục'}
                {!last && <ArrowRight className="size-4" />}
              </Button>
            </div>
          </div>
        </div>

        <aside className="lg:sticky lg:top-20">
          <WizardSidebar wizard={wizard} nav={nav} onEdit={goTo} />
        </aside>

        {/* pb-7: chừa chỗ cho nút "Đặt mới" nổi của thanh điều hướng dưới, tránh đè lên nút. */}
        <StickyActionBar className="pb-7 shadow-[0_-4px_16px_rgba(15,23,42,0.06)] md:hidden">
          <div className="mb-2 flex items-center justify-between text-xs">
            <span className="font-semibold text-slate-900">
              Bước {step + 1}/{WIZARD_STEPS.length}
              <span className="font-medium text-slate-500"> · {WIZARD_STEPS[step]!.title}</span>
            </span>
            <span className="flex gap-1" aria-hidden>
              {WIZARD_STEPS.map((s, i) => (
                <span key={s.title} className={cx('h-1.5 rounded-full transition-all', i === step ? 'w-5 bg-brand' : i < step ? 'w-1.5 bg-brand' : 'w-1.5 bg-slate-200')} />
              ))}
            </span>
          </div>
          <div className="flex items-center gap-2">
            {step > 0 && (
              <Button variant="secondary" icon={ArrowLeft} onClick={back} className="h-12 shrink-0 px-4">
                Quay lại
              </Button>
            )}
            <Button type="submit" block variant={last ? 'primary' : 'dark'} icon={last ? Send : undefined} className="h-12 min-w-0 flex-1">
              <span className="truncate">{last ? 'Gửi yêu cầu' : 'Tiếp tục'}</span>
              {!last && <ArrowRight className="size-4 shrink-0" />}
            </Button>
          </div>
        </StickyActionBar>
      </form>
    </div>
  );
};
