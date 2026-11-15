import { useState } from 'react';
import { Alert, Button, cx, Field, Input, Modal, Select } from '@/components/ui';
import {
  LICENSE_LABEL,
  licenseExpiringSoon,
  SKILL_LABEL,
  SKILL_ORDER,
  staffChangeBlocker,
  validateStaffProfile,
  WORK_STATUS_META,
} from '@/services/staff';
import { useApp } from '@/services/store/AppStore';
import type { LicenseClass, StaffMember, StaffSkill, StaffWorkStatus } from '@/types';

const todayISO = () => new Date().toISOString().slice(0, 10);

/** Kỹ năng gọn một dòng, VD "Lái xe · Bốc xếp". */
export const StaffSkillsCell = ({ staff }: { staff: StaffMember }) => (
  <span className="text-slate-700">{SKILL_ORDER.filter((k) => staff.skills.includes(k)).map((k) => SKILL_LABEL[k]).join(' · ')}</span>
);

/** Bằng lái gọn một dòng; hết hạn tô đỏ, sắp hết hạn (30 ngày) tô vàng. */
export const LicenseCell = ({ staff }: { staff: StaffMember }) => {
  if (!staff.license) return <span className="text-slate-400">—</span>;
  const today = todayISO();
  const expired = staff.license.expiresAt < today;
  const soon = licenseExpiringSoon(staff, today);
  return (
    <span className={cx('tabular-nums', expired ? 'font-semibold text-rose-600' : soon ? 'font-semibold text-amber-700' : 'text-slate-700')}>
      <b className="font-semibold">{staff.license.licenseClass}</b> · {expired ? 'hết hạn' : 'đến'} {staff.license.expiresAt.split('-').reverse().join('/')}
    </span>
  );
};

export interface StaffProfileDraft {
  code: string;
  skills: StaffSkill[];
  licenseClass: LicenseClass | '';
  licenseExpiresAt: string;
  status: StaffWorkStatus;
}

export const draftOf = (s: StaffMember): StaffProfileDraft => ({
  code: s.code,
  skills: s.skills,
  licenseClass: s.license?.licenseClass ?? '',
  licenseExpiresAt: s.license?.expiresAt ?? '',
  status: s.status,
});

export const applyDraft = (base: StaffMember, d: StaffProfileDraft): StaffMember => ({
  ...base,
  code: d.code.trim().toUpperCase(),
  skills: SKILL_ORDER.filter((k) => d.skills.includes(k)),
  license: d.licenseClass ? { licenseClass: d.licenseClass, expiresAt: d.licenseExpiresAt } : null,
  status: d.status,
});

/** Các trường hồ sơ nhân viên — dùng chung cho form tạo tài khoản và form sửa hồ sơ. */
export const StaffProfileFields = ({ value, onChange, showStatus = true }: { value: StaffProfileDraft; onChange: (d: StaffProfileDraft) => void; showStatus?: boolean }) => {
  const set = <K extends keyof StaffProfileDraft>(k: K, v: StaffProfileDraft[K]) => onChange({ ...value, [k]: v });
  const toggleSkill = (k: StaffSkill) => set('skills', value.skills.includes(k) ? value.skills.filter((x) => x !== k) : [...value.skills, k]);
  const drives = value.skills.includes('DRIVE');
  return (
    <div className="space-y-4">
      <div className={cx('grid gap-3', showStatus && 'sm:grid-cols-2')}>
        <Field label="Mã nhân viên" hint="Dạng NV-001">
          <Input value={value.code} onChange={(e) => set('code', e.target.value)} className="uppercase tabular-nums" />
        </Field>
        {showStatus && (
          <Field label="Tình trạng" hint="Nghỉ phép / nghỉ việc thì không được gán vào chuyến">
            <Select value={value.status} onChange={(e) => set('status', e.target.value as StaffWorkStatus)}>
              {(Object.keys(WORK_STATUS_META) as StaffWorkStatus[]).map((s) => (
                <option key={s} value={s}>{WORK_STATUS_META[s].label}</option>
              ))}
            </Select>
          </Field>
        )}
      </div>
      <Field label="Kỹ năng" hint="Chọn nhiều. Vai trò trong từng chuyến do Điều phối chọn khi gán đội.">
        <div className="flex flex-wrap gap-2">
          {SKILL_ORDER.map((k) => {
            const on = value.skills.includes(k);
            return (
              <button
                key={k}
                type="button"
                role="checkbox"
                aria-checked={on}
                onClick={() => toggleSkill(k)}
                className={cx(
                  'h-9 rounded-lg border px-3 text-sm font-semibold transition-colors',
                  on ? 'border-brand bg-brand/10 text-brand' : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300',
                )}
              >
                {on ? '✓ ' : ''}
                {SKILL_LABEL[k]}
              </button>
            );
          })}
        </div>
      </Field>
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label={drives ? 'Hạng bằng lái (bắt buộc)' : 'Hạng bằng lái'}>
          <Select value={value.licenseClass} onChange={(e) => set('licenseClass', e.target.value as LicenseClass | '')}>
            <option value="">Không có</option>
            {(Object.keys(LICENSE_LABEL) as LicenseClass[]).map((c) => (
              <option key={c} value={c}>{LICENSE_LABEL[c]}</option>
            ))}
          </Select>
        </Field>
        {value.licenseClass && (
          <Field label="Ngày hết hạn bằng">
            <Input type="date" value={value.licenseExpiresAt} onChange={(e) => set('licenseExpiresAt', e.target.value)} />
          </Field>
        )}
      </div>
    </div>
  );
};

/** Admin sửa hồ sơ nhân viên của một tài khoản STAFF. */
export const StaffProfileModal = ({ staff, onClose }: { staff: StaffMember; onClose: () => void }) => {
  const { state, dispatch } = useApp();
  const [draft, setDraft] = useState<StaffProfileDraft>(() => draftOf(staff));
  const next = applyDraft(staff, draft);
  const error = validateStaffProfile(next, state.resources.staff) ?? staffChangeBlocker(next, state.orders);

  return (
    <Modal
      open
      onClose={onClose}
      title={`Hồ sơ nhân viên — ${staff.name}`}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Hủy</Button>
          <Button
            disabled={!!error}
            onClick={() => {
              dispatch({ type: 'UPSERT_STAFF', staff: next });
              onClose();
            }}
          >
            Lưu hồ sơ
          </Button>
        </>
      }
    >
      <StaffProfileFields value={draft} onChange={setDraft} />
      {error && <Alert tone="warning" className="mt-4">{error}</Alert>}
    </Modal>
  );
};
