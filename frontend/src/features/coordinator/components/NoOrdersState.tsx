import { KanbanSquare } from 'lucide-react';
import { Card, EmptyState } from '@/components/ui';

export const NoOrdersState = () => (
  <Card>
    <EmptyState icon={KanbanSquare} title="Không tìm thấy đơn hàng">
      Đơn có thể đã bị xóa hoặc đường dẫn không đúng. Quay lại danh sách đơn hàng để chọn đơn khác.
    </EmptyState>
  </Card>
);
