import { useState } from 'react';
import { ClipboardList, CloudUpload, Headset, ImagePlus, Info, Plus, Trash2, X } from 'lucide-react';
import { Badge, Button, Input, cx } from '@/components/ui';
import { HOUSE_TYPE_META } from '@/constants/order';
import { HOUSE_ICONS } from '@/features/customer/constants';
import { uid } from '@/services/format';
import type { SurveyPreference } from '@/types';
import { HOME_SCALES, INVENTORY_PRESETS, MAX_PHOTOS, SURVEY_CONTACT_SLOTS, SURVEY_PREFERENCES } from './constants';
import { CompactStepper, FieldError, SectionCard, choiceClass } from './parts';
import type { BookingWizard, StepErrors } from './useBookingWizard';

export const StepInventory = ({ wizard, errors }: { wizard: BookingWizard; errors: StepErrors }) => {
  const { draft, update, setDraft, items, houseType, photos, addPhotos, removePhoto } = wizard;
  const [custom, setCustom] = useState({ name: '', quantity: 1, fragile: false });
  const HouseIcon = HOUSE_ICONS[houseType];
  const totalUnits = items.reduce((s, i) => s + i.quantity, 0);
  const volume = items.reduce((s, i) => s + (i.volumeM3 ?? 0) * i.quantity, 0);

  const setQty = (name: string, q: number) => setDraft((d) => ({ ...d, quantities: { ...d.quantities, [name]: q } }));
  const pickScale = (label: string) => {
    const scale = HOME_SCALES.find((s) => s.label === label);
    setDraft((d) => {
      const empty = Object.values(d.quantities).every((q) => !q);
      return { ...d, homeScale: label, quantities: empty && scale ? { ...scale.preset } : d.quantities };
    });
  };
  const addCustom = () => {
    const name = custom.name.trim();
    if (!name) return;
    setDraft((d) => ({ ...d, customItems: [...d.customItems, { id: uid('it'), name, quantity: custom.quantity, volumeM3: null, fragile: custom.fragile }] }));
    setCustom({ name: '', quantity: 1, fragile: false });
  };

  return (
    <div className="space-y-4">
      <SectionCard
        id="inventory"
        icon={ClipboardList}
        tone="brand"
        title="Không gian & đồ đạc sơ bộ"
        subtitle="Khai báo càng đầy đủ, báo giá càng chính xác"
        action={totalUnits > 0 ? <Badge tone="brand">Đã chọn {items.length} loại • {totalUnits} món</Badge> : null}
      >
        <div className="flex items-start gap-3 rounded-xl border border-slate-200 bg-slate-50/60 p-3.5">
          <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-white text-slate-600 ring-1 ring-slate-200">
            <HouseIcon className="size-[18px]" />
          </span>
          <div className="min-w-0 text-sm">
            <p className="font-semibold text-slate-900">Điều kiện bốc xếp: {HOUSE_TYPE_META[houseType].label}</p>
            <p className="text-xs text-slate-500">
              Theo địa chỉ khó tiếp cận hơn ở Bước 1 ({draft.from.floor.toLowerCase()} → {draft.to.floor.toLowerCase()}). {HOUSE_TYPE_META[houseType].hint}.
            </p>
          </div>
        </div>

        <div>
          <p className="mb-2 text-[13px] font-semibold text-slate-700">Quy mô nhà ở hiện tại</p>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-5" role="radiogroup" aria-label="Quy mô nhà ở">
            {HOME_SCALES.map((s) => {
              const sel = draft.homeScale === s.label;
              return (
                <button key={s.label} type="button" role="radio" aria-checked={sel} onClick={() => pickScale(s.label)} className={cx(choiceClass(sel), 'p-3')}>
                  <span className={cx('block text-sm font-semibold', sel ? 'text-slate-900' : 'text-slate-800')}>{s.label}</span>
                  <span className="block text-xs text-slate-500">{s.hint}</span>
                </button>
              );
            })}
          </div>
          <p className="mt-2 text-xs text-slate-500">Chọn quy mô khi danh mục trống để hệ thống gợi ý số lượng — bạn vẫn có thể chỉnh từng món.</p>
        </div>

        <div>
          <div className="mb-2 flex items-center justify-between gap-2">
            <p className="text-[13px] font-semibold text-slate-700">Danh mục đồ lớn cần lưu ý vận chuyển</p>
            {volume > 0 && <span className="text-xs font-medium text-slate-500 tabular-nums">≈ {volume.toFixed(1)} m³</span>}
          </div>
          <div className="grid gap-2 sm:grid-cols-2">
            {INVENTORY_PRESETS.map((p) => {
              const q = draft.quantities[p.name] ?? 0;
              return (
                <div key={p.name} className={cx('flex items-center gap-3 rounded-xl border p-3 transition-colors', q > 0 ? 'border-brand/40 bg-orange-50/40' : 'border-slate-200')}>
                  <span className={cx('grid size-9 shrink-0 place-items-center rounded-lg', q > 0 ? 'bg-brand text-white' : 'bg-slate-100 text-slate-500')}>
                    <p.icon className="size-[18px]" />
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="flex items-center gap-1.5 text-sm font-semibold text-slate-900">
                      <span className="truncate">{p.name}</span>
                      {p.fragile && <Badge tone="warning">Dễ vỡ</Badge>}
                    </span>
                    <span className="block truncate text-xs text-slate-500">{p.hint}</span>
                  </span>
                  <CompactStepper value={q} onChange={(n) => setQty(p.name, n)} label={p.name} />
                </div>
              );
            })}
          </div>
        </div>

        <div className="space-y-3 rounded-2xl border border-dashed border-slate-300 p-4">
          <p className="flex items-center gap-2 text-[13px] font-semibold text-slate-700">
            <Plus className="size-4 text-brand" /> Thêm đồ đạc hoặc ghi chú đặc biệt khác (đàn piano, két sắt, gương soi…)
          </p>
          <div className="grid gap-3 sm:grid-cols-[1fr_96px_auto_auto] sm:items-center">
            <Input
              aria-label="Tên đồ vật"
              placeholder="VD: Đàn piano đứng"
              value={custom.name}
              onChange={(e) => setCustom({ ...custom, name: e.target.value })}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault();
                  addCustom();
                }
              }}
              className="text-base sm:text-sm"
            />
            <Input
              aria-label="Số lượng"
              type="number"
              min={1}
              value={custom.quantity}
              onChange={(e) => setCustom({ ...custom, quantity: Math.max(1, Math.floor(Number(e.target.value)) || 1) })}
              className="text-base sm:text-sm"
            />
            <label className="flex items-center gap-2 text-sm font-medium text-slate-700">
              <input type="checkbox" checked={custom.fragile} onChange={(e) => setCustom({ ...custom, fragile: e.target.checked })} className="size-4 rounded accent-[#FF5A1F]" /> Dễ vỡ
            </label>
            <Button icon={Plus} variant="secondary" disabled={!custom.name.trim()} onClick={addCustom}>
              Thêm
            </Button>
          </div>
          {draft.customItems.length > 0 && (
            <ul className="divide-y divide-slate-100 rounded-xl border border-slate-200 bg-white">
              {draft.customItems.map((i) => (
                <li key={i.id} className="flex items-center gap-2 px-3 py-2.5 text-sm">
                  <span className="min-w-0 flex-1 truncate font-medium text-slate-800">{i.name}</span>
                  {i.fragile && <Badge tone="warning">Dễ vỡ</Badge>}
                  <span className="font-semibold tabular-nums">×{i.quantity}</span>
                  <button
                    type="button"
                    onClick={() => update('customItems', draft.customItems.filter((x) => x.id !== i.id))}
                    className="rounded-lg p-1.5 text-slate-400 hover:bg-rose-50 hover:text-rose-600"
                    aria-label={`Xóa ${i.name}`}
                  >
                    <Trash2 className="size-4" />
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
        <FieldError message={errors.items} />

        <div>
          <div className="mb-2 flex items-center justify-between gap-2">
            <p className="text-[13px] font-semibold text-slate-700">
              Hình ảnh đồ đạc thực tế <span className="font-normal text-slate-400">(khuyến khích)</span>
            </p>
            <span className="text-xs font-medium text-slate-500 tabular-nums">
              {photos.length}/{MAX_PHOTOS} ảnh
            </span>
          </div>
          <label
            className="flex cursor-pointer flex-col items-center justify-center gap-2 rounded-2xl border-2 border-dashed border-slate-200 bg-slate-50/50 px-4 py-6 text-center transition-colors hover:border-brand/50"
            onDragOver={(e) => e.preventDefault()}
            onDrop={(e) => {
              e.preventDefault();
              addPhotos(Array.from(e.dataTransfer.files).filter((f) => f.type.startsWith('image/')));
            }}
          >
            <span className="grid size-11 place-items-center rounded-xl bg-white text-brand ring-1 ring-slate-200">
              <CloudUpload className="size-5" />
            </span>
            <span className="text-sm font-semibold text-slate-800">
              Kéo thả hình ảnh vào đây hoặc <span className="text-brand underline">chọn tệp</span>
            </span>
            <span className="text-xs text-slate-500">Chụp nhanh góc phòng khách, phòng ngủ và lối đi hành lang</span>
            <input
              type="file"
              multiple
              accept="image/*"
              className="sr-only"
              disabled={photos.length >= MAX_PHOTOS}
              onChange={(e) => {
                addPhotos(Array.from(e.target.files ?? []));
                e.target.value = '';
              }}
            />
          </label>
          {photos.length > 0 && (
            <div className="mt-3 grid grid-cols-4 gap-2 sm:grid-cols-6">
              {photos.map((p) => (
                <div key={p.url} className="relative aspect-square overflow-hidden rounded-xl border border-slate-200">
                  <img src={p.url} alt={p.name} className="size-full object-cover" />
                  <button type="button" onClick={() => removePhoto(p.url)} className="absolute top-1 right-1 grid size-6 place-items-center rounded-full bg-slate-900/70 text-white" aria-label={`Xóa ảnh ${p.name}`}>
                    <X className="size-3.5" />
                  </button>
                </div>
              ))}
              {photos.length < MAX_PHOTOS && (
                <label className="flex aspect-square cursor-pointer flex-col items-center justify-center gap-1 rounded-xl border-2 border-dashed border-slate-200 text-slate-400 transition-colors hover:border-brand/50 hover:text-brand">
                  <ImagePlus className="size-5" />
                  <span className="text-[10px] font-semibold">Thêm ảnh</span>
                  <input
                    type="file"
                    multiple
                    accept="image/*"
                    capture="environment"
                    className="sr-only"
                    onChange={(e) => {
                      addPhotos(Array.from(e.target.files ?? []));
                      e.target.value = '';
                    }}
                  />
                </label>
              )}
            </div>
          )}
        </div>
      </SectionCard>

      <SectionCard icon={Headset} title="Lựa chọn hình thức khảo sát" subtitle="Miễn phí 100% — không phát sinh chi phí dù không sử dụng dịch vụ" action={<Badge tone="success">0 đ</Badge>}>
        <div className="space-y-2" role="radiogroup" aria-label="Hình thức khảo sát">
          {(Object.keys(SURVEY_PREFERENCES) as SurveyPreference[]).map((k) => {
            const meta = SURVEY_PREFERENCES[k];
            const sel = draft.surveyPreference === k;
            return (
              <button key={k} type="button" role="radio" aria-checked={sel} onClick={() => update('surveyPreference', k)} className={cx(choiceClass(sel), 'flex w-full items-start gap-3 p-4')}>
                <span className={cx('mt-0.5 grid size-5 shrink-0 place-items-center rounded-full border-2', sel ? 'border-brand' : 'border-slate-300')}>
                  {sel && <span className="size-2.5 rounded-full bg-brand" />}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="flex flex-wrap items-center gap-2 text-sm font-semibold text-slate-900">
                    {meta.title}
                    {meta.badge && <Badge tone={k === 'VIDEO_CALL' ? 'brand' : 'neutral'}>{meta.badge}</Badge>}
                  </span>
                  <span className="mt-1 block text-[13px] leading-relaxed text-slate-500">{meta.desc}</span>
                  <span className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs font-medium text-slate-500">
                    {meta.meta.map((m) => (
                      <span key={m} className="inline-flex items-center gap-1">
                        <meta.icon className="size-3.5 text-slate-400" /> {m}
                      </span>
                    ))}
                  </span>
                </span>
              </button>
            );
          })}
        </div>

        {draft.surveyPreference !== 'PHOTOS_ONLY' ? (
          <div className="rounded-2xl bg-slate-50 p-4">
            <p className="mb-2 text-[13px] font-semibold text-slate-700">Khung giờ mong muốn chuyên viên liên hệ khảo sát</p>
            <div className="grid grid-cols-2 gap-2 lg:grid-cols-4">
              {SURVEY_CONTACT_SLOTS.map((s) => {
                const sel = draft.surveyContactSlot === s;
                return (
                  <button
                    key={s}
                    type="button"
                    aria-pressed={sel}
                    onClick={() => update('surveyContactSlot', s)}
                    className={cx(
                      'min-h-11 rounded-xl border px-3 py-2 text-xs font-semibold transition-colors',
                      sel ? 'border-slate-900 bg-slate-900 text-white' : 'border-slate-200 bg-white text-slate-700 hover:border-slate-300',
                    )}
                  >
                    {s}
                  </button>
                );
              })}
            </div>
            <p className="mt-2 flex items-center gap-1.5 text-xs text-slate-500">
              <Info className="size-3.5 shrink-0" /> Chuyên viên liên hệ trước 15 phút qua số điện thoại bạn khai báo để xác nhận cuộc gọi.
            </p>
          </div>
        ) : (
          photos.length === 0 && (
            <p className="flex items-start gap-2 rounded-xl border border-amber-200 bg-amber-50/80 px-3 py-2 text-xs text-amber-900">
              <Info className="mt-0.5 size-3.5 shrink-0 text-amber-600" /> Bạn chưa đính kèm ảnh nào — nên thêm ảnh để báo giá không cần khảo sát được chính xác.
            </p>
          )
        )}
      </SectionCard>
    </div>
  );
};
