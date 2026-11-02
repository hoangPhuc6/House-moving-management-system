import { useState } from 'react';
import { Pencil, Plus, Trash2, Truck, Wrench } from 'lucide-react';
import {
  Badge,
  Button,
  Card,
  cx,
  EmptyState,
  Field,
  Input,
  Modal,
  PageHeader,
  Select,
  Stat,
} from '@/components/ui';
import { PLATE_REGEX, VEHICLE_STATUS_META } from '@/constants/resources';
import { CAPACITIES } from '@/features/admin/constants';
import type { Draft } from '@/features/admin/types';
import { capLabel } from '@/features/admin/utils';
import { useApp } from '@/services/store/AppStore';
import type { Vehicle } from '@/types';

/** Quản lý đội xe: thêm, sửa, bảo dưỡng và xóa xe. */
export const VehiclesModule = ({ query }: { query: string }) => {
  const { state, dispatch } = useApp();
  const [draft, setDraft] = useState<Draft | null>(null);
  const [touched, setTouched] = useState(false);
  const [removing, setRemoving] = useState<Vehicle | null>(null);

  const vehicles = state.resources.vehicles.filter((v) => !query.trim() || v.plateNumber.toLowerCase().includes(query.trim().toLowerCase()));
  const count = (s: Vehicle['status']) => state.resources.vehicles.filter((v) => v.status === s).length;

  const plate = draft?.plateNumber.trim().toUpperCase() ?? '';
  const plateError = !draft
    ? null
    : !plate
      ? 'Vui lòng nhập biển số'
      : !PLATE_REGEX.test(plate)
        ? 'Sai định dạng — ví dụ: 51C-456.78'
        : state.resources.vehicles.some((v) => v.id !== draft.id && v.plateNumber === plate)
          ? 'Biển số này đã tồn tại'
          : null;

  const open = (d: Draft) => {
    setTouched(false);
    setDraft(d);
  };
  const save = () => {
    setTouched(true);
    if (!draft || plateError) return;
    dispatch(draft.id ? { type: 'UPDATE_VEHICLE', id: draft.id, plateNumber: plate, capacityTons: draft.capacityTons } : { type: 'ADD_VEHICLE', plateNumber: plate, capacityTons: draft.capacityTons });
    setDraft(null);
  };

  return (
    <>
      <PageHeader
        eyebrow="Vận hành"
        title="Quản lý xe"
        subtitle="Loại xe (tải trọng) phải khớp với báo giá khi điều phối. Xe đang gán cho đơn không thể sửa hoặc xóa."
        actions={<Button icon={Plus} onClick={() => open({ id: null, plateNumber: '', capacityTons: CAPACITIES[0] ?? 2 })}>Thêm xe</Button>}
      />
      <div className="mb-4 grid gap-4 sm:grid-cols-4">
        <Stat label="Tổng số xe" value={state.resources.vehicles.length} icon={Truck} />
        <Stat label="Sẵn sàng" value={count('AVAILABLE')} icon={Truck} tone="success" />
        <Stat label="Đang chạy đơn" value={count('BUSY')} icon={Truck} tone="info" />
        <Stat label="Bảo dưỡng" value={count('MAINTENANCE')} icon={Wrench} tone="warning" />
      </div>

      <Card padded={false}>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[720px] text-sm">
            <thead>
              <tr className="border-b border-slate-100 bg-slate-50/60 text-left text-xs font-semibold tracking-wide text-slate-500 uppercase">
                <th className="px-6 py-3">Biển số</th>
                <th className="px-3 py-3">Tải trọng</th>
                <th className="px-3 py-3">Trạng thái</th>
                <th className="px-3 py-3">Đơn đang gán</th>
                <th className="px-6 py-3 text-right">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {vehicles.length === 0 && (
                <tr><td colSpan={5} className="p-6"><EmptyState icon={Truck} title="Không có xe phù hợp" /></td></tr>
              )}
              {vehicles.map((v) => {
                const meta = VEHICLE_STATUS_META[v.status];
                const busy = v.status === 'BUSY';
                return (
                  <tr key={v.id} className={cx(v.status === 'MAINTENANCE' && 'bg-amber-50/30')}>
                    <td className="px-6 py-3.5 font-semibold tabular-nums">{v.plateNumber}</td>
                    <td className="px-3 py-3.5 tabular-nums">{capLabel(v.capacityTons)}</td>
                    <td className="px-3 py-3.5"><Badge tone={meta.tone} dot>{meta.label}</Badge></td>
                    <td className="px-3 py-3.5 text-slate-500 tabular-nums">{busy ? `#${v.busyOrderId} · ${v.currentShiftTime ?? ''}` : '—'}</td>
                    <td className="px-6 py-3.5">
                      <div className="flex justify-end gap-1.5">
                        <Button size="sm" variant="secondary" icon={Pencil} disabled={busy} onClick={() => open({ id: v.id, plateNumber: v.plateNumber, capacityTons: v.capacityTons })}>Sửa</Button>
                        <Button size="sm" variant="ghost" icon={Wrench} disabled={busy} onClick={() => dispatch({ type: 'SET_VEHICLE_STATUS', id: v.id, status: v.status === 'MAINTENANCE' ? 'AVAILABLE' : 'MAINTENANCE' })}>
                          {v.status === 'MAINTENANCE' ? 'Hoàn tất bảo dưỡng' : 'Bảo dưỡng'}
                        </Button>
                        <Button size="sm" variant="ghost" icon={Trash2} className="text-rose-600 hover:bg-rose-50" disabled={busy} onClick={() => setRemoving(v)}>Xóa</Button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </Card>

      <Modal
        open={!!draft}
        onClose={() => setDraft(null)}
        title={draft?.id ? 'Sửa thông tin xe' : 'Thêm xe mới'}
        footer={<><Button variant="secondary" onClick={() => setDraft(null)}>Hủy</Button><Button onClick={save}>Lưu</Button></>}
      >
        {draft && (
          <div className="space-y-4">
            <Field label="Biển số xe" error={touched ? plateError : null} hint="Định dạng: 51C-456.78">
              <Input value={draft.plateNumber} invalid={touched && !!plateError} onChange={(e) => setDraft({ ...draft, plateNumber: e.target.value })} placeholder="51C-456.78" />
            </Field>
            <Field label="Tải trọng" hint="Dùng để đối chiếu loại xe trong báo giá">
              <Select value={draft.capacityTons} onChange={(e) => setDraft({ ...draft, capacityTons: Number(e.target.value) })}>
                {CAPACITIES.map((c) => <option key={c} value={c}>{capLabel(c)}</option>)}
              </Select>
            </Field>
          </div>
        )}
      </Modal>

      <Modal
        open={!!removing}
        onClose={() => setRemoving(null)}
        tone="danger"
        title="Xóa xe khỏi đội xe?"
        footer={<><Button variant="secondary" onClick={() => setRemoving(null)}>Giữ lại</Button><Button variant="danger" onClick={() => { if (removing) dispatch({ type: 'REMOVE_VEHICLE', id: removing.id }); setRemoving(null); }}>Xóa xe</Button></>}
      >
        Xe <b className="tabular-nums">{removing?.plateNumber}</b> sẽ bị xóa khỏi danh sách điều phối. Thao tác này không thể hoàn tác.
      </Modal>
    </>
  );
};
