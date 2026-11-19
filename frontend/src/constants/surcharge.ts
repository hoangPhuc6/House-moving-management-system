import type { Tone } from '@/components/ui';
import { VEHICLE_PRICING } from '@/constants/pricing';
import { SURVEY_UNIT_PRICES } from '@/services/pricing';
import type { Order, ServiceGroup, Surcharge, SurchargeStatus, SurchargeType, Vehicle } from '@/types';

/** SYSTEM: hệ thống tự tính đơn giá · CATALOG: chọn dịch vụ trong bảng giá · MANUAL: nhập tay (Điều phối được sửa). */
export type SurchargePricingMode = 'SYSTEM' | 'CATALOG' | 'MANUAL';

export const SURCHARGE_TYPE_META: Record<
  SurchargeType,
  { label: string; mode: SurchargePricingMode; unit: string; qtyLabel: string; example: { surveyed: string; actual: string }; catalogGroups?: ServiceGroup[] }
> = {
  EXTRA_FLOOR: { label: 'Thêm lầu thang bộ', mode: 'SYSTEM', unit: 'lầu', qtyLabel: 'Số lầu tăng thêm', example: { surveyed: 'Lầu 2', actual: 'Lầu 4' } },
  NO_ELEVATOR: { label: 'Không dùng được thang máy', mode: 'SYSTEM', unit: 'lầu', qtyLabel: 'Số lầu phải khiêng bộ', example: { surveyed: 'Lầu 5, có thang máy', actual: 'Thang máy bảo trì' } },
  LONG_CARRY: { label: 'Hẻm sâu / khiêng xa hơn', mode: 'SYSTEM', unit: 'đoạn 50m', qtyLabel: 'Số đoạn 50m tăng thêm', example: { surveyed: 'Hẻm 50m', actual: 'Hẻm 200m, xe không vào được' } },
  EXTRA_DISTANCE: { label: 'Quãng đường tăng', mode: 'SYSTEM', unit: 'km', qtyLabel: 'Số km tăng thêm', example: { surveyed: '12 km', actual: '18 km (đường cấm tải)' } },
  EXTRA_ITEMS: { label: 'Đồ đạc nhiều hơn khai báo', mode: 'CATALOG', unit: '', qtyLabel: 'Số lượng thêm', catalogGroups: ['LABOR'], example: { surveyed: 'Không có tủ sách', actual: 'Thêm 2 tủ sách, 1 đàn piano' } },
  DISASSEMBLY: { label: 'Tháo lắp phát sinh', mode: 'CATALOG', unit: '', qtyLabel: 'Số món cần tháo lắp', catalogGroups: ['ASSEMBLY'], example: { surveyed: 'Không tháo lắp', actual: 'Tủ gỗ 4 cánh không qua cửa' } },
  PACKING_MATERIAL: { label: 'Vật tư đóng gói thêm', mode: 'CATALOG', unit: '', qtyLabel: 'Số lượng thêm', catalogGroups: ['PACKING'], example: { surveyed: '20 thùng carton', actual: 'Cần 30 thùng' } },
  OTHER: { label: 'Khác', mode: 'MANUAL', unit: 'gói', qtyLabel: 'Số lượng', example: { surveyed: '', actual: '' } },
};

export const SURCHARGE_TYPES = Object.keys(SURCHARGE_TYPE_META) as SurchargeType[];

export const SURCHARGE_STATUS_META: Record<SurchargeStatus, { label: string; tone: Tone }> = {
  PROPOSED: { label: 'Chờ Điều phối duyệt', tone: 'warning' },
  WAITING_CUSTOMER: { label: 'Chờ khách xác nhận', tone: 'info' },
  ACCEPTED: { label: 'Khách đã đồng ý', tone: 'success' },
  REJECTED: { label: 'Điều phối đã bác', tone: 'neutral' },
  REJECTED_BY_CUSTOMER: { label: 'Khách từ chối', tone: 'danger' },
  CANCELLED: { label: 'Đã hủy', tone: 'neutral' },
};

/** Còn khoản ở các trạng thái này thì đội chưa được bắt đầu làm / di chuyển (khớp BLOCKING_START backend). */
export const SURCHARGE_BLOCKING: SurchargeStatus[] = ['PROPOSED', 'WAITING_CUSTOMER', 'REJECTED_BY_CUSTOMER'];

/** Đội xe đang đứng chờ → Điều phối phải xử lý đề xuất trong 15 phút. */
export const SURCHARGE_REVIEW_SLA_MS = 15 * 60 * 1000;

export const isSurchargeBlocking = (order: Order): boolean => order.surcharges.some((s) => SURCHARGE_BLOCKING.includes(s.status));

/** Khoản khách nhìn thấy & đang chặn đội (khoản PROPOSED là nội bộ, khách chưa thấy). */
export const hasCustomerSurcharge = (order: Order): boolean => order.surcharges.some((s) => s.status === 'WAITING_CUSTOMER' || s.status === 'REJECTED_BY_CUSTOMER');

export const surchargesWithStatus = (order: Order, ...statuses: SurchargeStatus[]): Surcharge[] =>
  order.surcharges.filter((s) => statuses.includes(s.status));

/** Khoản Điều phối cần xử lý: NV mới đề xuất hoặc khách vừa từ chối. */
export const surchargesNeedingReview = (order: Order): Surcharge[] => surchargesWithStatus(order, 'PROPOSED', 'REJECTED_BY_CUSTOMER');

export const surchargeAmount = (s: Pick<Surcharge, 'quantity' | 'unitPrice'>): number => s.quantity * s.unitPrice;

/** Đơn giá hệ thống cho loại SYSTEM — km tính theo cước của xe được gán cho đơn. */
export const systemUnitPrice = (type: SurchargeType, order: Order, vehicles: Vehicle[]): number => {
  switch (type) {
    case 'EXTRA_FLOOR':
    case 'NO_ELEVATOR':
      return SURVEY_UNIT_PRICES.perFloor;
    case 'LONG_CARRY':
      return SURVEY_UNIT_PRICES.perAlleySegment;
    case 'EXTRA_DISTANCE': {
      const cap = vehicles.find((v) => v.id === order.vehicleId)?.capacityTons;
      return (cap !== undefined ? VEHICLE_PRICING[cap]?.perKm : undefined) ?? VEHICLE_PRICING[2]!.perKm;
    }
    default:
      return 0;
  }
};

export interface SurchargeReport {
  reportId: string;
  note: string;
  proposedAt: number;
  lines: Surcharge[];
}

/** Gom các dòng theo lần báo (giữ thứ tự báo); mỗi dòng vẫn mang trạng thái riêng. */
export const groupSurchargesByReport = (lines: Surcharge[]): SurchargeReport[] => {
  const map = new Map<string, SurchargeReport>();
  for (const s of lines) {
    const g = map.get(s.reportId);
    if (g) g.lines.push(s);
    else map.set(s.reportId, { reportId: s.reportId, note: s.reportNote, proposedAt: s.proposedAt, lines: [s] });
  }
  return [...map.values()];
};

export const sumSurcharges = (lines: Surcharge[]): number => lines.reduce((sum, s) => sum + surchargeAmount(s), 0);

/** "Khảo sát ghi" lấy từ biên bản khảo sát của đơn theo loại sai lệch — rỗng khi biên bản không có số liệu tương ứng. */
export const surveyedFromRecord = (type: SurchargeType, order: Order): string => {
  const b = order.surveyRecord?.baseline;
  if (!b) return '';
  switch (type) {
    case 'EXTRA_FLOOR':
    case 'NO_ELEVATOR':
      return b.floorsFrom + b.floorsTo > 0 ? `Thang bộ: điểm đi ${b.floorsFrom} lầu, điểm đến ${b.floorsTo} lầu` : 'Không phải khiêng thang bộ';
    case 'LONG_CARRY':
      return b.alleyFrom + b.alleyTo > 0 ? `Hẻm: điểm đi ${b.alleyFrom}m, điểm đến ${b.alleyTo}m` : 'Xe vào tận nơi cả 2 đầu';
    case 'EXTRA_DISTANCE':
      return `${b.distanceKm} km`;
    case 'PACKING_MATERIAL':
      return `${b.cartons} thùng carton, ${b.tapeRolls} cuộn băng keo, ${b.peFilmRolls} cuộn PE`;
    default:
      return '';
  }
};
