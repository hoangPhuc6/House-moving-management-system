import { useState } from 'react';
import { Pencil, Plus, Power, SlidersHorizontal } from 'lucide-react';
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
  Tabs,
} from '@/components/ui';
import { SERVICE_GROUP_LABEL } from '@/constants/catalog';
import { PricingEstimator } from '@/features/admin/components/PricingEstimator';
import { EMPTY_SERVICE } from '@/features/admin/constants';
import { formatVND } from '@/services/format';
import { useApp } from '@/services/store/AppStore';
import { useCatalog } from '@/services/store/CatalogStore';
import type { ServiceGroup, ServiceItem } from '@/types';

export const ServicesModule = ({ query }: { query: string }) => {
  const { catalog, dispatchCatalog } = useCatalog();
  const { dispatch } = useApp();
  const [group, setGroup] = useState<ServiceGroup | 'ALL'>('ALL');
  const [editing, setEditing] = useState<(Omit<ServiceItem, 'id'> & { id?: string }) | null>(null);

  const list = catalog.services.filter(
    (s) => (group === 'ALL' || s.group === group) && (!query.trim() || `${s.name} ${s.description}`.toLowerCase().includes(query.trim().toLowerCase())),
  );
  const nameError = editing && !editing.name.trim() ? 'Vui lòng nhập tên dịch vụ' : null;
  const priceError = editing && editing.basePrice <= 0 ? 'Đơn giá phải lớn hơn 0' : null;
  const unitError = editing && !editing.unit.trim() ? 'Vui lòng nhập đơn vị tính' : null;

  const save = () => {
    if (!editing || nameError || priceError || unitError) return;
    dispatchCatalog({ type: 'UPSERT_SERVICE', service: editing });
    dispatch({ type: 'PUSH_TOAST', tone: 'success', message: editing.id ? 'Đã cập nhật dịch vụ.' : 'Đã thêm dịch vụ mới.' });
    setEditing(null);
  };

  return (
    <>
      <PageHeader
        eyebrow="Danh mục"
        title="Dịch vụ & bảng giá"
        subtitle="Dịch vụ ngừng hoạt động không thể dùng cho đơn mới."
        actions={<Button icon={Plus} onClick={() => setEditing({ ...EMPTY_SERVICE })}>Thêm dịch vụ</Button>}
      />
      <Tabs<ServiceGroup | 'ALL'>
        className="mb-4"
        value={group}
        onChange={setGroup}
        tabs={[{ id: 'ALL', label: 'Tất cả' }, ...(Object.keys(SERVICE_GROUP_LABEL) as ServiceGroup[]).map((g) => ({ id: g, label: SERVICE_GROUP_LABEL[g] }))]}
      />
      <Card padded={false} className="mb-4">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[760px] text-sm">
            <thead>
              <tr className="border-b border-slate-100 bg-slate-50/60 text-left text-xs font-semibold tracking-wide text-slate-500 uppercase">
                <th className="px-6 py-3">Dịch vụ</th><th className="px-3 py-3">Nhóm</th><th className="px-3 py-3 text-right">Đơn giá cơ bản</th><th className="px-3 py-3">Trạng thái</th><th className="px-6 py-3 text-right">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {list.length === 0 && <tr><td colSpan={5} className="p-6"><EmptyState icon={SlidersHorizontal} title="Không có dịch vụ phù hợp" /></td></tr>}
              {list.map((s) => (
                <tr key={s.id} className={cx(!s.active && 'bg-slate-50/50 text-slate-400')}>
                  <td className="px-6 py-3.5"><p className="font-semibold">{s.name}</p><p className="text-xs text-slate-500">{s.description}</p></td>
                  <td className="px-3 py-3.5">{SERVICE_GROUP_LABEL[s.group]}</td>
                  <td className="px-3 py-3.5 text-right tabular-nums">{formatVND(s.basePrice)} <span className="text-xs text-slate-400">/ {s.unit}</span></td>
                  <td className="px-3 py-3.5"><Badge tone={s.active ? 'success' : 'neutral'} dot>{s.active ? 'Đang hoạt động' : 'Ngừng hoạt động'}</Badge></td>
                  <td className="px-6 py-3.5">
                    <div className="flex justify-end gap-1.5">
                      <Button size="sm" variant="secondary" icon={Pencil} onClick={() => setEditing({ ...s })}>Sửa</Button>
                      <Button size="sm" variant="ghost" icon={Power} onClick={() => dispatchCatalog({ type: 'TOGGLE_SERVICE', id: s.id })}>{s.active ? 'Ngừng' : 'Kích hoạt'}</Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
      <PricingEstimator />

      <Modal
        open={!!editing}
        onClose={() => setEditing(null)}
        title={editing?.id ? 'Sửa dịch vụ' : 'Thêm dịch vụ'}
        footer={<><Button variant="secondary" onClick={() => setEditing(null)}>Hủy</Button><Button disabled={!!(nameError || priceError || unitError)} onClick={save}>Lưu</Button></>}
      >
        {editing && (
          <div className="space-y-4">
            <Field label="Tên dịch vụ" error={nameError}><Input value={editing.name} onChange={(e) => setEditing({ ...editing, name: e.target.value })} /></Field>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Nhóm">
                <Select value={editing.group} onChange={(e) => setEditing({ ...editing, group: e.target.value as ServiceGroup })}>
                  {(Object.keys(SERVICE_GROUP_LABEL) as ServiceGroup[]).map((g) => <option key={g} value={g}>{SERVICE_GROUP_LABEL[g]}</option>)}
                </Select>
              </Field>
              <Field label="Đơn vị tính" error={unitError}><Input value={editing.unit} onChange={(e) => setEditing({ ...editing, unit: e.target.value })} placeholder="chuyến, người…" /></Field>
            </div>
            <Field label="Đơn giá cơ bản" hint={formatVND(editing.basePrice)} error={priceError}>
              <Input type="number" min={0} step={5_000} value={editing.basePrice} onChange={(e) => setEditing({ ...editing, basePrice: Math.max(0, Number(e.target.value)) })} />
            </Field>
            <Field label="Mô tả"><Input value={editing.description} onChange={(e) => setEditing({ ...editing, description: e.target.value })} /></Field>
          </div>
        )}
      </Modal>
    </>
  );
};

// ===========================================================================
// Module 8 — Kho & dụng cụ
// ===========================================================================
