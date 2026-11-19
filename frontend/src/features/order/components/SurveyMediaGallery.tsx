import { Image as ImageIcon } from 'lucide-react';
import { mediaOfCategory, SURVEY_MEDIA_CATEGORIES } from '@/features/order/surveyStage';
import type { Order } from '@/types';

/** Ảnh / video khách gửi, nhóm theo danh mục gợi ý ở trang khách. */
export const SurveyMediaGallery = ({ order }: { order: Order }) => {
  const groups = SURVEY_MEDIA_CATEGORIES.map((c) => ({ label: c.label, files: mediaOfCategory(order, c.key) })).filter((g) => g.files.length > 0);
  if (groups.length === 0) return null;
  return (
    <div className="space-y-3">
      {groups.map((g) => (
        <div key={g.label}>
          <p className="mb-1.5 text-xs font-semibold text-slate-500">
            {g.label} <span className="tabular-nums">({g.files.length})</span>
          </p>
          <ul className="grid grid-cols-4 gap-2 sm:grid-cols-6">
            {g.files.map((name, i) => (
              <li
                key={`${name}-${i}`}
                className="flex aspect-square flex-col items-center justify-center gap-1 rounded-xl bg-slate-100 p-1.5 text-slate-400"
              >
                <ImageIcon className="size-5" />
                <span className="w-full truncate text-center text-[10px] font-medium">{name}</span>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </div>
  );
};

