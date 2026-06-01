import { useEffect, useState } from 'react';
import { API } from '../api/gateway';
import { useToast } from '../context/ToastContext';

const STATUSES = ['UP', 'DOWN', 'OUT_OF_SERVICE'];
const sBtnStyle = s => ({
  UP:              { border:'1px solid #86efac', color:'#16a34a' },
  DOWN:            { border:'1px solid #fca5a5', color:'#dc2626' },
  OUT_OF_SERVICE:  { border:'1px solid #fde68a', color:'#d97706' },
})[s] || {};

const fmt = dt => dt ? new Date(dt).toLocaleString() : 'Never';

export default function Health() {
  const toast = useToast();
  const [data,     setData]     = useState(null);
  const [loading,  setLoading]  = useState(true);
  const [checking, setChecking] = useState(false);

  const load = async () => {
    setLoading(true);
    try { setData(await API.getInstances()); }
    catch { toast.error('Failed to load health data'); }
    finally { setLoading(false); }
  };

  useEffect(() => { load(); }, []);

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
    try { await API.updateInstanceStatus(id, healthStatus); toast.success(`Instance → ${healthStatus}`); load(); }
    catch { toast.error('Status update failed'); }
  };

  const instances = data?.instances || [];
  const byService = instances.reduce((acc, i) => {
    const k = i.serviceId || 'UNKNOWN';
    (acc[k] = acc[k] || []).push(i);
    return acc;
  }, {});

  return (
    <div>
      <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center', marginBottom:'1rem' }}>
        <span className="text-muted text-sm">Probe interval: 30 s &bull; Timeout: 3 s</span>
        <button className="btn btn-primary btn-sm" onClick={triggerCheck} disabled={checking}>
          {checking ? <><div className="spinner" style={{width:14,height:14,borderWidth:2}} /> Checking…</> : <><i className="fa-solid fa-stethoscope" /> Run Health Check</>}
        </button>
      </div>

      {loading ? <div className="loading-center"><div className="spinner" /></div> :
       instances.length === 0 ? <div className="empty-state"><i className="fa-solid fa-heart-pulse" />No service instances registered</div> :
       Object.entries(byService).map(([svc, nodes]) => (
        <div key={svc} className="card" style={{ marginBottom: '1rem' }}>
          <div className="card-header">
            <span><i className="fa-solid fa-server" style={{color:'#3b82f6',marginRight:'.5rem'}} />{svc}</span>
            <span className="text-muted text-sm">{nodes.filter(n=>n.healthStatus==='UP').length}/{nodes.length} UP</span>
          </div>
          <div className="card-body">
            <div className="instance-grid">
              {nodes.map(n => (
                <div key={n.id} className="instance-card">
                  <div style={{ display:'flex', justifyContent:'space-between', alignItems:'flex-start', marginBottom:'.5rem' }}>
                    <span className="instance-host">{n.host}:{n.port}{n.secure?' 🔒':''}</span>
                    <span className={`badge badge-${(n.healthStatus||'').toLowerCase() === 'up' ? 'up' : (n.healthStatus||'').toLowerCase() === 'down' ? 'down' : 'oos'}`}>{n.healthStatus||'—'}</span>
                  </div>
                  <div className="text-muted text-sm" style={{ marginBottom:'.6rem' }}>
                    <i className="fa-regular fa-clock" style={{marginRight:'.3rem'}} />
                    {fmt(n.lastHealthCheck)}
                    {n.weight > 1 && ` · weight ${n.weight}`}
                  </div>
                  <div style={{ display:'flex', gap:'.3rem', flexWrap:'wrap' }}>
                    {STATUSES.map(s => (
                      <button key={s}
                        onClick={() => setStatus(n.id, s)}
                        disabled={n.healthStatus === s}
                        style={{ fontSize:'.7rem', padding:'.15em .5em', borderRadius:'.3rem', background:'#fff', cursor:'pointer', opacity: n.healthStatus===s ? .45 : 1, ...sBtnStyle(s) }}>
                        {s}
                      </button>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}
