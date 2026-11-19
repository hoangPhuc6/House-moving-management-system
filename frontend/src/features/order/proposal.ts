import { CARTON_ORDER, CARTON_SIZES, vehicleLinesLabel } from '@/constants/pricing';
import { hoursLabel } from '@/constants/resources';
import type { CartonSize, SurveyInput } from '@/types';

/**
 * Các ô số liệu tùy chọn của đề xuất khảo sát (vật tư, lối đi). Người khảo sát chỉ thêm ô cần dùng;
 * ô bằng 0 coi như không có và không hiển thị.
 */
export interface ProposalField {
  key: string;
  label: string;
  hint?: string;
  group: 'MATERIAL' | 'ACCESS';
  unit: string;
  /** Giá trị khi vừa thêm ô. */
  initial: number;
  step: number;
  max: number;
  get: (p: SurveyInput) => number;
  set: (p: SurveyInput, v: number) => SurveyInput;
}

const cartonField = (size: CartonSize): ProposalField => {
  const c = CARTON_SIZES[size];
  return {
    key: `carton-${size}`,
    label: `${c.label} ${c.dims}`,
    hint: c.use,
    group: 'MATERIAL',
    unit: 'thùng',
    initial: 10,
    step: 5,
    max: 200,
    get: (p) => p.cartons[size],
    set: (p, v) => ({ ...p, cartons: { ...p.cartons, [size]: v } }),
  };
};

type NumKey = 'tapeRolls' | 'peFilmRolls' | 'floorsFrom' | 'floorsTo' | 'alleyFrom' | 'alleyTo';
const numField = (k: NumKey, label: string, group: ProposalField['group'], unit: string, initial: number, step: number, max: number, hint?: string): ProposalField => ({
  key: k,
  label,
  hint,
  group,
  unit,
  initial,
  step,
  max,
  get: (p) => p[k],
  set: (p, v) => ({ ...p, [k]: v }),
});

export const PROPOSAL_FIELDS: ProposalField[] = [
  ...CARTON_ORDER.map(cartonField),
  numField('tapeRolls', 'Băng keo', 'MATERIAL', 'cuộn', 2, 1, 50),
  numField('peFilmRolls', 'Màng PE', 'MATERIAL', 'cuộn', 1, 1, 20),
  numField('floorsFrom', 'Thang bộ — điểm đi', 'ACCESS', 'lầu', 1, 1, 20, 'Số lầu phải khiêng bộ (không thang máy)'),
  numField('floorsTo', 'Thang bộ — điểm đến', 'ACCESS', 'lầu', 1, 1, 20, 'Số lầu phải khiêng bộ (không thang máy)'),
  numField('alleyFrom', 'Hẻm — điểm đi', 'ACCESS', 'm', 50, 10, 500, 'Quãng khiêng bộ từ chỗ xe đỗ'),
  numField('alleyTo', 'Hẻm — điểm đến', 'ACCESS', 'm', 50, 10, 500, 'Quãng khiêng bộ từ chỗ xe đỗ'),
];

/** Tóm tắt đề xuất — chỉ liệt kê mục có số lượng. */
export const proposalParts = (p: SurveyInput): string[] =>
  [
    vehicleLinesLabel(p.vehicles),
    p.estimatedHours > 0 ? `dự kiến ${hoursLabel(p.estimatedHours)}` : '',
    p.workers > 0 ? `${p.workers} người bốc xếp` : '',
    `${p.distanceKm} km`,
    ...PROPOSAL_FIELDS.map((f) => {
      const v = f.get(p);
      return v > 0 ? `${f.label}: ${v} ${f.unit}` : '';
    }),
    ...(p.extras ?? []).filter((x) => x.qty > 0).map((x) => `${x.name} × ${x.qty} ${x.unit}${x.note ? ` (${x.note})` : ''}`),
  ].filter(Boolean);
