import type { ChangeEvent, ReactNode } from 'react';
import { Camera, Play, X } from 'lucide-react';
import { cx } from '@/components/ui';
import { SITE_PHOTO_CHECKLIST } from '@/features/order/surveyStage';
import { formatTime } from '@/services/format';
import type { EvidencePhoto, SitePhoto } from '@/types';

/** Đọc ảnh / video từ input file / camera thành bằng chứng (object URL, bản demo không tải lên máy chủ). */
export const photosFromFiles = (files: FileList | null): EvidencePhoto[] => {
  const now = Date.now();
  return Array.from(files ?? [])
    .filter((f) => f.type.startsWith('image/') || f.type.startsWith('video/'))
    .map((f, i) => ({
      id: `ph-${now}-${i}-${Math.random().toString(36).slice(2, 7)}`,
      name: f.name,
      url: URL.createObjectURL(f),
      takenAt: now,
      kind: f.type.startsWith('video/') ? ('video' as const) : ('image' as const),
    }));
};

/** Ô xem trước một ảnh / video. */
const Thumb = ({ p, onRemove, className }: { p: EvidencePhoto; onRemove?: (id: string) => void; className?: string }) => (
  <div className={cx('relative overflow-hidden rounded-xl bg-slate-100', className)}>
    <a href={p.url} target="_blank" rel="noreferrer" title={`${p.name} · ${formatTime(p.takenAt)}`} className="block size-full">
      {p.kind === 'video' ? (
        <>
          <video src={p.url} muted preload="metadata" className="size-full object-cover" />
          <Play className="absolute top-1/2 left-1/2 size-5 -translate-x-1/2 -translate-y-1/2 fill-white text-white drop-shadow" />
        </>
      ) : (
        <img src={p.url} alt={p.name} className="size-full object-cover" loading="lazy" />
      )}
    </a>
    {onRemove && (
      <button
        type="button"
        aria-label={`Xóa ${p.name}`}
        onClick={() => onRemove(p.id)}
        className="absolute top-0.5 right-0.5 grid size-5 place-items-center rounded-full bg-black/60 text-white active:bg-black/80"
      >
        <X className="size-3" />
      </button>
    )}
  </div>
);

/** Nhóm ảnh: theo đối tượng chụp (món đồ, vị trí) nếu có, không thì theo nhóm checklist. */
const groupsOf = (photos: SitePhoto[]) => {
  const map = new Map<string, SitePhoto[]>();
  for (const c of SITE_PHOTO_CHECKLIST) {
    for (const p of photos.filter((x) => x.category === c.key)) {
      const label = p.subject || c.label;
      map.set(label, [...(map.get(label) ?? []), p]);
    }
  }
  return [...map.entries()].map(([label, files]) => ({ label, files }));
};

/** Ảnh hiện trường NV khảo sát chụp, nhóm theo món đồ / vị trí. Truyền onRemove để cho phép xóa (khi đang khảo sát). */
export const SitePhotoGallery = ({ photos, onRemove }: { photos: SitePhoto[]; onRemove?: (id: string) => void }) => {
  const groups = groupsOf(photos);
  if (groups.length === 0) return <p className="text-sm text-slate-500">Chưa có ảnh hiện trường.</p>;
  return (
    <div className="space-y-3">
      {groups.map((g) => (
        <div key={g.label}>
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
      <li key={p.id} className="relative aspect-square">
        <Thumb p={p} onRemove={onRemove} className="size-full" />
        <span className="pointer-events-none absolute inset-x-0 bottom-0 rounded-b-xl bg-black/45 px-1 py-0.5 text-center text-[10px] font-medium text-white tabular-nums">
          {formatTime(p.takenAt)}
        </span>
      </li>
    ))}
  </ul>
);

/** Dải ảnh nhỏ ngay dưới một dòng (món đồ, lối đi…). */
export const PhotoStrip = ({ photos, onRemove, className }: { photos: EvidencePhoto[]; onRemove?: (id: string) => void; className?: string }) =>
  photos.length === 0 ? null : (
    <div className={cx('flex gap-1.5 overflow-x-auto', className)}>
      {photos.map((p) => (
        <Thumb key={p.id} p={p} onRemove={onRemove} className="size-14 shrink-0" />
      ))}
    </div>
  );

/**
 * Nút chụp / tải ảnh – video gọn đặt cạnh một dòng. Hiện số tệp đã có.
 * `capture`: mở thẳng camera sau trên điện thoại (khảo sát tận nơi).
 */
export const PhotoButton = ({
  count,
  onAdd,
  capture,
  label = 'Chụp ảnh / quay video',
}: {
  count: number;
  onAdd: (photos: EvidencePhoto[]) => void;
  capture?: boolean;
  label?: string;
}) => (
  <label
    title={label}
    className={cx(
      'relative grid size-9 shrink-0 cursor-pointer place-items-center rounded-lg ring-1 transition-colors ring-inset',
      'bg-brand/5 text-brand ring-brand/30 hover:bg-brand/10',
    )}
  >
    <Camera className="size-4" />
    {count > 0 && (
      <span className="absolute -top-1.5 -right-1.5 grid min-w-4 place-items-center rounded-full bg-brand px-1 text-[10px] leading-4 font-semibold text-white ring-2 ring-white tabular-nums">
        {count}
      </span>
    )}
    <span className="sr-only">{label}</span>
    <input
      type="file"
      accept="image/*,video/*"
      capture={capture ? 'environment' : undefined}
      multiple
      className="sr-only"
      onChange={(e: ChangeEvent<HTMLInputElement>) => {
        const added = photosFromFiles(e.target.files);
        e.target.value = '';
        if (added.length > 0) onAdd(added);
      }}
    />
  </label>
);
