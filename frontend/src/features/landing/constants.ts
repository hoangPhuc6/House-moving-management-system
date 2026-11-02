import {
  BadgeCheck,
  Building2,
  Calendar,
  FileText,
  type LucideIcon,
  ScanLine,
  Store,
  Truck,
} from 'lucide-react';
import type { HouseType } from '@/types';

export const LINKS = [
  { href: '#features', label: 'Dịch vụ' },
  { href: '#process', label: 'Quy trình' },
  { href: '#pricing', label: 'Bảng giá' },
  { href: '#faq', label: 'Hỏi đáp' },
];

export const PLANS = [
  { name: 'Căn hộ nhỏ', desc: 'Studio, 1 phòng ngủ', price: 1_900_000, items: ['Xe tải 1,25 tấn', '2 nhân công bốc xếp', '10 thùng carton', 'Bảo hiểm đồ đạc 20 triệu'] },
  { name: 'Gia đình', desc: '2–3 phòng ngủ, nhà phố', price: 3_800_000, items: ['Xe tải 2 tấn', '4 nhân công + tháo lắp cơ bản', '20 thùng carton + màng PE', 'Bảo hiểm đồ đạc 50 triệu', 'Theo dõi tiến độ trực tiếp'], featured: true },
  { name: 'Văn phòng', desc: 'Doanh nghiệp, cửa hàng', price: 8_500_000, items: ['Xe tải 5 tấn hoặc nhiều xe', 'Đội ngũ 6–10 người', 'Đóng gói thiết bị IT', 'Làm ngoài giờ & cuối tuần', 'Quản lý dự án riêng'] },
];

export const REVIEWS = [
  { name: 'Chị Minh Thư', place: 'Q.3 → Thủ Đức', text: 'Báo giá rõ từng dòng, phát sinh hẻm sâu cũng được báo trước để mình xác nhận. Không bị "chặt chém" như lần trước.' },
  { name: 'Anh Quốc Huy', place: 'Văn phòng 40 người, Q.1', text: 'Đội làm việc buổi tối, sáng hôm sau văn phòng đã chạy bình thường. Xem được tiến độ từng bước trên điện thoại rất tiện.' },
  { name: 'Cô Thanh Hương', place: 'Bình Thạnh → Q.7', text: 'Tủ lạnh bị trục trặc, gửi khiếu nại kèm ảnh, 2 ngày đã được bồi thường. Rất chuyên nghiệp.' },
];

export const FAQS = [
  ['Báo giá có thay đổi vào ngày chuyển không?', 'Không, trừ khi hiện trường khác với khảo sát (VD: hẻm sâu hơn, đồ cần tháo lắp). Khi đó đội xe gửi báo giá điều chỉnh và chỉ bắt đầu vận chuyển sau khi bạn xác nhận trên web.'],
  ['Tôi có thể dời lịch hoặc hủy đơn không?', 'Bạn tự dời lịch miễn phí khi còn trên 12 giờ trước giờ khởi hành. Hủy trước 12 giờ được hoàn cọc 100%; dưới 12 giờ có thể chọn bảo lưu cọc trong 3 tháng.'],
  ['Phí chờ được tính như thế nào?', '30 phút chờ đầu tiên hoàn toàn miễn phí. Sau đó tính 150.000đ cho mỗi giờ bắt đầu, hiển thị minh bạch trên hóa đơn.'],
  ['Nếu đồ đạc bị hư hỏng thì sao?', 'Mọi đồ đạc được chụp ảnh trước khi đóng gói. Bạn gửi khiếu nại kèm ảnh, chúng tôi đối chiếu và phản hồi trong 48 giờ, bồi thường theo hạn mức bảo hiểm.'],
];

export const FOOTER_COLS: { title: string; links: string[] }[] = [
  { title: 'Dịch vụ', links: ['Chuyển nhà trọn gói', 'Chuyển văn phòng', 'Chuyển kho xưởng', 'Đóng gói & lưu kho'] },
  { title: 'Công ty', links: ['Về chúng tôi', 'Tuyển dụng', 'Đối tác vận tải', 'Tin tức'] },
  { title: 'Hỗ trợ', links: ['Trung tâm trợ giúp', 'Chính sách hủy đơn', 'Chính sách bảo hiểm', 'Điều khoản dịch vụ'] },
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
