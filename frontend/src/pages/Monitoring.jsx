import { useCallback, useEffect, useRef, useState } from 'react';
import { API } from '../api/gateway';
import { useToast } from '../context/ToastContext';

// ── Helpers ────────────────────────────────────────────────────────────────────

function pct(val, total) {
  if (!total) return 0;
  return Math.round((val / total) * 1000) / 10;
}

function fmtMs(ms) {
  if (ms == null || ms === 0) return '—';
  return ms >= 1000 ? `${(ms / 1000).toFixed(2)}s` : `${ms}ms`;
}

function fmtNum(n) {
  if (n == null) return '0';
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000)     return `${(n / 1_000).toFixed(1)}k`;
  return String(n);
}

// ── Sub-components ─────────────────────────────────────────────────────────────

function StatCard({ label, value, sub, color }) {
  return (
    <div className="stat-card stat-card-col" style={{ borderTop: `3px solid ${color || 'var(--primary)'}` }}>
      <div className="stat-value">{value ?? '—'}</div>
      <div className="stat-label">{label}</div>
      {sub && <div className="stat-sub">{sub}</div>}
    </div>
  );
}

function Sparkline({ data = [], maxVal }) {
  if (!data.length) return <div className="sparkline-empty">No data</div>;
  const top = maxVal || Math.max(...data.map(d => d.requests || 0), 1);
  return (
    <div className="sparkline">
      {data.map((d, i) => {
        const h = Math.max(2, Math.round(((d.requests || 0) / top) * 48));
        const hasErr = (d.errors || 0) > 0;
        return (
          <div key={i} className="spark-bar-wrap" title={`${d.minute}: ${d.requests} req, ${d.errors} err`}>
            <div className="spark-bar" style={{ height: h, background: hasErr ? '#ef4444' : 'var(--primary)' }} />
          </div>
        );
      })}
    </div>
  );
}

function WindowSelect({ value, onChange, options }) {
  return (
    <select className="form-control" style={{ width: 'auto', fontSize: '.8rem' }} value={value} onChange={e => onChange(+e.target.value)}>
      {options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
    </select>
  );
}

function ApiTable({ rows = [], cols, emptyMsg }) {
  if (!rows.length) return <div className="empty-state"><i className="fa-solid fa-chart-simple" />{emptyMsg}</div>;
  return (
    <table>
      <thead>
        <tr>{cols.map(c => <th key={c.key}>{c.label}</th>)}</tr>
      </thead>
      <tbody>
        {rows.map((r, i) => (
          <tr key={i}>
            {cols.map(c => (
              <td key={c.key} className={c.cls || ''}>{c.render ? c.render(r) : r[c.key]}</td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
}

// ── Main page ──────────────────────────────────────────────────────────────────

const GW_WINDOWS  = [
  { value: 15,   label: 'Last 15 min' },
  { value: 60,   label: 'Last 1 hour' },
  { value: 360,  label: 'Last 6 hours' },
  { value: 1440, label: 'Last 24 hours' },
];
const API_WINDOWS = [
  { value: 1,  label: 'Last 1 hour' },
  { value: 6,  label: 'Last 6 hours' },
  { value: 24, label: 'Last 24 hours' },
];

const API_TABS = ['top', 'slow', 'failed'];

export default function Monitoring() {
  const toast = useToast();

  // Gateway metrics
  const [gw,        setGw]        = useState(null);
  const [gwWindow,  setGwWindow]  = useState(60);
  const [gwLoading, setGwLoading] = useState(true);

  // API metrics
  const [apiData,    setApiData]    = useState(null);
  const [apiWindow,  setApiWindow]  = useState(1);
  const [apiTab,     setApiTab]     = useState('top');
  const [apiLoading, setApiLoading] = useState(true);

  // Consumer metrics
  const [con,        setCon]        = useState(null);
  const [conWindow,  setConWindow]  = useState(1);
  const [conLoading, setConLoading] = useState(true);

  // Auto-refresh
  const [autoRefresh, setAutoRefresh] = useState(true);
  const timerRef = useRef(null);

  const loadGateway = useCallback(async () => {
    setGwLoading(true);
    try { setGw(await API.getGatewayMetrics(gwWindow)); }
    catch { toast.error('Failed to load gateway metrics'); }
    finally { setGwLoading(false); }
  }, [gwWindow]);

  const loadApis = useCallback(async () => {
    setApiLoading(true);
    try { setApiData(await API.getApiMetrics(apiWindow)); }
    catch { toast.error('Failed to load API metrics'); }
    finally { setApiLoading(false); }
  }, [apiWindow]);

  const loadConsumers = useCallback(async () => {
    setConLoading(true);
    try { setCon(await API.getConsumerMetrics(conWindow)); }
    catch { toast.error('Failed to load consumer metrics'); }
    finally { setConLoading(false); }
  }, [conWindow]);

  const loadAll = useCallback(() => {
    loadGateway();
    loadApis();
    loadConsumers();
  }, [loadGateway, loadApis, loadConsumers]);

  useEffect(() => { loadGateway(); }, [loadGateway]);
  useEffect(() => { loadApis();    }, [loadApis]);
  useEffect(() => { loadConsumers(); }, [loadConsumers]);

  useEffect(() => {
    if (timerRef.current) clearInterval(timerRef.current);
    if (autoRefresh) {
      timerRef.current = setInterval(loadAll, 10_000);
    }
    return () => { if (timerRef.current) clearInterval(timerRef.current); };
  }, [autoRefresh, loadAll]);

  // ── Derived values ───────────────────────────────────────────────────────────

  const timeline = gw?.timeline || [];
  const maxReqs  = Math.max(...timeline.map(d => d.requests || 0), 1);

  const methodBadge = m => {
    const cls = { GET: 'method-GET', POST: 'method-POST', PUT: 'method-PUT', DELETE: 'method-DELETE' };
    return <span className={`method-badge ${cls[m] || 'method-badge'}`}>{m || '—'}</span>;
  };

  const topApiCols = [
    { key: 'path',   label: 'Path',      cls: 'font-mono', render: r => <span style={{ fontSize: '.82rem' }}>{r.path}</span> },
    { key: 'method', label: 'Method',    render: r => methodBadge(r.method) },
    { key: 'requests', label: 'Requests', render: r => fmtNum(r.requests) },
    { key: 'errors',  label: 'Errors',   render: r => <span style={{ color: r.errors > 0 ? '#ef4444' : 'inherit' }}>{fmtNum(r.errors)}</span> },
    { key: 'error_rate', label: 'Error %', render: r => `${r.error_rate ?? 0}%` },
    { key: 'avg_latency_ms', label: 'Avg Latency', render: r => fmtMs(r.avg_latency_ms) },
  ];

  const slowApiCols = [
    { key: 'path',   label: 'Path',      cls: 'font-mono', render: r => <span style={{ fontSize: '.82rem' }}>{r.path}</span> },
    { key: 'method', label: 'Method',    render: r => methodBadge(r.method) },
    { key: 'avg_latency_ms', label: 'Avg Latency', render: r => <span style={{ color: r.avg_latency_ms > 1000 ? '#ef4444' : r.avg_latency_ms > 500 ? '#f59e0b' : 'inherit' }}>{fmtMs(r.avg_latency_ms)}</span> },
    { key: 'requests', label: 'Requests', render: r => fmtNum(r.requests) },
  ];

  const failedApiCols = [
    { key: 'path',   label: 'Path',      cls: 'font-mono', render: r => <span style={{ fontSize: '.82rem' }}>{r.path}</span> },
    { key: 'method', label: 'Method',    render: r => methodBadge(r.method) },
    { key: 'errors', label: 'Errors',    render: r => <span style={{ color: '#ef4444', fontWeight: 600 }}>{fmtNum(r.errors)}</span> },
    { key: 'error_rate', label: 'Error %', render: r => <span style={{ color: r.error_rate > 50 ? '#ef4444' : '#f59e0b' }}>{r.error_rate ?? 0}%</span> },
    { key: 'requests', label: 'Total',   render: r => fmtNum(r.requests) },
  ];

  // Render top consumers with rank index
  const topConRows = (con?.top_consumers || []).map((r, i) => ({ ...r, _rank: i + 1 }));
  const conColsWithRank = [
    { key: '_rank',      label: '#',           render: r => r._rank },
    { key: 'consumer_id', label: 'Consumer',   cls: 'font-mono', render: r => <span style={{ fontSize: '.82rem' }}>{r.consumer_id}</span> },
    { key: 'requests',   label: 'Requests',    render: r => fmtNum(r.requests) },
  ];

  return (
    <div>

      {/* ── Toolbar ─────────────────────────────────────────────────────────── */}
      <div className="filter-bar">
        <span style={{ fontSize: '.85rem', color: 'var(--muted)', fontWeight: 500 }}>
          <i className="fa-solid fa-chart-line" style={{ marginRight: '.4rem' }} />
          Live Monitoring
        </span>
        <div className="ms-auto" style={{ display: 'flex', gap: '.5rem', alignItems: 'center' }}>
          <label style={{ display: 'flex', alignItems: 'center', gap: '.35rem', fontSize: '.8rem', color: 'var(--muted)', cursor: 'pointer' }}>
            <input type="checkbox" checked={autoRefresh} onChange={e => setAutoRefresh(e.target.checked)} />
            Auto-refresh (10s)
          </label>
          <button className="btn btn-secondary btn-sm" onClick={loadAll}>
            <i className="fa-solid fa-arrows-rotate" /> Refresh
          </button>
        </div>
      </div>

      {/* ── Gateway Metrics ──────────────────────────────────────────────────── */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '.75rem' }}>
        <h3 style={{ margin: 0, fontSize: '1rem', fontWeight: 700 }}>
          <i className="fa-solid fa-server" style={{ marginRight: '.5rem', color: 'var(--primary)' }} />
          Gateway Metrics
        </h3>
        <WindowSelect value={gwWindow} onChange={setGwWindow} options={GW_WINDOWS} />
      </div>

      {gwLoading && !gw
        ? <div className="loading-center"><div className="spinner" /></div>
        : (
          <>
            <div className="stats-grid">
              <StatCard label="Requests / sec"   value={gw?.rps ?? '—'}           color="#6366f1" />
              <StatCard label="Total Requests"   value={fmtNum(gw?.total_requests)} color="#3b82f6" />
              <StatCard label="Success Rate"     value={`${gw?.success_rate ?? '—'}%`} color="#22c55e"
                sub={`${fmtNum(gw?.success_count)} OK`} />
              <StatCard label="Error Rate"       value={`${gw?.error_rate ?? '—'}%`}  color="#ef4444"
                sub={`${fmtNum(gw?.error_count)} errors`} />
              <StatCard label="P95 Latency"      value={fmtMs(gw?.p95_latency_ms)} color="#f59e0b" />
              <StatCard label="P99 Latency"      value={fmtMs(gw?.p99_latency_ms)} color="#ec4899" />
            </div>

            {/* Timeline sparkline */}
            {timeline.length > 0 && (
              <div className="card" style={{ marginBottom: '1.5rem', padding: '1rem 1.25rem' }}>
                <div style={{ fontSize: '.75rem', color: 'var(--muted)', marginBottom: '.5rem', fontWeight: 600 }}>
                  REQUEST VOLUME (per minute)
                </div>
                <Sparkline data={timeline} maxVal={maxReqs} />
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '.68rem', color: 'var(--muted)', marginTop: '.25rem' }}>
                  <span>{timeline[0]?.minute}</span>
                  <span>{timeline[timeline.length - 1]?.minute}</span>
                </div>
              </div>
            )}
          </>
        )
      }

      {/* ── API Metrics ──────────────────────────────────────────────────────── */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', margin: '1.5rem 0 .75rem' }}>
        <h3 style={{ margin: 0, fontSize: '1rem', fontWeight: 700 }}>
          <i className="fa-solid fa-route" style={{ marginRight: '.5rem', color: '#6366f1' }} />
          API Metrics
        </h3>
        <WindowSelect value={apiWindow} onChange={setApiWindow} options={API_WINDOWS} />
      </div>

      <div className="card" style={{ marginBottom: '1.5rem' }}>
        {/* Tabs */}
        <div style={{ display: 'flex', gap: 0, borderBottom: '1px solid var(--border)', padding: '0 1rem' }}>
          {[
            { key: 'top',    label: 'Top APIs',    icon: 'fa-trophy' },
            { key: 'slow',   label: 'Slow APIs',   icon: 'fa-clock' },
            { key: 'failed', label: 'Failed APIs', icon: 'fa-triangle-exclamation' },
          ].map(t => (
            <button
              key={t.key}
              onClick={() => setApiTab(t.key)}
              style={{
                background: 'none', border: 'none', padding: '.75rem 1rem',
                fontSize: '.82rem', fontWeight: 600, cursor: 'pointer',
                color: apiTab === t.key ? 'var(--primary)' : 'var(--muted)',
                borderBottom: apiTab === t.key ? '2px solid var(--primary)' : '2px solid transparent',
                marginBottom: -1,
              }}
            >
              <i className={`fa-solid ${t.icon}`} style={{ marginRight: '.35rem' }} />
              {t.label}
              {t.key === 'failed' && (apiData?.failed_apis?.length || 0) > 0 && (
                <span className="badge badge-inact" style={{ marginLeft: '.4rem', fontSize: '.65rem' }}>
                  {apiData.failed_apis.length}
                </span>
              )}
            </button>
          ))}
        </div>

        <div className="table-wrap">
          {apiLoading && !apiData
            ? <div className="loading-center"><div className="spinner" /></div>
            : apiTab === 'top'
              ? <ApiTable rows={apiData?.top_apis}    cols={topApiCols}    emptyMsg="No API data yet" />
              : apiTab === 'slow'
              ? <ApiTable rows={apiData?.slow_apis}   cols={slowApiCols}   emptyMsg="No slow APIs detected" />
              : <ApiTable rows={apiData?.failed_apis} cols={failedApiCols} emptyMsg="No failed APIs" />
          }
        </div>
      </div>

      {/* ── Consumer Metrics ─────────────────────────────────────────────────── */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', margin: '1.5rem 0 .75rem' }}>
        <h3 style={{ margin: 0, fontSize: '1rem', fontWeight: 700 }}>
          <i className="fa-solid fa-users" style={{ marginRight: '.5rem', color: '#22c55e' }} />
          Consumer Metrics
        </h3>
        <WindowSelect value={conWindow} onChange={setConWindow} options={API_WINDOWS} />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem', marginBottom: '2rem' }}>

        {/* Top Consumers */}
        <div className="card">
          <div style={{ padding: '.75rem 1rem', borderBottom: '1px solid var(--border)', fontWeight: 700, fontSize: '.85rem' }}>
            <i className="fa-solid fa-ranking-star" style={{ marginRight: '.4rem', color: '#f59e0b' }} />
            Top Consumers
          </div>
          <div className="table-wrap">
            {conLoading && !con
              ? <div className="loading-center"><div className="spinner" /></div>
              : <ApiTable rows={topConRows} cols={conColsWithRank} emptyMsg="No consumer data yet" />
            }
          </div>
        </div>

        {/* Abuse Detection */}
        <div className="card">
          <div style={{ padding: '.75rem 1rem', borderBottom: '1px solid var(--border)', fontWeight: 700, fontSize: '.85rem' }}>
            <i className="fa-solid fa-shield-exclamation" style={{ marginRight: '.4rem', color: '#ef4444' }} />
            Abuse Detection
            {con?.abuse_flags?.length > 0 && (
              <span className="badge badge-inact" style={{ marginLeft: '.5rem', fontSize: '.65rem' }}>
                {con.abuse_flags.length} flagged
              </span>
            )}
          </div>
          <div className="table-wrap">
            {conLoading && !con
              ? <div className="loading-center"><div className="spinner" /></div>
              : !con?.abuse_flags?.length
                ? (
                  <div className="empty-state" style={{ color: '#22c55e' }}>
                    <i className="fa-solid fa-shield-check" />
                    No abuse detected
                    {con?.abuse_threshold && (
                      <span style={{ fontSize: '.72rem', color: 'var(--muted)', marginTop: '.25rem', display: 'block' }}>
                        Threshold: {con.abuse_threshold} req/min
                      </span>
                    )}
                  </div>
                )
                : (
                  <table>
                    <thead>
                      <tr>
                        <th>Consumer</th>
                        <th>Req / min</th>
                        <th>Threshold</th>
                        <th>Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      {con.abuse_flags.map((f, i) => (
                        <tr key={i}>
                          <td className="font-mono" style={{ fontSize: '.82rem' }}>{f.consumer_id}</td>
                          <td style={{ color: '#ef4444', fontWeight: 700 }}>{fmtNum(f.requests_per_minute)}</td>
                          <td className="text-muted text-sm">{f.threshold}</td>
                          <td>
                            <span className="badge badge-inact">
                              <i className="fa-solid fa-triangle-exclamation" style={{ marginRight: '.3rem' }} />
                              Flagged
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )
            }
          </div>
        </div>
      </div>
    </div>
  );
}
