import type { SitePhoto, SurveyItem, SurveyReport } from '@/types';

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
  items
    .map(({ note, ...i }) => ({ ...i, name: i.name.trim(), qty: Math.max(1, i.qty), ...(note?.trim() ? { note: note.trim() } : {}) }))
    .filter((i) => i.name);

/** Gợi ý ghi chú hay dùng cho một món — bấm để chèn vào ô ghi chú. */
export const COMMON_ITEM_NOTES = ['Nặng, cần 4 người khiêng', 'Cồng kềnh, phải nghiêng qua cửa', 'Giá trị cao', 'Cần bọc chăn / mút', 'Đồ cũ, dễ gãy chân'];

/** Ảnh hiện trường không gắn với món đồ nào (lối đi, chỗ đỗ xe…) — ảnh món đồ đã hiện cạnh danh sách đồ. */
export const photosOutsideItems = (r: Pick<SurveyReport, 'items' | 'photos'>): SitePhoto[] => {
  const itemPhotoIds = new Set(r.items.flatMap((it) => (it.photos ?? []).map((ph) => ph.id)));
  return r.photos.filter((ph) => !itemPhotoIds.has(ph.id));
};

export const surveyItemsTotal = (items: SurveyItem[]) => items.reduce((n, i) => n + i.qty, 0);
