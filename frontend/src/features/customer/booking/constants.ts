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
  { title: 'Dịch vụ', subtitle: 'Gói dịch vụ & khảo sát' },
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
    desc: 'MoveFlow lo trọn từ A đến Z: chuyên viên đến tận nơi khảo sát, tư vấn phương án chuyển dọn; đội ngũ đóng gói, tháo lắp, vận chuyển và sắp xếp lại đồ đạc tại nhà mới.',
    perks: [
      'Chuyên viên khảo sát tận nơi',
      'Tư vấn phương án & lộ trình phù hợp',
      'Cam kết bồi thường 100% tài sản theo biên bản',
    ],
    icon: Package,
  },
  TRUCK_ONLY: {
    title: 'Chỉ thuê xe tải vận chuyển',
    badge: 'Tiết kiệm',
    desc: 'Dành cho gia chủ đã tự đóng gói. Xe tới đúng giờ, tài xế hỗ trợ chằng buộc thùng hàng an toàn — có thể thuê thêm nhân viên bốc vác nếu cần.',
    perks: ['Xe thùng kín, bửng nâng hạ tải trọng linh hoạt', 'Tài xế thông thạo lộ trình, tối ưu thời gian', 'Tùy chọn thuê thêm nhân viên bốc vác khi cần hỗ trợ'],
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
    title: 'Video Call',
    badge: 'Nhanh',
    desc: 'Khoảng 10 phút qua Zalo / Messenger, chuyên viên xem trực tiếp đồ đạc và lối đi để đánh giá khối lượng.',
    meta: ['~10 phút', 'Zalo / Messenger'],
    icon: Video,
  },
  ON_SITE: {
    title: 'Trực tiếp tận nơi',
    badge: 'Chính xác nhất',
    desc: 'Chuyên viên đến đo đạc, kiểm tra lối vào, hẻm xe và tư vấn phương án cho đồ lớn / phức tạp trong khoảng 30 phút.',
    meta: ['~30 phút', 'Tại nhà bạn'],
    icon: MapPinned,
  },
  PHOTOS_ONLY: {
    title: 'Gửi ảnh / video',
    desc: 'Không cần hẹn lịch. Sau khi tạo đơn, bạn tải ảnh / video đồ đạc và lối đi ở trang chi tiết đơn để chuyên viên lập báo giá.',
    meta: ['Không cần hẹn', 'Tải lên sau khi tạo đơn'],
    icon: Camera,
  },
};

/** Video Call / tận nơi cần hẹn lịch; PHOTOS_ONLY tải ảnh / video sau khi tạo đơn. */
export const needsSurveyAppointment = (p: SurveyPreference): boolean => p !== 'PHOTOS_ONLY';

export const SURVEY_TIME_SLOTS = ['08:30 - 11:30 (Sáng)', '13:30 - 16:30 (Chiều)', '17:30 - 19:30 (Tối)'];

export const DRAFT_STORAGE_KEY = 'moveflow.booking-draft.v2';

export const HOTLINE = { display: '1900 6868', tel: 'tel:19006868' };

export const PHONE_REGEX = /^(0|\+84)[\s.]?\d{2,3}([\s.]?\d{3}){2}$/;

export const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
