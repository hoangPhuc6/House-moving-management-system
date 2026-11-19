import type { AccessRow } from '@/features/staff/components/AccessEditor';
import type { SurveyInput, SurveyItem } from '@/types';

/** Nháp kết quả khảo sát của một đơn — người khảo sát chuyển trang / tải lại không mất phần đã nhập. */
export interface SurveyDraft {
  items: SurveyItem[];
  note: string;
  proposal: SurveyInput;
  access: AccessRow[];
  savedAt: number;
}

/**
 * Hai tầng lưu:
 * - Bộ nhớ (trong phiên): giữ đủ cả ảnh / video — object URL còn sống khi chuyển trang trong app.
 * - localStorage: chỉ chữ & số; ảnh không lưu được qua lần tải lại trang (bản demo chưa tải ảnh lên máy chủ).
 */
const memory = new Map<string, SurveyDraft>();
const key = (orderId: string) => `survey-draft:${orderId}`;

/** `restored`: nháp đọc lại sau khi tải trang — ảnh / video đã mất. */
export const loadDraft = (orderId: string): { draft: SurveyDraft; restored: boolean } | null => {
  const mem = memory.get(orderId);
  if (mem) return { draft: mem, restored: false };
  try {
    const raw = localStorage.getItem(key(orderId));
    if (!raw) return null;
    const draft = JSON.parse(raw) as SurveyDraft;
    if (!Array.isArray(draft.items) || !draft.proposal) return null;
    return { draft, restored: true };
  } catch {
    return null;
  }
};

export const saveDraft = (orderId: string, draft: SurveyDraft) => {
  memory.set(orderId, draft);
  const withoutMedia: SurveyDraft = {
    ...draft,
    items: draft.items.map(({ photos: _photos, ...it }) => it),
    access: draft.access.map((r) => ({ ...r, photos: [] })),
  };
  try {
    localStorage.setItem(key(orderId), JSON.stringify(withoutMedia));
  } catch {
    /* hết dung lượng / chặn lưu trữ — vẫn còn bản trong bộ nhớ */
  }
};

export const clearDraft = (orderId: string) => {
  memory.delete(orderId);
  try {
    localStorage.removeItem(key(orderId));
  } catch {
    /* bỏ qua */
  }
};

/** Số ảnh / video trong nháp — dùng để cảnh báo trước khi tải lại / đóng trang. */
export const draftMediaCount = (d: Pick<SurveyDraft, 'items' | 'access'>) =>
  d.items.reduce((n, it) => n + (it.photos?.length ?? 0), 0) + d.access.reduce((n, r) => n + r.photos.length, 0);
