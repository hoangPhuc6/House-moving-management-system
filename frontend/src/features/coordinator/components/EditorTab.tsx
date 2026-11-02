import { useEffect, useState } from 'react';
import { History, ReceiptText, Save, Undo2 } from 'lucide-react';
import {
  Button,
  Card,
  CardHeader,
  cx,
  EmptyState,
  Field,
  Input,
  PageHeader,
} from '@/components/ui';
import { OrderInfoCard } from '@/features/coordinator/components/OrderInfoCard';
import { OrderPicker } from '@/features/coordinator/components/OrderPicker';
import { SurveyPanel } from '@/features/coordinator/components/SurveyPanel';
import { formatTime, formatVND } from '@/services/format';
import { lineTotal } from '@/services/pricing';
import { useApp } from '@/services/store/AppStore';
import type { Order, QuoteItemPatch } from '@/types';

export const EditorTab = ({
  order,
  onSelect,
}: {
  order: Order;
  onSelect: (id: string) => void;
}) => {
  const { dispatch } = useApp();
  const toPatches = (o: Order): QuoteItemPatch[] =>
    o.quoteItems.map(({ id, quantity, unitPrice }) => ({
      id,
      quantity,
      unitPrice,
    }));
  const [draft, setDraft] = useState<QuoteItemPatch[]>(() => toPatches(order));
  const [discountPct, setDiscountPct] = useState(order.discountPercent);
  const [note, setNote] = useState("");

  // Đồng bộ lại bản nháp khi đổi đơn hoặc dữ liệu gốc thay đổi từ vai trò khác.
  const signature =
    order.quoteItems
      .map((i) => `${i.id}:${i.quantity}:${i.unitPrice}`)
      .join("|") + `|${order.discountPercent}`;
  useEffect(() => {
    setDraft(toPatches(order));
    setDiscountPct(order.discountPercent);
  }, [order.id, signature]);

  const merged = order.quoteItems.map((it) => ({
    ...it,
    ...(draft.find((d) => d.id === it.id) ?? {}),
  }));
  const dirty =
    discountPct !== order.discountPercent ||
    merged.some((m, i) => {
      const o = order.quoteItems[i];
      return !!o && (o.quantity !== m.quantity || o.unitPrice !== m.unitPrice);
    });
  const draftSubtotal = merged.reduce((s, i) => s + lineTotal(i), 0);
  const draftDiscount = Math.round((draftSubtotal * discountPct) / 100);
  const draftTotal = draftSubtotal - draftDiscount;
  const patch = (id: string, k: keyof Omit<QuoteItemPatch, "id">, v: number) =>
    setDraft((d) =>
      d.map((x) => (x.id === id ? { ...x, [k]: Math.max(0, v) } : x)),
    );

  const cellInput =
    "h-9 w-full rounded-lg border border-slate-200 bg-white px-2 text-right text-sm tabular-nums focus:border-brand focus:ring-4 focus:ring-brand/10 focus:outline-none";

  return (
    <>
      <PageHeader
        eyebrow="Báo giá"
        title="Chỉnh sửa báo giá"
        subtitle="Mọi chỉnh sửa được ghi nhật ký và cập nhật ngay cho khách hàng."
        actions={<OrderPicker value={order.id} onChange={onSelect} />}
      />
      <div className="grid gap-4 xl:grid-cols-[1fr_360px]">
        <Card padded={false}>
          {order.status === "NEW_PENDING_SURVEY" ? (
            <SurveyPanel key={order.id} order={order} />
          ) : order.quoteItems.length === 0 ? (
            <div className="p-6 pt-0">
              <EmptyState icon={ReceiptText} title="Đơn chưa có báo giá">
                Đơn này không có hạng mục báo giá.
              </EmptyState>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[720px] text-sm">
                <thead>
                  <tr className="border-y border-slate-100 bg-slate-50/60 text-left text-xs font-semibold tracking-wide text-slate-500 uppercase">
                    <th className="px-6 py-3">Hạng mục</th>
                    <th className="w-24 px-2 py-3 text-center">SL</th>
                    <th className="w-36 px-2 py-3 text-center">Đơn giá</th>
                    <th className="px-6 py-3 text-right">Thành tiền</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {merged.map((it) => (
                    <tr key={it.id}>
                      <td className="px-6 py-2.5">
                        <p className="font-medium text-slate-900">{it.name}</p>
                        <p className="text-xs text-slate-400">{it.unit}</p>
                      </td>
                      <td className="px-2 py-2.5">
                        <input
                          type="number"
                          className={cellInput}
                          value={it.quantity}
                          onChange={(e) =>
                            patch(it.id, "quantity", Number(e.target.value))
                          }
                        />
                      </td>
                      <td className="px-2 py-2.5">
                        <input
                          type="number"
                          step={10_000}
                          className={cellInput}
                          value={it.unitPrice}
                          onChange={(e) =>
                            patch(it.id, "unitPrice", Number(e.target.value))
                          }
                        />
                      </td>
                      <td className="px-6 py-2.5 text-right font-semibold tabular-nums">
                        {formatVND(lineTotal(it))}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <div className="flex flex-wrap items-end justify-between gap-4 border-t border-slate-100 p-6">
            <Field
              label="Giảm giá toàn đơn (%)"
              hint="Áp dụng cho tổng báo giá, không chiết khấu từng hạng mục"
              className="w-full sm:w-64"
            >
              <input
                type="number"
                min={0}
                max={100}
                className={cellInput}
                value={discountPct}
                onChange={(e) =>
                  setDiscountPct(
                    Math.min(100, Math.max(0, Number(e.target.value))),
                  )
                }
              />
            </Field>
            <div className="min-w-56 text-right text-sm tabular-nums">
              <p className="text-slate-500">
                Tạm tính:{" "}
                <span className="font-semibold text-slate-800">
                  {formatVND(draftSubtotal)}
                </span>
              </p>
              <p className="text-slate-500">
                Giảm giá ({discountPct}%):{" "}
                <span className="font-semibold text-emerald-600">
                  −{formatVND(draftDiscount)}
                </span>
              </p>
              <div className="mt-2 border-t border-slate-100 pt-2">
                <p className="text-xl text-slate-500">
                  Tổng:{" "}
                  <span className="font-bold text-slate-900">
                    {formatVND(draftTotal)}
                  </span>
                </p>
              </div>
            </div>
          </div>
          <div className="flex flex-col gap-3 border-t border-slate-100 p-6 sm:flex-row sm:items-end">
            <Field label="Lý do chỉnh sửa (ghi vào nhật ký)" className="flex-1">
              <Input
                value={note}
                onChange={(e) => setNote(e.target.value)}
                placeholder="VD: Khách thân thiết, áp dụng chiết khấu"
              />
            </Field>
            <div className="flex gap-2">
              <Button
                variant="secondary"
                icon={Undo2}
                disabled={!dirty}
                onClick={() => {
                  setDraft(toPatches(order));
                  setDiscountPct(order.discountPercent);
                }}
              >
                Hoàn tác
              </Button>
              <Button
                icon={Save}
                disabled={!dirty}
                onClick={() => {
                  dispatch({
                    type: "UPDATE_QUOTE_ITEMS",
                    orderId: order.id,
                    patches: draft,
                    discountPercent: discountPct,
                    note,
                  });
                  setNote("");
                }}
              >
                Lưu & ghi Audit Log
              </Button>
            </div>
          </div>
        </Card>

        <div className="space-y-4">
          <OrderInfoCard order={order} />
          <Card>
            <CardHeader
              title="Audit Log"
              subtitle={`${order.auditLog.length} sự kiện`}
              icon={History}
            />
            <ol className="relative max-h-[560px] space-y-4 overflow-y-auto border-l border-slate-200 pl-5">
              {[...order.auditLog].reverse().map((l) => (
                <li key={l.id} className="relative">
                  <span
                    className={cx(
                      "absolute top-1 -left-[25px] size-2.5 rounded-full ring-4 ring-white",
                      l.actorRole === "COORDINATOR"
                        ? "bg-brand"
                        : l.actorRole === "CUSTOMER"
                          ? "bg-blue-500"
                          : l.actorRole === "STAFF"
                            ? "bg-emerald-500"
                            : "bg-slate-400",
                    )}
                  />
                  <p className="text-sm font-semibold text-slate-900">
                    {l.action}
                  </p>
                  <p className="mt-0.5 text-sm text-slate-600">{l.detail}</p>
                  <p className="mt-1 text-xs text-slate-400">
                    {l.actor} ·{" "}
                    <span className="tabular-nums">
                      {formatTime(l.timestamp)}
                    </span>
                  </p>
                </li>
              ))}
              {order.auditLog.length === 0 && (
                <li className="text-sm text-slate-400">Chưa có lịch sử.</li>
              )}
            </ol>
          </Card>
        </div>
      </div>
    </>
  );
};
