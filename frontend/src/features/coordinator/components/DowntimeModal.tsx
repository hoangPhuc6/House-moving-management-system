import { useState } from 'react';
import { CalendarOff, X } from 'lucide-react';
import { Badge, Button, Field, Input, Modal, Select } from '@/components/ui';
import {
  addDaysISO,
  DOWNTIME_REASON_META,
  downtimeRangeLabel,
  STAFF_DOWNTIME_REASONS,
  todayLocalISO,
  upcomingDowntimes,
  VEHICLE_DOWNTIME_REASONS,
} from '@/services/downtime';
import { useApp } from '@/services/store/AppStore';
import type { DowntimeReason, DowntimeTarget } from '@/types';

export interface DowntimeSubject {
  target: DowntimeTarget;
  id: string;
  name: string;
}

/**
 * Ghi lịch nghỉ cho một xe (bảo dưỡng, sửa chữa, đăng kiểm) hoặc nhân sự (nghỉ phép, ốm…):
 * bắt buộc có từ ngày → đến ngày để lịch xếp xe / khảo sát tự chặn đúng những ngày đó.
 */
export const DowntimeModal = ({ subject, defaultFrom, onClose }: { subject: DowntimeSubject | null; defaultFrom: string; onClose: () => void }) => {
  const { state, dispatch } = useApp();
  const today = todayLocalISO();
  const start = defaultFrom < today ? today : defaultFrom;
  const reasons = subject?.target === 'VEHICLE' ? VEHICLE_DOWNTIME_REASONS : STAFF_DOWNTIME_REASONS;
  const [reason, setReason] = useState<DowntimeReason>(reasons[0]!);
  const [from, setFrom] = useState(start);
  const [to, setTo] = useState(subject?.target === 'VEHICLE' ? addDaysISO(start, 1) : start);
  const [note, setNote] = useState('');
  const [tried, setTried] = useState(false);

  if (!subject) return null;
  const existing = upcomingDowntimes(state.resources.downtimes, subject.target, subject.id, today);
  const fromErr = !from ? 'Chọn ngày bắt đầu' : from < today ? 'Không được trước hôm nay' : null;
  const toErr = !to ? 'Chọn ngày kết thúc' : to < from ? 'Phải từ ngày bắt đầu trở đi' : null;
  const days = from && to && to >= from ? Math.round((Date.parse(to) - Date.parse(from)) / 86_400_000) + 1 : 0;

  const save = () => {
    setTried(true);
    if (fromErr || toErr) return;
    // Modal vẫn mở: lưu được thì lịch hiện ngay ở danh sách trên; bị từ chối (trùng lịch / đã nhận việc) thì toast báo lý do để sửa.
    dispatch({ type: 'ADD_DOWNTIME', target: subject.target, resourceId: subject.id, reason, from, to, note });
    setTried(false);
    setNote('');
  };

  return (
    <Modal
      open
      onClose={onClose}
      title={
        <span className="flex items-center gap-2">
          <CalendarOff className="size-4" /> Lịch nghỉ · {subject.name}
        </span>
      }
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Đóng
          </Button>
          <Button icon={CalendarOff} onClick={save}>
            Lưu lịch nghỉ{days > 0 ? ` (${days} ngày)` : ''}
          </Button>
        </>
      }
    >
      {existing.length > 0 && (
        <div className="mb-4">
          <p className="mb-1.5 text-xs font-semibold text-slate-500">Đang / sắp nghỉ</p>
          <ul className="divide-y divide-slate-100 rounded-xl border border-slate-200">
            {existing.map((d) => {
              const ongoing = d.from <= today;
              return (
                <li key={d.id} className="flex items-center gap-2 px-3 py-2 text-sm">
                  <Badge tone={DOWNTIME_REASON_META[d.reason].tone}>{DOWNTIME_REASON_META[d.reason].label}</Badge>
                  <span className="min-w-0 flex-1">
                    <span className="font-semibold tabular-nums">{downtimeRangeLabel(d)}</span>
                    {d.note && <span className="block truncate text-xs text-slate-500">{d.note}</span>}
                  </span>
                  <Button size="sm" variant="ghost" icon={X} onClick={() => dispatch({ type: 'END_DOWNTIME', id: d.id })}>
                    {ongoing ? 'Kết thúc sớm' : 'Hủy'}
                  </Button>
                </li>
              );
            })}
          </ul>
        </div>
      )}

      <div className="space-y-3">
        <Field label="Lý do *">
          <Select value={reason} onChange={(e) => setReason(e.target.value as DowntimeReason)}>
            {reasons.map((r) => (
              <option key={r} value={r}>
                {DOWNTIME_REASON_META[r].label}
              </option>
            ))}
          </Select>
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Từ ngày *" error={tried ? fromErr : null}>
            <Input
              type="date"
              min={today}
              value={from}
              invalid={tried && !!fromErr}
              onChange={(e) => {
                setFrom(e.target.value);
                if (to < e.target.value) setTo(e.target.value);
              }}
              className="tabular-nums"
            />
          </Field>
          <Field label="Đến hết ngày *" error={tried ? toErr : null}>
            <Input type="date" min={from || today} value={to} invalid={tried && !!toErr} onChange={(e) => setTo(e.target.value)} className="tabular-nums" />
          </Field>
        </div>
        <Field label="Ghi chú" hint="Không nhận việc trong các ngày này. Đơn đã gán trùng ngày phải đổi người / xe trước.">
          <Input
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder={subject.target === 'VEHICLE' ? 'VD: Thay nhớt, kiểm tra phanh tại garage Q7' : 'VD: Về quê đám cưới'}
          />
        </Field>
      </div>
    </Modal>
  );
};
