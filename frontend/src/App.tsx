import { AuthProvider, useAuth } from './auth/AuthContext';
import { AuthScreen } from './components/AuthScreen';
import { ChatApp } from './components/ChatApp';
import { useTheme } from './hooks/useTheme';

function Root() {
  const { user, restoring } = useAuth();
  const theme = useTheme();
  if (restoring) {
    return (
      <div className="splash" role="status">
        <div className="spinner" aria-hidden />
        <span className="sr-only">Loading…</span>
      </div>
    );
  }
  return user ? <ChatApp user={user} theme={theme} /> : <AuthScreen theme={theme} />;
}

export function App() {
  return (
    <AuthProvider>
      <Root />
    </AuthProvider>
  );
}
