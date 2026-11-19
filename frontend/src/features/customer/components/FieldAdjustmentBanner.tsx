import { useState } from 'react';
import { AlertOctagon, CheckCircle2, MessageSquareX, PhoneCall } from 'lucide-react';
import { Button, controlBase, cx } from '@/components/ui';
import { groupSurchargesByReport, surchargeAmount, sumSurcharges } from '@/constants/surcharge';
import { SurchargeDetails } from '@/features/order/components/SurchargeDetails';
import { formatVND } from '@/services/format';
import { useApp } from '@/services/store/AppStore';
import type { Order, Surcharge } from '@/types';

/**
 * Phát sinh hiện trường chờ khách xác nhận (Điều phối đã duyệt), gom theo lần báo:
 * xem lý do, ảnh, cách tính; "Đồng ý tất cả" một lần hoặc đồng ý / từ chối từng dòng.
 */
export const FieldAdjustmentBanner = ({ order }: { order: Order }) => {
  const { dispatch } = useApp();
  const visible = order.surcharges.filter((s) => s.status === 'WAITING_CUSTOMER' || s.status === 'REJECTED_BY_CUSTOMER');
  if (visible.length === 0) return null;
  const waiting = visible.filter((s) => s.status === 'WAITING_CUSTOMER');
  const total = sumSurcharges(waiting);
  const reports = groupSurchargesByReport(visible);

  return (
    <div className="mb-4 overflow-hidden rounded-2xl border border-rose-200 bg-gradient-to-br from-rose-50 to-amber-50 p-4 sm:p-5" role="alert">
      <div className="flex flex-wrap items-start gap-3">
        <span className="relative grid size-10 shrink-0 place-items-center rounded-xl bg-rose-600 text-white">
          {waiting.length > 0 && <span className="absolute inset-0 animate-ping rounded-xl bg-rose-500/40" />}
          <AlertOctagon className="relative size-5" />
        </span>
        <div className="min-w-0 flex-1 text-sm leading-relaxed text-rose-950">
          <p className="font-semibold">Phát sinh tại hiện trường</p>
          <p className="mt-0.5">
            {waiting.length > 0 ? (
              <>
                Đội thi công phát hiện thực tế khác khảo sát; điều phối viên đã kiểm tra và đề nghị thêm{' '}
                <b className="tabular-nums">{formatVND(total)}</b> ({waiting.length} khoản). Đội xe tạm dừng tới khi bạn phản hồi.
              </>
            ) : (
              'Bạn đã từ chối khoản phát sinh. Điều phối viên sẽ gọi để thống nhất phương án.'
            )}
          </p>
        </div>
        {waiting.length > 1 && (
          <Button
            icon={CheckCircle2}
            className="w-full sm:w-auto"
            onClick={() => dispatch({ type: 'CUSTOMER_ACCEPT_SURCHARGE', orderId: order.id, surchargeIds: waiting.map((s) => s.id) })}
          >
            Đồng ý tất cả +{formatVND(total)}
          </Button>
        )}
      </div>

      <div className="mt-4 space-y-3">
        {reports.map((r) => (
          <div key={r.reportId} className="space-y-3 rounded-xl bg-white/80 p-3 ring-1 ring-rose-100 sm:p-4">
            {r.note && <p className="text-sm text-slate-700">{r.note}</p>}
            <ul className={cx('space-y-3', r.note && 'border-t border-slate-100 pt-3')}>
              {r.lines.map((s) => (
                <li key={s.id} className="border-slate-100 [&:not(:first-child)]:border-t [&:not(:first-child)]:pt-3">
                  {s.status === 'WAITING_CUSTOMER' ? (
                    <WaitingLine order={order} s={s} primary={waiting.length === 1} />
                  ) : (
                    <SurchargeDetails s={s} compact>
                      <p className="flex items-center gap-1.5 text-xs font-medium text-slate-600">
                        <PhoneCall className="size-3.5" /> Điều phối viên sẽ liên hệ bạn để thương lượng lại.
                      </p>
                    </SurchargeDetails>
                  )}
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
    </div>
  );
};

const WaitingLine = ({ order, s, primary }: { order: Order; s: Surcharge; primary: boolean }) => {
  const { dispatch } = useApp();
  const [rejecting, setRejecting] = useState(false);
  const [note, setNote] = useState('');
  const [submitted, setSubmitted] = useState(false);

  return (
    <SurchargeDetails s={s} compact showStatus={false}>
      {rejecting ? (
        <div className="space-y-2">
          <textarea
            value={note}
            onChange={(e) => setNote(e.target.value)}
            rows={2}
            placeholder="Lý do bạn không đồng ý khoản này (VD: hẻm đã báo khi khảo sát)…"
            className={cx(controlBase, 'h-auto py-2', submitted && !note.trim() && 'border-rose-400')}
          />
          {submitted && !note.trim() && <p className="text-xs text-rose-600">Vui lòng nhập lý do.</p>}
          <div className="flex flex-wrap gap-2">
            <Button
              size="sm"
              variant="danger"
              icon={MessageSquareX}
              onClick={() => {
                setSubmitted(true);
                if (note.trim()) dispatch({ type: 'CUSTOMER_REJECT_SURCHARGE', orderId: order.id, surchargeId: s.id, note });
              }}
            >
              Gửi từ chối
            </Button>
            <Button size="sm" variant="secondary" onClick={() => setRejecting(false)}>
              Quay lại
            </Button>
          </div>
        </div>
      ) : (
        <div className="flex flex-wrap gap-2">
          <Button size={primary ? 'md' : 'sm'} variant={primary ? 'primary' : 'secondary'} icon={CheckCircle2} onClick={() => dispatch({ type: 'CUSTOMER_ACCEPT_SURCHARGE', orderId: order.id, surchargeIds: [s.id] })}>
            Đồng ý khoản này +{formatVND(surchargeAmount(s))}
          </Button>
          <Button size="sm" variant="secondary" icon={MessageSquareX} onClick={() => setRejecting(true)}>
            Từ chối
          </Button>
        </div>
      )}
    </SurchargeDetails>
  );
};
