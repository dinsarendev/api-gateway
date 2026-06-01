import { useState } from 'react';
import { API } from '../api/gateway';
import { auth } from '../auth';

export default function Login({ onLogin }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading,  setLoading]  = useState(false);
  const [error,    setError]    = useState('');

  const submit = async (e) => {
    e.preventDefault();
    if (!username.trim() || !password) { setError('Enter username and password.'); return; }
    setLoading(true);
    setError('');
    try {
      const data = await API.login({ username: username.trim(), password });
      auth.setTokens(data);
      onLogin();
    } catch {
      setError('Invalid username or password.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{
      minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center',
      background: 'linear-gradient(135deg, #0f172a 0%, #1e3a5f 100%)',
    }}>
      <div style={{
        width: 380, background: '#fff', borderRadius: 12,
        boxShadow: '0 20px 60px rgba(0,0,0,.35)', overflow: 'hidden',
      }}>
        {/* Header */}
        <div style={{
          background: 'linear-gradient(135deg, #1d4ed8, #0f172a)',
          padding: '2rem', textAlign: 'center', color: '#fff',
        }}>
          <div style={{ fontSize: '2rem', marginBottom: '.5rem' }}>
            <i className="fa-solid fa-network-wired" />
          </div>
          <div style={{ fontWeight: 800, fontSize: '1.2rem', letterSpacing: '.04em' }}>
            API Gateway
          </div>
          <div style={{ fontSize: '.8rem', color: '#93c5fd', marginTop: '.25rem' }}>
            Admin Console
          </div>
        </div>

        {/* Form */}
        <form onSubmit={submit} style={{ padding: '2rem' }}>
          {error && (
            <div style={{
              background: '#fef2f2', border: '1px solid #fecaca', color: '#dc2626',
              borderRadius: 6, padding: '.65rem .9rem', marginBottom: '1rem', fontSize: '.85rem',
            }}>
              <i className="fa-solid fa-circle-xmark" style={{ marginRight: '.4rem' }} />
              {error}
            </div>
          )}

          <div style={{ marginBottom: '1rem' }}>
            <label style={{ display: 'block', fontWeight: 600, fontSize: '.85rem', marginBottom: '.4rem', color: '#374151' }}>
              Username
            </label>
            <input
              className="form-control"
              value={username}
              onChange={e => setUsername(e.target.value)}
              placeholder="admin"
              autoFocus
              autoComplete="username"
            />
          </div>

          <div style={{ marginBottom: '1.5rem' }}>
            <label style={{ display: 'block', fontWeight: 600, fontSize: '.85rem', marginBottom: '.4rem', color: '#374151' }}>
              Password
            </label>
            <input
              className="form-control"
              type="password"
              value={password}
              onChange={e => setPassword(e.target.value)}
              placeholder="••••••••"
              autoComplete="current-password"
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            style={{
              width: '100%', padding: '.75rem', background: loading ? '#93c5fd' : '#1d4ed8',
              color: '#fff', border: 'none', borderRadius: 8, fontWeight: 700,
              fontSize: '.95rem', cursor: loading ? 'not-allowed' : 'pointer',
              transition: 'background .2s',
            }}
          >
            {loading
              ? <><i className="fa-solid fa-circle-notch fa-spin" style={{ marginRight: '.5rem' }} />Signing in…</>
              : <><i className="fa-solid fa-right-to-bracket" style={{ marginRight: '.5rem' }} />Sign In</>
            }
          </button>
        </form>
      </div>
    </div>
  );
}