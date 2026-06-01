import { useState, useEffect } from 'react';
import { API } from '../api/gateway';
import { auth } from '../auth';
import { useToast } from '../context/ToastContext';

const ROLE_COLORS = {
  SUPER_ADMIN: { bg: '#ede9fe', color: '#5b21b6' },
  OPERATOR:    { bg: '#dbeafe', color: '#1e40af' },
  VIEWER:      { bg: '#f0fdf4', color: '#166534' },
};

export default function Profile() {
  const toast = useToast();

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
    </div>
  );
}