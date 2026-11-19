import { useState } from 'react';
import { Camera, CheckCircle2, CornerUpLeft, FileWarning, Lock, Scale, Search, Send, XCircle } from 'lucide-react';
import {
  Alert,
  Badge,
  Button,
  Card,
  CardHeader,
  cx,
  EmptyState,
  Field,
  Input,
  KeyValue,
  PageHeader,
  StatusBadge,
} from '@/components/ui';
import { isTruckOnly, TRUCK_ONLY_CLAIM_SCOPE } from '@/constants/order';
import { EvidenceTile } from '@/features/admin/components/EvidenceTile';
import { AdminPeriodPicker, OutOfPeriodNotice } from '@/features/admin/components/PeriodPicker';
import { useAdminPeriod } from '@/features/admin/hooks';
import { CLAIM_STATUS } from '@/features/admin/constants';
import { matches } from '@/features/admin/utils';
import { formatVND } from '@/services/format';
import { computeTotals } from '@/services/pricing';
import { useApp } from '@/services/store/AppStore';
import type { ClaimStatus, ClaimTicket } from '@/types';

export type ClaimsRole = 'COORDINATOR' | 'ADMIN';

/**
 * Chia việc khiếu nại theo vai trò:
 * - Điều phối tiếp nhận, đối chiếu ảnh đóng gói với ảnh khách gửi, rồi đề xuất mức bồi thường hoặc từ chối.
 * - Admin chỉ duyệt khoản chi (hoặc trả lại Điều phối) — không phải xử lý từng phiếu từ đầu.
 */
const SECTIONS: Record<ClaimsRole, { title: string; statuses: ClaimStatus[]; empty: string }[]> = {
  COORDINATOR: [
    { title: 'Cần xử lý', statuses: ['OPEN', 'INVESTIGATING'], empty: 'Khiếu nại mới từ khách sẽ xuất hiện tại đây.' },
    { title: 'Chờ Admin duyệt chi', statuses: ['PENDING_APPROVAL'], empty: 'Chưa có đề xuất nào đang chờ duyệt.' },
    { title: 'Đã xử lý', statuses: ['APPROVED', 'REJECTED'], empty: 'Phiếu đã duyệt hoặc từ chối sẽ lưu tại đây.' },
  ],
  ADMIN: [
    { title: 'Chờ duyệt chi', statuses: ['PENDING_APPROVAL'], empty: 'Điều phối chưa gửi đề xuất bồi thường nào.' },
    { title: 'Điều phối đang xử lý', statuses: ['OPEN', 'INVESTIGATING'], empty: 'Không có phiếu đang đối chiếu.' },
    { title: 'Đã xử lý', statuses: ['APPROVED', 'REJECTED'], empty: 'Phiếu đã duyệt hoặc từ chối sẽ lưu tại đây.' },
  ],
};

/** Khiếu nại chờ vai trò này hành động — dùng cho badge menu. */
export const claimsAwaiting = (claims: ClaimTicket[], role: ClaimsRole) =>
  claims.filter((c) => SECTIONS[role][0]!.statuses.includes(c.status)).length;

/** Khu xử lý của Điều phối: đối chiếu → đề xuất mức bồi thường hoặc từ chối. */
const CoordinatorDecision = ({ claim }: { claim: ClaimTicket }) => {
  const { dispatch } = useApp();
  const [amount, setAmount] = useState(claim.proposedAmount ?? claim.requestedAmount);
  const [note, setNote] = useState('');
  const [tried, setTried] = useState(false);
  const noteErr = tried && !note.trim() ? 'Bắt buộc — căn cứ đề xuất hoặc lý do từ chối' : null;

  if (claim.status === 'PENDING_APPROVAL') {
    return (
      <Alert tone="warning" className="mt-3" title={`Đã đề xuất ${formatVND(claim.proposedAmount ?? 0)} — chờ Admin duyệt chi`}>
        {claim.handlingNote}
      </Alert>
    );
  }
  const act = (fn: () => void) => {
    setTried(true);
    if (!note.trim()) return;
    fn();
  };

  return (
    <>
      {claim.status === 'INVESTIGATING' && claim.handlingNote.startsWith('Admin trả lại') && (
        <Alert tone="info" className="mt-3" title="Admin trả lại">
          {claim.handlingNote.replace(/^Admin trả lại:\s*/, '')}
        </Alert>
      )}
      {claim.status === 'OPEN' && (
        <Button variant="secondary" icon={Search} className="mt-3" onClick={() => dispatch({ type: 'CLAIM_INVESTIGATE', claimId: claim.id })}>
          Nhận xử lý & đối chiếu
        </Button>
      )}
      <Field label="Mức bồi thường đề xuất" hint={formatVND(amount)} className="mt-3">
        <Input type="number" min={0} step={50_000} value={amount} onChange={(e) => setAmount(Math.max(0, Number(e.target.value)))} />
      </Field>
      <Field label="Căn cứ / lý do *" error={noteErr} className="mt-3">
        <Input
          value={note}
          invalid={!!noteErr}
          onChange={(e) => setNote(e.target.value)}
          placeholder="VD: Ảnh đóng gói cho thấy tủ còn nguyên, vết nứt phát sinh khi khiêng lên lầu"
        />
      </Field>
      <div className="mt-4 flex flex-wrap gap-2">
        <Button
          variant="ghost"
          icon={XCircle}
          className="text-rose-600 hover:bg-rose-50"
          onClick={() => act(() => dispatch({ type: 'CLAIM_REJECT', claimId: claim.id, note }))}
        >
          Từ chối
        </Button>
        <Button
          icon={Send}
          disabled={amount <= 0}
          onClick={() => act(() => dispatch({ type: 'CLAIM_PROPOSE_PAYOUT', claimId: claim.id, amount, note }))}
        >
          Gửi Admin duyệt chi
        </Button>
      </div>
    </>
  );
};

/** Khu duyệt của Admin: chỉ với phiếu Điều phối đã đề xuất. */
const AdminDecision = ({ claim }: { claim: ClaimTicket }) => {
  const { dispatch } = useApp();
  const [amount, setAmount] = useState(claim.proposedAmount ?? 0);
  const [note, setNote] = useState('');
  const [tried, setTried] = useState(false);

  if (claim.status !== 'PENDING_APPROVAL') {
    return (
      <p className="mt-3 rounded-lg bg-slate-50 p-3 text-sm text-slate-500">
        Điều phối đang đối chiếu — phiếu sẽ chuyển sang đây khi có đề xuất bồi thường.
      </p>
    );
  }
  return (
    <>
      <KeyValue label="Điều phối đề xuất" value={formatVND(claim.proposedAmount ?? 0)} strong />
      {claim.handlingNote && <p className="mt-2 rounded-lg bg-slate-50 p-3 text-sm text-slate-700">{claim.handlingNote}</p>}
      <Field label="Số tiền duyệt chi" hint={formatVND(amount)} className="mt-3">
        <Input type="number" min={0} step={50_000} value={amount} onChange={(e) => setAmount(Math.max(0, Number(e.target.value)))} />
      </Field>
      <Field label="Lý do trả lại (nếu trả lại)" error={tried && !note.trim() ? 'Ghi lý do để Điều phối bổ sung' : null} className="mt-3">
        <Input value={note} onChange={(e) => setNote(e.target.value)} placeholder="VD: Cần thêm ảnh hiện trường lúc bàn giao" />
      </Field>
      <div className="mt-4 flex flex-wrap gap-2">
        <Button
          variant="ghost"
          icon={CornerUpLeft}
          onClick={() => {
            setTried(true);
            if (note.trim()) dispatch({ type: 'CLAIM_SEND_BACK', claimId: claim.id, note });
          }}
        >
          Trả lại Điều phối
        </Button>
        <Button variant="success" icon={CheckCircle2} disabled={amount <= 0} onClick={() => dispatch({ type: 'CLAIM_APPROVE_PAYOUT', claimId: claim.id, amount })}>
          Duyệt chi
        </Button>
      </div>
    </>
  );
};

/** Trang khiếu nại của Admin: lọc theo kỳ chọn chung (cần outlet context của AdminLayout). */
export const AdminClaimsModule = ({ query }: { query: string }) => {
  const { inPeriodTs } = useAdminPeriod();
  return <ClaimsModule query={query} role="ADMIN" inPeriodTs={inPeriodTs} />;
};

export const ClaimsModule = ({
  query,
  role = 'ADMIN',
  inPeriodTs,
}: {
  query: string;
  role?: ClaimsRole;
  /** Lọc theo kỳ (chỉ khu Admin) — bỏ trống để thấy mọi phiếu. */
  inPeriodTs?: (t: number) => boolean;
}) => {
  const { state } = useApp();
  // Admin lọc theo kỳ chọn chung (ngày khách gửi khiếu nại); Điều phối luôn thấy mọi phiếu cần xử lý.
  const searched = state.claims.filter((c) => matches(query, c.id, c.orderId, c.customerName, c.title));
  const visibleClaims = inPeriodTs ? searched.filter((c) => inPeriodTs(c.createdAt)) : searched;
  const openOutside =
    inPeriodTs ? searched.filter((c) => SECTIONS[role][0]!.statuses.includes(c.status) && !inPeriodTs(c.createdAt)).length : 0;
  const sections = SECTIONS[role].map((s) => ({ ...s, items: visibleClaims.filter((c) => s.statuses.includes(c.status)) }));
  const [selectedId, setSelectedId] = useState(() => sections.find((s) => s.items.length)?.items[0]?.id ?? visibleClaims[0]?.id ?? '');
  const claim = visibleClaims.find((c) => c.id === selectedId) ?? visibleClaims[0];
  const order = claim ? state.orders.find((o) => o.id === claim.orderId) : undefined;
  // Khiếu nại có thể đến trước khi đóng hồ sơ: khi đó doanh thu chưa khóa, hiển thị tổng hóa đơn hiện tại.
  const revenueLocked = order?.finalRevenue != null;
  const orderRevenue = order ? (order.finalRevenue ?? computeTotals(order).grandTotal) : 0;

  return (
    <>
      <PageHeader
        eyebrow="Chăm sóc sau bán"
        title={role === 'ADMIN' ? 'Duyệt chi bồi thường' : 'Khiếu nại'}
        subtitle={
          role === 'ADMIN'
            ? 'Điều phối đối chiếu và đề xuất mức bồi thường — Admin duyệt khoản chi. Doanh thu gốc của đơn không bị thay đổi.'
            : 'Đối chiếu ảnh đóng gói với ảnh khách gửi, rồi đề xuất mức bồi thường để Admin duyệt chi hoặc từ chối kèm lý do.'
        }
        actions={inPeriodTs ? <AdminPeriodPicker /> : undefined}
      />
      <OutOfPeriodNotice count={openOutside} what="khiếu nại chưa xử lý" />
      <div className="grid gap-4 xl:grid-cols-[360px_1fr]">
        <div className="h-fit space-y-4">
          {sections.map((section) => (
            <Card key={section.title} padded={false}>
              <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
                <p className="text-sm font-semibold">{section.title}</p>
                <span className="text-xs text-slate-400 tabular-nums">{section.items.length} phiếu</span>
              </div>
              {section.items.length === 0 ? (
                <p className="p-4 text-sm text-slate-500">{section.empty}</p>
              ) : (
                <ul className="divide-y divide-slate-100">
                  {section.items.map((c) => (
                    <li key={c.id}>
                      <button
                        type="button"
                        onClick={() => setSelectedId(c.id)}
                        className={cx('w-full px-4 py-3.5 text-left transition-colors', c.id === claim?.id ? 'bg-slate-50' : 'hover:bg-slate-50/60')}
                      >
                        <div className="flex items-center justify-between gap-2">
                          <span className="font-semibold tabular-nums">#{c.id}</span>
                          <Badge tone={CLAIM_STATUS[c.status].tone}>{CLAIM_STATUS[c.status].label}</Badge>
                        </div>
                        <p className="mt-1 truncate text-sm text-slate-700">{c.title}</p>
                        <p className="mt-0.5 text-xs text-slate-500 tabular-nums">
                          Liên kết #{c.orderId} · {c.customerName}
                        </p>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </Card>
          ))}
        </div>

        {claim ? (
          <div className="space-y-4">
            <Card>
              <CardHeader
                eyebrow={`#${claim.id} → #${claim.orderId}`}
                title={claim.title}
                subtitle={claim.description}
                action={<Badge tone={CLAIM_STATUS[claim.status].tone}>{CLAIM_STATUS[claim.status].label}</Badge>}
              />
              <div className="grid gap-4 md:grid-cols-2">
                <div className="rounded-2xl border border-slate-200 bg-slate-50/60 p-4">
                  <p className="mb-3 flex items-center gap-2 text-sm font-semibold text-slate-900">
                    <Camera className="size-4 text-slate-500" /> Ảnh/Video lúc đóng gói (Đội xe chụp)
                  </p>
                  {claim.packingEvidence.length === 0 ? (
                    <p className="text-sm text-slate-600">
                      {order && isTruckOnly(order) ? `Không có — khách tự đóng gói. ${TRUCK_ONLY_CLAIM_SCOPE.replace('bạn tự đóng gói, nên ', '')}` : 'Không có ảnh đóng gói.'}
                    </p>
                  ) : (
                    <div className="grid grid-cols-2 gap-2">
                      {claim.packingEvidence.map((m) => (
                        <EvidenceTile key={m.id} m={m} />
                      ))}
                    </div>
                  )}
                </div>
                <div className="rounded-2xl border border-rose-200 bg-rose-50/40 p-4">
                  <p className="mb-3 flex items-center gap-2 text-sm font-semibold text-rose-900">
                    <FileWarning className="size-4 text-rose-500" /> Ảnh khách hàng khiếu nại
                  </p>
                  <div className="grid grid-cols-2 gap-2">
                    {claim.customerEvidence.map((m) => (
                      <EvidenceTile key={m.id} m={m} />
                    ))}
                  </div>
                </div>
              </div>
            </Card>

            <div className="grid gap-4 lg:grid-cols-2">
              <Card>
                <CardHeader title="Hồ sơ đơn gốc" icon={Lock} subtitle={revenueLocked ? 'Read-only — bất biến' : 'Chưa đóng hồ sơ — doanh thu chưa khóa'} />
                <KeyValue label="Đơn hàng" value={`#${claim.orderId}`} />
                <KeyValue label="Trạng thái" value={order ? <StatusBadge status={order.status} /> : '—'} />
                <KeyValue
                  label={
                    revenueLocked ? (
                      <span className="inline-flex items-center gap-1.5">
                        <Lock className="size-3.5" /> Doanh thu gốc
                      </span>
                    ) : (
                      'Tổng hóa đơn (chưa khóa)'
                    )
                  }
                  value={formatVND(orderRevenue)}
                  strong
                />
                <p className="mt-3 rounded-lg bg-slate-50 p-3 text-xs text-slate-500">
                  Khoản bồi thường được hạch toán vào “Chi phí bồi thường khiếu nại” trên sổ cái — doanh thu của đơn gốc giữ nguyên.
                </p>
              </Card>
              <Card>
                <CardHeader title={role === 'ADMIN' ? 'Duyệt chi bồi thường' : 'Xử lý khiếu nại'} icon={Scale} />
                <KeyValue label="Khách đề nghị" value={formatVND(claim.requestedAmount)} />
                {claim.status === 'APPROVED' ? (
                  <Alert tone="success" className="mt-3" title={`Đã duyệt chi ${formatVND(claim.approvedAmount ?? 0)}`}>
                    {revenueLocked ? 'Doanh thu gốc' : 'Tổng hóa đơn'} #{claim.orderId} vẫn là <b className="tabular-nums">{formatVND(orderRevenue)}</b>.
                  </Alert>
                ) : claim.status === 'REJECTED' ? (
                  <Alert tone="danger" className="mt-3" title="Đã từ chối khiếu nại">
                    {claim.handlingNote}
                  </Alert>
                ) : role === 'ADMIN' ? (
                  <AdminDecision key={`${claim.id}-${claim.status}`} claim={claim} />
                ) : (
                  <CoordinatorDecision key={`${claim.id}-${claim.status}`} claim={claim} />
                )}
              </Card>
            </div>
          </div>
        ) : (
          <Card>
            <EmptyState icon={FileWarning} title="Chưa có phiếu khiếu nại" />
          </Card>
        )}
      </div>
    </>
  );
};
