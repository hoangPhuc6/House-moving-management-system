import { AlertOctagon, CheckCircle2 } from 'lucide-react';
import { Button } from '@/components/ui';
import { formatVND } from '@/services/format';
import { useApp } from '@/services/store/AppStore';
import type { Order } from '@/types';

export const FieldAdjustmentBanner = ({ order }: { order: Order }) => {
  const { dispatch } = useApp();
  return (
    <div
      className="mb-4 overflow-hidden rounded-2xl border border-rose-200 bg-gradient-to-br from-rose-50 to-amber-50 p-4 sm:p-5"
      role="alert"
    >
      <div className="flex items-start gap-3">
        <span className="relative grid size-10 shrink-0 place-items-center rounded-xl bg-rose-600 text-white">
          <span className="absolute inset-0 animate-ping rounded-xl bg-rose-500/40" />
          <AlertOctagon className="relative size-5" />
        </span>
        <div className="text-sm leading-relaxed text-rose-950">
          <p className="font-semibold">Báo giá điều chỉnh tại hiện trường</p>
          <p className="mt-0.5">
            Đội thi công phát hiện phát sinh thực tế (
            <b>Hẻm sâu 200m + Tháo lắp tủ gỗ</b>). Vui lòng xác nhận để đội xe
            bắt đầu vận chuyển.
          </p>
        </div>
      </div>
      <Button
        variant="danger"
        size="lg"
        block
        icon={CheckCircle2}
        className="mt-4 sm:w-auto"
        onClick={() =>
          dispatch({ type: "CONFIRM_FIELD_ADJUSTMENT", orderId: order.id })
        }
      >
        Xác nhận báo giá điều chỉnh (+
        {formatVND(order.pendingAdjustment?.unitPrice ?? 350_000)})
      </Button>
    </div>
  );
};
