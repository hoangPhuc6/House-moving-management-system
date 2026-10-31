import { Timer } from 'lucide-react';
import { Badge } from '@/components/ui';
import { formatCountdown } from '@/services/format';
import { useNow } from '@/services/store/AppStore';

/** Mã 1.1.3 — đếm ngược tới hạn tự động hủy 24h. */
export const AutoCancelCountdown = ({ deadline, compact }: { deadline: number; compact?: boolean }) => {
  const now = useNow();
  const left = deadline - now;
  if (left <= 0) {
    return (
      <Badge tone="danger" dot pulse>
        {compact ? 'Quá hạn 24h' : 'Đã quá hạn 24h — chờ hệ thống hủy'}
      </Badge>
    );
  }
  return (
    <Badge tone={left < 3 * 60 * 60 * 1000 ? 'danger' : 'warning'} icon={Timer} className="tabular-nums">
      {compact ? formatCountdown(left) : `Còn ${formatCountdown(left)} trước khi tự động hủy 24h`}
    </Badge>
  );
};
