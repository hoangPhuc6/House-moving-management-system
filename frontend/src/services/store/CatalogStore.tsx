import { createContext, type ReactNode, useContext, useReducer } from 'react';
import { createInitialCatalog } from '@/constants/catalog';
import { catalogReducer } from '@/services/store/catalogReducer';
import type { CatalogContextValue } from '@/types';

export const CatalogContext = createContext<CatalogContextValue | null>(null);

export const CatalogProvider = ({ children }: { children: ReactNode }) => {
  const [catalog, dispatchCatalog] = useReducer(catalogReducer, undefined, createInitialCatalog);
  return <CatalogContext.Provider value={{ catalog, dispatchCatalog }}>{children}</CatalogContext.Provider>;
};

export const useCatalog = (): CatalogContextValue => {
  const ctx = useContext(CatalogContext);
  if (!ctx) throw new Error('useCatalog must be used within CatalogProvider');
  return ctx;
};
