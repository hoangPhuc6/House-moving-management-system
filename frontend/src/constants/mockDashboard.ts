import type { FeedbackEntry, HistoricOrder } from '@/types';

/**
 * MOCK DATA cho Dashboard báo cáo của Admin: lịch sử đơn đã đóng + đánh giá của khách,
 * từ 01/01 năm trước tới hôm qua (đủ để xem theo tháng / năm và so sánh với kỳ trước, cùng kỳ năm trước).
 *
 * - Sinh bằng PRNG có seed cố định → mỗi lần tải trang ra cùng một bộ số liệu (dễ demo, dễ so sánh).
 * - Ngày tính lùi từ hôm nay → tháng / năm hiện tại lúc nào cũng có dữ liệu.
 * - Nhịp đơn mô phỏng thực tế: cuối tuần & đầu / cuối tháng đông hơn, xu hướng tăng dần về hiện tại.
 * - Tên dịch vụ khớp danh mục trong constants/catalog.ts để thống kê "Dịch vụ được dùng nhiều" đúng.
 *
 * Chỉ nạp vào CatalogState.history / feedbacks — không tạo Order thật, nên không lọt sang màn hình vận hành.
 */

const DAY = 86_400_000;

/** Số ngày mock: từ 01/01 năm trước tới hôm qua — đủ cho xem theo năm và so sánh cùng kỳ năm trước. */
const defaultMockDays = (now: number): number => {
  const year = new Date(now).getUTCFullYear();
  return Math.floor((now - Date.UTC(year - 1, 0, 1)) / DAY);
};
/** Đổi seed để ra bộ số khác. Ngày tính lùi từ hôm nay nên bộ số xê dịch nhẹ theo ngày, xu hướng tổng thể giữ nguyên. */
const SEED = 8080;
const CANCEL_RATE = 0.07;
const FEEDBACK_RATE = 0.38;

/** PRNG mulberry32 — đủ tốt cho dữ liệu giả lập, có thể tái lập theo seed. */
const createRandom = (seed: number) => {
  let a = seed >>> 0;
  const next = () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4_294_967_296;
  };
  return {
    next,
    chance: (p: number) => next() < p,
    between: (min: number, max: number) => min + next() * (max - min),
    pick: <T,>(list: readonly T[]): T => list[Math.floor(next() * list.length)]!,
    /** Chọn theo trọng số: [[giá trị, trọng số], …]. */
    weighted: <T,>(list: readonly (readonly [T, number])[]): T => {
      let r = next() * list.reduce((s, [, w]) => s + w, 0);
      for (const [v, w] of list) {
        r -= w;
        if (r <= 0) return v;
      }
      return list[list.length - 1]![0];
    },
  };
};

type Random = ReturnType<typeof createRandom>;

const FAMILY = ['Nguyễn', 'Trần', 'Lê', 'Phạm', 'Hoàng', 'Huỳnh', 'Phan', 'Vũ', 'Võ', 'Đặng', 'Bùi', 'Đỗ', 'Ngô', 'Dương'] as const;
const MIDDLE = ['Văn', 'Thị', 'Minh', 'Ngọc', 'Thanh', 'Quốc', 'Hoài', 'Gia', 'Đức', 'Thu', 'Bảo', 'Khánh'] as const;
const GIVEN = ['An', 'Bình', 'Châu', 'Dũng', 'Hà', 'Hải', 'Hạnh', 'Hiếu', 'Hương', 'Khoa', 'Linh', 'Long', 'Mai', 'Nam', 'Ngân', 'Phúc', 'Quân', 'Tâm', 'Thảo', 'Trang', 'Tú', 'Vy', 'Yến', 'Sơn', 'Trinh', 'Duy'] as const;
const COMPANIES = ['Công ty TNHH Sao Mai', 'Công ty CP Tân Phát', 'Văn phòng Luật Minh Tâm', 'Công ty TNHH Bếp Việt', 'Studio Ánh Dương', 'Công ty CP Gia Khang'] as const;

/**
 * Gói dịch vụ điển hình — tỷ trọng, khoảng doanh thu, loại xe (chọn đúng 1) và dịch vụ kèm xác suất có mặt.
 * Thứ tự: chỉ thuê xe · trọn gói nhà nhỏ · trọn gói nhà lớn · văn phòng.
 */
const PROFILES = [
  {
    weight: 34,
    revenue: [800_000, 2_300_000],
    office: false,
    vehicles: ['Xe tải 1.25 tấn', 'Xe tải 2 tấn'],
    services: [['Nhân viên bốc xếp', 0.4]],
  },
  {
    weight: 36,
    revenue: [2_800_000, 6_200_000],
    office: false,
    vehicles: ['Xe tải 1.25 tấn'],
    services: [
      ['Nhân viên bốc xếp', 1],
      ['Thùng carton 60x40x40', 1],
      ['Băng keo đóng gói', 1],
      ['Màng PE bọc đồ', 0.6],
      ['Tháo lắp tủ / giường', 0.4],
      ['Phụ phí lầu (không thang máy)', 0.35],
      ['Phụ phí hẻm (trung chuyển)', 0.2],
    ],
  },
  {
    weight: 24,
    revenue: [6_500_000, 16_000_000],
    office: false,
    vehicles: ['Xe tải 2 tấn'],
    services: [
      ['Nhân viên bốc xếp', 1],
      ['Thùng carton 60x40x40', 1],
      ['Băng keo đóng gói', 1],
      ['Màng PE bọc đồ', 0.9],
      ['Tháo lắp máy lạnh', 0.6],
      ['Tháo lắp tủ / giường', 0.7],
      ['Phụ phí lầu (không thang máy)', 0.3],
      ['Phụ phí hẻm (trung chuyển)', 0.3],
    ],
  },
  {
    weight: 6,
    revenue: [15_000_000, 32_000_000],
    office: true,
    vehicles: ['Xe tải 5 tấn'],
    services: [
      ['Nhân viên bốc xếp', 1],
      ['Thùng carton 60x40x40', 1],
      ['Băng keo đóng gói', 1],
      ['Màng PE bọc đồ', 1],
      ['Tháo lắp tủ / giường', 0.8],
      ['Tháo lắp máy lạnh', 0.5],
    ],
  },
] as const;

const COMMENTS: Record<number, readonly string[]> = {
  5: [
    'Đội làm nhanh, gọn, đồ đạc bọc kỹ. Sẽ giới thiệu bạn bè.',
    'Nhân viên lịch sự, đến đúng giờ, báo giá rõ ràng không phát sinh.',
    'Tháo lắp máy lạnh cẩn thận, lắp lại chạy tốt.',
    'Rất hài lòng, chuyển xong còn sắp xếp đồ giúp.',
    'Giá hợp lý, xe sạch, anh trưởng nhóm nhiệt tình.',
    '',
  ],
  4: [
    'Làm tốt, chỉ hơi trễ giờ hẹn khoảng 20 phút.',
    'Đóng gói kỹ, nhưng thiếu thùng carton phải chờ mang thêm.',
    'Ổn, nhân viên vui vẻ. Mong có thêm khung giờ tối.',
    'Dịch vụ tốt, giá hơi cao so với tham khảo ban đầu.',
    '',
  ],
  3: [
    'Tạm được, khảo sát qua ảnh ước lượng thiếu đồ nên phát sinh thêm.',
    'Đến trễ gần 1 tiếng, có báo trước nhưng vẫn ảnh hưởng lịch.',
    'Bốc xếp nhanh nhưng xếp đồ ở nhà mới chưa đúng phòng.',
  ],
  2: ['Một chân bàn bị trầy, đã báo và đang chờ xử lý.', 'Phải gọi nhiều lần mới xác nhận được lịch.'],
  1: ['Hẹn sáng nhưng chiều mới tới, không hài lòng.'],
};

const isoDate = (t: number) => new Date(t).toISOString().slice(0, 10);

const customerName = (rnd: Random, office: boolean) =>
  office ? rnd.pick(COMPANIES) : `${rnd.pick(FAMILY)} ${rnd.pick(MIDDLE)} ${rnd.pick(GIVEN)}`;

/** Số đơn kỳ vọng trong ngày: nền tăng dần về hiện tại, cộng thêm cuối tuần và đầu / cuối tháng. */
const expectedOrders = (date: Date, daysAgo: number, days: number): number => {
  const trend = 1 + 1.6 * (1 - daysAgo / days);
  const dow = date.getUTCDay();
  const dom = date.getUTCDate();
  const weekend = dow === 0 || dow === 6 ? 1.4 : 0;
  const monthEdge = dom >= 27 || dom <= 3 ? 1.2 : 0;
  return trend + weekend + monthEdge;
};

const buildOrder = (rnd: Random, id: string, date: string): HistoricOrder => {
  const profile = rnd.weighted(PROFILES.map((p) => [p, p.weight] as const));
  const services: string[] = [rnd.pick(profile.vehicles), ...profile.services.filter(([, p]) => rnd.chance(p)).map(([name]) => name)];
  const cancelled = rnd.chance(CANCEL_RATE);
  const revenue = Math.round(rnd.between(profile.revenue[0], profile.revenue[1]) / 10_000) * 10_000;
  return {
    id,
    date,
    customerName: customerName(rnd, profile.office),
    status: cancelled ? 'CANCELLED' : 'COMPLETED',
    revenue: cancelled ? 0 : revenue,
    services,
  };
};

const buildFeedback = (rnd: Random, order: HistoricOrder, now: number): FeedbackEntry => {
  const stars = rnd.weighted([
    [5, 58],
    [4, 28],
    [3, 9],
    [2, 4],
    [1, 1],
  ] as const);
  // Khách thường đánh giá trong 1–2 ngày sau khi hoàn tất; không vượt quá hiện tại.
  const createdAt = Math.round(Math.min(now - 3_600_000, new Date(`${order.date}T00:00:00Z`).getTime() + rnd.between(0.3, 2) * DAY));
  return { id: `fb-${order.id}`, orderId: order.id, customerName: order.customerName, stars, comment: rnd.pick(COMMENTS[stars]!), createdAt };
};

export interface DashboardMock {
  history: HistoricOrder[];
  feedbacks: FeedbackEntry[];
}

export const createDashboardMock = (now = Date.now(), days = defaultMockDays(now), seed = SEED): DashboardMock => {
  const rnd = createRandom(seed);
  const history: HistoricOrder[] = [];
  // Từ ngày cũ nhất tới hôm qua — đơn hôm nay thuộc dữ liệu vận hành thật.
  for (let daysAgo = days; daysAgo >= 1; daysAgo--) {
    const t = now - daysAgo * DAY;
    const date = isoDate(t);
    const count = Math.max(0, Math.round(expectedOrders(new Date(t), daysAgo, days) * rnd.between(0.55, 1.45)));
    for (let i = 1; i <= count; i++) history.push(buildOrder(rnd, `ORD-${date.replace(/-/g, '')}-${String(i).padStart(2, '0')}`, date));
  }
  const feedbacks = history
    .filter((o) => o.status === 'COMPLETED' && rnd.chance(FEEDBACK_RATE))
    .map((o) => buildFeedback(rnd, o, now))
    .sort((a, b) => b.createdAt - a.createdAt);
  return { history, feedbacks };
};

let cached: DashboardMock | null = null;

/** Bộ mock dùng chung cho cả phiên (sinh một lần). */
export const dashboardMock = (): DashboardMock => (cached ??= createDashboardMock());
