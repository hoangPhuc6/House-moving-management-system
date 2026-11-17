import { useState } from 'react';
import { KanbanSquare, ListTodo } from 'lucide-react';
import { PageHeader, Tabs } from '@/components/ui';
import { KanbanBoard } from '@/features/coordinator/components/KanbanBoard';
import { TodoView } from '@/features/coordinator/components/TodoView';
import type { CoordTab } from '@/features/coordinator/types';
import { weekdayOf } from '@/features/order/surveyStage';
import { useNow } from '@/services/store/AppStore';

type QueueView = 'todo' | 'kanban';

/** "Ca sáng · Thứ Năm, 29/10/2026" theo giờ hiện tại. */
const shiftLabel = (epoch: number) => {
  const d = new Date(epoch);
  const p = (n: number) => String(n).padStart(2, '0');
  const iso = `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
  const shift = d.getHours() < 12 ? 'Ca sáng' : d.getHours() < 18 ? 'Ca chiều' : 'Ca tối';
  return `${shift} · ${weekdayOf(iso)}, ${p(d.getDate())}/${p(d.getMonth() + 1)}/${d.getFullYear()}`;
};

export const QueueTab = ({
  onOpen,
  query,
}: {
  onOpen: (id: string, t: CoordTab) => void;
  query: string;
}) => {
  const now = useNow(60_000);
  const [view, setView] = useState<QueueView>('todo');

  return (
    <>
      <PageHeader
        eyebrow={shiftLabel(now)}
        title={view === 'todo' ? 'Việc cần làm' : 'Tất cả đơn hàng'}
        subtitle={
          view === 'todo'
            ? 'Việc gấp ở trên cùng, sau đó là đơn theo từng bước quy trình.'
            : 'Toàn bộ vòng đời đơn hàng. Đơn chưa chốt sau 24h sẽ tự động hủy.'
        }
        actions={
          <Tabs<QueueView>
            value={view}
            onChange={setView}
            tabs={[
              { id: 'todo', label: 'Việc cần làm', icon: ListTodo },
              { id: 'kanban', label: 'Tất cả đơn', icon: KanbanSquare },
            ]}
          />
        }
      />
      {view === 'todo' ? <TodoView onOpen={onOpen} query={query} /> : <KanbanBoard onOpen={onOpen} query={query} />}
    </>
  );
};
