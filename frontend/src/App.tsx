import { AppRoutes } from '@/routes/AppRoutes';
import { AppProvider } from '@/services/store/AppStore';
import { CatalogProvider } from '@/services/store/CatalogStore';

export const App = () => (
  <AppProvider>
    <CatalogProvider>
      <AppRoutes />
    </CatalogProvider>
  </AppProvider>
);
