import { Footprints, X } from 'lucide-react';
import { NumberStepper } from '@/components/ui';
import { PhotoButton, PhotoStrip } from '@/features/order/components/SitePhotoGallery';
import { Block } from '@/features/staff/components/Block';
import { removeBtnCls, SuggestChip } from '@/features/order/components/surveyUi';
import { uid } from '@/services/format';
import type { EvidencePhoto, SitePhoto, SitePhotoCategory, SurveyInput } from '@/types';

export type AccessEnd = 'from' | 'to';
export type AccessKind = 'PARKING' | 'ALLEY' | 'STAIRS' | 'ELEVATOR' | 'OTHER';

/** Một dòng điều kiện lối đi ở một đầu: có thể kèm số liệu (lầu, mét), ghi chú và ảnh / video. */
export interface AccessRow {
  key: string;
  end: AccessEnd;
  kind: AccessKind;
  /** Tên tự đặt — chỉ với OTHER. */
  label: string;
  note: string;
  photos: EvidencePhoto[];
}

const KIND_META: Record<AccessKind, { label: string; placeholder: string; category: SitePhotoCategory }> = {
  PARKING: { label: 'Chỗ đỗ xe / cổng', placeholder: 'VD: đỗ được xe 2 tấn trước cổng', category: 'PARKING' },
  ALLEY: { label: 'Hẻm / khiêng bộ', placeholder: '', category: 'PARKING' },
  STAIRS: { label: 'Thang bộ', placeholder: '', category: 'ACCESS' },
  ELEVATOR: { label: 'Thang máy', placeholder: 'VD: thang máy hàng, phải đăng ký BQL', category: 'ACCESS' },
  OTHER: { label: 'Khác', placeholder: 'Mô tả', category: 'ACCESS' },
};
const KIND_ORDER: AccessKind[] = ['PARKING', 'ALLEY', 'STAIRS', 'ELEVATOR', 'OTHER'];
const END_LABEL: Record<AccessEnd, string> = { from: 'Điểm đi', to: 'Điểm đến' };

/** Số liệu tương ứng trong đề xuất (lầu thang bộ, mét hẻm). */
const numKey = (kind: AccessKind, end: AccessEnd): 'floorsFrom' | 'floorsTo' | 'alleyFrom' | 'alleyTo' | null =>
  kind === 'STAIRS' ? (end === 'from' ? 'floorsFrom' : 'floorsTo') : kind === 'ALLEY' ? (end === 'from' ? 'alleyFrom' : 'alleyTo') : null;

const rowLabel = (r: AccessRow) => (r.kind === 'OTHER' ? r.label.trim() || 'Khác' : KIND_META[r.kind].label);

export const newAccessRow = (end: AccessEnd, kind: AccessKind): AccessRow => ({ key: uid('acc'), end, kind, label: '', note: '', photos: [] });

/** Dòng khởi tạo: các số liệu đã có (từ thông tin khách) + chỗ đỗ xe hai đầu khi cần ảnh. */
export const initialAccessRows = (p: SurveyInput, withParking: boolean): AccessRow[] =>
  (['from', 'to'] as AccessEnd[]).flatMap((end) => [
    ...(withParking ? [newAccessRow(end, 'PARKING')] : []),
    ...(['ALLEY', 'STAIRS'] as AccessKind[]).filter((k) => p[numKey(k, end)!] > 0).map((k) => newAccessRow(end, k)),
  ]);

/** Ảnh / video của các dòng lối đi, gắn nhóm checklist & tên vị trí. */
export const accessPhotos = (rows: AccessRow[]): SitePhoto[] =>
  rows.flatMap((r) => r.photos.map((ph) => ({ ...ph, category: KIND_META[r.kind].category, subject: `${rowLabel(r)} — ${END_LABEL[r.end].toLowerCase()}` })));

/** Ghi chú lối đi gộp vào ghi chú hiện trường khi gửi. */
export const accessNotes = (rows: AccessRow[]): string[] =>
  rows.filter((r) => r.note.trim() || (r.kind === 'OTHER' && r.label.trim())).map((r) => `${END_LABEL[r.end]} — ${rowLabel(r)}${r.note.trim() ? `: ${r.note.trim()}` : ''}`);

const inputCls = 'h-9 w-full min-w-0 rounded-lg border border-slate-200 bg-white px-2.5 text-base focus:border-brand focus:outline-none sm:text-sm';

/**
 * Lối đi & chỗ đỗ xe theo từng đầu. Mỗi dòng: số liệu hoặc ghi chú + nút chụp ảnh / video ngay bên cạnh.
 * Thêm dòng qua gợi ý; bỏ dòng thì số liệu về 0.
 */
export const AccessEditor = ({
  value: p,
  onChange,
  rows,
  onRowsChange,
  capture,
}: {
  value: SurveyInput;
  onChange: (next: SurveyInput) => void;
  rows: AccessRow[];
  onRowsChange: (rows: AccessRow[]) => void;
  capture: boolean;
}) => {
  const patch = (key: string, x: Partial<AccessRow>) => onRowsChange(rows.map((r) => (r.key === key ? { ...r, ...x } : r)));
  const add = (end: AccessEnd, kind: AccessKind) => {
    onRowsChange([...rows, newAccessRow(end, kind)]);
    const k = numKey(kind, end);
    if (k && p[k] === 0) onChange({ ...p, [k]: kind === 'STAIRS' ? 1 : 50 });
  };
  const remove = (r: AccessRow) => {
    r.photos.forEach((ph) => URL.revokeObjectURL(ph.url));
    onRowsChange(rows.filter((x) => x.key !== r.key));
    const k = numKey(r.kind, r.end);
    if (k) onChange({ ...p, [k]: 0 });
  };

  return (
    <Block title="Lối đi & chỗ đỗ xe" icon={Footprints}>
      <div className="space-y-4">
        {(['from', 'to'] as AccessEnd[]).map((end) => {
          const mine = rows.filter((r) => r.end === end);
          // Mỗi loại một dòng / đầu, riêng "Khác" thêm được nhiều lần.
          const addable = KIND_ORDER.filter((k) => k === 'OTHER' || !mine.some((r) => r.kind === k));
          return (
            <div key={end}>
              <p className="mb-1 text-xs font-semibold tracking-wide text-slate-500 uppercase">{END_LABEL[end]}</p>
              {mine.length > 0 && (
                <ul className="divide-y divide-slate-100 rounded-xl border border-slate-200">
                  {mine.map((r) => {
                    const k = numKey(r.kind, r.end);
                    const meta = KIND_META[r.kind];
                    return (
                      <li key={r.key} className="space-y-1.5 px-2.5 py-2">
                        <div className="flex items-center gap-1.5">
                          {r.kind === 'OTHER' ? (
                            <input value={r.label} onChange={(e) => patch(r.key, { label: e.target.value })} placeholder="Tên (VD: Cửa hẹp 70cm)" className={`${inputCls} flex-1`} />
                          ) : (
                            <span className="min-w-0 flex-1 text-sm font-medium text-slate-700">{meta.label}</span>
                          )}
                          {k && (
                            <div className="w-36 shrink-0">
                              <NumberStepper
                                value={p[k]}
                                onChange={(v) => onChange({ ...p, [k]: v })}
                                min={1}
                                max={r.kind === 'STAIRS' ? 20 : 500}
                                step={r.kind === 'STAIRS' ? 1 : 10}
                                suffix={r.kind === 'STAIRS' ? 'lầu' : 'm'}
                              />
                            </div>
                          )}
                          <PhotoButton
                            count={r.photos.length}
                            capture={capture}
                            onAdd={(added) => patch(r.key, { photos: [...r.photos, ...added] })}
                          />
                          <button
                            type="button"
                            onClick={() => remove(r)}
                            aria-label="Bỏ dòng này"
                            className={removeBtnCls}
                          >
                            <X className="size-4" />
                          </button>
                        </div>
                        {!k && <input value={r.note} onChange={(e) => patch(r.key, { note: e.target.value })} placeholder={meta.placeholder} className={inputCls} />}
                        <PhotoStrip
                          photos={r.photos}
                          onRemove={(id) => {
                            const ph = r.photos.find((x) => x.id === id);
                            if (ph) URL.revokeObjectURL(ph.url);
                            patch(r.key, { photos: r.photos.filter((x) => x.id !== id) });
                          }}
                        />
                      </li>
                    );
                  })}
                </ul>
              )}
              <div className="mt-1.5 flex flex-wrap gap-1.5">
                {addable.map((kind) => (
                  <SuggestChip key={kind} onClick={() => add(end, kind)}>
                    {KIND_META[kind].label}
                  </SuggestChip>
                ))}
              </div>
            </div>
          );
        })}
      </div>
    </Block>
  );
};
