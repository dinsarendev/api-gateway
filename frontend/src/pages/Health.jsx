import { useEffect, useState } from 'react';
import { API } from '../api/gateway';
import Modal from '../components/Modal';
import { useToast } from '../context/ToastContext';
import { useAuth, PERMS } from '../context/AuthContext';

const STATUSES = ['UP', 'DOWN', 'OUT_OF_SERVICE'];

const statusStyle = s => ({
  UP:             { border: '1px solid #86efac', color: '#16a34a' },
  DOWN:           { border: '1px solid #fca5a5', color: '#dc2626' },
  OUT_OF_SERVICE: { border: '1px solid #fde68a', color: '#d97706' },
})[s] || {};

const badgeClass = s =>
  s === 'UP' ? 'up' : s === 'DOWN' ? 'down' : 'oos';

const fmt = dt => dt ? new Date(dt).toLocaleString() : 'Never';

const EMPTY_FORM = {
  service_id: '', host: '', port: '', secure: false, weight: 1,
  health_path: '/actuator/health',
};

export default function Health() {
  const toast = useToast();
  const { can } = useAuth();
  const canWrite = can(PERMS.ROUTE_WRITE);

  const [data,     setData]     = useState(null);
  const [loading,  setLoading]  = useState(true);
  const [checking, setChecking] = useState(false);

  // modal
  const [modal,   setModal]   = useState(false);
  const [editId,  setEditId]  = useState(null);
  const [form,    setForm]    = useState(EMPTY_FORM);
  const [saving,  setSaving]  = useState(false);

  const load = async () => {
    setLoading(true);
    try { setData(await API.getInstances()); }
    catch { toast.error('Failed to load health data'); }
    finally { setLoading(false); }
  };

  useEffect(() => { load(); }, []);

  // ── Health check ──────────────────────────────────────────────────────────
  const triggerCheck = async () => {
    setChecking(true);
    try {
      const result = await API.triggerCheck();
      toast.success(`Check complete: ${result.up}/${result.total} UP`);
      setData(result);
    } catch { toast.error('Health check failed'); }
    finally { setChecking(false); }
  };

  const setStatus = async (id, healthStatus) => {
    try { await API.updateInstanceStatus(id, healthStatus); toast.success(`→ ${healthStatus}`); load(); }
    catch { toast.error('Status update failed'); }
  };

  // ── CRUD modal ────────────────────────────────────────────────────────────
  const f = (key, val) => setForm(prev => ({ ...prev, [key]: val }));

  const openCreate = () => {
    setEditId(null);
    setForm(EMPTY_FORM);
    setModal(true);
  };

  const openEdit = n => {
    setEditId(n.id);
    setForm({
      service_id:  n.serviceId  || '',
      host:        n.host       || '',
      port:        n.port       ?? '',
      secure:      n.secure     ?? false,
      weight:      n.weight     ?? 1,
      health_path: n.healthPath ?? '/actuator/health',
    });
    setModal(true);
  };

  const closeModal = () => { setModal(false); setSaving(false); };

  const save = async () => {
    if (!form.service_id.trim()) return toast.warn('Service ID is required.');
    if (!form.host.trim())       return toast.warn('Host is required.');
    if (!form.port)              return toast.warn('Port is required.');

    const payload = {
      service_id:  form.service_id.trim().toUpperCase(),
      host:        form.host.trim(),
      port:        parseInt(form.port, 10),
      secure:      form.secure,
      weight:      parseInt(form.weight, 10) || 1,
      // empty string = no health probe (always UP)
      health_path: form.health_path.trim(),
    };

    setSaving(true);
    try {
      if (editId) {
        await API.updateInstance(editId, payload);
        toast.success('Instance updated');
      } else {
        await API.createInstance(payload);
        toast.success('Instance added');
      }
      closeModal();
      load();
    } catch (e) {
      const msg = e?.message || JSON.stringify(e);
      toast.error('Save failed: ' + msg);
    } finally {
      setSaving(false);
    }
  };

  const remove = async id => {
    if (!window.confirm(`Remove instance #${id}?`)) return;
    try { await API.deleteInstance(id); toast.success('Instance removed'); load(); }
    catch { toast.error('Delete failed'); }
  };

  // ── Group by service ──────────────────────────────────────────────────────
  const instances = data?.instances || [];
  const byService = instances.reduce((acc, i) => {
    const k = i.serviceId || 'UNKNOWN';
    (acc[k] = acc[k] || []).push(i);
    return acc;
  }, {});

  return (
    <div>
      {/* Toolbar */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
        <span className="text-muted text-sm">Probe interval: 30 s &bull; Timeout: 3 s &bull; Empty health path = no probe (always UP)</span>
        <div style={{ display: 'flex', gap: '.5rem' }}>
          <button className="btn btn-primary btn-sm" onClick={triggerCheck} disabled={checking}>
            {checking
              ? <><div className="spinner" style={{ width: 14, height: 14, borderWidth: 2 }} /> Checking…</>
              : <><i className="fa-solid fa-stethoscope" /> Run Health Check</>}
          </button>
          {canWrite && (
            <button className="btn btn-primary btn-sm" onClick={openCreate}>
              <i className="fa-solid fa-plus" /> Add Instance
            </button>
          )}
        </div>
      </div>

      {/* Instance list */}
      {loading
        ? <div className="loading-center"><div className="spinner" /></div>
        : instances.length === 0
          ? <div className="empty-state"><i className="fa-solid fa-heart-pulse" />No service instances registered</div>
          : Object.entries(byService).map(([svc, nodes]) => (
            <div key={svc} className="card" style={{ marginBottom: '1rem' }}>
              <div className="card-header">
                <span><i className="fa-solid fa-server" style={{ color: '#3b82f6', marginRight: '.5rem' }} />{svc}</span>
                <span className="text-muted text-sm">
                  {nodes.filter(n => n.healthStatus === 'UP').length}/{nodes.length} UP
                </span>
              </div>
              <div className="card-body">
                <div className="instance-grid">
                  {nodes.map(n => (
                    <div key={n.id} className="instance-card">
                      {/* Header row */}
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '.5rem' }}>
                        <span className="instance-host">{n.host}:{n.port}{n.secure ? ' 🔒' : ''}</span>
                        <span className={`badge badge-${badgeClass(n.healthStatus)}`}>{n.healthStatus || '—'}</span>
                      </div>

                      {/* Meta */}
                      <div className="text-muted text-sm" style={{ marginBottom: '.3rem' }}>
                        <i className="fa-regular fa-clock" style={{ marginRight: '.3rem' }} />
                        {fmt(n.lastHealthCheck)}
                        {n.weight > 1 && ` · weight ${n.weight}`}
                      </div>
                      <div className="text-muted text-sm" style={{ marginBottom: '.6rem' }}>
                        <i className="fa-solid fa-stethoscope" style={{ marginRight: '.3rem' }} />
                        {n.healthPath ? n.healthPath : <em>no probe (always UP)</em>}
                      </div>

                      {/* Status buttons */}
                      <div style={{ display: 'flex', gap: '.3rem', flexWrap: 'wrap', marginBottom: '.4rem' }}>
                        {STATUSES.map(s => (
                          <button key={s}
                            onClick={() => setStatus(n.id, s)}
                            disabled={n.healthStatus === s}
                            style={{ fontSize: '.7rem', padding: '.15em .5em', borderRadius: '.3rem', background: '#fff', cursor: 'pointer', opacity: n.healthStatus === s ? .45 : 1, ...statusStyle(s) }}>
                            {s}
                          </button>
                        ))}
                      </div>

                      {/* Edit / Delete */}
                      {canWrite && (
                        <div style={{ display: 'flex', gap: '.3rem' }}>
                          <button className="btn-action" title="Edit" onClick={() => openEdit(n)}>
                            <i className="fa-solid fa-pen" />
                          </button>
                          <button className="btn-action danger" title="Remove" onClick={() => remove(n.id)}>
                            <i className="fa-solid fa-trash" />
                          </button>
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            </div>
          ))}

      {/* Create / Edit modal */}
      <Modal
        show={modal}
        onClose={closeModal}
        title={editId ? `Edit Instance #${editId}` : 'Add Service Instance'}
        size="md"
        footer={
          <>
            <button className="btn btn-secondary" onClick={closeModal}>Cancel</button>
            <button className="btn btn-primary" onClick={save} disabled={saving}>
              {saving ? 'Saving…' : editId ? 'Update' : 'Add Instance'}
            </button>
          </>
        }
      >
        {/* Service ID */}
        <div className="form-group">
          <label className="form-label">Service ID <span className="text-required">*</span></label>
          <input className="form-control" value={form.service_id}
            onChange={e => f('service_id', e.target.value.toUpperCase())}
            placeholder="AUTH  (must match the group code using lb://)" />
          <div className="form-hint">Must match the group code registered with <code>lb://</code></div>
        </div>

        {/* Host + Port */}
        <div className="form-row">
          <div className="form-group" style={{ gridColumn: 'span 2' }}>
            <label className="form-label">Host <span className="text-required">*</span></label>
            <input className="form-control mono" value={form.host}
              onChange={e => f('host', e.target.value)}
              placeholder="localhost  or  192.168.1.10" />
          </div>
          <div className="form-group">
            <label className="form-label">Port <span className="text-required">*</span></label>
            <input className="form-control" type="number" min="1" max="65535" value={form.port}
              onChange={e => f('port', e.target.value)}
              placeholder="8080" />
          </div>
        </div>

        {/* Secure + Weight */}
        <div className="form-row">
          <div className="form-group">
            <label className="form-label">Secure (HTTPS)</label>
            <select className="form-select" value={form.secure ? 'true' : 'false'}
              onChange={e => f('secure', e.target.value === 'true')}>
              <option value="false">No (HTTP)</option>
              <option value="true">Yes (HTTPS)</option>
            </select>
          </div>
          <div className="form-group">
            <label className="form-label">Weight</label>
            <input className="form-control" type="number" min="1" value={form.weight}
              onChange={e => f('weight', e.target.value)} />
          </div>
        </div>

        {/* Health path */}
        <div className="form-group">
          <label className="form-label">Health Check Path</label>
          <input className="form-control mono" value={form.health_path}
            onChange={e => f('health_path', e.target.value)}
            placeholder="/actuator/health  (leave empty to skip probing)" />
          <div className="form-hint">
            <i className="fa-solid fa-circle-info" style={{ marginRight: '.3rem' }} />
            Leave <strong>empty</strong> to skip health probing — instance is always treated as UP.
            Useful for local/test services without an actuator endpoint.
          </div>
        </div>
      </Modal>
    </div>
  );
}
