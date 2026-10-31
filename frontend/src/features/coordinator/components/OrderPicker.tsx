import { Select } from '@/components/ui';
import { useApp } from '@/services/store/AppStore';
import type { Order } from '@/types';

export const OrderPicker = ({
  value,
  onChange,
  filter,
}: {
  value: string;
  onChange: (id: string) => void;
  filter?: (o: Order) => boolean;
}) => {
  const { state } = useApp();
  const list = state.orders.filter((o) =>
    filter ? filter(o) || o.id === value : true,
  );
  return (
    <Select
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className="w-auto min-w-[240px] font-semibold tabular-nums"
    >
      {list.map((o) => (
        <option key={o.id} value={o.id}>
          #{o.id} · {o.customerName}
        </option>
      ))}
    </Select>
  );
};
