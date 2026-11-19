import type { ReactNode } from 'react';
import { ArrowRight } from 'lucide-react';
import { Badge, cx } from '@/components/ui';
import { SURCHARGE_STATUS_META, surchargeAmount } from '@/constants/surcharge';
import { SitePhotoGrid } from '@/features/order/components/SitePhotoGallery';
import { formatTime, formatVND } from '@/services/format';
import type { Surcharge } from '@/types';

/**
 * Chi tiết một khoản phát sinh hiện trường — dùng chung cho NV, Điều phối và khách:
 * khảo sát ghi → thực tế, mô tả, ảnh bằng chứng, cách tính tiền và ghi chú các bên.
 */
export const SurchargeDetails = ({
  s,
  showStatus = true,
  priceLabel,
  compact = false,
  children,
  className,
}: {
  s: Surcharge;
  /** Dòng con trong một lần báo: ẩn giờ báo (đã hiện ở đầu nhóm). */
  compact?: boolean;
  showStatus?: boolean;
  /** Mặc định hiện "SL × đơn giá = thành tiền"; truyền để thay (VD: "Ước tính"). */
  priceLabel?: string;
  children?: ReactNode;
  className?: string;
}) => {
  const st = SURCHARGE_STATUS_META[s.status];
  return (
    <div className={cx('space-y-3', className)}>
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="text-sm font-semibold text-slate-900">{s.name}</p>
          {!compact && <p className="text-xs text-slate-500 tabular-nums">Báo lúc {formatTime(s.proposedAt)}</p>}
        </div>
        {showStatus && (
          <Badge tone={st.tone} dot>
            {st.label}
          </Badge>
        )}
      </div>

      <div className="grid grid-cols-[1fr_auto_1fr] items-stretch gap-2 text-sm">
        <div className="rounded-xl bg-slate-50 px-3 py-2">
          <p className="text-[11px] font-semibold tracking-wide text-slate-400 uppercase">Khảo sát ghi</p>
          <p className="text-slate-700">{s.surveyed || '—'}</p>
        </div>
        <ArrowRight className="size-4 self-center text-slate-400" />
        <div className="rounded-xl bg-amber-50 px-3 py-2">
          <p className="text-[11px] font-semibold tracking-wide text-amber-600 uppercase">Thực tế</p>
          <p className="font-medium text-amber-900">{s.actual}</p>
        </div>
      </div>

      {s.description && <p className="text-sm text-slate-600">{s.description}</p>}
      {s.photos.length > 0 && <SitePhotoGrid photos={s.photos} />}

      <div className="flex items-center justify-between rounded-xl border border-slate-200 px-3 py-2 text-sm">
        <span className="text-slate-500 tabular-nums">
          {priceLabel ?? `${s.quantity} ${s.unit} × ${formatVND(s.unitPrice)}`}
        </span>
        <span className="font-bold text-slate-900 tabular-nums">+{formatVND(surchargeAmount(s))}</span>
      </div>

      {s.reviewNote && (
        <p className="rounded-xl bg-blue-50 px-3 py-2 text-xs text-blue-900">
          <b>Điều phối:</b> {s.reviewNote}
        </p>
      )}
      {s.customerNote && (
        <p className="rounded-xl bg-rose-50 px-3 py-2 text-xs text-rose-900">
          <b>Khách:</b> {s.customerNote}
        </p>
      )}
      {children}
    </div>
  );
};
