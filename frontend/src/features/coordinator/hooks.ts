import { useOutletContext } from 'react-router-dom';
import type { CoordinatorOutletContext } from '@/features/coordinator/types';

export const useCoordinatorContext = (): CoordinatorOutletContext => useOutletContext<CoordinatorOutletContext>();
