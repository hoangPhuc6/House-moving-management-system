import { useState } from 'react';
import { IdCard, Mail, Pencil, Power, UserPlus, Users } from 'lucide-react';
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
  Tabs,
} from '@/components/ui';
import {
  applyDraft,
  LicenseCell,
  StaffProfileFields,
  type StaffProfileDraft,
  StaffProfileModal,
  StaffSkillsCell,
} from '@/features/admin/components/StaffProfile';
import { EMAIL_REGEX, ROLE_LABEL } from '@/features/admin/constants';
import { uid } from '@/services/format';
import { licenseExpiringSoon, nextStaffCode, staffChangeBlocker, validateStaffProfile, WORK_STATUS_META } from '@/services/staff';
import { useApp } from '@/services/store/AppStore';
import { useCatalog } from '@/services/store/CatalogStore';
import type { StaffMember, UserAccount, UserRole } from '@/types';

const todayISO = () => new Date().toISOString().slice(0, 10);

const emptyProfile = (code: string): StaffProfileDraft => ({ code, skills: ['LOAD'], licenseClass: '', licenseExpiresAt: '', status: 'ACTIVE' });

export const UsersModule = ({ query }: { query: string }) => {
  const { state, dispatch } = useApp();
  const { catalog, dispatchCatalog } = useCatalog();
  const staff = state.resources.staff;
  const [adding, setAdding] = useState(false);
  const [draft, setDraft] = useState({ name: '', email: '', phone: '', role: 'STAFF' as UserRole });
  const [profileDraft, setProfileDraft] = useState<StaffProfileDraft>(() => emptyProfile(nextStaffCode(staff)));
  const [editing, setEditing] = useState<string | null>(null);
  const [tab, setTab] = useState<'accounts' | 'staff'>('accounts');

  const profileOf = (u: UserAccount) => staff.find((s) => s.userId === u.id);
  const hit = (...fields: string[]) => !query.trim() || fields.join(' ').toLowerCase().includes(query.trim().toLowerCase());
  const list = catalog.users.filter((u) => hit(u.name, u.email, u.phone));
  const STATUS_RANK = { ACTIVE: 0, ON_LEAVE: 1, RESIGNED: 2 } as const;
  const staffList = staff
    .filter((s) => hit(s.name, s.code, s.phone))
    .sort((a, b) => STATUS_RANK[a.status] - STATUS_RANK[b.status] || a.code.localeCompare(b.code));
  const dup = catalog.users.some((u) => u.email.toLowerCase() === draft.email.trim().toLowerCase());
  const emailError = !draft.email ? null : !EMAIL_REGEX.test(draft.email.trim()) ? 'Email không hợp lệ' : dup ? 'Email này đã được sử dụng' : null;

  // Tài khoản STAFF tạo kèm hồ sơ nhân viên → có ngay trong danh sách gán đội.
  const newProfile: StaffMember = applyDraft(
    { id: '', code: '', name: draft.name.trim(), phone: draft.phone.trim(), userId: null, skills: [], license: null, status: 'ACTIVE' },
    profileDraft,
  );
  const profileError = draft.role === 'STAFF' ? validateStaffProfile({ ...newProfile, name: draft.name.trim() || '—' }, staff) : null;
  const valid = !!draft.name.trim() && EMAIL_REGEX.test(draft.email.trim()) && !dup && !profileError;

  const today = todayISO();
  const activeStaff = staff.filter((s) => s.status === 'ACTIVE');
  const licenseAlerts = activeStaff.filter((s) => s.license && (s.license.expiresAt < today || licenseExpiringSoon(s, today)));
  const editingStaff = staff.find((s) => s.id === editing);

  const openAdd = () => {
    setDraft({ name: '', email: '', phone: '', role: 'STAFF' });
    setProfileDraft(emptyProfile(nextStaffCode(staff)));
    setAdding(true);
  };

  const create = () => {
    if (!valid) return;
    const userId = uid('u');
    dispatchCatalog({ type: 'ADD_USER', user: { ...draft, id: userId, name: draft.name.trim(), email: draft.email.trim(), phone: draft.phone.trim() } });
    if (draft.role === 'STAFF') dispatch({ type: 'UPSERT_STAFF', staff: { ...newProfile, id: uid('st'), userId } });
    dispatch({ type: 'PUSH_TOAST', tone: 'success', message: `Đã tạo tài khoản ${draft.email.trim()}.` });
    setAdding(false);
  };

  /** Đổi vai trò: rời STAFF → hồ sơ chuyển "nghỉ việc"; vào STAFF → kích hoạt lại / tạo hồ sơ mới rồi mở để bổ sung. */
  const changeRole = (u: UserAccount, role: UserRole) => {
    const profile = profileOf(u);
    if (u.role === 'STAFF' && role !== 'STAFF' && profile && profile.status !== 'RESIGNED') {
      const resigned = { ...profile, status: 'RESIGNED' as const };
      const blocker = staffChangeBlocker(resigned, state.orders);
      if (blocker) {
        dispatch({ type: 'PUSH_TOAST', tone: 'danger', message: blocker });
        return;
      }
      dispatch({ type: 'UPSERT_STAFF', staff: resigned });
    }
    if (role === 'STAFF' && u.role !== 'STAFF') {
      if (profile) {
        dispatch({ type: 'UPSERT_STAFF', staff: { ...profile, status: 'ACTIVE' } });
      } else {
        const created: StaffMember = { id: uid('st'), code: nextStaffCode(staff), name: u.name, phone: u.phone, userId: u.id, skills: ['LOAD'], license: null, status: 'ACTIVE' };
        dispatch({ type: 'UPSERT_STAFF', staff: created });
        setEditing(created.id);
      }
    }
    dispatchCatalog({ type: 'SET_USER_ROLE', id: u.id, role });
  };

  return (
    <>
      <PageHeader eyebrow="Hệ thống" title="Tài khoản & phân quyền" subtitle="Email duy nhất; tài khoản nhân viên kèm hồ sơ kỹ năng & bằng lái dùng khi gán đội." actions={<Button icon={UserPlus} onClick={openAdd}>Tạo tài khoản</Button>} />
      <div className="mb-4 grid grid-cols-2 gap-4 xl:grid-cols-4">
        <Stat label="Tổng tài khoản" value={catalog.users.length} icon={Users} />
        <Stat label="Nhân viên đang làm" value={activeStaff.length} icon={IdCard} tone="info" hint={`${activeStaff.filter((s) => s.skills.includes('DRIVE')).length} lái xe · ${activeStaff.filter((s) => s.skills.includes('LOAD')).length} bốc xếp`} />
        <Stat
          label="Bằng lái cần gia hạn"
          value={licenseAlerts.length}
          icon={IdCard}
          tone={licenseAlerts.length ? 'warning' : 'success'}
          hint={licenseAlerts.map((s) => s.name).join(', ') || 'Không có bằng sắp hết hạn trong 30 ngày'}
        />
        <Stat label="Đang bị khóa" value={catalog.users.filter((u) => u.locked).length} icon={Power} tone="danger" hint={`${catalog.users.filter((u) => !u.emailVerified).length} chưa xác thực email`} />
      </div>
      <Tabs
        className="mb-4"
        value={tab}
        onChange={setTab}
        tabs={[
          { id: 'accounts', label: `Tài khoản (${catalog.users.length})` },
          { id: 'staff', label: `Nhân sự vận hành (${staff.filter((s) => s.status !== 'RESIGNED').length})` },
        ]}
      />

      {tab === 'accounts' && (
        <Card padded={false}>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] text-sm">
              <thead>
                <tr className="border-b border-slate-100 bg-slate-50/60 text-left text-xs font-semibold tracking-wide text-slate-500 uppercase">
                  <th className="px-6 py-3">Người dùng</th>
                  <th className="px-3 py-3">Vai trò</th>
                  <th className="px-3 py-3">Trạng thái</th>
                  <th className="px-6 py-3 text-right">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {list.length === 0 && <tr><td colSpan={4} className="p-6"><EmptyState icon={Users} title="Không tìm thấy người dùng" /></td></tr>}
                {list.map((u) => (
                  <tr key={u.id} className={cx(u.locked && 'bg-slate-50/60 text-slate-500')}>
                    <td className="px-6 py-3">
                      <p className="font-semibold text-slate-900">{u.name}</p>
                      <p className="text-xs whitespace-nowrap text-slate-500">{u.email} · <span className="tabular-nums">{u.phone}</span></p>
                    </td>
                    <td className="px-3 py-3">
                      <select
                        value={u.role}
                        onChange={(e) => changeRole(u, e.target.value as UserRole)}
                        aria-label={`Vai trò của ${u.name}`}
                        className="h-9 w-40 rounded-lg border border-slate-200 bg-white px-2.5 text-sm text-slate-800 focus:border-brand focus:ring-4 focus:ring-brand/10 focus:outline-none"
                      >
                        {(Object.keys(ROLE_LABEL) as UserRole[]).map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}
                      </select>
                    </td>
                    <td className="px-3 py-3">
                      {/* Một trạng thái duy nhất, ưu tiên điều cần xử lý. */}
                      {u.locked ? <Badge tone="danger" dot>Đã khóa</Badge> : !u.emailVerified ? <Badge tone="warning" dot>Chưa xác thực email</Badge> : <Badge tone="success" dot>Hoạt động</Badge>}
                    </td>
                    <td className="px-6 py-3">
                      <div className="flex justify-end gap-1">
                        {!u.emailVerified && !u.locked && (
                          <Button size="sm" variant="ghost" icon={Mail} onClick={() => dispatch({ type: 'PUSH_TOAST', tone: 'info', message: `Đã gửi lại email xác thực tới ${u.email}.` })}>Gửi lại</Button>
                        )}
                        <Button size="sm" variant="ghost" icon={Power} onClick={() => dispatchCatalog({ type: 'TOGGLE_USER_LOCK', id: u.id })}>{u.locked ? 'Mở khóa' : 'Khóa'}</Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      {tab === 'staff' && (
        <Card padded={false}>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[820px] text-sm">
              <thead>
                <tr className="border-b border-slate-100 bg-slate-50/60 text-left text-xs font-semibold tracking-wide text-slate-500 uppercase">
                  <th className="px-6 py-3">Nhân viên</th>
                  <th className="px-3 py-3">Kỹ năng</th>
                  <th className="px-3 py-3">Bằng lái</th>
                  <th className="px-3 py-3">Tình trạng</th>
                  <th className="px-6 py-3 text-right">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {staffList.length === 0 && <tr><td colSpan={5} className="p-6"><EmptyState icon={IdCard} title="Không tìm thấy nhân viên" /></td></tr>}
                {staffList.map((s) => (
                  <tr key={s.id} className={cx(s.status !== 'ACTIVE' && 'text-slate-500')}>
                    <td className="px-6 py-3">
                      <p className="font-semibold text-slate-900">{s.name}</p>
                      <p className="text-xs text-slate-500 tabular-nums">{s.code} · {s.phone}</p>
                    </td>
                    <td className="px-3 py-3"><StaffSkillsCell staff={s} /></td>
                    <td className="px-3 py-3 whitespace-nowrap"><LicenseCell staff={s} /></td>
                    <td className="px-3 py-3"><Badge tone={WORK_STATUS_META[s.status].tone} dot>{WORK_STATUS_META[s.status].label}</Badge></td>
                    <td className="px-6 py-3 text-right">
                      <Button size="sm" variant="ghost" icon={Pencil} onClick={() => setEditing(s.id)}>Sửa hồ sơ</Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}
      <Alert tone="info" className="mt-4">Mật khẩu được mã hóa phía máy chủ — giao diện này không bao giờ hiển thị mật khẩu. Tài khoản mới nhận email đặt mật khẩu lần đầu.</Alert>

      <Modal
        open={adding}
        onClose={() => setAdding(false)}
        title="Tạo tài khoản"
        size={draft.role === 'STAFF' ? 'lg' : 'md'}
        footer={<><Button variant="secondary" onClick={() => setAdding(false)}>Hủy</Button><Button disabled={!valid} onClick={create}>Tạo</Button></>}
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
          {draft.role === 'STAFF' && (
            <div className="rounded-xl border border-slate-200 p-4">
              <p className="mb-3 text-sm font-semibold text-slate-800">Hồ sơ nhân viên</p>
              <StaffProfileFields value={profileDraft} onChange={setProfileDraft} showStatus={false} />
              {profileError && draft.name.trim() && <Alert tone="warning" className="mt-3">{profileError}</Alert>}
            </div>
          )}
        </div>
      </Modal>

      {editingStaff && <StaffProfileModal key={editingStaff.id} staff={editingStaff} onClose={() => setEditing(null)} />}
    </>
  );
};
