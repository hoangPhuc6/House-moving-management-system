import { useNavigate } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { CheckCircle2, ImagePlus, Plus, Trash2, X } from 'lucide-react';
import { Badge, Button, Card, cx, Field, Input } from '@/components/ui';
import { SERVICE_GROUP_LABEL } from '@/constants/catalog';
import { HOUSE_TYPE_META } from '@/constants/order';
import { TIME_SLOTS } from '@/constants/resources';
import { CUSTOMER, HOUSE_ICONS } from '@/features/customer/constants';
import { StickyActionBar } from '@/layouts/MobileShell';
import { formatVND, uid } from '@/services/format';
import { ROUTES } from '@/routes/paths';
import { buildReferenceQuote } from '@/services/referenceQuote';
import { useApp } from '@/services/store/AppStore';
import { useCatalog } from '@/services/store/CatalogStore';
import type { BookingItem, HouseType, NewOrderInput, ServiceGroup } from '@/types';

export const CreateRequest = () => {
  const { dispatch } = useApp();
  const navigate = useNavigate();
  const { catalog } = useCatalog();
  const [items, setItems] = useState<BookingItem[]>([]);
  const [itemDraft, setItemDraft] = useState({
    name: "",
    quantity: 1,
    volume: "",
    fragile: false,
  });
  const [services, setServices] = useState<string[]>([]);
  const [form, setForm] = useState<
    Omit<NewOrderInput, "photoNames" | "items" | "requestedServices" | "referenceQuote">
  >({
    customerName: CUSTOMER.name,
    phone: CUSTOMER.phone,
    fromAddress: "",
    toAddress: "",
    houseType: "APARTMENT",
    scheduledDate: "2026-11-05",
    timeSlot: "07:00 - 09:00",
  });
  const [photos, setPhotos] = useState<{ name: string; url: string }[]>([]);
  const [touched, setTouched] = useState(false);

  useEffect(
    () => () => photos.forEach((p) => URL.revokeObjectURL(p.url)),
    [photos],
  );

  const set = <K extends keyof typeof form>(k: K, v: (typeof form)[K]) =>
    setForm((f) => ({ ...f, [k]: v }));
  const addItem = (
    name: string,
    quantity = 1,
    fragile = false,
    volume = "",
  ) => {
    const n = name.trim();
    if (!n || quantity < 1) return;
    const v = Number(volume);
    setItems((arr) => [
      ...arr,
      { id: uid("it"), name: n, quantity, volumeM3: v > 0 ? v : null, fragile },
    ]);
    setItemDraft({ name: "", quantity: 1, volume: "", fragile: false });
  };
  const missing =
    items.length === 0 ||
    !form.fromAddress.trim() ||
    !form.toAddress.trim() ||
    !form.customerName.trim();

  const submit = () => {
    setTouched(true);
    if (missing) return;
    dispatch({
      type: "CREATE_ORDER",
      input: {
        ...form,
        photoNames: photos.map((p) => p.name),
        items,
        requestedServices: services,
        referenceQuote: buildReferenceQuote(
          {
            fromAddress: form.fromAddress,
            toAddress: form.toAddress,
            houseType: form.houseType,
            items,
            requestedServices: services,
          },
          catalog.services,
        ),
      },
    });
    navigate(ROUTES.customer.orders);
  };

  return (
    <div className="mx-auto max-w-2xl">
      <div className="mb-4 px-1">
        <h1 className="text-2xl font-bold tracking-tight">
          Đặt lịch chuyển nhà
        </h1>
        <p className="mt-1 text-sm text-slate-500">
          Gửi ảnh đồ đạc để được khảo sát & báo giá nhanh, không cần chờ người
          đến nhà
        </p>
      </div>
      <form
        className="space-y-4"
        onSubmit={(e) => {
          e.preventDefault();
          submit();
        }}
      >
        <Card className="space-y-4">
          <p className="text-sm font-semibold text-slate-900">
            1. Thông tin khách hàng
          </p>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field
              label="Họ tên"
              error={
                touched && !form.customerName.trim()
                  ? "Vui lòng nhập họ tên"
                  : null
              }
            >
              <Input
                value={form.customerName}
                onChange={(e) => set("customerName", e.target.value)}
                autoComplete="name"
                className="text-base sm:text-sm"
                placeholder="Nguyễn Văn A"
              />
            </Field>
            <Field label="Số điện thoại">
              <Input
                value={form.phone}
                onChange={(e) => set("phone", e.target.value)}
                inputMode="tel"
                autoComplete="tel"
                className="text-base sm:text-sm"
                placeholder="0123456789"
              />
            </Field>
          </div>
        </Card>

        <Card className="space-y-4">
          <p className="text-sm font-semibold text-slate-900">2. Địa chỉ</p>
          <Field
            label="Địa chỉ đi"
            error={
              touched && !form.fromAddress.trim()
                ? "Vui lòng nhập địa chỉ đi"
                : null
            }
          >
            <Input
              invalid={touched && !form.fromAddress.trim()}
              placeholder="Số nhà, đường, phường, quận"
              value={form.fromAddress}
              onChange={(e) => set("fromAddress", e.target.value)}
              className="text-base sm:text-sm"
            />
          </Field>
          <Field
            label="Địa chỉ đến"
            error={
              touched && !form.toAddress.trim()
                ? "Vui lòng nhập địa chỉ đến"
                : null
            }
          >
            <Input
              invalid={touched && !form.toAddress.trim()}
              placeholder="Số nhà, đường, phường, quận"
              value={form.toAddress}
              onChange={(e) => set("toAddress", e.target.value)}
              className="text-base sm:text-sm"
            />
          </Field>
          <div>
            <p className="mb-2 text-[13px] font-semibold text-slate-700">
              Loại hình nhà ở
            </p>
            <div className="grid gap-2 sm:grid-cols-3" role="radiogroup">
              {(Object.keys(HOUSE_TYPE_META) as HouseType[]).map((h) => {
                const Icon = HOUSE_ICONS[h];
                const sel = form.houseType === h;
                return (
                  <button
                    key={h}
                    type="button"
                    role="radio"
                    aria-checked={sel}
                    onClick={() => set("houseType", h)}
                    className={cx(
                      "flex items-center gap-3 rounded-2xl border p-3.5 text-left transition-all sm:flex-col sm:items-start",
                      sel
                        ? "border-brand bg-orange-50/60 ring-4 ring-brand/10"
                        : "border-slate-200 hover:border-slate-300",
                    )}
                  >
                    <span
                      className={cx(
                        "grid size-10 shrink-0 place-items-center rounded-xl",
                        sel
                          ? "bg-brand text-white"
                          : "bg-slate-100 text-slate-500",
                      )}
                    >
                      <Icon className="size-5" />
                    </span>
                    <span>
                      <span className="block text-sm font-semibold text-slate-900">
                        {HOUSE_TYPE_META[h].label}
                      </span>
                      <span className="block text-xs text-slate-500">
                        {HOUSE_TYPE_META[h].hint}
                      </span>
                    </span>
                  </button>
                );
              })}
            </div>
          </div>
        </Card>

        <Card className="space-y-4">
          <p className="text-sm font-semibold text-slate-900">3. Thời gian</p>
          <Field label="Ngày chuyển">
            <Input
              type="date"
              value={form.scheduledDate}
              onChange={(e) => set("scheduledDate", e.target.value)}
              className="text-base sm:text-sm"
            />
          </Field>
          <div>
            <p className="mb-2 text-[13px] font-semibold text-slate-700">
              Khung giờ
            </p>
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
              {TIME_SLOTS.map((s) => {
                const sel = form.timeSlot === s.label;
                return (
                  <button
                    key={s.id}
                    type="button"
                    onClick={() => set("timeSlot", s.label)}
                    className={cx(
                      "rounded-xl border p-3 text-left text-sm font-semibold tabular-nums transition-colors",
                      sel
                        ? "border-brand bg-orange-50/60 text-slate-900 ring-4 ring-brand/10"
                        : "border-slate-200 text-slate-900 hover:border-slate-300",
                    )}
                  >
                    {s.label}
                  </button>
                );
              })}
            </div>
          </div>
        </Card>

        <Card className="space-y-4">
          <div>
            <p className="text-sm font-semibold text-slate-900">
              4. Đồ đạc cần vận chuyển
            </p>
            <p className="mt-0.5 text-xs text-slate-500">
              Khai báo càng đầy đủ, báo giá càng chính xác.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            {[
              "Tủ lạnh",
              "Máy giặt",
              "Tivi",
              "Giường",
              "Tủ quần áo",
              "Bàn ghế",
              "Máy lạnh",
              "Thùng carton",
            ].map((n) => (
              <button
                key={n}
                type="button"
                onClick={() => addItem(n, 1, n === "Tivi")}
                className="rounded-full border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-600 hover:border-brand hover:text-brand"
              >
                + {n}
              </button>
            ))}
          </div>
          <div className="space-y-3 rounded-2xl border border-slate-200 bg-slate-50/60 p-4">
            <Field label="Tên đồ vật">
              <Input
                placeholder="VD: Sofa 3 chỗ, bàn làm việc…"
                value={itemDraft.name}
                onChange={(e) =>
                  setItemDraft({ ...itemDraft, name: e.target.value })
                }
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    e.preventDefault();
                    addItem(
                      itemDraft.name,
                      itemDraft.quantity,
                      itemDraft.fragile,
                      itemDraft.volume,
                    );
                  }
                }}
                className="text-base sm:text-sm"
              />
            </Field>
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-[110px_150px_1fr_auto] sm:items-end">
              <Field label="Số lượng">
                <Input
                  type="number"
                  min={1}
                  value={itemDraft.quantity}
                  onChange={(e) =>
                    setItemDraft({
                      ...itemDraft,
                      quantity: Math.max(
                        1,
                        Math.floor(Number(e.target.value)) || 1,
                      ),
                    })
                  }
                  className="text-base sm:text-sm"
                />
              </Field>
              <Field label="Thể tích / món (m³)">
                <Input
                  type="number"
                  min={0}
                  step={0.1}
                  placeholder="Tùy chọn"
                  value={itemDraft.volume}
                  onChange={(e) =>
                    setItemDraft({ ...itemDraft, volume: e.target.value })
                  }
                  className="text-base sm:text-sm"
                />
              </Field>
              <label className="flex h-11 items-center gap-2 text-sm font-medium text-slate-700">
                <input
                  type="checkbox"
                  checked={itemDraft.fragile}
                  onChange={(e) =>
                    setItemDraft({ ...itemDraft, fragile: e.target.checked })
                  }
                  className="size-4 rounded accent-[#FF5A1F]"
                />{" "}
                Đồ dễ vỡ
              </label>
              <Button
                type="button"
                icon={Plus}
                disabled={!itemDraft.name.trim()}
                onClick={() =>
                  addItem(
                    itemDraft.name,
                    itemDraft.quantity,
                    itemDraft.fragile,
                    itemDraft.volume,
                  )
                }
                className="col-span-2 sm:col-span-1"
              >
                Thêm
              </Button>
            </div>
          </div>
          {items.length === 0 ? (
            <p
              className={cx(
                "rounded-xl border border-dashed p-4 text-center text-sm",
                touched
                  ? "border-rose-300 bg-rose-50/50 text-rose-600"
                  : "border-slate-200 text-slate-500",
              )}
            >
              Chưa có đồ đạc nào. Hãy thêm ít nhất một món.
            </p>
          ) : (
            <ul className="divide-y divide-slate-100 rounded-xl border border-slate-200">
              {items.map((i) => (
                <li
                  key={i.id}
                  className="flex items-center gap-2 px-3 py-2.5 text-sm"
                >
                  <span className="min-w-0 flex-1 truncate font-medium text-slate-800">
                    {i.name}
                  </span>
                  {i.fragile && <Badge tone="warning">Dễ vỡ</Badge>}
                  {i.volumeM3 ? (
                    <span className="text-xs text-slate-400 tabular-nums">
                      {i.volumeM3} m³
                    </span>
                  ) : null}
                  <span className="font-semibold tabular-nums">
                    ×{i.quantity}
                  </span>
                  <button
                    type="button"
                    onClick={() =>
                      setItems((arr) => arr.filter((x) => x.id !== i.id))
                    }
                    className="rounded-lg p-1.5 text-slate-400 hover:bg-rose-50 hover:text-rose-600"
                    aria-label={`Xóa ${i.name}`}
                  >
                    <Trash2 className="size-4" />
                  </button>
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card className="space-y-4">
          <p className="text-sm font-semibold text-slate-900">5. Ảnh đồ đạc</p>
          <div>
            <p className="mb-2 text-[13px] font-semibold text-slate-700">
              Ảnh đồ đạc{" "}
              <span className="font-normal text-slate-400">(khuyến khích)</span>
            </p>
            <p className="mb-2 text-xs text-slate-500">
              Điều phối viên sẽ lập báo giá dựa trên ảnh bạn gửi. Chỉ khi cần
              thiết, chúng tôi mới cử nhân viên đến khảo sát trực tiếp.
            </p>
            <div className="grid grid-cols-4 gap-2 sm:grid-cols-6">
              {photos.map((p, i) => (
                <div
                  key={p.url}
                  className="relative aspect-square overflow-hidden rounded-xl border border-slate-200"
                >
                  <img
                    src={p.url}
                    alt={p.name}
                    className="size-full object-cover"
                  />
                  <button
                    type="button"
                    onClick={() =>
                      setPhotos((arr) => arr.filter((_, j) => j !== i))
                    }
                    className="absolute top-1 right-1 grid size-6 place-items-center rounded-full bg-slate-900/70 text-white"
                    aria-label="Xóa ảnh"
                  >
                    <X className="size-3.5" />
                  </button>
                </div>
              ))}
              {photos.length < 12 && (
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
                      const files = Array.from(e.target.files ?? []).slice(
                        0,
                        12 - photos.length,
                      );
                      setPhotos((p) => [
                        ...p,
                        ...files.map((f) => ({
                          name: f.name,
                          url: URL.createObjectURL(f),
                        })),
                      ]);
                      e.target.value = "";
                    }}
                  />
                </label>
              )}
            </div>
          </div>
        </Card>

        <Card className="space-y-4">
          <div>
            <p className="text-sm font-semibold text-slate-900">
              6. Dịch vụ mong muốn
            </p>
            <p className="mt-0.5 text-xs text-slate-500">
              Chọn các dịch vụ đang cung cấp. Giá chính thức sẽ được báo sau
              khảo sát.
            </p>
          </div>
          {(Object.keys(SERVICE_GROUP_LABEL) as ServiceGroup[]).map((g) => {
            const list = catalog.services.filter(
              (x) => x.active && x.group === g,
            );
            if (list.length === 0) return null;
            return (
              <div key={g}>
                <p className="mb-2 text-[13px] font-semibold text-slate-700">
                  {SERVICE_GROUP_LABEL[g]}
                </p>
                <div className="grid gap-2 sm:grid-cols-2">
                  {list.map((x) => {
                    const sel = services.includes(x.name);
                    return (
                      <button
                        key={x.id}
                        type="button"
                        role="checkbox"
                        aria-checked={sel}
                        onClick={() =>
                          setServices((arr) =>
                            sel
                              ? arr.filter((n) => n !== x.name)
                              : [...arr, x.name],
                          )
                        }
                        className={cx(
                          "flex items-start gap-3 rounded-xl border p-3 text-left transition-colors",
                          sel
                            ? "border-brand bg-orange-50/60 ring-4 ring-brand/10"
                            : "border-slate-200 hover:border-slate-300",
                        )}
                      >
                        <span
                          className={cx(
                            "mt-0.5 grid size-5 shrink-0 place-items-center rounded-md border",
                            sel
                              ? "border-brand bg-brand text-white"
                              : "border-slate-300",
                          )}
                        >
                          {sel && <CheckCircle2 className="size-3.5" />}
                        </span>
                        <span className="min-w-0">
                          <span className="block text-sm font-semibold text-slate-900">
                            {x.name}
                          </span>
                          <span className="block text-xs text-slate-500">
                            Từ {formatVND(x.basePrice)} / {x.unit}
                          </span>
                        </span>
                      </button>
                    );
                  })}
                </div>
              </div>
            );
          })}
        </Card>

        <StickyActionBar>
          <div className="flex items-center gap-3">
            <p className="min-w-0 flex-1 text-xs text-slate-500">
              Bằng việc gửi yêu cầu, bạn đồng ý với{" "}
              <span className="font-semibold text-slate-700">
                Điều khoản dịch vụ
              </span>
              .
            </p>
            <Button type="submit" size="lg">
              Gửi yêu cầu
            </Button>
          </div>
        </StickyActionBar>
      </form>
    </div>
  );
};
