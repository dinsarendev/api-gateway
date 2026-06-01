import { useEffect, useMemo, useState } from 'react';
import { API } from '../api/gateway';
import Modal from '../components/Modal';
import { useToast } from '../context/ToastContext';
import { useAuth, PERMS } from '../context/AuthContext';

const EMPTY = { type: 'BLACKLIST', ip_cidr: '', scope: 'GLOBAL', scope_id: '', description: '' };

const SCOPE_LABELS = { GLOBAL: 'All Routes', GROUP: 'Service Group', ROUTE: 'Route ID' };

export default function IpAccessControl() {
  const toast    = useToast();
  const { can }  = useAuth();
  const canWrite = can(PERMS.SECURITY_WRITE);
  const [rules,   setRules]   = useState([]);
  const [loading, setLoading] = useState(true);
  const [modal,   setModal]   = useState(false);
  const [form,    setForm]    = useState(EMPTY);
  const [saving,  setSaving]  = useState(false);
  const [typeFilter, setTypeFilter] = useState('');
  const [search, setSearch] = useState('');

  const load = async () => {
    setLoading(true);
    try { setRules(await API.getIpAcl()); }
    catch { toast.error('Failed to load IP rules'); }
    finally { setLoading(false); }
  };

  useEffect(() => { load(); }, []);

  const filtered = useMemo(() =>
    rules.filter(r =>
      (!typeFilter || r.type === typeFilter) &&
      (!search || r.ip_cidr?.includes(search) || r.scope_id?.toLowerCase().includes(search.toLowerCase()))
    ), [rules, typeFilter, search]
  );

  const f = (k, v) => setForm(p => ({ ...p, [k]: v }));

  const save = async () => {
    if (!form.ip_cidr.trim()) return toast.warn('IP / CIDR is required.');
    setSaving(true);
    try {
      await API.createIpAcl(form);
      toast.success('IP rule created');
      setModal(false);
      load();
    } catch(e) { toast.error('Failed: ' + (e.message || JSON.stringify(e))); }
    finally { setSaving(false); }
  };

  const remove = async (id) => {
    if (!window.confirm('Delete this IP rule?')) return;
    try { await API.deleteIpAcl(id); toast.success('Rule deleted'); load(); }
    catch { toast.error('Delete failed'); }
  };

  return (
    <div>
      <div className="filter-bar">
        <select className="form-select" style={{ width: 'auto' }} value={typeFilter}
          onChange={e => setTypeFilter(e.target.value)}>
          <option value="">All Types</option>
          <option value="BLACKLIST">Blacklist</option>
          <option value="WHITELIST">Whitelist</option>
        </select>
        <input className="form-control" style={{ width: '200px' }} placeholder="Search IP or scope…"
          value={search} onChange={e => setSearch(e.target.value)} />
        {canWrite && (
          <div className="ms-auto">
            <button className="btn btn-primary btn-sm" onClick={() => { setForm(EMPTY); setModal(true); }}>
              <i className="fa-solid fa-plus" /> Add Rule
            </button>
          </div>
        )}
      </div>

      <div className="card">
        <div className="table-wrap">
          {loading ? <div className="loading-center"><div className="spinner" /></div> : (
            <table>
              <thead>
                <tr><th>ID</th><th>Type</th><th>IP / CIDR</th><th>Scope</th><th>Scope Target</th><th>Description</th><th>Actions</th></tr>
              </thead>
              <tbody>
                {filtered.length === 0
                  ? <tr><td colSpan={7}><div className="empty-state"><i className="fa-solid fa-inbox" />No rules found</div></td></tr>
                  : filtered.map(r => (
                    <tr key={r.id}>
                      <td className="text-muted text-sm">#{r.id}</td>
                      <td>
                        <span className={`badge badge-${r.type === 'BLACKLIST' ? 'inact' : 'act'}`}>
                          {r.type === 'BLACKLIST' ? '⛔ Blacklist' : '✅ Whitelist'}
                        </span>
                      </td>
                      <td className="font-mono text-sm">{r.ip_cidr}</td>
                      <td className="text-sm">{SCOPE_LABELS[r.scope] || r.scope}</td>
                      <td className="text-sm text-muted">{r.scope_id || '—'}</td>
                      <td className="text-sm text-muted">{r.description || '—'}</td>
                      <td>
                        {canWrite && (
                          <button className="btn-action danger" title="Delete" onClick={() => remove(r.id)}>
                            <i className="fa-solid fa-trash" />
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
              </tbody>
            </table>
          )}
        </div>
      </div>

      <Modal show={modal} onClose={() => setModal(false)} title="Add IP Rule"
        footer={<>
          <button className="btn btn-secondary" onClick={() => setModal(false)}>Cancel</button>
          <button className="btn btn-primary" onClick={save} disabled={saving}>{saving ? 'Saving…' : 'Add Rule'}</button>
        </>}>
        <div className="form-row">
          <div className="form-group">
            <label className="form-label">Type <span className="text-required">*</span></label>
            <select className="form-select" value={form.type} onChange={e => f('type', e.target.value)}>
              <option value="BLACKLIST">Blacklist (block)</option>
              <option value="WHITELIST">Whitelist (allow only)</option>
            </select>
          </div>
          <div className="form-group" style={{ gridColumn: 'span 2' }}>
            <label className="form-label">IP / CIDR <span className="text-required">*</span></label>
            <input className="form-control mono" value={form.ip_cidr}
              onChange={e => f('ip_cidr', e.target.value)} placeholder="192.168.1.0/24 or 10.0.0.1" />
          </div>
        </div>
        <div className="form-row">
          <div className="form-group">
            <label className="form-label">Scope</label>
            <select className="form-select" value={form.scope} onChange={e => f('scope', e.target.value)}>
              <option value="GLOBAL">Global (all routes)</option>
              <option value="GROUP">Service Group</option>
              <option value="ROUTE">Route ID</option>
            </select>
          </div>
          {form.scope !== 'GLOBAL' && (
            <div className="form-group" style={{ gridColumn: 'span 2' }}>
              <label className="form-label">{form.scope === 'GROUP' ? 'Group Code' : 'Route ID'}</label>
              <input className="form-control" value={form.scope_id}
                onChange={e => f('scope_id', e.target.value)}
                placeholder={form.scope === 'GROUP' ? 'e.g. AUTH' : 'e.g. 42'} />
            </div>
          )}
        </div>
        <div className="form-group">
          <label className="form-label">Description</label>
          <input className="form-control" value={form.description}
            onChange={e => f('description', e.target.value)} placeholder="optional note" />
        </div>
      </Modal>
    </div>
  );
}