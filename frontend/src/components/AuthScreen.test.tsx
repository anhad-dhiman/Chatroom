import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AuthProvider } from '../auth/AuthContext';
import { AuthScreen } from './AuthScreen';

const theme = { theme: 'light' as const, toggle: vi.fn() };

function renderScreen() {
  return render(
    <AuthProvider>
      <AuthScreen theme={theme} />
    </AuthProvider>,
  );
}

describe('AuthScreen', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('switches between sign in and sign up', async () => {
    const user = userEvent.setup();
    renderScreen();
    expect(screen.getByRole('heading', { name: 'Welcome back' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Sign up' }));
    expect(screen.getByRole('heading', { name: 'Create your account' })).toBeInTheDocument();
    expect(screen.getByLabelText(/Display name/)).toBeInTheDocument();
  });

  it('shows the server error message when login fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ error: 'Invalid username or password' }), { status: 401 }),
    ));
    const user = userEvent.setup();
    renderScreen();
    await user.type(screen.getByLabelText('Username'), 'alice');
    await user.type(screen.getByLabelText('Password'), 'wrong-password');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid username or password');
  });
});
