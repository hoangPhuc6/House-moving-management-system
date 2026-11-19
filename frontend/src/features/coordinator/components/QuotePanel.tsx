import { useEffect, useMemo, useState } from 'react';
import { Lock, MessageSquareWarning, Plus, ReceiptText, RotateCcw, Send, Trash2, Undo2 } from 'lucide-react';
import { Alert, Button, Card, cx, EmptyState, Field, Input, Select } from '@/components/ui';
import { SurveyPanel } from '@/features/coordinator/components/SurveyPanel';
import { SurveyRecordCard } from '@/features/order/components/SurveyRecordCard';
import { formatTime, formatVND, uid } from '@/services/format';
import { isQuoteExtraService, lineTotal, quoteItemFromService } from '@/services/pricing';
import { useApp } from '@/services/store/AppStore';
import { useCatalog } from '@/services/store/CatalogStore';
import type { Order, QuoteItem, QuoteItemPatch } from '@/types';

const toPatches = (o: Order): QuoteItemPatch[] => o.quoteItems.map(({ id, quantity, unitPrice }) => ({ id, quantity, unitPrice }));

const cellInput =
  'h-9 w-full rounded-lg border border-slate-200 bg-white px-2 text-right text-sm tabular-nums focus:border-brand focus:ring-4 focus:ring-brand/10 focus:outline-none disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-500';

/**
 * Mục "Khảo sát & Báo giá" của trang chi tiết đơn: khảo sát → lập báo giá → thương lượng / chỉnh sửa (UC-15).
 * Khi chờ khách chốt: số lượng sửa được mọi dòng; đơn giá chỉ khoản ngoài bảng giá (thương lượng giá → giảm giá toàn đơn).
 * Bỏ dòng, thêm dịch vụ bảng giá / khoản ngoài bảng giá; sửa thì bắt buộc ghi lý do, rồi gửi lại cho khách.
 */
export const QuotePanel = ({ order }: { order: Order }) => {
  const { dispatch } = useApp();
  const { catalog } = useCatalog();
  const [draft, setDraft] = useState<QuoteItemPatch[]>(() => toPatches(order));
  const [removedIds, setRemovedIds] = useState<string[]>([]);
  const [added, setAdded] = useState<QuoteItem[]>([]);
  const [discountPct, setDiscountPct] = useState(order.discountPercent);
  const [note, setNote] = useState('');
  const [tried, setTried] = useState(false);

  // Đồng bộ lại bản nháp khi đổi đơn hoặc dữ liệu gốc thay đổi từ vai trò khác.
  const signature = order.quoteItems.map((i) => `${i.id}:${i.quantity}:${i.unitPrice}`).join('|') + `|${order.discountPercent}`;
  const reset = () => {
    setDraft(toPatches(order));
    setRemovedIds([]);
    setAdded([]);
    setDiscountPct(order.discountPercent);
    setTried(false);
  };
  useEffect(reset, [order.id, signature]);

  const merged = order.quoteItems.map((it) => ({ ...it, ...(draft.find((d) => d.id === it.id) ?? {}) }));
  const kept = merged.filter((it) => !removedIds.includes(it.id));
  const allLines = [...kept, ...added];
  const dirty =
    discountPct !== order.discountPercent ||
    removedIds.length > 0 ||
    added.length > 0 ||
    merged.some((m, i) => {
      const o = order.quoteItems[i];
      return !!o && (o.quantity !== m.quantity || o.unitPrice !== m.unitPrice);
    });
  // Giá chỉ được sửa khi báo giá đang chờ khách xác nhận; các trạng thái khác chỉ xem.
  const editable = order.status === 'SURVEYED_PENDING_CONFIRM';
  const draftSubtotal = allLines.reduce((s, i) => s + lineTotal(i), 0);
  const draftDiscount = Math.round((draftSubtotal * discountPct) / 100);
  const draftTotal = draftSubtotal - draftDiscount;
  const vehicleLeft = allLines.some((i) => i.category === 'VEHICLE');

  const extraServices = useMemo(() => catalog.services.filter(isQuoteExtraService), [catalog.services]);
  const pickable = extraServices.filter((x) => !allLines.some((l) => l.name === x.name));
  const customInvalid = added.some((i) => i.offCatalog && (!i.name.trim() || i.unitPrice <= 0 || !i.note?.trim()));
  const noteMissing = !note.trim();

  const patch = (id: string, k: keyof Omit<QuoteItemPatch, 'id'>, v: number) =>
    setDraft((d) => d.map((x) => (x.id === id ? { ...x, [k]: Math.max(0, v) } : x)));
  const patchAdded = (id: string, p: Partial<QuoteItem>) => setAdded((xs) => xs.map((x) => (x.id === id ? { ...x, ...p } : x)));

  /** Lưu thay đổi (nếu có) rồi gửi lại cho khách — bộ đếm 24h chạy lại từ đầu. */
  const saveAndResend = () => {
    if (dirty) {
      setTried(true);
      if (noteMissing || customInvalid || !vehicleLeft) return;
      dispatch({ type: 'UPDATE_QUOTE_ITEMS', orderId: order.id, patches: draft, removedIds, added, discountPercent: discountPct, note });
    }
    dispatch({ type: 'RESEND_QUOTE', orderId: order.id, note: dirty ? '' : note });
    setNote('');
    setTried(false);
  };

  return (
        <div className="space-y-4">
          <Card padded={false}>
            {order.status === 'NEW_PENDING_SURVEY' ? (
              <SurveyPanel key={order.id} order={order} />
            ) : order.quoteItems.length === 0 ? (
              <div className="p-6 pt-0">
                <EmptyState icon={ReceiptText} title="Đơn chưa có báo giá">
                  Đơn này không có hạng mục báo giá.
                </EmptyState>
              </div>
            ) : (
              <>
                {order.negotiation && editable && (
                  <Alert tone="warning" icon={MessageSquareWarning} title="Khách yêu cầu thương lượng" className="mx-6 mt-6">
                    <span className="block whitespace-pre-line">“{order.negotiation.message}”</span>
                    <span className="mt-1 block text-xs opacity-80">
                      Gửi lúc <span className="tabular-nums">{formatTime(order.negotiation.requestedAt)}</span> · Bộ đếm 24h đang tạm dừng — sửa báo giá bên dưới rồi gửi lại cho khách.
                    </span>
                  </Alert>
                )}
                {!editable && (
                  <div className="mx-6 mt-6 flex items-center gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                    <Lock className="size-4 shrink-0" />
                    Báo giá đã khóa — chỉ chỉnh sửa được khi đơn đang chờ khách xác nhận báo giá.
                  </div>
                )}
                {editable && (
                  <p className="mx-6 mt-6 text-xs text-slate-500">
                    Đơn giá theo bảng giá được khóa — chỉ khoản <b>ngoài bảng giá</b> sửa được đơn giá. Thương lượng giá với khách bằng <b>giảm giá toàn đơn</b>.
                  </p>
                )}
                <div className="mt-3 overflow-x-auto">
                  <table className="w-full min-w-[760px] text-sm">
                    <thead>
                      <tr className="border-y border-slate-100 bg-slate-50/60 text-left text-xs font-semibold tracking-wide text-slate-500 uppercase">
                        <th className="px-6 py-3">Hạng mục</th>
                        <th className="w-24 px-2 py-3 text-center">SL</th>
                        <th className="w-36 px-2 py-3 text-center">Đơn giá</th>
                        <th className="px-4 py-3 text-right">Thành tiền</th>
                        <th className="w-12 py-3 pr-4" />
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {merged.map((it) => {
                        const removed = removedIds.includes(it.id);
                        return (
                          <tr key={it.id} className={cx(removed && 'bg-rose-50/50')}>
                            <td className="px-6 py-2.5">
                              <p className={cx('font-medium text-slate-900', removed && 'text-slate-400 line-through')}>
                                {it.name}
                                {it.offCatalog && (
                                  <span className="ml-1.5 rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-semibold text-amber-800 no-underline">Ngoài bảng giá</span>
                                )}
                              </p>
                              <p className="text-xs text-slate-400">
                                {it.unit}
                                {it.note && <> · {it.note}</>}
                              </p>
                            </td>
                            <td className="px-2 py-2.5">
                              <input
                                type="number"
                                min={it.category === 'VEHICLE' ? 1 : 0}
                                disabled={!editable || removed}
                                className={cellInput}
                                value={it.quantity}
                                onChange={(e) => patch(it.id, 'quantity', Number(e.target.value))}
                              />
                            </td>
                            <td className="px-2 py-2.5">
                              <input
                                type="number"
                                step={10_000}
                                disabled={!editable || removed || !it.offCatalog}
                                title={it.offCatalog ? undefined : 'Theo bảng giá — không sửa được'}
                                className={cellInput}
                                value={it.unitPrice}
                                onChange={(e) => patch(it.id, 'unitPrice', Number(e.target.value))}
                              />
                            </td>
                            <td className={cx('px-4 py-2.5 text-right font-semibold tabular-nums', removed && 'text-slate-400 line-through')}>{formatVND(lineTotal(it))}</td>
                            <td className="py-2.5 pr-4 text-right">
                              {editable &&
                                (removed ? (
                                  <button
                                    type="button"
                                    onClick={() => setRemovedIds((r) => r.filter((x) => x !== it.id))}
                                    className="grid size-8 place-items-center rounded-lg text-slate-500 hover:bg-slate-100"
                                    aria-label={`Khôi phục ${it.name}`}
                                    title="Khôi phục"
                                  >
                                    <RotateCcw className="size-4" />
                                  </button>
                                ) : (
                                  <button
                                    type="button"
                                    onClick={() => setRemovedIds((r) => [...r, it.id])}
                                    className="grid size-8 place-items-center rounded-lg text-slate-400 hover:bg-rose-50 hover:text-rose-600"
                                    aria-label={`Bỏ ${it.name}`}
                                    title="Bỏ dòng"
                                  >
                                    <Trash2 className="size-4" />
                                  </button>
                                ))}
                            </td>
                          </tr>
                        );
                      })}
                      {added.map((it) => {
                        const bad = (k: 'name' | 'price' | 'note') =>
                          tried && it.offCatalog && (k === 'price' ? it.unitPrice <= 0 : k === 'name' ? !it.name.trim() : !it.note?.trim());
                        return (
                          <tr key={it.id} className="bg-emerald-50/40 align-top">
                            <td className="px-6 py-2.5">
                              {it.offCatalog ? (
                                <div className="space-y-1.5">
                                  <Input value={it.name} invalid={bad('name')} onChange={(e) => patchAdded(it.id, { name: e.target.value })} placeholder="Tên khoản ngoài bảng giá *" className="h-9" />
                                  <Input value={it.note ?? ''} invalid={bad('note')} onChange={(e) => patchAdded(it.id, { note: e.target.value })} placeholder="Lý do (khách xem được) *" className="h-9" />
                                </div>
                              ) : (
                                <>
                                  <p className="font-medium text-slate-900">{it.name}</p>
                                  <p className="text-xs text-emerald-700">Thêm mới · {it.unit} · theo bảng giá</p>
                                </>
                              )}
                            </td>
                            <td className="px-2 py-2.5">
                              <input type="number" min={1} className={cellInput} value={it.quantity} onChange={(e) => patchAdded(it.id, { quantity: Math.max(1, Number(e.target.value)) })} />
                            </td>
                            <td className="px-2 py-2.5">
                              <input
                                type="number"
                                step={10_000}
                                disabled={!it.offCatalog}
                                className={cx(cellInput, bad('price') && 'border-rose-400')}
                                value={it.unitPrice}
                                onChange={(e) => patchAdded(it.id, { unitPrice: Math.max(0, Number(e.target.value)) })}
                              />
                            </td>
                            <td className="px-4 py-2.5 text-right font-semibold tabular-nums">{formatVND(lineTotal(it))}</td>
                            <td className="py-2.5 pr-4 text-right">
                              <button
                                type="button"
                                onClick={() => setAdded((xs) => xs.filter((x) => x.id !== it.id))}
                                className="grid size-8 place-items-center rounded-lg text-slate-400 hover:bg-rose-50 hover:text-rose-600"
                                aria-label="Bỏ dòng thêm mới"
                              >
                                <Trash2 className="size-4" />
                              </button>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>

                {editable && (
                  <div className="flex flex-wrap items-center gap-2 border-t border-slate-100 px-6 py-4">
                    <Select
                      value=""
                      onChange={(e) => {
                        const svc = catalog.services.find((x) => x.id === e.target.value);
                        if (svc) setAdded((xs) => [...xs, quoteItemFromService(svc, 1)]);
                      }}
                      className="h-10 min-w-56 flex-1"
                      aria-label="Thêm dịch vụ từ bảng giá"
                    >
                      <option value="">＋ Thêm dịch vụ từ bảng giá…</option>
                      {pickable.map((x) => (
                        <option key={x.id} value={x.id}>
                          {x.name} — {formatVND(x.basePrice)}/{x.unit}
                        </option>
                      ))}
                    </Select>
                    <Button
                      variant="soft"
                      icon={Plus}
                      onClick={() =>
                        setAdded((xs) => [...xs, { id: uid('qi'), name: '', category: 'SURCHARGE', unit: 'gói', unitPrice: 0, quantity: 1, note: '', offCatalog: true }])
                      }
                    >
                      Thêm khoản ngoài bảng giá
                    </Button>
                  </div>
                )}
              </>
            )}
            {order.status !== 'NEW_PENDING_SURVEY' && order.quoteItems.length > 0 && (
            <>
            <div className="flex flex-wrap items-end justify-between gap-4 border-t border-slate-100 p-6">
              <Field label="Giảm giá toàn đơn (%)" hint="Áp dụng cho tổng báo giá, không chiết khấu từng hạng mục" className="w-full sm:w-64">
                <input
                  type="number"
                  min={0}
                  max={100}
                  disabled={!editable}
                  className={cellInput}
                  value={discountPct}
                  onChange={(e) => setDiscountPct(Math.min(100, Math.max(0, Number(e.target.value))))}
                />
              </Field>
              <div className="min-w-56 text-right text-sm tabular-nums">
                <p className="text-slate-500">
                  Tạm tính: <span className="font-semibold text-slate-800">{formatVND(draftSubtotal)}</span>
                </p>
                <p className="text-slate-500">
                  Giảm giá ({discountPct}%): <span className="font-semibold text-emerald-600">−{formatVND(draftDiscount)}</span>
                </p>
                <div className="mt-2 border-t border-slate-100 pt-2">
                  <p className="text-xl text-slate-500">
                    Tổng: <span className="font-bold text-slate-900">{formatVND(draftTotal)}</span>
                  </p>
                </div>
              </div>
            </div>
            <div className="flex flex-col gap-3 border-t border-slate-100 p-6 sm:flex-row sm:items-end">
              <Field
                label={dirty ? 'Lý do chỉnh sửa (bắt buộc, ghi vào nhật ký)' : 'Ghi chú (ghi vào nhật ký)'}
                error={
                  tried && noteMissing
                    ? 'Bắt buộc ghi lý do'
                    : tried && !vehicleLeft
                      ? 'Báo giá phải còn ít nhất 1 dòng xe'
                      : tried && customInvalid
                        ? 'Khoản ngoài bảng giá cần tên, đơn giá và lý do'
                        : null
                }
                className="flex-1"
              >
                <Input disabled={!editable} value={note} invalid={tried && noteMissing} onChange={(e) => setNote(e.target.value)} placeholder={order.negotiation ? 'VD: Bớt 1 nhân viên theo yêu cầu khách' : 'VD: Khách gọi bỏ tháo lắp máy lạnh, thêm 1 tủ cần tháo'} />
              </Field>
              <div className="flex gap-2">
                <Button variant="secondary" icon={Undo2} disabled={!dirty} onClick={reset}>
                  Hoàn tác
                </Button>
                <Button icon={Send} disabled={!editable || (!dirty && !order.negotiation)} onClick={saveAndResend}>
                  {dirty ? 'Lưu & gửi lại báo giá' : 'Giữ giá & gửi lại'}
                </Button>
              </div>
            </div>
            </>
            )}
          </Card>

          {order.surveyRecord && <SurveyRecordCard record={order.surveyRecord} audience="internal" />}
        </div>
  );
};
