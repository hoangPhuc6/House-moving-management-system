import { KanbanSquare } from 'lucide-react';
import { Card, EmptyState } from '@/components/ui';

export const NoOrdersState = () => (
  <Card>
    <EmptyState icon={KanbanSquare} title="Chưa có đơn hàng nào">
      Đơn sẽ xuất hiện tại đây khi khách hàng gửi yêu cầu đặt dịch vụ.
    </EmptyState>
  </Card>
);
