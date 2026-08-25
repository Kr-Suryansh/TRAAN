// ============================================================
// sih-dashboard · src/context/AuthContext.tsx
// Stores access_token + user in sessionStorage (not localStorage).
// Listens for auth:expired event from axios interceptor.
// ============================================================

import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
} from 'react';
import type { AuthorityUser } from '../types';

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  user: AuthorityUser | null;
}

interface AuthContextValue extends AuthState {
  login: (accessToken: string, refreshToken: string, user: AuthorityUser) => void;
  logout: () => void;
  isAuthenticated: boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<AuthState>(() => {
    try {
      const accessToken = sessionStorage.getItem('access_token');
      const refreshToken = sessionStorage.getItem('refresh_token');
      const userJson = sessionStorage.getItem('authority_user');
      return {
        accessToken,
        refreshToken,
        user: userJson ? JSON.parse(userJson) : null,
      };
    } catch {
      return { accessToken: null, refreshToken: null, user: null };
    }
  });

  const login = useCallback((accessToken: string, refreshToken: string, user: AuthorityUser) => {
    sessionStorage.setItem('access_token', accessToken);
    sessionStorage.setItem('refresh_token', refreshToken);
    sessionStorage.setItem('authority_user', JSON.stringify(user));
    setState({ accessToken, refreshToken, user });
  }, []);

  const logout = useCallback(() => {
    sessionStorage.removeItem('access_token');
    sessionStorage.removeItem('refresh_token');
    sessionStorage.removeItem('authority_user');
    setState({ accessToken: null, refreshToken: null, user: null });
  }, []);

  // Handle 401 from axios interceptor
  useEffect(() => {
    const handler = () => logout();
    window.addEventListener('auth:expired', handler);
    return () => window.removeEventListener('auth:expired', handler);
  }, [logout]);

  return (
    <AuthContext.Provider
      value={{ ...state, login, logout, isAuthenticated: !!state.accessToken }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
