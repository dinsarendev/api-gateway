import { useState } from 'react';
import { API } from '../api/gateway';
import { auth } from '../auth';
import { useBrand } from '../context/BrandContext';

export default function Login({ onLogin }) {
  const { brand } = useBrand();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading,  setLoading]  = useState(false);
  const [error,    setError]    = useState('');
  const [showPwd,  setShowPwd]  = useState(false);

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
      background: `linear-gradient(135deg, ${brand.loginBgFrom} 0%, ${brand.loginBgTo} 100%)`,
    }}>
      <div style={{
        width: 380, background: 'var(--bg-surface)', borderRadius: 12,
        boxShadow: '0 20px 60px rgba(0,0,0,.35)', overflow: 'hidden',
      }}>
        {/* Header */}
        <div style={{
          background: `linear-gradient(135deg, ${brand.accentColor}, ${brand.loginBgFrom})`,
          padding: '2rem', textAlign: 'center', color: '#fff',
        }}>
          <div style={{ fontSize: '2rem', marginBottom: '.5rem' }}>
            {brand.brandLogo
              ? <img src={brand.brandLogo} alt={brand.brandName} style={{ height: 48, objectFit: 'contain', display: 'block', margin: '0 auto .25rem' }} />
              : <i className="fa-solid fa-network-wired" />
            }
          </div>
          <div style={{ fontWeight: 800, fontSize: '1.2rem', letterSpacing: '.04em' }}>
            {brand.brandName}
          </div>
          <div style={{ fontSize: '.8rem', color: 'rgba(255,255,255,.7)', marginTop: '.25rem' }}>
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
            <label style={{ display: 'block', fontWeight: 600, fontSize: '.85rem', marginBottom: '.4rem', color: 'var(--text-primary)' }}>
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
            <label style={{ display: 'block', fontWeight: 600, fontSize: '.85rem', marginBottom: '.4rem', color: 'var(--text-primary)' }}>
              Password
            </label>
            <div style={{ position: 'relative' }}>
              <input
                className="form-control"
                type={showPwd ? 'text' : 'password'}
                value={password}
                onChange={e => setPassword(e.target.value)}
                placeholder="••••••••"
                autoComplete="current-password"
                style={{ paddingRight: '2.5rem' }}
              />
              <button
                type="button"
                onClick={() => setShowPwd(v => !v)}
                style={{
                  position: 'absolute', top: '50%', right: '.75rem', transform: 'translateY(-50%)',
                  background: 'none', border: 'none', cursor: 'pointer',
                  color: 'var(--text-secondary)', padding: 0, lineHeight: 1,
                }}
                aria-label={showPwd ? 'Hide password' : 'Show password'}
              >
                <i className={`fa-solid ${showPwd ? 'fa-eye-slash' : 'fa-eye'}`} />
              </button>
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            style={{
              width: '100%', padding: '.75rem',
              background: loading ? 'var(--accent-hover)' : brand.accentColor,
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
