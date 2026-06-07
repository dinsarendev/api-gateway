import { useState, useEffect } from 'react';
import { API } from '../api/gateway';
import { auth } from '../auth';
import { useToast } from '../context/ToastContext';
import { useBrand, BRAND_DEFAULTS } from '../context/BrandContext';

const ROLE_COLORS = {
  SUPER_ADMIN: { bg: '#ede9fe', color: '#5b21b6' },
  OPERATOR:    { bg: '#dbeafe', color: '#1e40af' },
  VIEWER:      { bg: '#f0fdf4', color: '#166534' },
};

export default function Profile() {
  const toast = useToast();
  const { brand, update: updateBrand, reset: resetBrand } = useBrand();
  const [draft, setDraft] = useState({ ...brand });

  const [profile, setProfile]           = useState({ fullName: auth.getFullName(), email: '' });
  const [roles, setRoles]               = useState(auth.getRoles());
  const [profileLoaded, setProfileLoaded] = useState(false);
  const [profileSaving, setProfileSaving] = useState(false);

  const [pwd, setPwd]         = useState({ current: '', next: '', confirm: '' });
  const [pwdSaving, setPwdSaving] = useState(false);
  const [showPwd, setShowPwd]     = useState({ current: false, next: false, confirm: false });

  useEffect(() => {
    API.profile().then(data => {
      setProfile({ fullName: data.full_name || '', email: data.email || '' });
      setRoles(data.roles || []);
      setProfileLoaded(true);
    }).catch(() => setProfileLoaded(true));
  }, []);

  const saveProfile = async (e) => {
    e.preventDefault();
    setProfileSaving(true);
    try {
      await API.updateProfile({ full_name: profile.fullName, email: profile.email });
      localStorage.setItem('gw_full_name', profile.fullName);
      toast.success('Profile updated successfully');
    } catch (err) {
      toast.error(err?.message || 'Failed to update profile');
    } finally {
      setProfileSaving(false);
    }
  };

  const savePassword = async (e) => {
    e.preventDefault();
    if (pwd.next !== pwd.confirm) { toast.error('New passwords do not match'); return; }
    if (pwd.next.length < 8)      { toast.error('Password must be at least 8 characters'); return; }
    setPwdSaving(true);
    try {
      await API.changePassword({ current_password: pwd.current, new_password: pwd.next });
      setPwd({ current: '', next: '', confirm: '' });
      toast.success('Password changed successfully');
    } catch (err) {
      toast.error(err?.message || 'Current password is incorrect');
    } finally {
      setPwdSaving(false);
    }
  };

  const username = auth.getUsername();
  const initials = (profile.fullName || username || '?').trim().split(' ')
    .map(w => w[0]).slice(0, 2).join('').toUpperCase();

  const togglePwd = (field) => setShowPwd(p => ({ ...p, [field]: !p[field] }));

  return (
    <div style={{ maxWidth: 720, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>

      {/* ── Identity banner ─────────────────────────────────────────── */}
      <div className="card" style={{ overflow: 'hidden' }}>
        <div style={{
          height: 80,
          background: 'linear-gradient(135deg, #1d4ed8 0%, #3b82f6 60%, #60a5fa 100%)',
        }} />
        <div style={{ padding: '0 1.5rem 1.25rem', position: 'relative' }}>
          {/* Avatar */}
          <div style={{
            width: 72, height: 72, borderRadius: '50%',
            background: '#1d4ed8', border: '4px solid #fff',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: '1.5rem', fontWeight: 700, color: '#fff',
            position: 'absolute', top: -36, left: '1.5rem',
            boxShadow: '0 2px 8px rgba(0,0,0,.15)',
          }}>
            {initials}
          </div>

          <div style={{ paddingTop: 44, display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', flexWrap: 'wrap', gap: '.5rem' }}>
            <div>
              <div style={{ fontSize: '1.05rem', fontWeight: 700, color: '#0f172a' }}>
                {profile.fullName || username}
              </div>
              <div style={{ fontSize: '.82rem', color: '#64748b', marginTop: 2 }}>
                @{username}
                {profile.email && (
                  <span style={{ marginLeft: '.75rem' }}>
                    <i className="fa-solid fa-envelope" style={{ marginRight: '.3rem', opacity: .6 }} />
                    {profile.email}
                  </span>
                )}
              </div>
            </div>
            <div style={{ display: 'flex', gap: '.4rem', flexWrap: 'wrap' }}>
              {roles.map(r => {
                const c = ROLE_COLORS[r] || { bg: '#f1f5f9', color: '#334155' };
                return (
                  <span key={r} className="badge" style={{ background: c.bg, color: c.color }}>
                    <i className="fa-solid fa-shield-halved" style={{ fontSize: '.6rem' }} />
                    {r.replace('_', ' ')}
                  </span>
                );
              })}
            </div>
          </div>
        </div>
      </div>

      {/* ── Two-column area ─────────────────────────────────────────── */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.25rem' }}>

        {/* Profile info */}
        <div className="card">
          <div className="card-header">
            <span><i className="fa-solid fa-user" style={{ marginRight: '.5rem', color: '#3b82f6' }} />Profile Information</span>
          </div>
          <div className="card-body">
            <form onSubmit={saveProfile}>
              <div className="form-group">
                <label className="form-label">Username</label>
                <input className="form-control" value={username} disabled
                  style={{ background: '#f8fafc', cursor: 'not-allowed', color: '#64748b' }} />
                <div className="form-hint">Username cannot be changed</div>
              </div>
              <div className="form-group">
                <label className="form-label">Full Name</label>
                <input
                  className="form-control"
                  value={profile.fullName}
                  onChange={e => setProfile(p => ({ ...p, fullName: e.target.value }))}
                  placeholder="Your full name"
                />
              </div>
              <div className="form-group" style={{ marginBottom: '1.25rem' }}>
                <label className="form-label">Email Address</label>
                <input
                  className="form-control"
                  type="email"
                  value={profile.email}
                  onChange={e => setProfile(p => ({ ...p, email: e.target.value }))}
                  placeholder="your@email.com"
                />
              </div>
              <button
                className="btn btn-primary"
                type="submit"
                disabled={profileSaving || !profileLoaded}
                style={{ width: '100%', justifyContent: 'center' }}
              >
                {profileSaving
                  ? <><span className="spinner" style={{ width: 14, height: 14, borderWidth: 2 }} /> Saving…</>
                  : <><i className="fa-solid fa-floppy-disk" /> Save Changes</>}
              </button>
            </form>
          </div>
        </div>

        {/* Change password */}
        <div className="card">
          <div className="card-header">
            <span><i className="fa-solid fa-lock" style={{ marginRight: '.5rem', color: '#3b82f6' }} />Change Password</span>
          </div>
          <div className="card-body">
            <form onSubmit={savePassword}>
              {['current', 'next', 'confirm'].map((field) => {
                const labels = { current: 'Current Password', next: 'New Password', confirm: 'Confirm New Password' };
                const placeholders = { current: 'Enter current password', next: 'At least 8 characters', confirm: 'Repeat new password' };
                return (
                  <div className="form-group" key={field}>
                    <label className="form-label">{labels[field]}</label>
                    <div style={{ position: 'relative' }}>
                      <input
                        className="form-control"
                        type={showPwd[field] ? 'text' : 'password'}
                        value={pwd[field]}
                        onChange={e => setPwd(p => ({ ...p, [field]: e.target.value }))}
                        placeholder={placeholders[field]}
                        required
                        style={{ paddingRight: '2.4rem' }}
                      />
                      <button
                        type="button"
                        onClick={() => togglePwd(field)}
                        style={{
                          position: 'absolute', right: '.6rem', top: '50%', transform: 'translateY(-50%)',
                          background: 'none', border: 'none', color: '#94a3b8', padding: 0, fontSize: '.85rem',
                        }}
                      >
                        <i className={`fa-solid ${showPwd[field] ? 'fa-eye-slash' : 'fa-eye'}`} />
                      </button>
                    </div>
                  </div>
                );
              })}
              <button
                className="btn btn-primary"
                type="submit"
                disabled={pwdSaving}
                style={{ width: '100%', justifyContent: 'center', marginTop: '.25rem' }}
              >
                {pwdSaving
                  ? <><span className="spinner" style={{ width: 14, height: 14, borderWidth: 2 }} /> Changing…</>
                  : <><i className="fa-solid fa-key" /> Change Password</>}
              </button>
            </form>
          </div>
        </div>

      </div>

      {/* ── Branding & Appearance ────────────────────────────────────── */}
      <div className="card">
        <div className="card-header">
          <span><i className="fa-solid fa-palette" style={{ marginRight: '.5rem', color: '#3b82f6' }} />Branding &amp; Appearance</span>
        </div>
        <div className="card-body">
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem' }}>

            {/* Settings column */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>

              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">Company / App Name</label>
                <input
                  className="form-control"
                  value={draft.brandName}
                  onChange={e => setDraft(d => ({ ...d, brandName: e.target.value }))}
                  placeholder="API Gateway"
                />
              </div>

              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">
                  Logo URL <span className="text-muted text-sm">(blank = default icon)</span>
                </label>
                <input
                  className="form-control"
                  value={draft.brandLogo}
                  onChange={e => setDraft(d => ({ ...d, brandLogo: e.target.value }))}
                  placeholder="https://example.com/logo.png"
                />
              </div>

              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">Login Background</label>
                <div style={{ display: 'flex', alignItems: 'center', gap: '.75rem' }}>
                  <div>
                    <div className="form-hint" style={{ marginBottom: '.25rem' }}>From</div>
                    <input
                      type="color"
                      value={draft.loginBgFrom}
                      onChange={e => setDraft(d => ({ ...d, loginBgFrom: e.target.value }))}
                      style={{ width: 44, height: 34, padding: 2, border: '1px solid var(--border)', borderRadius: 6, cursor: 'pointer', background: 'none' }}
                    />
                  </div>
                  <i className="fa-solid fa-arrow-right" style={{ color: 'var(--muted)', marginTop: '1rem' }} />
                  <div>
                    <div className="form-hint" style={{ marginBottom: '.25rem' }}>To</div>
                    <input
                      type="color"
                      value={draft.loginBgTo}
                      onChange={e => setDraft(d => ({ ...d, loginBgTo: e.target.value }))}
                      style={{ width: 44, height: 34, padding: 2, border: '1px solid var(--border)', borderRadius: 6, cursor: 'pointer', background: 'none' }}
                    />
                  </div>
                </div>
              </div>

              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">Accent Color</label>
                <div style={{ display: 'flex', alignItems: 'center', gap: '.75rem' }}>
                  <input
                    type="color"
                    value={draft.accentColor}
                    onChange={e => setDraft(d => ({ ...d, accentColor: e.target.value }))}
                    style={{ width: 44, height: 34, padding: 2, border: '1px solid var(--border)', borderRadius: 6, cursor: 'pointer', background: 'none' }}
                  />
                  <span className="text-muted text-sm">Buttons, active nav, highlights</span>
                </div>
              </div>

              <div style={{ display: 'flex', gap: '.5rem', marginTop: '.25rem' }}>
                <button
                  className="btn btn-primary"
                  onClick={() => { updateBrand(draft); toast.success('Branding applied'); }}
                >
                  <i className="fa-solid fa-floppy-disk" /> Apply
                </button>
                <button
                  className="btn btn-secondary"
                  onClick={() => { resetBrand(); setDraft({ ...BRAND_DEFAULTS }); toast.success('Reset to defaults'); }}
                >
                  <i className="fa-solid fa-rotate-left" /> Reset
                </button>
              </div>
            </div>

            {/* Live preview column */}
            <div>
              <div className="form-label" style={{ marginBottom: '.75rem' }}>Preview</div>
              <div style={{ borderRadius: 10, overflow: 'hidden', boxShadow: '0 4px 20px rgba(0,0,0,.2)', maxWidth: 220, margin: '0 auto' }}>
                {/* Login header preview */}
                <div style={{
                  background: `linear-gradient(135deg, ${draft.accentColor}, ${draft.loginBgFrom})`,
                  padding: '1rem', textAlign: 'center', color: '#fff',
                }}>
                  {draft.brandLogo
                    ? <img src={draft.brandLogo} alt="" style={{ height: 28, objectFit: 'contain', display: 'block', margin: '0 auto .3rem' }} onError={e => { e.target.style.display = 'none'; }} />
                    : <i className="fa-solid fa-network-wired" style={{ fontSize: '1.3rem', display: 'block', marginBottom: '.3rem' }} />
                  }
                  <div style={{ fontWeight: 700, fontSize: '.82rem' }}>{draft.brandName || 'API Gateway'}</div>
                  <div style={{ fontSize: '.65rem', opacity: .7 }}>Admin Console</div>
                </div>
                {/* Form fields mock */}
                <div style={{
                  background: `linear-gradient(135deg, ${draft.loginBgFrom} 0%, ${draft.loginBgTo} 100%)`,
                  padding: '.75rem', display: 'flex', flexDirection: 'column', gap: '.4rem',
                }}>
                  <div style={{ height: 22, borderRadius: 4, background: 'rgba(255,255,255,.15)' }} />
                  <div style={{ height: 22, borderRadius: 4, background: 'rgba(255,255,255,.15)' }} />
                  <div style={{ height: 26, borderRadius: 5, background: draft.accentColor, opacity: .9 }} />
                </div>
              </div>

              {/* Sidebar brand preview */}
              <div style={{ marginTop: '1rem', maxWidth: 220, margin: '1rem auto 0' }}>
                <div className="form-hint" style={{ marginBottom: '.4rem', textAlign: 'center' }}>Sidebar brand</div>
                <div style={{
                  background: '#0f172a', borderRadius: 8, padding: '.65rem 1rem',
                  display: 'flex', alignItems: 'center', gap: '.5rem',
                }}>
                  {draft.brandLogo
                    ? <img src={draft.brandLogo} alt="" style={{ height: 18, objectFit: 'contain', flexShrink: 0 }} onError={e => { e.target.style.display = 'none'; }} />
                    : <i className="fa-solid fa-network-wired" style={{ color: draft.accentColor, fontSize: '.95rem' }} />
                  }
                  <span style={{ color: '#f1f5f9', fontWeight: 700, fontSize: '.82rem', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {draft.brandName || 'API Gateway'}
                  </span>
                </div>
              </div>
            </div>

          </div>
        </div>
      </div>

    </div>
  );
}