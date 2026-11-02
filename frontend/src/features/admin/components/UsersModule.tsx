import { useState } from 'react';
import { Mail, Power, UserPlus, Users } from 'lucide-react';
import {
  Alert,
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
import { EMAIL_REGEX, ROLE_LABEL } from '@/features/admin/constants';
import { useApp } from '@/services/store/AppStore';
import { useCatalog } from '@/services/store/CatalogStore';
import type { UserRole } from '@/types';

export const UsersModule = ({ query }: { query: string }) => {
  const { dispatch } = useApp();
  const { catalog, dispatchCatalog } = useCatalog();
  const [adding, setAdding] = useState(false);
  const [draft, setDraft] = useState({ name: '', email: '', phone: '', role: 'STAFF' as UserRole });

  const list = catalog.users.filter((u) => !query.trim() || `${u.name} ${u.email} ${u.phone}`.toLowerCase().includes(query.trim().toLowerCase()));
  const dup = catalog.users.some((u) => u.email.toLowerCase() === draft.email.trim().toLowerCase());
  const emailError = !draft.email ? null : !EMAIL_REGEX.test(draft.email.trim()) ? 'Email không hợp lệ' : dup ? 'Email này đã được sử dụng' : null;
  const valid = draft.name.trim() && EMAIL_REGEX.test(draft.email.trim()) && !dup;

  return (
    <>
      <PageHeader eyebrow="Hệ thống" title="Tài khoản & phân quyền" subtitle="Email duy nhất; Customer không thể gọi chức năng chỉ dành cho Admin." actions={<Button icon={UserPlus} onClick={() => setAdding(true)}>Tạo tài khoản</Button>} />
      <div className="mb-4 grid gap-4 sm:grid-cols-3">
        <Stat label="Tổng tài khoản" value={catalog.users.length} icon={Users} />
        <Stat label="Chưa xác thực email" value={catalog.users.filter((u) => !u.emailVerified).length} icon={Mail} tone="warning" />
        <Stat label="Đang bị khóa" value={catalog.users.filter((u) => u.locked).length} icon={Power} tone="danger" />
      </div>
      <Card padded={false}>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[800px] text-sm">
            <thead>
              <tr className="border-b border-slate-100 bg-slate-50/60 text-left text-xs font-semibold tracking-wide text-slate-500 uppercase">
                <th className="px-6 py-3">Người dùng</th><th className="px-3 py-3">Vai trò</th><th className="px-3 py-3">Trạng thái</th><th className="px-6 py-3 text-right">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {list.length === 0 && <tr><td colSpan={4} className="p-6"><EmptyState icon={Users} title="Không tìm thấy người dùng" /></td></tr>}
              {list.map((u) => (
                <tr key={u.id} className={cx(u.locked && 'bg-slate-50/60')}>
                  <td className="px-6 py-3.5"><p className="font-semibold">{u.name}</p><p className="text-xs text-slate-500">{u.email} · {u.phone}</p></td>
                  <td className="px-3 py-3.5">
                    <Select value={u.role} onChange={(e) => dispatchCatalog({ type: 'SET_USER_ROLE', id: u.id, role: e.target.value as UserRole })} className="h-9 w-44" aria-label={`Vai trò của ${u.name}`}>
                      {(Object.keys(ROLE_LABEL) as UserRole[]).map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}
                    </Select>
                  </td>
                  <td className="px-3 py-3.5">
                    <div className="flex flex-wrap gap-1.5">
                      <Badge tone={u.locked ? 'danger' : 'success'} dot>{u.locked ? 'Đã khóa' : 'Hoạt động'}</Badge>
                      {!u.emailVerified && <Badge tone="warning">Chưa xác thực email</Badge>}
                    </div>
                  </td>
                  <td className="px-6 py-3.5">
                    <div className="flex justify-end gap-1.5">
                      {!u.emailVerified && <Button size="sm" variant="secondary" icon={Mail} onClick={() => dispatch({ type: 'PUSH_TOAST', tone: 'info', message: `Đã gửi lại email xác thực tới ${u.email}.` })}>Gửi lại xác thực</Button>}
                      <Button size="sm" variant="ghost" icon={Power} onClick={() => dispatchCatalog({ type: 'TOGGLE_USER_LOCK', id: u.id })}>{u.locked ? 'Mở khóa' : 'Khóa'}</Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
      <Alert tone="info" className="mt-4">Mật khẩu được mã hóa phía máy chủ — giao diện này không bao giờ hiển thị mật khẩu. Tài khoản mới nhận email đặt mật khẩu lần đầu.</Alert>

      <Modal
        open={adding}
        onClose={() => setAdding(false)}
        title="Tạo tài khoản"
        footer={<><Button variant="secondary" onClick={() => setAdding(false)}>Hủy</Button><Button disabled={!valid} onClick={() => { dispatchCatalog({ type: 'ADD_USER', user: { ...draft, name: draft.name.trim(), email: draft.email.trim() } }); dispatch({ type: 'PUSH_TOAST', tone: 'success', message: `Đã tạo tài khoản ${draft.email.trim()}.` }); setAdding(false); setDraft({ name: '', email: '', phone: '', role: 'STAFF' }); }}>Tạo</Button></>}
      >
        <div className="space-y-4">
          <Field label="Họ và tên"><Input value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} /></Field>
          <Field label="Email" error={emailError}><Input type="email" value={draft.email} onChange={(e) => setDraft({ ...draft, email: e.target.value })} invalid={!!emailError} /></Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Số điện thoại"><Input value={draft.phone} onChange={(e) => setDraft({ ...draft, phone: e.target.value })} /></Field>
            <Field label="Vai trò">
              <Select value={draft.role} onChange={(e) => setDraft({ ...draft, role: e.target.value as UserRole })}>
                {(Object.keys(ROLE_LABEL) as UserRole[]).map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}
              </Select>
            </Field>
          </div>
        </div>
      </Modal>
    </>
  );
};
