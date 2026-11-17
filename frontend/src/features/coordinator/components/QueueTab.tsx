import { PageHeader } from '@/components/ui';
import { TodoView } from '@/features/coordinator/components/TodoView';
import type { OrderSection } from '@/features/coordinator/types';
import { weekdayOf } from '@/features/order/surveyStage';
import { useNow } from '@/services/store/AppStore';

/** "Ca sáng · Thứ Năm, 29/10/2026" theo giờ hiện tại. */
const shiftLabel = (epoch: number) => {
  const d = new Date(epoch);
  const p = (n: number) => String(n).padStart(2, '0');
  const iso = `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
  const shift = d.getHours() < 12 ? 'Ca sáng' : d.getHours() < 18 ? 'Ca chiều' : 'Ca tối';
  return `${shift} · ${weekdayOf(iso)}, ${p(d.getDate())}/${p(d.getMonth() + 1)}/${d.getFullYear()}`;
};

/** Trang đầu của Điều phối — việc gấp ở trên, sau đó là đơn theo từng bước quy trình. */
export const QueueTab = ({ onOpen, query }: { onOpen: (id: string, s?: OrderSection) => void; query: string }) => {
  const now = useNow(60_000);
  return (
    <>
      <PageHeader eyebrow={shiftLabel(now)} title="Việc cần làm" subtitle="Việc gấp ở trên cùng, sau đó là đơn theo từng bước quy trình." />
      <TodoView onOpen={onOpen} query={query} />
    </>
  );
};
