import type { SurveyItem } from '@/types';

/** Đồ đạc hay gặp — người khảo sát bấm để thêm nhanh, bấm lại thì tăng số lượng. */
export const COMMON_SURVEY_ITEMS: { name: string; fragile?: boolean; disassemble?: boolean }[] = [
  { name: 'Tủ lạnh' },
  { name: 'Máy giặt' },
  { name: 'Tivi', fragile: true },
  { name: 'Điều hòa', disassemble: true },
  { name: 'Giường', disassemble: true },
  { name: 'Tủ quần áo', disassemble: true },
  { name: 'Sofa' },
  { name: 'Bàn ăn' },
  { name: 'Bàn làm việc' },
  { name: 'Ghế' },
  { name: 'Kệ / giá sách' },
  { name: 'Gương / kính', fragile: true },
  { name: 'Thùng đồ' },
];

export const emptySurveyItem = (): SurveyItem => ({ name: '', qty: 1, fragile: false, disassemble: false });

/** Bỏ dòng trống, chuẩn hoá tên trước khi gửi. */
export const cleanSurveyItems = (items: SurveyItem[]): SurveyItem[] =>
  items.map((i) => ({ ...i, name: i.name.trim(), qty: Math.max(1, i.qty) })).filter((i) => i.name);

export const surveyItemsTotal = (items: SurveyItem[]) => items.reduce((n, i) => n + i.qty, 0);
