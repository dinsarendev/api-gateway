import { useCallback, useEffect, useState } from 'react';
import { API } from '../api/gateway';
import Modal from '../components/Modal';
import { useToast } from '../context/ToastContext';
import { useAuth, PERMS } from '../context/AuthContext';

// ── Constants ──────────────────────────────────────────────────────────────────

const CHANNEL_TYPES = ['EMAIL', 'TELEGRAM', 'SLACK'];
const SEVERITIES    = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];

const TYPE_META = {
  EMAIL:    { icon: 'fa-envelope',      color: '#3b82f6', label: 'Email' },
  TELEGRAM: { icon: 'fa-paper-plane',   color: '#2563eb', label: 'Telegram' },
  SLACK:    { icon: 'fa-hashtag',       color: '#7c3aed', label: 'Slack' },
};

// ── Helpers ────────────────────────────────────────────────────────────────────

const buildConfig = (type, fields) => {
  if (type === 'EMAIL') {
    const recipients = fields.recipients.split(',').map(r => r.trim()).filter(Boolean);
    return JSON.stringify({
      host:       fields.smtp_host,
      port:       parseInt(fields.smtp_port, 10) || 587,
      username:   fields.smtp_username,
      password:   fields.smtp_password,
      from:       fields.smtp_from,
      tls:        fields.smtp_tls,
      recipients,
    });
  }
  if (type === 'TELEGRAM') return JSON.stringify({ bot_token: fields.bot_token, chat_id: fields.chat_id });
  if (type === 'SLACK')    return JSON.stringify({ webhook_url: fields.webhook_url });
  return '{}';
};

const parseConfig = (type, configStr) => {
  try {
    const cfg = JSON.parse(configStr || '{}');
    if (type === 'EMAIL') return {
      smtp_host:     cfg.host       || '',
      smtp_port:     String(cfg.port ?? 587),
      smtp_username: cfg.username   || '',
      smtp_password: cfg.password   || '',
      smtp_from:     cfg.from       || '',
      smtp_tls:      cfg.tls !== false,
      recipients:    (cfg.recipients || []).join(', '),
      bot_token: '', chat_id: '', webhook_url: '',
    };
    if (type === 'TELEGRAM') return {
      recipients: '', smtp_host: '', smtp_port: '587', smtp_username: '',
      smtp_password: '', smtp_from: '', smtp_tls: true, webhook_url: '',
      bot_token: cfg.bot_token || '', chat_id: cfg.chat_id || '',
    };
    if (type === 'SLACK') return {
      recipients: '', smtp_host: '', smtp_port: '587', smtp_username: '',
      smtp_password: '', smtp_from: '', smtp_tls: true, bot_token: '', chat_id: '',
      webhook_url: cfg.webhook_url || '',
    };
  } catch { /* ignore */ }
  return EMPTY_FIELDS;
};

const EMPTY_FIELDS = {
  recipients: '', bot_token: '', chat_id: '', webhook_url: '',
  smtp_host: '', smtp_port: '587', smtp_username: '', smtp_password: '',
  smtp_from: '', smtp_tls: true,
};

const EMPTY_FORM = {
  name: '', type: 'EMAIL', enabled: true,
  minSeverity: '', onOpen: true, onAcknowledge: false, onResolve: true,
  ...EMPTY_FIELDS,
};

// ── Sub-components ─────────────────────────────────────────────────────────────

function TypeBadge({ type }) {
  const m = TYPE_META[type] || { icon: 'fa-bell', color: '#64748b', label: type };
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: '.3rem',
      background: m.color + '18', color: m.color,
      fontSize: '.72rem', fontWeight: 700, padding: '.2em .55em',
      borderRadius: '.3rem', whiteSpace: 'nowrap',
    }}>
      <i className={`fa-solid ${m.icon}`} style={{ fontSize: '.65rem' }} />
      {m.label}
    </span>
  );
}

function Toggle({ checked, onChange, label }) {
  return (
    <label style={{ display: 'flex', alignItems: 'center', gap: '.45rem', cursor: 'pointer', userSelect: 'none' }}>
      <div
        onClick={() => onChange(!checked)}
        style={{
          width: 36, height: 20, borderRadius: 10, position: 'relative', transition: 'background .2s',
          background: checked ? '#2563eb' : '#cbd5e1', cursor: 'pointer', flexShrink: 0,
        }}
      >
        <div style={{
          position: 'absolute', top: 2, left: checked ? 18 : 2,
          width: 16, height: 16, borderRadius: '50%', background: '#fff',
          transition: 'left .2s', boxShadow: '0 1px 3px rgba(0,0,0,.2)',
        }} />
      </div>
      {label && <span style={{ fontSize: '.8rem', color: 'var(--text-secondary)' }}>{label}</span>}
    </label>
  );
}

// ── Config fields per channel type ─────────────────────────────────────────────

function ConfigFields({ type, fields, onChange }) {
  const f = (k, v) => onChange({ ...fields, [k]: v });

  if (type === 'EMAIL') return (
    <>
      {/* SMTP server */}
      <div style={{
        background: 'var(--bg-surface-alt)', border: '1px solid var(--border)',
        borderRadius: '.4rem', padding: '.85rem 1rem', marginBottom: '.75rem',
      }}>
        <div style={{ fontSize: '.75rem', fontWeight: 700, color: 'var(--muted)', marginBottom: '.65rem', letterSpacing: '.04em' }}>
          SMTP SERVER
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 100px', gap: '.75rem' }}>
          <div className="form-group" style={{ margin: 0 }}>
            <label className="form-label">Host <span className="text-required">*</span></label>
            <input className="form-control" value={fields.smtp_host}
              onChange={e => f('smtp_host', e.target.value)}
              placeholder="smtp.gmail.com" />
          </div>
          <div className="form-group" style={{ margin: 0 }}>
            <label className="form-label">Port</label>
            <input className="form-control" type="number" value={fields.smtp_port}
              onChange={e => f('smtp_port', e.target.value)}
              placeholder="587" />
          </div>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '.75rem', marginTop: '.75rem' }}>
          <div className="form-group" style={{ margin: 0 }}>
            <label className="form-label">Username</label>
            <input className="form-control" value={fields.smtp_username}
              onChange={e => f('smtp_username', e.target.value)}
              placeholder="user@gmail.com" autoComplete="off" />
          </div>
          <div className="form-group" style={{ margin: 0 }}>
            <label className="form-label">Password / App Password</label>
            <input className="form-control" type="password" value={fields.smtp_password}
              onChange={e => f('smtp_password', e.target.value)}
              placeholder="••••••••••••" autoComplete="new-password" />
          </div>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr auto', gap: '.75rem', marginTop: '.75rem', alignItems: 'flex-end' }}>
          <div className="form-group" style={{ margin: 0 }}>
            <label className="form-label">From Address <span className="text-required">*</span></label>
            <input className="form-control" value={fields.smtp_from}
              onChange={e => f('smtp_from', e.target.value)}
              placeholder="noreply@example.com" />
          </div>
          <div style={{ paddingBottom: '.15rem' }}>
            <Toggle checked={fields.smtp_tls} onChange={v => f('smtp_tls', v)} label="STARTTLS" />
          </div>
        </div>
      </div>

      {/* Recipients */}
      <div className="form-group">
        <label className="form-label">Recipients <span className="text-required">*</span></label>
        <input className="form-control" value={fields.recipients}
          onChange={e => f('recipients', e.target.value)}
          placeholder="admin@example.com, ops@example.com" />
        <div style={{ fontSize: '.72rem', color: 'var(--muted)', marginTop: '.25rem' }}>
          Comma-separated — all addresses receive every alert
        </div>
      </div>
    </>
  );

  if (type === 'TELEGRAM') return (
    <>
      <div className="form-group">
        <label className="form-label">Bot Token <span className="text-required">*</span></label>
        <input
          className="form-control"
          type="password"
          value={fields.bot_token}
          onChange={e => f('bot_token', e.target.value)}
          placeholder="123456789:ABCDefGhIJKlmNoPQRsTUVwXYZ"
        />
        <div style={{ fontSize: '.72rem', color: 'var(--muted)', marginTop: '.25rem' }}>
          From @BotFather — keep this secret
        </div>
      </div>
      <div className="form-group">
        <label className="form-label">Chat ID <span className="text-required">*</span></label>
        <input
          className="form-control"
          value={fields.chat_id}
          onChange={e => f('chat_id', e.target.value)}
          placeholder="-1001234567890"
        />
        <div style={{ fontSize: '.72rem', color: 'var(--muted)', marginTop: '.25rem' }}>
          Group chat ID (negative) or user chat ID
        </div>
      </div>
    </>
  );

  if (type === 'SLACK') return (
    <div className="form-group">
      <label className="form-label">Webhook URL <span className="text-required">*</span></label>
      <input
        className="form-control"
        value={fields.webhook_url}
        onChange={e => f('webhook_url', e.target.value)}
        placeholder="https://hooks.slack.com/services/T.../B.../..."
      />
      <div style={{ fontSize: '.72rem', color: 'var(--muted)', marginTop: '.25rem' }}>
        Incoming Webhook URL from your Slack app settings
      </div>
    </div>
  );

  return null;
}

// ── Main page ──────────────────────────────────────────────────────────────────

export default function Notifications() {
  const toast    = useToast();
  const { can }  = useAuth();
  const canWrite = can(PERMS.NOTIFICATION_WRITE);

  const [channels, setChannels] = useState([]);
  const [loading,  setLoading]  = useState(true);

  // Modal state
  const [modal,    setModal]    = useState(false);
  const [editing,  setEditing]  = useState(null); // null = create, else channel object
  const [form,     setForm]     = useState(EMPTY_FORM);
  const [saving,   setSaving]   = useState(false);
  const [testing,  setTesting]  = useState(null); // id of channel being tested

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setChannels(await API.getNotificationChannels());
    } catch { toast.error('Failed to load channels'); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { load(); }, [load]);

  // ── Open modal ─────────────────────────────────────────────────────────────

  const openCreate = () => {
    setEditing(null);
    setForm(EMPTY_FORM);
    setModal(true);
  };

  const openEdit = (ch) => {
    setEditing(ch);
    const configFields = parseConfig(ch.type, ch.config);
    setForm({
      name: ch.name, type: ch.type,
      enabled: ch.enabled,
      minSeverity: ch.minSeverity || '',
      onOpen: ch.onOpen, onAcknowledge: ch.onAcknowledge, onResolve: ch.onResolve,
      ...configFields,
    });
    setModal(true);
  };

  // ── Save ───────────────────────────────────────────────────────────────────

  const save = async () => {
    if (!form.name.trim()) return toast.warn('Name is required.');
    const configFields = {
      recipients:    form.recipients,
      bot_token:     form.bot_token,
      chat_id:       form.chat_id,
      webhook_url:   form.webhook_url,
      smtp_host:     form.smtp_host,
      smtp_port:     form.smtp_port,
      smtp_username: form.smtp_username,
      smtp_password: form.smtp_password,
      smtp_from:     form.smtp_from,
      smtp_tls:      form.smtp_tls,
    };
    const config = buildConfig(form.type, configFields);

    // Basic validation
    if (form.type === 'EMAIL') {
      if (!form.smtp_host.trim())   return toast.warn('SMTP host is required.');
      if (!form.smtp_from.trim())   return toast.warn('From address is required.');
      if (!form.recipients.trim())  return toast.warn('At least one recipient is required.');
    }
    if (form.type === 'TELEGRAM' && (!form.bot_token.trim() || !form.chat_id.trim()))
      return toast.warn('Bot token and Chat ID are required.');
    if (form.type === 'SLACK' && !form.webhook_url.trim())   return toast.warn('Webhook URL is required.');

    const payload = {
      name: form.name.trim(),
      type: form.type,
      config,
      enabled:       form.enabled,
      min_severity:  form.minSeverity || null,
      on_open:       form.onOpen,
      on_acknowledge: form.onAcknowledge,
      on_resolve:    form.onResolve,
    };

    setSaving(true);
    try {
      if (editing) {
        await API.updateNotificationChannel(editing.id, payload);
        toast.success('Channel updated');
      } else {
        await API.createNotificationChannel(payload);
        toast.success('Channel created');
      }
      setModal(false);
      load();
    } catch (e) { toast.error('Failed: ' + (e.message || JSON.stringify(e))); }
    finally { setSaving(false); }
  };

  // ── Test ───────────────────────────────────────────────────────────────────

  const test = async (id) => {
    setTesting(id);
    try {
      const res = await API.testNotificationChannel(id);
      toast.success(res?.message || 'Test notification sent');
    } catch (e) { toast.error('Test failed: ' + (e.message || JSON.stringify(e))); }
    finally { setTesting(null); }
  };

  // ── Delete ─────────────────────────────────────────────────────────────────

  const del = async (id) => {
    if (!window.confirm('Delete this notification channel?')) return;
    try {
      await API.deleteNotificationChannel(id);
      toast.success('Channel deleted');
      load();
    } catch { toast.error('Failed to delete'); }
  };

  const f = (k, v) => setForm(p => ({ ...p, [k]: v }));
  const fmt = dt => dt ? new Date(dt).toLocaleString() : '—';

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <div>

      {/* ── Summary cards ────────────────────────────────────────────────────── */}
      <div className="stats-grid" style={{ marginBottom: '1.25rem' }}>
        {CHANNEL_TYPES.map(type => {
          const count   = channels.filter(c => c.type === type).length;
          const enabled = channels.filter(c => c.type === type && c.enabled).length;
          const m       = TYPE_META[type];
          return (
            <div key={type} className="stat-card stat-card-col" style={{ borderTop: `3px solid ${m.color}` }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '.5rem', marginBottom: '.25rem' }}>
                <i className={`fa-solid ${m.icon}`} style={{ color: m.color, fontSize: '.9rem' }} />
                <span className="stat-label" style={{ marginTop: 0 }}>{m.label}</span>
              </div>
              <div className="stat-value">{count}</div>
              <div className="stat-sub">{enabled} active</div>
            </div>
          );
        })}
        <div className="stat-card stat-card-col" style={{ borderTop: '3px solid #22c55e' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '.5rem', marginBottom: '.25rem' }}>
            <i className="fa-solid fa-bell" style={{ color: '#22c55e', fontSize: '.9rem' }} />
            <span className="stat-label" style={{ marginTop: 0 }}>Total Active</span>
          </div>
          <div className="stat-value">{channels.filter(c => c.enabled).length}</div>
          <div className="stat-sub">of {channels.length} channels</div>
        </div>
      </div>

      {/* ── Toolbar ──────────────────────────────────────────────────────────── */}
      <div className="filter-bar">
        <div className="ms-auto" style={{ display: 'flex', gap: '.5rem' }}>
          <button className="btn btn-secondary btn-sm" onClick={load}>
            <i className="fa-solid fa-arrows-rotate" />
          </button>
          {canWrite && (
            <button className="btn btn-primary btn-sm" onClick={openCreate}>
              <i className="fa-solid fa-plus" /> Add Channel
            </button>
          )}
        </div>
      </div>

      {/* ── Channel table ─────────────────────────────────────────────────────── */}
      <div className="card">
        <div className="table-wrap">
          {loading
            ? <div className="loading-center"><div className="spinner" /></div>
            : channels.length === 0
              ? (
                <div className="empty-state">
                  <i className="fa-solid fa-bell-slash" style={{ color: '#94a3b8' }} />
                  No notification channels configured
                </div>
              )
              : (
                <table>
                  <thead>
                    <tr>
                      <th>Name</th>
                      <th>Type</th>
                      <th>Triggers</th>
                      <th>Min Severity</th>
                      <th>Enabled</th>
                      <th>Created</th>
                      <th style={{ width: 120 }}>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {channels.map(ch => (
                      <tr key={ch.id}>
                        <td>
                          <div style={{ fontWeight: 600, fontSize: '.875rem' }}>{ch.name}</div>
                        </td>
                        <td><TypeBadge type={ch.type} /></td>
                        <td>
                          <div style={{ display: 'flex', gap: '.25rem', flexWrap: 'wrap' }}>
                            {ch.onOpen       && <TriggerPill label="Open"        color="#ef4444" />}
                            {ch.onAcknowledge && <TriggerPill label="Acknowledge" color="#f97316" />}
                            {ch.onResolve    && <TriggerPill label="Resolve"      color="#22c55e" />}
                          </div>
                        </td>
                        <td>
                          <span style={{ fontSize: '.78rem', color: 'var(--muted)' }}>
                            {ch.minSeverity || <span style={{ fontStyle: 'italic' }}>All</span>}
                          </span>
                        </td>
                        <td>
                          <span style={{
                            fontSize: '.72rem', fontWeight: 700, padding: '.15em .45em',
                            borderRadius: '.3rem',
                            background: ch.enabled ? '#dcfce7' : '#f1f5f9',
                            color: ch.enabled ? '#166534' : '#64748b',
                          }}>
                            {ch.enabled ? 'Active' : 'Disabled'}
                          </span>
                        </td>
                        <td className="text-sm text-muted">{fmt(ch.createdAt)}</td>
                        <td>
                          <div className="actions-row">
                            {canWrite && (
                              <button
                                className="btn-action"
                                title="Test"
                                onClick={() => test(ch.id)}
                                disabled={testing === ch.id}
                                style={{ color: '#6366f1' }}
                              >
                                {testing === ch.id
                                  ? <i className="fa-solid fa-spinner fa-spin" />
                                  : <i className="fa-solid fa-paper-plane" />}
                              </button>
                            )}
                            {canWrite && (
                              <button
                                className="btn-action"
                                title="Edit"
                                onClick={() => openEdit(ch)}
                                style={{ color: '#3b82f6' }}
                              >
                                <i className="fa-solid fa-pen" />
                              </button>
                            )}
                            {canWrite && (
                              <button
                                className="btn-action danger"
                                title="Delete"
                                onClick={() => del(ch.id)}
                              >
                                <i className="fa-solid fa-trash" />
                              </button>
                            )}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )
          }
        </div>
      </div>

      {/* ── Create / Edit modal ───────────────────────────────────────────────── */}
      <Modal
        show={modal}
        onClose={() => setModal(false)}
        title={editing ? 'Edit Notification Channel' : 'Add Notification Channel'}
        footer={
          <>
            <button className="btn btn-secondary" onClick={() => setModal(false)}>Cancel</button>
            <button className="btn btn-primary" onClick={save} disabled={saving}>
              {saving ? 'Saving…' : (editing ? 'Save Changes' : 'Create Channel')}
            </button>
          </>
        }
      >
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
          <div className="form-group">
            <label className="form-label">Channel Name <span className="text-required">*</span></label>
            <input className="form-control" value={form.name} onChange={e => f('name', e.target.value)}
              placeholder="e.g. Ops Team Email" />
          </div>
          <div className="form-group">
            <label className="form-label">Type <span className="text-required">*</span></label>
            <select
              className="form-control"
              value={form.type}
              onChange={e => {
                const type = e.target.value;
                setForm(p => ({ ...p, type, ...EMPTY_FIELDS }));
              }}
              disabled={!!editing}
            >
              {CHANNEL_TYPES.map(t => (
                <option key={t} value={t}>{TYPE_META[t].label}</option>
              ))}
            </select>
          </div>
        </div>

        {/* Type-specific config */}
        <ConfigFields
          type={form.type}
          fields={{ recipients: form.recipients, bot_token: form.bot_token, chat_id: form.chat_id, webhook_url: form.webhook_url }}
          onChange={fields => setForm(p => ({ ...p, ...fields }))}
        />

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem', marginTop: '.25rem' }}>
          <div className="form-group">
            <label className="form-label">Minimum Severity</label>
            <select className="form-control" value={form.minSeverity} onChange={e => f('minSeverity', e.target.value)}>
              <option value="">All severities</option>
              {SEVERITIES.map(s => <option key={s} value={s}>{s} and above</option>)}
            </select>
          </div>
          <div className="form-group" style={{ display: 'flex', alignItems: 'center', paddingTop: '1.6rem' }}>
            <Toggle checked={form.enabled} onChange={v => f('enabled', v)} label="Channel enabled" />
          </div>
        </div>

        <div style={{
          background: 'var(--bg-surface-alt)', border: '1px solid var(--border)',
          borderRadius: '.4rem', padding: '.85rem 1rem',
        }}>
          <div style={{ fontSize: '.78rem', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '.65rem' }}>
            Send notification when incident is…
          </div>
          <div style={{ display: 'flex', gap: '1.5rem', flexWrap: 'wrap' }}>
            <Toggle checked={form.onOpen}        onChange={v => f('onOpen', v)}        label="Opened" />
            <Toggle checked={form.onAcknowledge} onChange={v => f('onAcknowledge', v)} label="Acknowledged" />
            <Toggle checked={form.onResolve}     onChange={v => f('onResolve', v)}     label="Resolved" />
          </div>
        </div>
      </Modal>
    </div>
  );
}

function TriggerPill({ label, color }) {
  return (
    <span style={{
      fontSize: '.65rem', fontWeight: 700, padding: '.15em .4em',
      borderRadius: '.25rem', background: color + '18', color,
    }}>
      {label}
    </span>
  );
}
