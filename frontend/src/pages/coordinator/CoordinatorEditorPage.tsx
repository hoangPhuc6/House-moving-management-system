import { EditorTab } from '@/features/coordinator/components/EditorTab';
import { NoOrdersState } from '@/features/coordinator/components/NoOrdersState';
import { useCoordinatorContext } from '@/features/coordinator/hooks';

/** /coordinator/editor — lập báo giá & nhật ký. */
export const CoordinatorEditorPage = () => {
  const { selected, setSelectedId } = useCoordinatorContext();
  if (!selected) return <NoOrdersState />;
  return <EditorTab order={selected} onSelect={setSelectedId} />;
};
