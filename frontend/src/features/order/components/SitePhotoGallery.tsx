import type { ReactNode } from 'react';
import { X } from 'lucide-react';
import { SITE_PHOTO_CHECKLIST } from '@/features/order/surveyStage';
import { formatTime } from '@/services/format';
import type { EvidencePhoto, SitePhoto } from '@/types';

/** Đọc ảnh từ input file / camera thành ảnh bằng chứng (object URL, bản demo không tải lên máy chủ). */
export const photosFromFiles = (files: FileList | null): EvidencePhoto[] => {
  const now = Date.now();
  return Array.from(files ?? [])
    .filter((f) => f.type.startsWith('image/'))
    .map((f, i) => ({ id: `ph-${now}-${i}-${Math.random().toString(36).slice(2, 7)}`, name: f.name, url: URL.createObjectURL(f), takenAt: now }));
};

/** Ảnh hiện trường NV khảo sát chụp, nhóm theo checklist. Truyền onRemove để cho phép xóa (khi đang khảo sát). */
export const SitePhotoGallery = ({ photos, onRemove }: { photos: SitePhoto[]; onRemove?: (id: string) => void }) => {
  const groups = SITE_PHOTO_CHECKLIST.map((c) => ({ ...c, files: photos.filter((p) => p.category === c.key) })).filter((g) => g.files.length > 0);
  if (groups.length === 0) return <p className="text-sm text-slate-500">Chưa có ảnh hiện trường.</p>;
  return (
    <div className="space-y-3">
      {groups.map((g) => (
        <div key={g.key}>
          <p className="mb-1.5 text-xs font-semibold text-slate-500">
            {g.label} <span className="tabular-nums">({g.files.length})</span>
          </p>
          <SitePhotoGrid photos={g.files} onRemove={onRemove} />
        </div>
      ))}
    </div>
  );
};

export const SitePhotoGrid = ({ photos, onRemove, addTile }: { photos: EvidencePhoto[]; onRemove?: (id: string) => void; addTile?: ReactNode }) => (
  <ul className="grid grid-cols-4 gap-2 sm:grid-cols-6">
    {addTile && <li className="aspect-square">{addTile}</li>}
    {photos.map((p) => (
      <li key={p.id} className="relative aspect-square overflow-hidden rounded-xl bg-slate-100">
        <a href={p.url} target="_blank" rel="noreferrer" title={`${p.name} · ${formatTime(p.takenAt)}`}>
          <img src={p.url} alt={p.name} className="size-full object-cover" loading="lazy" />
        </a>
        <span className="pointer-events-none absolute inset-x-0 bottom-0 bg-black/45 px-1 py-0.5 text-center text-[10px] font-medium text-white tabular-nums">
          {formatTime(p.takenAt)}
        </span>
        {onRemove && (
          <button
            type="button"
            aria-label={`Xóa ảnh ${p.name}`}
            onClick={() => onRemove(p.id)}
            className="absolute top-1 right-1 grid size-6 place-items-center rounded-full bg-black/60 text-white active:bg-black/80"
          >
            <X className="size-3.5" />
          </button>
        )}
      </li>
    ))}
  </ul>
);
