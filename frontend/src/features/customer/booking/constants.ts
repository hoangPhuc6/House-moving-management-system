import { Camera, MapPinned, Package, Truck, Video, type LucideIcon } from 'lucide-react';
import type { AddressDetail, HouseType, ServiceMode, SurveyPreference } from '@/types';

export const WIZARD_STEPS: { title: string; subtitle: string }[] = [
  { title: 'Địa điểm & Thời gian', subtitle: 'Xuất phát, đích & giờ dọn' },
  { title: 'Dịch vụ', subtitle: 'Gói dịch vụ & khảo sát' },
  { title: 'Xác nhận & Gửi', subtitle: 'Kiểm tra & gửi đơn' },
];

/**
 * Địa giới hành chính 2 cấp (Tỉnh / Thành phố → Phường / Xã) theo sắp xếp từ 01/07/2025.
 * Danh sách rút gọn cho bản demo — thay bằng API địa chỉ khi có backend.
 */
export const ADMIN_AREAS: Record<string, string[]> = {
  'TP. Hồ Chí Minh': [
    'Phường Sài Gòn',
    'Phường Bến Thành',
    'Phường Tân Định',
    'Phường Cầu Ông Lãnh',
    'Phường Bàn Cờ',
    'Phường Xuân Hòa',
    'Phường Nhiêu Lộc',
    'Phường Tân Thuận',
    'Phường Tân Hưng',
    'Phường Tân Mỹ',
    'Phường Phú Thuận',
    'Phường Bình Thạnh',
    'Phường Gia Định',
    'Phường Thạnh Mỹ Tây',
    'Phường Bình Quới',
    'Phường Gò Vấp',
    'Phường Hạnh Thông',
    'Phường An Nhơn',
    'Phường An Khánh',
    'Phường Thủ Đức',
    'Phường Linh Xuân',
    'Phường Long Bình',
    'Phường Thủ Dầu Một',
    'Phường Vũng Tàu',
  ],
  'Hà Nội': [
    'Phường Hoàn Kiếm',
    'Phường Cửa Nam',
    'Phường Cầu Giấy',
    'Phường Nghĩa Đô',
    'Phường Yên Hòa',
    'Phường Đống Đa',
    'Phường Láng',
    'Phường Kim Liên',
    'Phường Ô Chợ Dừa',
    'Phường Hà Đông',
    'Phường Dương Nội',
  ],
  'Đà Nẵng': [
    'Phường Hải Châu',
    'Phường Hòa Cường',
    'Phường Thanh Khê',
    'Phường Sơn Trà',
    'Phường An Hải',
    'Phường Ngũ Hành Sơn',
    'Phường Hội An',
  ],
};

/** Địa chỉ khách đã lưu trong tài khoản (bản demo) — chọn nhanh khi điền điểm đi / điểm đến. */
export const SAVED_ADDRESSES: { label: string; address: Pick<AddressDetail, 'province' | 'ward' | 'street' | 'propertyType' | 'floor'> }[] = [
  {
    label: 'Nhà hiện tại',
    address: { province: 'TP. Hồ Chí Minh', ward: 'Phường Thạnh Mỹ Tây', street: '128/4 Ung Văn Khiêm', propertyType: 'Nhà trong ngõ/hẻm (xe tải vào được)', floor: 'Tầng 2 (thang bộ)' },
  },
  {
    label: 'Căn hộ mới',
    address: { province: 'TP. Hồ Chí Minh', ward: 'Phường Long Bình', street: 'Căn hộ 14.08, Toà S2.03, Vinhomes Grand Park', propertyType: 'Chung cư có thang máy', floor: 'Tầng cao (thang máy)' },
  },
];

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
  ward: '',
  street: '',
  propertyType: PROPERTY_TYPES[0]!.label,
  floor: FLOOR_OPTIONS[0]!,
  note: '',
});

export const isAddressComplete = (a: AddressDetail): boolean => Boolean(a.province && a.ward && a.street.trim());

export const formatAddress = (a: AddressDetail): string => [a.street.trim(), a.ward, a.province].filter(Boolean).join(', ');

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

/** Quy mô nhà (gói trọn gói) → xe & số nhân viên bốc xếp ước lượng cho giá tham khảo; chốt lại sau khảo sát. */
export const HOME_SCALES: { label: string; hint: string; vehicleTons: number; labor: number }[] = [
  { label: 'Phòng trọ / Studio', hint: '≈ 20 – 30 m²', vehicleTons: 1.25, labor: 2 },
  { label: '1 phòng ngủ', hint: '≈ 40 – 55 m²', vehicleTons: 1.25, labor: 2 },
  { label: '2 phòng ngủ', hint: '≈ 65 – 80 m²', vehicleTons: 2, labor: 3 },
  { label: '3+ phòng ngủ', hint: '> 90 m²', vehicleTons: 5, labor: 4 },
  { label: 'Nhà nguyên căn / Văn phòng', hint: 'Khối lượng lớn', vehicleTons: 5, labor: 5 },
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

/** v3: địa chỉ 2 cấp (bỏ quận / huyện) — nháp v2 có phường cũ nên không khôi phục. */
export const DRAFT_STORAGE_KEY = 'moveflow.booking-draft.v3';

export const HOTLINE = { display: '1900 6868', tel: 'tel:19006868' };

export const PHONE_REGEX = /^(0|\+84)[\s.]?\d{2,3}([\s.]?\d{3}){2}$/;

export const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
