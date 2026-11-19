import type { Tone } from '@/components/ui';
import type {
  AssignmentRequirement,
  CrewAssignment,
  LicenseClass,
  Order,
  StaffMember,
  StaffSkill,
  StaffWorkStatus,
  TripRole,
  Vehicle,
} from '@/types';

export const SKILL_LABEL: Record<StaffSkill, string> = {
  DRIVE: 'Lái xe',
  LOAD: 'Bốc xếp',
  ASSEMBLY: 'Tháo lắp',
};

export const SKILL_ORDER: StaffSkill[] = ['DRIVE', 'LOAD', 'ASSEMBLY'];

export const TRIP_ROLE_LABEL: Record<TripRole, string> = { DRIVER: 'Tài xế', LOADER: 'Bốc xếp' };

export const WORK_STATUS_META: Record<StaffWorkStatus, { label: string; tone: Tone }> = {
  ACTIVE: { label: 'Đang làm', tone: 'success' },
  ON_LEAVE: { label: 'Nghỉ phép', tone: 'warning' },
  RESIGNED: { label: 'Nghỉ việc', tone: 'neutral' },
};

/** Tải trọng tối đa (tấn) theo hạng GPLX. */
export const LICENSE_MAX_TONS: Record<LicenseClass, number> = { B2: 3.5, C: Number.POSITIVE_INFINITY };

export const LICENSE_LABEL: Record<LicenseClass, string> = { B2: 'B2 · xe ≤ 3,5 tấn', C: 'C · mọi xe tải' };

export const hasSkill = (s: StaffMember, skill: StaffSkill) => s.skills.includes(skill);
export const isActiveStaff = (s: StaffMember) => s.status === 'ACTIVE';

/** Lý do không lái được xe tải `tons` vào ngày `onDate` (ISO); null = lái được. */
export const driveBlockReason = (s: StaffMember, tons: number, onDate: string): string | null => {
  if (!hasSkill(s, 'DRIVE')) return 'Không có kỹ năng lái xe';
  if (!s.license) return 'Chưa khai báo bằng lái';
  if (s.license.expiresAt < onDate) return `Bằng lái hết hạn ${s.license.expiresAt.split('-').reverse().join('/')}`;
  if (tons > LICENSE_MAX_TONS[s.license.licenseClass]) return `Bằng ${s.license.licenseClass} không lái được xe ${String(tons).replace('.', ',')} tấn`;
  return null;
};

/** Bằng lái sắp hết hạn trong `days` ngày tính từ `today`. */
export const licenseExpiringSoon = (s: StaffMember, today: string, days = 30): boolean => {
  if (!s.license) return false;
  const limit = new Date(Date.parse(`${today}T00:00:00Z`) + days * 86_400_000).toISOString().slice(0, 10);
  return s.license.expiresAt >= today && s.license.expiresAt <= limit;
};

export const crewIdsOf = (crew: CrewAssignment[]): string[] => [...new Set(crew.map((c) => c.staffId))];

/** Trưởng nhóm = tài xế đầu tiên trong đội (đội được dựng theo xe lớn nhất trước). */
export const leaderOf = (crew: CrewAssignment[]): string | null => crew.find((c) => c.role === 'DRIVER')?.staffId ?? null;

/**
 * Tự ghép các tài xế đã chọn với xe: xe lớn trước, mỗi xe lấy người lái được có bằng "vừa đủ" nhất
 * (để dành bằng C cho xe lớn). Trả về danh sách DRIVER theo thứ tự xe lớn → nhỏ, hoặc lỗi nếu không ghép đủ.
 */
export const pairDrivers = (
  vehicles: Vehicle[],
  driverIds: string[],
  staff: StaffMember[],
  onDate: string,
): { drivers: CrewAssignment[]; error: string | null } => {
  const remaining = driverIds.map((id) => staff.find((s) => s.id === id)).filter((s): s is StaffMember => !!s);
  const drivers: CrewAssignment[] = [];
  const maxTons = (s: StaffMember) => (s.license ? LICENSE_MAX_TONS[s.license.licenseClass] : 0);
  for (const v of [...vehicles].sort((a, b) => b.capacityTons - a.capacityTons)) {
    const fit = remaining.filter((s) => !driveBlockReason(s, v.capacityTons, onDate)).sort((a, b) => maxTons(a) - maxTons(b))[0];
    if (!fit) {
      const tons = String(v.capacityTons).replace('.', ',');
      return { drivers, error: `Không còn tài xế đã chọn lái được xe ${v.plateNumber} (${tons} tấn${v.capacityTons > LICENSE_MAX_TONS.B2 ? ', cần bằng C' : ''}).` };
    }
    drivers.push({ staffId: fit.id, role: 'DRIVER', vehicleId: v.id });
    remaining.splice(remaining.indexOf(fit), 1);
  }
  return { drivers, error: null };
};

/** Mã nhân viên kế tiếp dạng NV-xxx. */
export const nextStaffCode = (staff: StaffMember[]): string => {
  const max = staff.reduce((m, s) => Math.max(m, Number(/\d+/.exec(s.code)?.[0] ?? 0)), 0);
  return `NV-${String(max + 1).padStart(3, '0')}`;
};

/** Hồ sơ hợp lệ: mã duy nhất, có ít nhất một kỹ năng, lái xe thì phải khai bằng lái. */
export const validateStaffProfile = (s: StaffMember, all: StaffMember[]): string | null => {
  if (!s.name.trim()) return 'Thiếu họ tên.';
  if (!/^NV-\d{3,}$/.test(s.code)) return 'Mã nhân viên dạng NV-001.';
  if (all.some((x) => x.id !== s.id && x.code === s.code)) return `Mã ${s.code} đã được dùng.`;
  if (s.skills.length === 0) return 'Chọn ít nhất một kỹ năng.';
  if (hasSkill(s, 'DRIVE') && !s.license) return 'Nhân viên lái xe phải khai báo hạng và hạn bằng lái.';
  if (s.license && !/^\d{4}-\d{2}-\d{2}$/.test(s.license.expiresAt)) return 'Ngày hết hạn bằng lái không hợp lệ.';
  return null;
};

const OPEN_JOB_STATUSES = ['ASSIGNED', 'IN_TRANSIT', 'PAUSED_WAITING_CUSTOMER'];

/**
 * Hồ sơ mới có mâu thuẫn với lệnh đang chạy không: cho người trong đội nghỉ, gỡ kỹ năng lái của tài xế.
 * Trả thông báo lỗi hoặc null.
 */
export const staffChangeBlocker = (next: StaffMember, orders: Order[]): string | null => {
  const open = orders.filter((o) => OPEN_JOB_STATUSES.includes(o.status) && o.crewIds.includes(next.id));
  const blocker =
    (next.status !== 'ACTIVE' && open[0]) ||
    (!hasSkill(next, 'DRIVE') && open.find((o) => o.crew.some((c) => c.staffId === next.id && c.role === 'DRIVER')));
  return blocker ? `${next.name} đang trong lệnh #${blocker.id} — đổi đội ở Điều phối trước khi cập nhật hồ sơ này.` : null;
};

/**
 * Kiểm tra đội của một chuyến; trả lỗi đầu tiên hoặc null.
 * - Mỗi xe đúng một tài xế: có kỹ năng lái, bằng đúng hạng tải trọng, còn hạn vào ngày chuyển.
 * - Số người bốc xếp đúng báo giá, đều có kỹ năng bốc xếp.
 * - Không ai bị gán hai vai; mọi người đang làm (không nghỉ phép / nghỉ việc).
 * - Đơn có tháo lắp thì đội có ít nhất một người biết tháo lắp.
 * Trưởng nhóm không kiểm riêng: là tài xế ({@link leaderOf}).
 */
export const validateCrew = (
  req: AssignmentRequirement,
  vehicles: Vehicle[],
  crew: CrewAssignment[],
  staff: StaffMember[],
  onDate: string,
): string | null => {
  const find = (id: string) => staff.find((s) => s.id === id);
  if (crewIdsOf(crew).length !== crew.length) return 'Một nhân viên không thể giữ hai vai trong cùng chuyến.';
  for (const c of crew) {
    const s = find(c.staffId);
    if (!s) return 'Có nhân viên không tồn tại trong danh sách nhân sự.';
    if (!isActiveStaff(s)) return `${s.name} đang ${WORK_STATUS_META[s.status].label.toLowerCase()}.`;
  }

  const drivers = crew.filter((c) => c.role === 'DRIVER');
  for (const v of vehicles) {
    const assigned = drivers.filter((d) => d.vehicleId === v.id);
    if (assigned.length !== 1) return `Xe ${v.plateNumber} cần đúng một tài xế.`;
    const reason = driveBlockReason(find(assigned[0]!.staffId)!, v.capacityTons, onDate);
    if (reason) return `${find(assigned[0]!.staffId)!.name} — ${reason}.`;
  }
  if (drivers.some((d) => !vehicles.some((v) => v.id === d.vehicleId))) return 'Có tài xế chưa được ghép với xe đã chọn.';

  const loaders = crew.filter((c) => c.role === 'LOADER');
  if (loaders.length !== req.workers) return `Cần đúng ${req.workers} nhân viên bốc xếp theo báo giá (đang chọn ${loaders.length}).`;
  const notLoader = loaders.map((l) => find(l.staffId)!).find((s) => !hasSkill(s, 'LOAD'));
  if (notLoader) return `${notLoader.name} không có kỹ năng bốc xếp.`;

  if (req.needsAssembly && !crew.some((c) => hasSkill(find(c.staffId)!, 'ASSEMBLY'))) {
    return 'Đơn có dịch vụ tháo lắp — đội cần ít nhất một người biết tháo lắp.';
  }
  return null;
};

/** Mô tả đội cho nhật ký, VD: "Tài xế: A (51C-889.92) · Bốc xếp: B, C · Trưởng nhóm: A". */
export const describeCrew = (crew: CrewAssignment[], leaderId: string | null, staff: StaffMember[], vehicles: Vehicle[]): string => {
  const name = (id: string) => staff.find((s) => s.id === id)?.name ?? id;
  const plate = (id: string | null) => vehicles.find((v) => v.id === id)?.plateNumber ?? '?';
  const drivers = crew.filter((c) => c.role === 'DRIVER').map((c) => `${name(c.staffId)} (${plate(c.vehicleId)})`);
  const loaders = crew.filter((c) => c.role === 'LOADER').map((c) => name(c.staffId));
  return [
    drivers.length && `Tài xế: ${drivers.join(', ')}`,
    loaders.length && `Bốc xếp: ${loaders.join(', ')}`,
    leaderId && `Trưởng nhóm: ${name(leaderId)}`,
  ]
    .filter(Boolean)
    .join(' · ');
};
