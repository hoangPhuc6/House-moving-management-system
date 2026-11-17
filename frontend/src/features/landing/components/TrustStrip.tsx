import { CalendarClock, Clock, Headset, QrCode, ShieldCheck } from 'lucide-react';

/** Các cam kết đang áp dụng trong quy trình đặt và thực hiện đơn. */
const COMMITMENTS = [
  { icon: Headset, label: 'Khảo sát miễn phí' },
  { icon: QrCode, label: 'Cọc 500.000đ qua VietQR' },
  { icon: CalendarClock, label: 'Dời lịch miễn phí trước 12h' },
  { icon: Clock, label: '30 phút chờ đầu miễn phí' },
  { icon: ShieldCheck, label: 'Bồi thường theo biên bản' },
];

export const TrustStrip = () => (
  <section className="border-b border-slate-200 bg-white py-8">
    <div className="mx-auto grid max-w-7xl grid-cols-2 gap-x-6 gap-y-4 px-4 sm:grid-cols-3 sm:px-6 lg:grid-cols-5">
      {COMMITMENTS.map(({ icon: Icon, label }) => (
        <div key={label} className="flex items-center justify-center gap-2 text-sm font-semibold text-slate-600">
          <Icon className="size-4 shrink-0 text-brand" /> {label}
        </div>
      ))}
    </div>
  </section>
);
