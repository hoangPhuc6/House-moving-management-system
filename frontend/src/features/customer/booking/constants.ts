import {
  Armchair,
  Bed,
  Box,
  Fan,
  Monitor,
  Package,
  Refrigerator,
  Shirt,
  Truck,
  Video,
  WashingMachine,
  Camera,
  MapPinned,
  type LucideIcon,
} from 'lucide-react';
import type { AddressDetail, HouseType, ServiceMode, SurveyPreference } from '@/types';

export const WIZARD_STEPS: { title: string; subtitle: string }[] = [
  { title: 'Địa điểm & Thời gian', subtitle: 'Xuất phát, đích & giờ dọn' },
  { title: 'Dịch vụ', subtitle: 'Đóng gói & bốc xếp' },
  { title: 'Đồ đạc & Khảo sát', subtitle: 'Danh mục tài sản' },
  { title: 'Xác nhận & Gửi', subtitle: 'Kiểm tra & gửi đơn' },
];

/** Địa giới hành chính rút gọn cho bản demo — thay bằng API địa chỉ khi có backend. */
export const ADMIN_AREAS: Record<string, Record<string, string[]>> = {
  'TP. Hồ Chí Minh': {
    'Quận 1': ['Phường Bến Nghé', 'Phường Bến Thành', 'Phường Đa Kao', 'Phường Tân Định'],
    'Quận 3': ['Phường Võ Thị Sáu', 'Phường 9', 'Phường 12'],
    'Quận 7': ['Phường Tân Phong', 'Phường Tân Phú', 'Phường Tân Hưng'],
    'Quận Bình Thạnh': ['Phường 22', 'Phường 25', 'Phường 26', 'Phường 27'],
    'Quận Gò Vấp': ['Phường 5', 'Phường 10', 'Phường 17'],
    'TP. Thủ Đức': ['Phường Thảo Điền', 'Phường An Phú', 'Phường Long Thạnh Mỹ', 'Phường Linh Trung'],
  },
  'Hà Nội': {
    'Quận Cầu Giấy': ['Phường Dịch Vọng', 'Phường Mai Dịch', 'Phường Nghĩa Đô', 'Phường Trung Hòa'],
    'Quận Đống Đa': ['Phường Láng Hạ', 'Phường Kim Liên', 'Phường Ô Chợ Dừa'],
    'Quận Hoàn Kiếm': ['Phường Hàng Bạc', 'Phường Tràng Tiền', 'Phường Lý Thái Tổ'],
    'Quận Hà Đông': ['Phường Mộ Lao', 'Phường Văn Quán', 'Phường La Khê'],
  },
  'Đà Nẵng': {
    'Quận Hải Châu': ['Phường Thạch Thang', 'Phường Hòa Cường Bắc', 'Phường Bình Hiên'],
    'Quận Sơn Trà': ['Phường An Hải Bắc', 'Phường Phước Mỹ', 'Phường Mân Thái'],
  },
};

/** Loại bất động sản & lối đi — mỗi lựa chọn quy về một HouseType để tính phụ phí. */
export const PROPERTY_TYPES: { label: string; houseType: HouseType }[] = [
  { label: 'Nhà mặt tiền (xe đỗ sát cửa)', houseType: 'GROUND_FLOOR' },
  { label: 'Nhà trong ngõ/hẻm (xe tải vào được)', houseType: 'GROUND_FLOOR' },
  { label: 'Nhà trong hẻm sâu > 100m', houseType: 'DEEP_ALLEY' },
  { label: 'Chung cư có thang máy', houseType: 'APARTMENT' },
  { label: 'Chung cư / nhà tập thể thang bộ', houseType: 'APARTMENT' },
  { label: 'Văn phòng / mặt bằng kinh doanh', houseType: 'GROUND_FLOOR' },
];

export const FLOOR_OPTIONS = ['Tầng trệt', 'Tầng 2 (thang bộ)', 'Tầng 3 (thang bộ)', 'Tầng 4+ (thang bộ)', 'Tầng cao (thang máy)'];

export const houseTypeOf = (propertyType: string): HouseType =>
  PROPERTY_TYPES.find((p) => p.label === propertyType)?.houseType ?? 'GROUND_FLOOR';

export const emptyAddress = (): AddressDetail => ({
  province: 'TP. Hồ Chí Minh',
  district: '',
  ward: '',
  street: '',
  propertyType: PROPERTY_TYPES[0]!.label,
  floor: FLOOR_OPTIONS[0]!,
  note: '',
});

export const isAddressComplete = (a: AddressDetail): boolean => Boolean(a.province && a.district && a.ward && a.street.trim());

export const formatAddress = (a: AddressDetail): string => [a.street.trim(), a.ward, a.district, a.province].filter(Boolean).join(', ');

export const SERVICE_MODES: Record<ServiceMode, { title: string; badge: string; desc: string; perks: string[]; icon: LucideIcon }> = {
  FULL_PACKAGE: {
    title: 'Chuyển nhà trọn gói',
    badge: 'Khuyên dùng',
    desc: 'Đội ngũ bốc xếp, tháo dỡ nội thất cơ bản, bọc đồ chống xước và bố trí xe tải chuyên dụng theo lộ trình riêng.',
    perks: ['Đội bốc xếp chuyên nghiệp 2 – 4 nhân sự', 'Hỗ trợ tháo lắp giường tủ, bàn ghế thông dụng', 'Cam kết bồi thường 100% tài sản theo biên bản'],
    icon: Package,
  },
  TRUCK_ONLY: {
    title: 'Chỉ thuê xe tải vận chuyển',
    badge: 'Tiết kiệm',
    desc: 'Dành cho gia chủ đã tự đóng gói và có người khiêng vác. Xe tới đúng giờ, tài xế hỗ trợ chằng buộc thùng hàng an toàn.',
    perks: ['Xe thùng kín, bửng nâng hạ tải trọng linh hoạt', 'Tài xế thông thạo lộ trình, tối ưu thời gian', 'Giám sát định vị xe di chuyển theo thời gian thực'],
    icon: Truck,
  },
};

/** Tên dịch vụ nhân công được tự thêm khi chọn gói trọn gói. */
export const LABOR_SERVICE_KEYWORD = 'bốc xếp';

export const HOME_SCALES: { label: string; hint: string; preset: Record<string, number> }[] = [
  { label: 'Phòng trọ / Studio', hint: '≈ 20 – 30 m²', preset: { 'Tủ lạnh': 1, 'Máy giặt': 1, 'Giường ngủ': 1, 'Thùng carton đồ cá nhân': 8 } },
  { label: '1 phòng ngủ', hint: '≈ 40 – 55 m²', preset: { 'Tủ lạnh': 1, 'Máy giặt': 1, 'Giường ngủ': 1, 'Tủ quần áo': 1, 'Tivi': 1, 'Thùng carton đồ cá nhân': 12 } },
  { label: '2 phòng ngủ', hint: '≈ 65 – 80 m²', preset: { 'Tủ lạnh': 1, 'Máy giặt': 1, 'Giường ngủ': 2, 'Tủ quần áo': 1, 'Bộ sofa + bàn trà': 1, 'Tivi': 1, 'Thùng carton đồ cá nhân': 18 } },
  { label: '3+ phòng ngủ', hint: '> 90 m²', preset: { 'Tủ lạnh': 1, 'Máy giặt': 1, 'Giường ngủ': 3, 'Tủ quần áo': 2, 'Bộ sofa + bàn trà': 1, 'Tivi': 2, 'Máy lạnh': 2, 'Thùng carton đồ cá nhân': 25 } },
  { label: 'Nhà nguyên căn / Văn phòng', hint: 'Khối lượng lớn', preset: { 'Bàn làm việc': 6, 'Máy lạnh': 3, 'Tủ lạnh': 1, 'Bộ sofa + bàn trà': 1, 'Thùng carton đồ cá nhân': 30 } },
];

export interface InventoryPreset {
  name: string;
  hint: string;
  volumeM3: number;
  fragile: boolean;
  icon: LucideIcon;
}

export const INVENTORY_PRESETS: InventoryPreset[] = [
  { name: 'Tủ lạnh', hint: 'Cần giữ thẳng đứng, bọc kỹ', volumeM3: 1.2, fragile: false, icon: Refrigerator },
  { name: 'Máy giặt', hint: 'Kèm tháo/lắp vòi cấp nước', volumeM3: 0.6, fragile: false, icon: WashingMachine },
  { name: 'Giường ngủ', hint: 'Thường cần tháo lắp mộc', volumeM3: 1.5, fragile: false, icon: Bed },
  { name: 'Tủ quần áo', hint: 'Gỗ công nghiệp / gỗ tự nhiên', volumeM3: 1.5, fragile: false, icon: Shirt },
  { name: 'Bộ sofa + bàn trà', hint: 'Bọc màng PE & chống sốc', volumeM3: 2, fragile: false, icon: Armchair },
  { name: 'Tivi', hint: 'Đồ dễ vỡ, cần thùng/xốp riêng', volumeM3: 0.3, fragile: true, icon: Monitor },
  { name: 'Máy lạnh', hint: 'Cục nóng + cục lạnh', volumeM3: 0.3, fragile: false, icon: Fan },
  { name: 'Bàn làm việc', hint: 'Bàn đơn, kèm ghế', volumeM3: 0.8, fragile: false, icon: Box },
  { name: 'Thùng carton đồ cá nhân', hint: 'Dự kiến thùng tiêu chuẩn 60x40x40', volumeM3: 0.1, fragile: false, icon: Package },
];

export const SURVEY_PREFERENCES: Record<SurveyPreference, { title: string; badge?: string; desc: string; meta: string[]; icon: LucideIcon }> = {
  VIDEO_CALL: {
    title: 'Khảo sát nhanh qua Video Call',
    badge: 'Khuyên dùng',
    desc: 'Tiết kiệm thời gian, chỉ mất khoảng 10 phút qua Zalo / Messenger. Điều phối viên đánh giá trực tiếp khối lượng đồ đạc.',
    meta: ['~10 phút', 'Zalo / Messenger', 'Báo giá nhanh'],
    icon: Video,
  },
  ON_SITE: {
    title: 'Khảo sát trực tiếp tận nơi',
    badge: 'Chuyên sâu',
    desc: 'Nhân viên đến đo đạc, kiểm tra lối vào, hẻm xe và khối lượng hàng hóa lớn/phức tạp trong khoảng 30 phút.',
    meta: ['~30 phút', 'Tại nhà bạn'],
    icon: MapPinned,
  },
  PHOTOS_ONLY: {
    title: 'Tôi đã cung cấp đủ ảnh & danh mục',
    desc: 'Không cần khảo sát thêm. Điều phối viên lập báo giá dựa trên danh mục chi tiết và hình ảnh đính kèm của bạn.',
    meta: ['Không cần hẹn lịch'],
    icon: Camera,
  },
};

export const SURVEY_CONTACT_SLOTS = ['08:30 - 11:30 (Sáng)', '13:30 - 16:30 (Chiều)', '17:30 - 19:30 (Tối)', 'Bất kỳ lúc nào'];

export const MAX_PHOTOS = 10;

export const DRAFT_STORAGE_KEY = 'moveflow.booking-draft.v1';

export const HOTLINE = { display: '1900 6868', tel: 'tel:19006868' };

export const PHONE_REGEX = /^(0|\+84)[\s.]?\d{2,3}([\s.]?\d{3}){2}$/;

export const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
