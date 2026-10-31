import { useMemo, useState } from 'react';
import { ArrowRight, Calendar, MapPin, Navigation, ShieldCheck, Sparkles } from 'lucide-react';
import { cx } from '@/components/ui';
import { HOUSE_TYPE_META } from '@/constants/order';
import { AREAS } from '@/features/landing/constants';
import { estimate } from '@/features/landing/utils';
import { formatVND } from '@/services/format';
import type { HouseType } from '@/types';

export const QuickQuote = ({ onBook }: { onBook: () => void }) => {
  const [from, setFrom] = useState("q3");
  const [to, setTo] = useState("td");
  const [house, setHouse] = useState<HouseType>("APARTMENT");
  const [date, setDate] = useState("2026-11-01");
  const [submitted, setSubmitted] = useState(false);
  const result = useMemo(() => estimate(from, to, house), [from, to, house]);

  const selectCls =
    "h-11 w-full appearance-none rounded-xl border border-white/10 bg-white/[0.04] pr-3 pl-10 text-sm font-medium text-white focus:border-brand/60 focus:ring-4 focus:ring-brand/15 focus:outline-none";

  return (
    <div className="relative rounded-3xl border border-white/10 bg-slate-900/70 p-6 shadow-2xl shadow-black/40 backdrop-blur-xl sm:p-7">
      <div className="mb-6 flex items-center justify-between">
        <div>
          <p className="text-xs font-semibold tracking-wider text-brand uppercase">
            Báo giá nhanh
          </p>
          <h2 className="mt-1 text-xl font-semibold">
            Ước tính chi phí trong 10 giây
          </h2>
        </div>
        <span className="grid size-10 place-items-center rounded-xl bg-white/5 ring-1 ring-white/10">
          <Sparkles className="size-5 text-brand" />
        </span>
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <label className="block">
          <span className="mb-1.5 block text-xs font-medium text-white/50">
            Điểm đi
          </span>
          <div className="relative">
            <MapPin className="absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-white/40" />
            <select
              value={from}
              onChange={(e) => {
                setFrom(e.target.value);
                setSubmitted(false);
              }}
              className={selectCls}
            >
              {AREAS.map((a) => (
                <option key={a.id} value={a.id} className="text-slate-900">
                  {a.label}
                </option>
              ))}
            </select>
          </div>
        </label>
        <label className="block">
          <span className="mb-1.5 block text-xs font-medium text-white/50">
            Điểm đến
          </span>
          <div className="relative">
            <Navigation className="absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-white/40" />
            <select
              value={to}
              onChange={(e) => {
                setTo(e.target.value);
                setSubmitted(false);
              }}
              className={selectCls}
            >
              {AREAS.map((a) => (
                <option key={a.id} value={a.id} className="text-slate-900">
                  {a.label}
                </option>
              ))}
            </select>
          </div>
        </label>
      </div>

      <div className="mt-3">
        <span className="mb-1.5 block text-xs font-medium text-white/50">
          Loại nhà
        </span>
        <div className="grid grid-cols-3 gap-1 rounded-xl border border-white/10 bg-white/[0.03] p-1">
          {(Object.keys(HOUSE_TYPE_META) as HouseType[]).map((h) => (
            <button
              key={h}
              type="button"
              onClick={() => {
                setHouse(h);
                setSubmitted(false);
              }}
              className={cx(
                "h-9 rounded-lg text-xs font-semibold transition-colors sm:text-[13px]",
                house === h
                  ? "bg-white text-slate-900"
                  : "text-white/60 hover:text-white",
              )}
            >
              {h === "GROUND_FLOOR"
                ? "Nhà trệt"
                : h === "APARTMENT"
                  ? "Chung cư"
                  : "Hẻm sâu"}
            </button>
          ))}
        </div>
      </div>

      <label className="mt-3 block">
        <span className="mb-1.5 block text-xs font-medium text-white/50">
          Ngày dự kiến
        </span>
        <div className="relative">
          <Calendar className="absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-white/40" />
          <input
            type="date"
            value={date}
            onChange={(e) => setDate(e.target.value)}
            className={cx(selectCls, "[color-scheme:dark]")}
          />
        </div>
      </label>

      <button
        type="button"
        onClick={() => setSubmitted(true)}
        className="mt-5 inline-flex h-12 w-full items-center justify-center gap-2 rounded-xl bg-brand text-[15px] font-semibold text-white hover:bg-brand-hover"
      >
        Nhận Báo Giá Ngay <ArrowRight className="size-4" />
      </button>

      {submitted && result && (
        <div className="anim-pop mt-5 rounded-2xl border border-white/10 bg-white/[0.04] p-4">
          <div className="flex items-end justify-between gap-3">
            <div>
              <p className="text-xs text-white/50">
                Chi phí ước tính · ~{result.km} km
              </p>
              <p className="mt-1 text-2xl font-bold tracking-tight tabular-nums">
                {formatVND(result.min)} <span className="text-white/40">–</span>{" "}
                {formatVND(result.max)}
              </p>
            </div>
            <button
              type="button"
              onClick={onBook}
              className="inline-flex h-10 shrink-0 items-center gap-1.5 rounded-xl bg-white px-3 text-sm font-semibold text-slate-900 hover:bg-white/90"
            >
              Đặt lịch <ArrowRight className="size-4" />
            </button>
          </div>
          <div className="mt-3 flex flex-wrap gap-2 text-[11px] font-medium text-white/60">
            {[
              "Xe tải 2 tấn",
              "4 nhân công",
              "20 thùng carton",
              HOUSE_TYPE_META[house].label,
            ].map((t) => (
              <span
                key={t}
                className="rounded-full border border-white/10 px-2 py-0.5"
              >
                {t}
              </span>
            ))}
          </div>
        </div>
      )}
      {!submitted && (
        <p className="mt-4 flex items-center gap-2 text-xs text-white/40">
          <ShieldCheck className="size-3.5" /> Giá cuối cùng chốt sau khảo sát —
          không phát sinh ngoài báo giá.
        </p>
      )}
    </div>
  );
};

// ---------------------------------------------------------------------------
