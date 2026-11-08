import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, setUnauthorizedHandler, tokenStore } from '../api/client';
import type { User } from '../api/types';

interface AuthState {
  user: User | null;
  token: string | null;
  /** True while an existing token is being validated on page load. */
  restoring: boolean;
  login(username: string, password: string): Promise<void>;
  register(username: string, password: string, displayName?: string): Promise<void>;
  logout(): void;
  setUser(user: User): void;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(() => tokenStore.get());
  const [user, setUser] = useState<User | null>(null);
  const [restoring, setRestoring] = useState<boolean>(() => tokenStore.get() !== null);

  const logout = useCallback(() => {
    tokenStore.set(null);
    setToken(null);
    setUser(null);
  }, []);

  useEffect(() => setUnauthorizedHandler(logout), [logout]);

  useEffect(() => {
    if (!token || user) {
      setRestoring(false);
      return;
    }
    let cancelled = false;
    api
      .me()
      .then((me) => !cancelled && setUser(me))
      .catch(() => !cancelled && logout())
      .finally(() => !cancelled && setRestoring(false));
    return () => {
      cancelled = true;
    };
  }, [token, user, logout]);

  const accept = useCallback((auth: { token: string; user: User }) => {
    tokenStore.set(auth.token);
    setToken(auth.token);
    setUser(auth.user);
  }, []);

  const value = useMemo<AuthState>(
    () => ({
      user,
      token,
      restoring,
      login: async (username, password) => accept(await api.login(username, password)),
      register: async (username, password, displayName) =>
        accept(await api.register(username, password, displayName)),
      logout,
      setUser,
    }),
    [user, token, restoring, accept, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
