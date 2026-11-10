import { useState, type FormEvent } from 'react';
import { useAuth } from '../auth/AuthContext';
import type { useTheme } from '../hooks/useTheme';
import { ChatIcon, MoonIcon, SunIcon } from './icons';

type Mode = 'login' | 'register';

export function AuthScreen({ theme }: { theme: ReturnType<typeof useTheme> }) {
  const { login, register } = useAuth();
  const [mode, setMode] = useState<Mode>('login');
  const [username, setUsername] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      if (mode === 'login') await login(username, password);
      else await register(username, password, displayName || undefined);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong');
      setBusy(false);
    }
  }

  const switchMode = () => {
    setMode(mode === 'login' ? 'register' : 'login');
    setError(null);
  };

  return (
    <main className="auth-screen" data-testid="auth-screen">
      <button className="icon-button theme-corner" onClick={theme.toggle} aria-label="Toggle dark mode">
        {theme.theme === 'dark' ? <SunIcon /> : <MoonIcon />}
      </button>
      <div className="auth-card">
        <div className="brand">
          <span className="brand-mark"><ChatIcon width={22} height={22} /></span>
          <span className="brand-name">Chatroom</span>
        </div>
        <h1>{mode === 'login' ? 'Welcome back' : 'Create your account'}</h1>
        <p className="muted">
          {mode === 'login' ? 'Sign in to pick up where you left off.' : 'Join rooms and message people in real time.'}
        </p>

        <form onSubmit={submit} noValidate>
          <label>
            Username
            <input
              name="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoComplete="username"
              autoCapitalize="none"
              required
            />
          </label>
          {mode === 'register' && (
            <label>
              <span>Display name <span className="muted">(optional)</span></span>
              <input name="displayName" value={displayName} onChange={(e) => setDisplayName(e.target.value)} maxLength={64} />
            </label>
          )}
          <label>
            Password
            <input
              type="password"
              name="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              required
            />
          </label>
          {mode === 'register' && <p className="hint">At least 8 characters. Usernames use letters, numbers and _.</p>}
          {error && <p className="form-error" role="alert">{error}</p>}
          <button className="button primary block" type="submit" disabled={busy || !username || !password}>
            {busy ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}
          </button>
        </form>

        <p className="auth-switch">
          {mode === 'login' ? "Don't have an account?" : 'Already have an account?'}{' '}
          <button className="link-button" onClick={switchMode}>
            {mode === 'login' ? 'Sign up' : 'Sign in'}
          </button>
        </p>
      </div>
    </main>
  );
}
