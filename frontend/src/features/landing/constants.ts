import {
  BadgeCheck,
  Building2,
  Calendar,
  FileText,
  Headset,
  type LucideIcon,
  ScanLine,
  ShieldCheck,
  Store,
  Truck,
  UserRound,
} from 'lucide-react';
import type { HouseType } from '@/types';

export const LINKS = [
  { href: '#features', label: 'Dịch vụ' },
  { href: '#process', label: 'Quy trình' },
  { href: '#roles', label: 'Vai trò' },
  { href: '#pricing', label: 'Bảng giá' },
  { href: '#faq', label: 'Hỏi đáp' },
];

export const FAQS = [
  ['Báo giá có thay đổi vào ngày chuyển không?', 'Không, trừ khi hiện trường khác với khảo sát (VD: hẻm sâu hơn, đồ cần tháo lắp). Khi đó đội xe gửi báo giá điều chỉnh và chỉ tiếp tục vận chuyển sau khi bạn xác nhận trên web.'],
  ['Báo giá được giữ trong bao lâu?', 'Báo giá có hiệu lực 24 giờ. Bạn đồng ý và đặt cọc 500.000đ qua VietQR để giữ lịch xe; quá 24 giờ chưa chốt, đơn tự động hủy.'],
  ['Tôi có thể dời lịch hoặc hủy đơn không?', 'Bạn tự dời lịch miễn phí khi còn trên 12 giờ trước giờ khởi hành. Hủy trước 12 giờ được hoàn cọc 100%; dưới 12 giờ có thể chọn bảo lưu cọc trong 3 tháng.'],
  ['Phí chờ được tính như thế nào?', '30 phút chờ đầu tiên hoàn toàn miễn phí. Sau đó tính 150.000đ cho mỗi giờ bắt đầu, hiển thị minh bạch trên hóa đơn.'],
  ['Nếu đồ đạc bị hư hỏng thì sao?', 'Sau khi bàn giao và thanh toán đủ, bạn gửi khiếu nại kèm ảnh/video và số tiền đề nghị bồi thường ở mục "Hỗ trợ sau chuyển nhà". Admin xem xét bằng chứng và duyệt số tiền bồi thường.'],
];

export const FOOTER_COLS: { title: string; links: { label: string; href: string }[] }[] = [
  { title: 'Dịch vụ', links: [{ label: 'Chuyển nhà trọn gói', href: '#pricing' }, { label: 'Chỉ thuê xe tải', href: '#pricing' }, { label: 'Báo giá nhanh', href: '#top' }] },
  { title: 'Hệ thống', links: [{ label: 'Quy trình 7 bước', href: '#process' }, { label: 'Vai trò người dùng', href: '#roles' }, { label: 'Tính năng', href: '#features' }] },
  { title: 'Hỗ trợ', links: [{ label: 'Chính sách dời lịch & hủy đơn', href: '#faq' }, { label: 'Phí chờ', href: '#faq' }, { label: 'Khiếu nại & bồi thường', href: '#faq' }] },
];

/** Luồng công việc thực tế của từng vai trò trong ứng dụng (khớp menu & thao tác của từng role). */
export const ROLE_FLOWS: { id: string; role: string; app: string; icon: LucideIcon; summary: string; steps: { title: string; desc: string }[] }[] = [
  {
    id: 'customer',
    role: 'Khách hàng',
    app: 'Web & mobile',
    icon: UserRound,
    summary: 'Đặt lịch, chốt báo giá, theo dõi và thanh toán — tất cả trên một trang đơn hàng.',
    steps: [
      { title: 'Đặt lịch 3 bước', desc: 'Địa điểm & thời gian → chọn gói trọn gói hoặc chỉ thuê xe → xác nhận & gửi. Nhận ngay báo giá tham khảo.' },
      { title: 'Chọn hình thức khảo sát', desc: 'Video call ~10 phút, khảo sát trực tiếp tận nơi, hoặc tự tải ảnh / video đồ đạc. Miễn phí.' },
      { title: 'Đồng ý báo giá & đặt cọc', desc: 'Xem báo giá chi tiết từng dòng, đặt cọc 500.000đ qua VietQR trong 24 giờ để giữ lịch xe.' },
      { title: 'Theo dõi tiến độ trực tiếp', desc: 'Xem đội xe ở mốc nào, xác nhận báo giá điều chỉnh khi có phát sinh hiện trường, tự dời lịch trước 12 giờ.' },
      { title: 'Thanh toán & hỗ trợ sau chuyển', desc: 'Thanh toán phần còn lại qua QR, gửi khiếu nại kèm ảnh nếu có hư hỏng.' },
    ],
  },
  {
    id: 'staff',
    role: 'Nhân viên vận chuyển',
    app: 'Ứng dụng di động',
    icon: Truck,
    summary: 'Nhận lệnh việc, cập nhật hiện trường và xử lý phát sinh ngay trên điện thoại.',
    steps: [
      { title: 'Nhận lệnh việc', desc: 'Danh sách lệnh trong ca với xe, nhân viên bốc xếp, vật tư và dụng cụ xuất kho.' },
      { title: 'Khảo sát trực tiếp', desc: 'Khi Điều phối yêu cầu, đến nhà khách đo đạc và gửi số liệu khảo sát.' },
      { title: 'Cập nhật tiến độ hiện trường', desc: 'Nhận vật tư tại kho → bắt đầu → đang di chuyển → đã tới nơi → lập biên bản bàn giao.' },
      { title: 'Xử lý phát sinh', desc: 'Báo sai lệch hiện trường để khách xác nhận giá điều chỉnh, bật đồng hồ chờ khách, báo xe hỏng.' },
    ],
  },
  {
    id: 'coordinator',
    role: 'Điều phối viên',
    app: 'Bảng điều khiển',
    icon: Headset,
    summary: 'Quản lý vòng đời đơn, lập báo giá và phân bổ xe, nhân sự theo khung giờ.',
    steps: [
      { title: 'Hàng đợi & Kanban', desc: 'Theo dõi đơn qua 6 cột trạng thái, từ chờ khảo sát đến đóng / hủy.' },
      { title: 'Khảo sát & lập báo giá', desc: 'Lập báo giá từ ảnh / video call hoặc cử nhân viên khảo sát trực tiếp. Mọi chỉnh sửa ghi vào Audit Log.' },
      { title: 'Điều phối nguồn lực', desc: 'Gán xe & nhân sự trên bảng theo giờ, hệ thống cảnh báo trùng lịch và chặn xe không khả dụng.' },
      { title: 'Xử lý sự cố khẩn cấp', desc: 'Khi xe hỏng giữa đường, chuyển giao phương tiện (tăng bo) sang xe thay thế.' },
    ],
  },
  {
    id: 'admin',
    role: 'Admin',
    app: 'Kho & Tài chính',
    icon: ShieldCheck,
    summary: 'Quyết toán, kiểm soát dòng tiền và quản trị danh mục của toàn hệ thống.',
    steps: [
      { title: 'Dashboard & báo cáo', desc: 'Doanh thu, booking theo trạng thái và các chỉ số vận hành.' },
      { title: 'Thanh toán & công nợ', desc: 'Xác nhận giao dịch, theo dõi khoản còn phải thu của từng đơn.' },
      { title: 'Vật tư & đóng hồ sơ', desc: 'Xác nhận thu hồi dụng cụ, công nợ = 0đ mới được đóng hồ sơ và khóa doanh thu.' },
      { title: 'Hoàn tiền & khiếu nại', desc: 'Duyệt phiếu chi hoàn cọc, xem bằng chứng và duyệt số tiền bồi thường.' },
      { title: 'Quản trị danh mục', desc: 'Kho & dụng cụ, quản lý xe, dịch vụ & bảng giá, tài khoản & phân quyền.' },
    ],
  },
];

// Toạ độ giả lập (km) để tính quãng đường ước tính giữa các khu vực.
export const AREAS: { id: string; label: string; x: number; y: number }[] = [
  { id: "q1", label: "Quận 1", x: 0, y: 0 },
  { id: "q3", label: "Quận 3", x: -2, y: 1.5 },
  { id: "q7", label: "Quận 7", x: 1, y: -7 },
  { id: "bt", label: "Bình Thạnh", x: 2, y: 3 },
  { id: "td", label: "TP. Thủ Đức", x: 9, y: 6 },
  { id: "gv", label: "Gò Vấp", x: -2, y: 7 },
  { id: "tb", label: "Tân Bình", x: -5, y: 3 },
  { id: "nb", label: "Nhà Bè", x: 2, y: -12 },
];

export const HOUSE_SURCHARGE: Record<HouseType, number> = {
  GROUND_FLOOR: 0,
  APARTMENT: 250_000,
  DEEP_ALLEY: 400_000,
};

export const STEP_ICONS: LucideIcon[] = [
  Calendar,
  ScanLine,
  FileText,
  Building2,
  Truck,
  BadgeCheck,
  Store,
];
