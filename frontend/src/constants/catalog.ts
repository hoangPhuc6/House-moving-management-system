import type { CatalogState, ServiceGroup, ServiceItem } from '@/types';

export const SERVICE_GROUP_LABEL: Record<ServiceGroup, string> = {
  TRANSPORT: 'Vận chuyển',
  LABOR: 'Nhân viên bốc xếp',
  PACKING: 'Đóng gói',
  ASSEMBLY: 'Tháo lắp',
  SURCHARGE: 'Phụ phí',
};

export const svc = (id: string, group: ServiceGroup, name: string, unit: string, basePrice: number, description: string, active = true): ServiceItem => ({
  id, group, name, unit, basePrice, description, active,
});

export const createInitialCatalog = (): CatalogState => ({
  services: [
    svc('sv-1', 'TRANSPORT', 'Xe tải 1.25 tấn', 'chuyến', 650_000, 'Phù hợp căn hộ nhỏ, phòng trọ. Cộng 12.000đ/km.'),
    svc('sv-2', 'TRANSPORT', 'Xe tải 2 tấn', 'chuyến', 900_000, 'Nhà phố, căn hộ 2 phòng ngủ. Cộng 15.000đ/km.'),
    svc('sv-3', 'TRANSPORT', 'Xe tải 5 tấn', 'chuyến', 1_600_000, 'Nhà nguyên căn, văn phòng. Cộng 22.000đ/km.'),
    svc('sv-4', 'LABOR', 'Nhân viên bốc xếp', 'người', 350_000, 'Bốc xếp, khiêng vác, sắp xếp đồ tại nơi mới.'),
    svc('sv-5', 'PACKING', 'Thùng carton 60x40x40', 'thùng', 25_000, 'Thùng 5 lớp chịu lực.'),
    svc('sv-6', 'PACKING', 'Băng keo đóng gói', 'cuộn', 15_000, 'Băng keo trong 100 yard.'),
    svc('sv-7', 'PACKING', 'Màng PE bọc đồ', 'cuộn', 120_000, 'Màng co quấn đồ nội thất.'),
    svc('sv-8', 'ASSEMBLY', 'Tháo lắp máy lạnh', 'bộ', 450_000, 'Tháo và lắp lại máy lạnh treo tường.'),
    svc('sv-9', 'ASSEMBLY', 'Tháo lắp tủ / giường', 'món', 150_000, 'Tháo lắp nội thất gỗ ghép.'),
    svc('sv-10', 'SURCHARGE', 'Phụ phí lầu (không thang máy)', 'lầu', 100_000, 'Tính theo số lầu phải khiêng bộ.'),
    svc('sv-11', 'SURCHARGE', 'Phụ phí hẻm (trung chuyển)', 'đoạn 50m', 80_000, 'Xe không vào được, trung chuyển thủ công.'),
    svc('sv-12', 'ASSEMBLY', 'Vệ sinh sau chuyển nhà', 'gói', 600_000, 'Dịch vụ đã ngừng cung cấp.', false),
  ],
  users: [
    { id: 'u-1', name: 'Nguyễn Thị Lan', email: 'lan.nguyen@gmail.com', phone: '0912 345 678', role: 'CUSTOMER', locked: false, emailVerified: true },
    { id: 'u-2', name: 'Trần Văn Hùng', email: 'hung.tv@moveflow.vn', phone: '0903 111 222', role: 'STAFF', locked: false, emailVerified: true },
    { id: 'u-3', name: 'Lê Minh Châu', email: 'chau.lm@moveflow.vn', phone: '0904 222 333', role: 'COORDINATOR', locked: false, emailVerified: true },
    { id: 'u-4', name: 'Phạm Thu Trang', email: 'trang.pt@moveflow.vn', phone: '0905 333 444', role: 'ADMIN', locked: false, emailVerified: true },
    { id: 'u-5', name: 'Võ Quốc Bảo', email: 'bao.vq@gmail.com', phone: '0988 777 666', role: 'CUSTOMER', locked: false, emailVerified: false },
    { id: 'u-6', name: 'Đặng Hoài Nam', email: 'nam.dh@moveflow.vn', phone: '0977 555 444', role: 'STAFF', locked: true, emailVerified: true },
  ],
  inventory: [
    { id: 'inv-1', name: 'Thùng carton 60x40x40', unit: 'thùng', stock: 240, minStock: 100 },
    { id: 'inv-2', name: 'Băng keo đóng gói', unit: 'cuộn', stock: 85, minStock: 40 },
    { id: 'inv-3', name: 'Màng PE bọc đồ', unit: 'cuộn', stock: 32, minStock: 30 },
    { id: 'inv-4', name: 'Xe đẩy hàng', unit: 'chiếc', stock: 8, minStock: 4 },
    { id: 'inv-5', name: 'Dây chằng hàng', unit: 'bộ', stock: 20, minStock: 10 },
    { id: 'inv-6', name: 'Chăn bọc nội thất', unit: 'tấm', stock: 36, minStock: 20 },
  ],
  txns: [
    { id: 'tx-1', itemId: 'inv-1', type: 'IN', quantity: 300, delta: 300, orderId: null, by: 'Phạm Thu Trang', note: 'Nhập lô đầu tháng', at: Date.now() - 6 * 86_400_000 },
    { id: 'tx-2', itemId: 'inv-1', type: 'OUT', quantity: 60, delta: -60, orderId: null, by: 'Phạm Thu Trang', note: 'Xuất cho các đơn trong tuần', at: Date.now() - 3 * 86_400_000 },
  ],
  payments: [],
  feedbacks: [],
  history: [],
});
