// ============================================================
// sih-dashboard · src/pages/LoginPage.tsx
// Authority login — POST /api/v1/auth/authority/login
// Demo credentials (mock mode): demo@sih.gov.in / demo1234
// ============================================================

import React, { useState } from 'react';
import { Shield, LogIn, AlertTriangle } from 'lucide-react';
import { loginAuthority } from '../api/auth';
import { useAuth } from '../context/AuthContext';
import { MOCK_MODE } from '../api/client';

export function LoginPage() {
  const { login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email.trim() || !password.trim()) {
      setError('Email and password are required.');
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const { access_token, user } = await loginAuthority(email.trim(), password);
      login(access_token, user);
    } catch (err) {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const msg = (err as any)?.response?.data?.detail ?? 'Login failed. Check credentials.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        height: '100%', background: 'var(--surface-0)',
        backgroundImage: 'radial-gradient(ellipse at 50% 50%, hsl(213 90% 58% / 0.05) 0%, transparent 70%)',
      }}
    >
      <div
        style={{
          width: '100%', maxWidth: 380, padding: 36,
          background: 'var(--surface-1)',
          border: '1px solid var(--border)',
          borderRadius: 'var(--radius-xl)',
          boxShadow: 'var(--shadow-lg)',
        }}
      >
        {/* Logo / brand */}
        <div style={{ textAlign: 'center', marginBottom: 28 }}>
          <div style={{ display: 'inline-flex', padding: 14, background: 'hsl(213 90% 12%)', borderRadius: 'var(--radius-lg)', border: '1px solid hsl(213 90% 28%)', marginBottom: 14 }}>
            <Shield size={28} color="var(--accent)" />
          </div>
          <h1 style={{ fontSize: '1.2rem', marginBottom: 6 }}>SIH Disaster Response</h1>
          <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
            Authority Coordination Dashboard
          </p>
        </div>

        {/* Mock-mode hint */}
        {MOCK_MODE && (
          <div
            style={{
              background: 'hsl(213 90% 8%)', border: '1px solid hsl(213 90% 25%)',
              borderRadius: 'var(--radius-md)', padding: '8px 12px',
              marginBottom: 20, fontSize: '0.75rem', color: 'hsl(213, 70%, 70%)',
              lineHeight: 1.5,
            }}
          >
            <strong>Mock mode active.</strong><br />
            Use: <code>demo@sih.gov.in</code> / <code>demo1234</code>
          </div>
        )}

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div>
            <label htmlFor="login-email" style={{ display: 'block', fontSize: '0.78rem', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}>
              Email
            </label>
            <input
              id="login-email"
              type="email"
              className="input"
              autoComplete="username"
              value={email}
              onChange={(e) => { setEmail(e.target.value); setError(null); }}
              placeholder="authority@district.gov.in"
              required
            />
          </div>

          <div>
            <label htmlFor="login-password" style={{ display: 'block', fontSize: '0.78rem', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}>
              Password
            </label>
            <input
              id="login-password"
              type="password"
              className="input"
              autoComplete="current-password"
              value={password}
              onChange={(e) => { setPassword(e.target.value); setError(null); }}
              placeholder="••••••••"
              required
            />
          </div>

          {error && (
            <div style={{ display: 'flex', gap: 8, alignItems: 'center', color: 'var(--high)', fontSize: '0.8rem', padding: '8px 12px', background: 'var(--high-bg)', border: '1px solid var(--high-border)', borderRadius: 'var(--radius-sm)' }}>
              <AlertTriangle size={14} />
              {error}
            </div>
          )}

          <button id="login-submit-btn" type="submit" className="btn btn-primary btn-lg" disabled={loading} style={{ marginTop: 6 }}>
            {loading ? 'Signing in…' : <><LogIn size={16} /> Sign In</>}
          </button>
        </form>

        <p style={{ textAlign: 'center', marginTop: 18, fontSize: '0.7rem', color: 'var(--text-muted)' }}>
          For authorised emergency personnel only.
        </p>
      </div>
    </div>
  );
}
