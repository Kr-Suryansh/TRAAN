// ============================================================
// sih-dashboard · src/App.tsx
// Root component — routes between LoginPage and DashboardPage.
// Wraps everything in AuthProvider + WebSocketProvider.
// ============================================================


import { AuthProvider, useAuth } from './context/AuthContext';
import { WebSocketProvider } from './context/WebSocketContext';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';

function AppInner() {
  const { isAuthenticated, accessToken } = useAuth();

  return (
    <WebSocketProvider accessToken={accessToken}>
      {isAuthenticated ? <DashboardPage /> : <LoginPage />}
    </WebSocketProvider>
  );
}

export function App() {
  return (
    <AuthProvider>
      <AppInner />
    </AuthProvider>
  );
}
