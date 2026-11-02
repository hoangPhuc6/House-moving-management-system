import { Camera, Play } from 'lucide-react';
import { cx } from '@/components/ui';
import type { EvidenceMedia } from '@/types';

export const EvidenceTile = ({ m }: { m: EvidenceMedia }) => (
  <figure className="overflow-hidden rounded-xl border border-slate-200 bg-white">
    <div
      className={cx(
        "relative grid aspect-[4/3] place-items-center",
        m.tone === "danger"
          ? "bg-gradient-to-br from-rose-100 to-rose-200"
          : m.tone === "warning"
            ? "bg-gradient-to-br from-amber-100 to-amber-200"
            : "bg-gradient-to-br from-slate-100 to-slate-200",
      )}
    >
      {m.kind === "VIDEO" ? (
        <span className="grid size-11 place-items-center rounded-full bg-slate-900/80 text-white">
          <Play className="size-5" />
        </span>
      ) : (
        <Camera
          className={cx(
            "size-8",
            m.tone === "danger"
              ? "text-rose-400"
              : m.tone === "warning"
                ? "text-amber-500"
                : "text-slate-400",
          )}
        />
      )}
      <span className="absolute top-2 left-2 rounded bg-slate-900/70 px-1.5 py-0.5 text-[10px] font-semibold text-white">
        {m.kind === "VIDEO" ? "VIDEO" : "ẢNH"}
      </span>
      <span className="absolute right-2 bottom-2 rounded bg-white/80 px-1.5 py-0.5 text-[10px] font-semibold text-slate-700 tabular-nums">
        {m.takenAt}
      </span>
    </div>
    <figcaption className="p-2.5 text-xs font-medium text-slate-700">
      {m.caption}
    </figcaption>
  </figure>
);
