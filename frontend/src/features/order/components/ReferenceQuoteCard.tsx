import { Info, Receipt, Route, Ticket } from 'lucide-react';
import { Alert, Badge, Card, CardHeader, Divider, KeyValue } from '@/components/ui';
import { formatVND } from '@/services/format';
import type { ReferenceQuote } from '@/types';

/** Báo giá tham khảo trả về ngay sau khi khách đặt đơn. */
export const ReferenceQuoteCard = ({ quote }: { quote: ReferenceQuote }) => {
  const { financialSummary: fin, categoryFees: fees } = quote;
  const money = (n: number) => (quote.currency === 'VND' ? formatVND(n) : `${n.toLocaleString('vi-VN')} ${quote.currency}`);
  const tiles = [
    { label: 'Vận chuyển', value: fees.transportFee },
    { label: 'Dịch vụ đã chọn', value: fees.serviceFee },
    { label: 'Phụ phí', value: fees.surchargeFee },
  ];

  return (
    <Card className="lg:col-span-2">
      <CardHeader
        title="Báo giá tham khảo"
        icon={Receipt}
        subtitle="Dự kiến theo thông tin bạn vừa đặt"
        action={<Badge tone="info">Tham khảo</Badge>}
      />

      <div className="mb-4 rounded-2xl bg-slate-900 p-5 text-white">
        <p className="text-xs text-white/60">Tổng chi phí dự kiến</p>
        <p className="mt-1 text-3xl font-bold tracking-tight tabular-nums">{money(fin.totalEstimated)}</p>
        <p className="mt-1 text-xs text-white/50">Đặt cọc khi xác nhận báo giá chính thức: <span className="font-semibold tabular-nums text-white/80">{money(fin.depositRequired)}</span></p>
      </div>

      <div className="mb-4 grid gap-2 sm:grid-cols-3">
        {tiles.map((t) => (
          <div key={t.label} className="rounded-xl border border-slate-200 bg-slate-50/60 p-3">
            <p className="text-xs text-slate-500">{t.label}</p>
            <p className="mt-0.5 text-[15px] font-semibold tabular-nums text-slate-900">{money(t.value)}</p>
          </div>
        ))}
      </div>

      <div className="mb-4 flex items-start gap-2.5 rounded-xl border border-blue-200 bg-blue-50/60 p-3 text-sm text-blue-900">
        <Route className="mt-0.5 size-4 shrink-0 text-blue-600" />
        <p>
          Quãng đường ước tính <b className="tabular-nums">{quote.distanceKm} km</b>. Giá xe đã gồm <b className="tabular-nums">{quote.includedKm} km</b>
          {quote.chargeableExtraKm > 0 ? <>, phần vượt <b className="tabular-nums">{quote.chargeableExtraKm} km</b> (làm tròn lên) được tính phụ phí.</> : <>, chuyến của bạn không phát sinh phụ phí vượt km.</>}
        </p>
      </div>

      <p className="mb-2 text-sm font-semibold text-slate-900">Chi tiết các khoản</p>
      <ul className="divide-y divide-slate-100 rounded-xl border border-slate-200">
        {quote.breakdownItems.map((b, i) => (
          <li key={`${b.serviceName}-${i}`} className="flex items-start gap-3 px-3 py-3 text-sm">
            <div className="min-w-0 flex-1">
              <p className="flex flex-wrap items-center gap-2 font-medium text-slate-900">
                {b.serviceName}
                {b.autoApplied && <Badge tone="warning">Hệ thống tự cộng</Badge>}
              </p>
              <p className="text-xs text-slate-500 tabular-nums">{b.quantity} × {money(b.unitPrice)}</p>
              {b.note && <p className="mt-0.5 text-xs text-slate-400">{b.note}</p>}
            </div>
            <p className="shrink-0 font-semibold tabular-nums">{money(b.totalPrice)}</p>
          </li>
        ))}
      </ul>

      <Divider className="my-4" />
      <KeyValue label="Tạm tính" value={money(fin.subtotal)} />
      {quote.appliedVoucher ? (
        <KeyValue
          label={<span className="inline-flex items-center gap-1.5"><Ticket className="size-3.5" /> Voucher {quote.appliedVoucher.code}</span>}
          value={`−${money(quote.appliedVoucher.discountAmount)}`}
          tone="success"
        />
      ) : (
        fin.discountAmount > 0 && <KeyValue label="Giảm giá" value={`−${money(fin.discountAmount)}`} tone="success" />
      )}
      <KeyValue label="Tổng dự kiến" value={money(fin.totalEstimated)} strong />
      {quote.appliedVoucher && <p className="mt-1 text-xs text-slate-500">{quote.appliedVoucher.description}</p>}

      <Alert tone="info" icon={Info} className="mt-4">{quote.disclaimer}</Alert>
    </Card>
  );
};
