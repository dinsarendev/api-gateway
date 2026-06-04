import { useState } from 'react';

// ── helpers ────────────────────────────────────────────────────────────────
const Section = ({ id, children }) => (
  <section id={id} style={{ marginBottom: '2.5rem' }}>{children}</section>
);

const H2 = ({ children }) => (
  <h2 style={{ fontSize: '1.05rem', fontWeight: 700, color: 'var(--text-heading)',
    borderBottom: '2px solid var(--border)', paddingBottom: '.5rem', marginBottom: '1rem', marginTop: 0 }}>
    {children}
  </h2>
);

const H3 = ({ children }) => (
  <h3 style={{ fontSize: '.9rem', fontWeight: 700, color: 'var(--text-primary)',
    marginBottom: '.6rem', marginTop: '1.25rem' }}>
    {children}
  </h3>
);

const P = ({ children, style }) => (
  <p style={{ fontSize: '.875rem', lineHeight: 1.7, color: 'var(--text-primary)', margin: '0 0 .75rem', ...style }}>
    {children}
  </p>
);

const Code = ({ children }) => (
  <code style={{ background: 'var(--bg-surface-alt)', border: '1px solid var(--border)',
    borderRadius: '.25rem', padding: '.1em .4em', fontSize: '.8rem', fontFamily: 'monospace',
    color: '#0369a1' }}>
    {children}
  </code>
);

const InfoBox = ({ color = '#dbeafe', border = '#bfdbfe', textColor = '#1e40af', icon = 'fa-circle-info', children }) => (
  <div style={{ display: 'flex', gap: '.75rem', background: color, border: `1px solid ${border}`,
    borderRadius: '.5rem', padding: '.85rem 1rem', marginBottom: '1rem', alignItems: 'flex-start' }}>
    <i className={`fa-solid ${icon}`} style={{ color: textColor, marginTop: '.15rem', flexShrink: 0 }} />
    <div style={{ fontSize: '.85rem', color: textColor, lineHeight: 1.6 }}>{children}</div>
  </div>
);

const Table = ({ cols, rows }) => (
  <div style={{ overflowX: 'auto', marginBottom: '1rem' }}>
    <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '.85rem' }}>
      <thead>
        <tr>
          {cols.map(c => (
            <th key={c} style={{ textAlign: 'left', padding: '.55rem .85rem', background: 'var(--bg-surface-alt)',
              borderBottom: '2px solid var(--border)', fontSize: '.75rem', textTransform: 'uppercase',
              letterSpacing: '.05em', color: 'var(--muted)', fontWeight: 600, whiteSpace: 'nowrap' }}>
              {c}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.map((row, i) => (
          <tr key={i} style={{ borderBottom: '1px solid var(--border-light)' }}>
            {row.map((cell, j) => (
              <td key={j} style={{ padding: '.55rem .85rem', verticalAlign: 'top',
                color: 'var(--text-primary)', fontSize: '.85rem' }}>
                {cell}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  </div>
);

const StatusPill = ({ label, bg, color, icon }) => (
  <span style={{ display: 'inline-flex', alignItems: 'center', gap: '.3rem',
    background: bg, color, borderRadius: 999, padding: '.2em .7em',
    fontSize: '.75rem', fontWeight: 600, whiteSpace: 'nowrap' }}>
    {icon && <i className={`fa-solid ${icon}`} style={{ fontSize: '.65rem' }} />}
    {label}
  </span>
);

const Arrow = ({ label }) => (
  <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '.15rem',
    padding: '0 .25rem' }}>
    <i className="fa-solid fa-arrow-right" style={{ color: 'var(--muted)', fontSize: '.8rem' }} />
    {label && <span style={{ fontSize: '.6rem', color: 'var(--muted)', whiteSpace: 'nowrap' }}>{label}</span>}
  </div>
);

const EndpointRow = ({ method, path, perm, desc }) => {
  const colors = { POST: '#dcfce7/#166534', PUT: '#fef9c3/#92400e', DELETE: '#fee2e2/#991b1b', GET: '#e0f2fe/#0369a1' };
  const [bg, fg] = (colors[method] || '#f1f5f9/#475569').split('/');
  return (
    <div style={{ display: 'flex', gap: '.75rem', alignItems: 'flex-start', padding: '.6rem 0',
      borderBottom: '1px solid var(--border-light)', flexWrap: 'wrap' }}>
      <span style={{ background: bg, color: fg, fontFamily: 'monospace', fontSize: '.72rem',
        fontWeight: 700, padding: '.2em .55em', borderRadius: '.3rem', flexShrink: 0, minWidth: 52, textAlign: 'center' }}>
        {method}
      </span>
      <code style={{ fontSize: '.82rem', fontFamily: 'monospace', color: '#0369a1',
        background: 'var(--bg-surface-alt)', padding: '.15em .5em', borderRadius: '.25rem', flexShrink: 0 }}>
        {path}
      </code>
      {perm && (
        <span style={{ fontSize: '.7rem', background: '#ede9fe', color: '#5b21b6',
          padding: '.15em .5em', borderRadius: '.25rem', fontWeight: 600, flexShrink: 0 }}>
          {perm}
        </span>
      )}
      <span style={{ fontSize: '.82rem', color: 'var(--muted)', flex: 1 }}>{desc}</span>
    </div>
  );
};

// ── TOC ─────────────────────────────────────────────────────────────────────
const TOC_ITEMS = [
  { id: 'overview',     label: 'Overview' },
  { id: 'lifecycle',    label: 'Route Lifecycle' },
  { id: 'approval',     label: 'Approval Workflow' },
  { id: 'deprecation',  label: 'Deprecation Workflow' },
  { id: 'versioning',   label: 'API Versioning' },
  { id: 'tags',         label: 'Policy Tags' },
  { id: 'sla',          label: 'SLA Tiers' },
  { id: 'documentation','label': 'Documentation Field' },
  { id: 'endpoints',    label: 'Endpoints Reference' },
  { id: 'permissions',  label: 'Permissions' },
  { id: 'security',     label: 'Security Policies' },
  { id: 'operational',  label: 'Operational Controls' },
];

// ── main ───────────────────────────────────────────────────────────────────
export default function GovernanceDocs() {
  const [activeSection, setActiveSection] = useState('overview');

  const scrollTo = id => {
    setActiveSection(id);
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  return (
    <div style={{ display: 'flex', gap: '1.5rem', alignItems: 'flex-start' }}>

      {/* TOC sidebar */}
      <div style={{ width: 200, flexShrink: 0, position: 'sticky', top: 0 }}>
        <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)',
          borderRadius: '.75rem', padding: '1rem', fontSize: '.82rem' }}>
          <div style={{ fontWeight: 700, fontSize: '.75rem', textTransform: 'uppercase',
            letterSpacing: '.07em', color: 'var(--muted)', marginBottom: '.75rem' }}>
            Contents
          </div>
          {TOC_ITEMS.map(item => (
            <button key={item.id} onClick={() => scrollTo(item.id)}
              style={{ display: 'block', width: '100%', textAlign: 'left', border: 'none',
                padding: '.35rem .5rem', borderRadius: '.35rem', cursor: 'pointer', fontSize: '.8rem',
                color: activeSection === item.id ? '#3b82f6' : 'var(--text-primary)',
                background: activeSection === item.id ? '#eff6ff' : 'transparent',
                fontWeight: activeSection === item.id ? 600 : 400 }}>
              {item.label}
            </button>
          ))}
        </div>
      </div>

      {/* Main content */}
      <div style={{ flex: 1, minWidth: 0 }}>

        {/* Page header */}
        <div style={{ background: 'linear-gradient(135deg, #1e3a5f 0%, #1d4ed8 100%)',
          borderRadius: '.75rem', padding: '1.75rem 2rem', marginBottom: '1.5rem', color: '#fff' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '.75rem', marginBottom: '.5rem' }}>
            <i className="fa-solid fa-scale-balanced" style={{ fontSize: '1.4rem', opacity: .9 }} />
            <h1 style={{ margin: 0, fontSize: '1.3rem', fontWeight: 800 }}>API Governance</h1>
          </div>
          <p style={{ margin: 0, opacity: .85, fontSize: '.9rem', lineHeight: 1.6 }}>
            Complete reference for route lifecycle management, approval workflows, policy
            enforcement, SLA tiers, and compliance controls built into the API Gateway.
          </p>
        </div>

        {/* ── Overview ── */}
        <div className="card" style={{ padding: '1.5rem', marginBottom: '1rem' }}>
          <Section id="overview">
            <H2><i className="fa-solid fa-compass" style={{ marginRight: '.5rem', color: '#3b82f6' }} />Overview</H2>
            <P>
              API Governance is a set of controls that ensure every API route deployed through this
              gateway is <strong>reviewed, approved, documented, and monitored</strong> before and
              after it serves production traffic.
            </P>
            <P>It covers four domains:</P>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1rem', marginBottom: '1rem' }}>
              {[
                { icon: 'fa-arrows-spin', color: '#3b82f6', bg: '#eff6ff', title: 'Lifecycle', text: 'DRAFT → PENDING → ACT → DEPRECATED → RETIRED with enforced transitions' },
                { icon: 'fa-shield-halved', color: '#7c3aed', bg: '#f5f3ff', title: 'Policy', text: 'Tags (PII, SENSITIVE), SLA tiers, auth requirements, and IP controls per route' },
                { icon: 'fa-book-open', color: '#0891b2', bg: '#ecfeff', title: 'Catalog', text: 'Version tracking, Markdown documentation, and header injection per route' },
                { icon: 'fa-clipboard-list', color: '#059669', bg: '#f0fdf4', title: 'Audit', text: 'Full audit trail of every create, update, approve, reject, and retire action' },
              ].map(c => (
                <div key={c.title} style={{ background: c.bg, border: `1px solid ${c.bg}`,
                  borderRadius: '.6rem', padding: '1rem' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '.5rem', marginBottom: '.5rem' }}>
                    <i className={`fa-solid ${c.icon}`} style={{ color: c.color }} />
                    <strong style={{ fontSize: '.85rem', color: c.color }}>{c.title}</strong>
                  </div>
                  <p style={{ margin: 0, fontSize: '.8rem', lineHeight: 1.5, color: '#334155' }}>{c.text}</p>
                </div>
              ))}
            </div>
          </Section>

          {/* ── Lifecycle ── */}
          <Section id="lifecycle">
            <H2><i className="fa-solid fa-arrows-spin" style={{ marginRight: '.5rem', color: '#3b82f6' }} />Route Lifecycle</H2>
            <P>Every route passes through a defined sequence of states. Only <strong>ACT</strong> and <strong>DEPRECATED</strong> routes are served by the gateway.</P>

            {/* Flow diagram */}
            <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: '.25rem',
              padding: '1.25rem 1rem', background: 'var(--bg-surface-alt)',
              borderRadius: '.6rem', marginBottom: '1.25rem', border: '1px solid var(--border)' }}>
              <StatusPill label="DRAFT"      bg="#dbeafe" color="#1e40af" icon="fa-file-pen" />
              <Arrow label="submit" />
              <StatusPill label="PENDING"    bg="#ede9fe" color="#5b21b6" icon="fa-hourglass-half" />
              <Arrow label="approve" />
              <StatusPill label="ACTIVE"     bg="#dcfce7" color="#166534" icon="fa-circle-check" />
              <Arrow label="deprecate" />
              <StatusPill label="DEPRECATED" bg="#fef3c7" color="#92400e" icon="fa-clock-rotate-left" />
              <Arrow label="retire / auto" />
              <StatusPill label="RETIRED"    bg="#f1f5f9" color="#475569" icon="fa-ban" />
            </div>
            <div style={{ display: 'flex', gap: '.5rem', flexWrap: 'wrap', marginBottom: '1rem', alignItems: 'center' }}>
              <span style={{ fontSize: '.78rem', color: 'var(--muted)' }}>Also valid:</span>
              <StatusPill label="PENDING" bg="#ede9fe" color="#5b21b6" icon="fa-hourglass-half" />
              <Arrow label="reject" />
              <StatusPill label="DRAFT" bg="#dbeafe" color="#1e40af" icon="fa-file-pen" />
              <span style={{ fontSize: '.78rem', color: 'var(--muted)', marginLeft: '.5rem' }}>and</span>
              <StatusPill label="ACTIVE" bg="#dcfce7" color="#166534" />
              <Arrow label="disable" />
              <StatusPill label="INACT" bg="#fee2e2" color="#991b1b" />
            </div>

            <Table
              cols={['Status', 'In Gateway', 'Description']}
              rows={[
                [<StatusPill label="DRAFT"      bg="#dbeafe" color="#1e40af" icon="fa-file-pen" />,      '✗', 'Created but not yet submitted. Editable. Not served by gateway.'],
                [<StatusPill label="PENDING"    bg="#ede9fe" color="#5b21b6" icon="fa-hourglass-half" />, '✗', 'Submitted for review. Awaiting ROUTE_APPROVE decision.'],
                [<StatusPill label="ACTIVE"     bg="#dcfce7" color="#166534" icon="fa-circle-check" />,   '✓', 'Approved and live. Routes traffic normally.'],
                [<StatusPill label="DEPRECATED" bg="#fef3c7" color="#92400e" icon="fa-clock-rotate-left" />,'✓ + headers', 'Serving traffic with Deprecation + Sunset response headers.'],
                [<StatusPill label="RETIRED"    bg="#f1f5f9" color="#475569" icon="fa-ban" />,            '✗', 'End-of-life. Removed from gateway. Not recoverable.'],
                [<StatusPill label="INACT"      bg="#fee2e2" color="#991b1b" />,                          '✗', 'Administratively disabled. Re-activatable.'],
              ]}
            />
          </Section>

          {/* ── Approval ── */}
          <Section id="approval">
            <H2><i className="fa-solid fa-user-check" style={{ marginRight: '.5rem', color: '#7c3aed' }} />Approval Workflow</H2>
            <InfoBox>
              New routes always start as <strong>DRAFT</strong>. They do not serve traffic until an
              approver reviews and activates them. This prevents untested or misconfigured routes
              from reaching production.
            </InfoBox>

            {[
              { n: 1, icon: 'fa-file-pen',      color: '#1d4ed8', title: 'Create (DRAFT)',         actor: 'Writer',   desc: 'Admin creates the route with path, method, group, security policy, SLA tier, tags, and documentation. Route is saved as DRAFT — not live.' },
              { n: 2, icon: 'fa-paper-plane',    color: '#7c3aed', title: 'Submit (DRAFT → PENDING)', actor: 'Writer',  desc: <>Click <strong>Submit for Approval</strong> (paper-plane icon) on the route row. The rejection reason (if any) is cleared. Status changes to PENDING.</> },
              { n: 3, icon: 'fa-magnifying-glass',color: '#0891b2', title: 'Review (PENDING)',       actor: 'Approver', desc: 'Approver views the route configuration, documentation, policy tags, and SLA tier. Approver must have the ROUTE_APPROVE permission.' },
              { n: 4, icon: 'fa-check',          color: '#16a34a', title: 'Approve (PENDING → ACT)', actor: 'Approver', desc: 'Click the green checkmark. Route immediately becomes ACTIVE and the gateway refreshes to start serving traffic.' },
              { n: 4, icon: 'fa-xmark',          color: '#dc2626', title: 'Reject (PENDING → DRAFT)', actor: 'Approver', desc: 'Click the red X, enter a rejection reason (required). Route returns to DRAFT with the reason visible to the author. Author edits and resubmits.' },
            ].map((step, i) => (
              <div key={i} style={{ display: 'flex', gap: '1rem', marginBottom: '1rem', alignItems: 'flex-start' }}>
                <div style={{ width: 32, height: 32, borderRadius: '50%', background: step.color,
                  display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                  <i className={`fa-solid ${step.icon}`} style={{ color: '#fff', fontSize: '.8rem' }} />
                </div>
                <div style={{ flex: 1 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '.5rem', marginBottom: '.25rem', flexWrap: 'wrap' }}>
                    <strong style={{ fontSize: '.875rem' }}>{step.title}</strong>
                    <span style={{ fontSize: '.72rem', background: '#f1f5f9', color: '#475569',
                      padding: '.1em .5em', borderRadius: '.25rem', fontWeight: 600 }}>
                      {step.actor}
                    </span>
                  </div>
                  <P style={{ margin: 0, color: 'var(--muted)' }}>{step.desc}</P>
                </div>
              </div>
            ))}
          </Section>

          {/* ── Deprecation ── */}
          <Section id="deprecation">
            <H2><i className="fa-solid fa-clock-rotate-left" style={{ marginRight: '.5rem', color: '#d97706' }} />Deprecation Workflow</H2>
            <P>Deprecation gives consumers advance notice before a route is removed. The route stays live but signals clients to migrate.</P>

            {[
              { icon: 'fa-clock-rotate-left', color: '#d97706', title: 'Deprecate (ACT → DEPRECATED)', desc: <>Click the amber clock icon, enter a <strong>Sunset Date</strong> (must be in the future). Route status becomes DEPRECATED. All responses from this route now include:<br/><Code>Deprecation: true</Code>  <Code>Sunset: Wed, 01 Jan 2027 00:00:00 GMT</Code></> },
              { icon: 'fa-rotate-left',       color: '#16a34a', title: 'Undeprecate (DEPRECATED → ACT)', desc: 'Click the green restore icon to reverse deprecation. Status returns to ACTIVE, sunset_date is cleared, deprecation headers stop.' },
              { icon: 'fa-ban',               color: '#dc2626', title: 'Manual Retire (→ RETIRED)',    desc: 'Click the red ban icon to immediately retire the route. Removed from gateway at next refresh cycle.' },
              { icon: 'fa-robot',             color: '#475569', title: 'Auto-Retire (scheduled)',      desc: <>A background scheduler runs every <strong>60 seconds</strong>. Any DEPRECATED route whose <Code>sunset_date</Code> has passed is automatically transitioned to RETIRED and the gateway refreshes.</> },
            ].map((step, i) => (
              <div key={i} style={{ display: 'flex', gap: '1rem', marginBottom: '1rem', alignItems: 'flex-start' }}>
                <div style={{ width: 32, height: 32, borderRadius: '50%', background: step.color,
                  display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                  <i className={`fa-solid ${step.icon}`} style={{ color: '#fff', fontSize: '.8rem' }} />
                </div>
                <div style={{ flex: 1 }}>
                  <strong style={{ fontSize: '.875rem', display: 'block', marginBottom: '.25rem' }}>{step.title}</strong>
                  <P style={{ margin: 0, color: 'var(--muted)' }}>{step.desc}</P>
                </div>
              </div>
            ))}
          </Section>

          {/* ── Versioning ── */}
          <Section id="versioning">
            <H2><i className="fa-solid fa-code-branch" style={{ marginRight: '.5rem', color: '#0891b2' }} />API Versioning</H2>
            <P>
              Each route can carry a <Code>version</Code> tag (e.g. <Code>v1</Code>, <Code>v2</Code>).
              When set, the gateway automatically injects a response header so consumers and upstream services
              know which version handled the request.
            </P>
            <div style={{ background: 'var(--bg-surface-alt)', border: '1px solid var(--border)',
              borderRadius: '.5rem', padding: '1rem', fontFamily: 'monospace', fontSize: '.82rem',
              marginBottom: '1rem', color: 'var(--text-primary)' }}>
              <div style={{ color: 'var(--muted)', marginBottom: '.5rem', fontSize: '.75rem' }}>Response headers injected by gateway:</div>
              <div><span style={{ color: '#0369a1' }}>X-API-Version:</span> v1</div>
              <div><span style={{ color: '#0369a1' }}>Deprecation:</span> true  <span style={{ color: 'var(--muted)' }}>(only on DEPRECATED routes)</span></div>
              <div><span style={{ color: '#0369a1' }}>Sunset:</span> Wed, 01 Jan 2027 00:00:00 GMT  <span style={{ color: 'var(--muted)' }}>(only on DEPRECATED routes)</span></div>
            </div>
            <P>Version is also used as a filter in the Routes table — use the <strong>All Versions</strong> dropdown to isolate routes by version.</P>
          </Section>

          {/* ── Tags ── */}
          <Section id="tags">
            <H2><i className="fa-solid fa-tags" style={{ marginRight: '.5rem', color: '#7c3aed' }} />Policy Tags</H2>
            <P>Tags are free-form comma-separated labels that mark compliance and access requirements for a route.</P>
            <Table
              cols={['Tag', 'Meaning', 'Suggested Use']}
              rows={[
                [<Code>PII</Code>,        'Route handles Personally Identifiable Information', 'Enable extra audit scrutiny, restrict access'],
                [<Code>SENSITIVE</Code>,  'Route returns sensitive business data',              'Require premium auth, restrict to internal networks'],
                [<Code>INTERNAL</Code>,   'Route is for internal services only',                'Combine with IP ACL rules to block external callers'],
                [<Code>BETA</Code>,       'Route is experimental or in preview',                'Pair with lower SLA tier, document caveats'],
                [<Code>PUBLIC_API</Code>, 'Route is part of the public API surface',            'Ensure documentation is complete before approval'],
              ]}
            />
            <InfoBox color="#f5f3ff" border="#ddd6fe" textColor="#5b21b6" icon="fa-lightbulb">
              Tags are informational at this stage — they guide approvers and operators but do not automatically enforce access restrictions.
              Combine with <strong>IP ACL rules</strong> and <strong>Required Roles / Permissions</strong> for enforcement.
            </InfoBox>
          </Section>

          {/* ── SLA ── */}
          <Section id="sla">
            <H2><i className="fa-solid fa-gauge-high" style={{ marginRight: '.5rem', color: '#d97706' }} />SLA Tiers</H2>
            <P>Every route carries an SLA tier that communicates its expected reliability and response time commitment.</P>
            <Table
              cols={['Tier', 'Badge', 'Intended Use']}
              rows={[
                [<strong>BASIC</strong>,    <span className="badge badge-sla-basic">Basic</span>,       'Low-priority, best-effort routes. Background jobs, non-critical reads.'],
                [<strong>STANDARD</strong>, <span className="badge badge-sla-standard">Standard</span>, 'Default. Most internal API routes.'],
                [<strong>PREMIUM</strong>,  <span className="badge badge-sla-premium">Premium</span>,   'Business-critical flows. Payment, orders, user auth.'],
                [<strong>CRITICAL</strong>, <span className="badge badge-sla-critical">Critical</span>, 'Zero-downtime requirement. Core infrastructure routes.'],
              ]}
            />
            <P>SLA tier is stored on the route and displayed as a colored badge alongside the status in the Routes table. Use it to prioritize incident response and circuit breaker configuration.</P>
          </Section>

          {/* ── Documentation ── */}
          <Section id="documentation">
            <H2><i className="fa-solid fa-book-open" style={{ marginRight: '.5rem', color: '#0891b2' }} />Documentation Field</H2>
            <P>
              Each route has a <Code>documentation</Code> text field that accepts Markdown content.
              This is the primary place to describe the API contract for approvers and consumers.
            </P>
            <H3>What to include</H3>
            <div style={{ background: 'var(--bg-surface-alt)', border: '1px solid var(--border)',
              borderRadius: '.5rem', padding: '1rem', fontFamily: 'monospace', fontSize: '.8rem',
              lineHeight: 1.7, color: 'var(--text-primary)', marginBottom: '1rem' }}>
              {`## Overview
What this route does and who should call it.

## Request
- Method: POST
- Content-Type: application/json
- Body: { "field": "value" }

## Response
- 200: { "result": "..." }
- 400: Bad request
- 401: Unauthorized

## Notes
- Rate limited to 100 req/5s
- Requires ADMIN role`}
            </div>
            <H3>Where it appears</H3>
            <ul style={{ fontSize: '.875rem', lineHeight: 1.8, color: 'var(--muted)', paddingLeft: '1.25rem' }}>
              <li>In the <strong>Create / Edit Route</strong> form under the Documentation section</li>
              <li>Via the <strong>book icon</strong> in the route table (only shown when documentation exists)</li>
              <li>In the documentation viewer modal alongside version, SLA, and tags</li>
            </ul>
          </Section>

          {/* ── Endpoints ── */}
          <Section id="endpoints">
            <H2><i className="fa-solid fa-plug" style={{ marginRight: '.5rem', color: '#059669' }} />Endpoints Reference</H2>
            <P>Base path: <Code>/api/management/admin/routes</Code></P>

            <H3>CRUD</H3>
            <EndpointRow method="GET"    path="/"       perm="ROUTE_READ"  desc="List routes by status (default: ACT). Supports DRAFT, PENDING, DEPRECATED, RETIRED, INACT." />
            <EndpointRow method="GET"    path="/{id}"   perm="ROUTE_READ"  desc="Get single route by ID." />
            <EndpointRow method="POST"   path="/"       perm="ROUTE_WRITE" desc="Create route. Always starts as DRAFT." />
            <EndpointRow method="PUT"    path="/{id}"   perm="ROUTE_WRITE" desc="Update route fields (path, method, group, security, governance, documentation)." />
            <EndpointRow method="DELETE" path="/{id}"   perm="ROUTE_WRITE" desc="Soft-delete (sets INACT)." />

            <H3>Approval Workflow</H3>
            <EndpointRow method="POST"   path="/{id}/submit"  perm="ROUTE_WRITE"   desc="DRAFT → PENDING. Clears rejection reason." />
            <EndpointRow method="POST"   path="/{id}/approve" perm="ROUTE_APPROVE" desc="PENDING → ACT. Triggers gateway refresh." />
            <EndpointRow method="POST"   path="/{id}/reject"  perm="ROUTE_APPROVE" desc="PENDING → DRAFT. Body: { reason: string }. Required." />

            <H3>Deprecation Lifecycle</H3>
            <EndpointRow method="PUT"    path="/{id}/deprecate"   perm="ROUTE_WRITE" desc="ACT → DEPRECATED. Body: { sunset_date: ISO datetime }. Required, must be future." />
            <EndpointRow method="PUT"    path="/{id}/undeprecate" perm="ROUTE_WRITE" desc="DEPRECATED → ACT. Clears sunset_date and deprecated flag." />
            <EndpointRow method="PUT"    path="/{id}/retire"      perm="ROUTE_WRITE" desc="→ RETIRED. Immediate. Triggers gateway refresh." />

            <H3>Administrative</H3>
            <EndpointRow method="PUT"    path="/{id}/enable"  perm="ROUTE_WRITE" desc="INACT → ACT." />
            <EndpointRow method="PUT"    path="/{id}/disable" perm="ROUTE_WRITE" desc="ACT → INACT." />
            <EndpointRow method="POST"   path="/reload"       perm="ROUTE_WRITE" desc="Force gateway route cache refresh." />
          </Section>

          {/* ── Permissions ── */}
          <Section id="permissions">
            <H2><i className="fa-solid fa-user-shield" style={{ marginRight: '.5rem', color: '#7c3aed' }} />Permissions</H2>
            <Table
              cols={['Permission', 'Granted to', 'What it controls']}
              rows={[
                [<Code>ROUTE_READ</Code>,    'VIEWER, OPERATOR, SUPER_ADMIN', 'View routes and their governance metadata'],
                [<Code>ROUTE_WRITE</Code>,   'OPERATOR, SUPER_ADMIN',         'Create, edit, submit, deprecate, retire, enable/disable routes'],
                [<Code>ROUTE_APPROVE</Code>, 'OPERATOR, SUPER_ADMIN',         'Approve or reject PENDING route submissions'],
              ]}
            />
            <InfoBox color="#f0fdf4" border="#bbf7d0" textColor="#166534" icon="fa-circle-check">
              <strong>SUPER_ADMIN</strong> holds all permissions and bypasses individual permission checks.
              Assign <strong>ROUTE_APPROVE</strong> to trusted senior operators to enforce a
              separation-of-duties review process.
            </InfoBox>
          </Section>

          {/* ── Security ── */}
          <Section id="security">
            <H2><i className="fa-solid fa-shield-halved" style={{ marginRight: '.5rem', color: '#dc2626' }} />Security Policies per Route</H2>
            <Table
              cols={['Field', 'Values', 'Effect']}
              rows={[
                [<Code>auth_type</Code>,             'JWT · OAUTH2 · API_KEY · NONE', 'How the caller must authenticate'],
                [<Code>required_roles</Code>,         'comma-separated role names',   'All listed roles must be present in the token'],
                [<Code>required_permissions</Code>,   'comma-separated perm names',   'All listed permissions must be present in the token'],
                [<Code>is_public</Code>,              'Y / N',                        'Y bypasses auth entirely (open endpoint)'],
                [<Code>is_encrypt</Code>,             'Y / N',                        'AES-GCM request decrypt + response encrypt'],
                [<Code>enable_circuit_breaker</Code>, 'Y / N',                        'Resilience4j circuit breaker with fallback handler'],
              ]}
            />
          </Section>

          {/* ── Operational ── */}
          <Section id="operational">
            <H2><i className="fa-solid fa-sliders" style={{ marginRight: '.5rem', color: '#059669' }} />Operational Controls</H2>
            <Table
              cols={['Feature', 'Field(s)', 'Description']}
              rows={[
                ['Rate Limiting',      <><Code>rate_limit</Code> + <Code>rate_limit_duration</Code></>, 'Max requests per time window per route'],
                ['Scheduled Availability', <><Code>start_time</Code> + <Code>end_time</Code></>,      'Route only active within this datetime window'],
                ['Priority',           <Code>priority</Code>,                                          'Lower number = higher match priority when routes overlap'],
                ['IP Access Control',  'ip_access_control table',                                      'WHITELIST / BLACKLIST per IP/CIDR — global, per group, or per route'],
                ['Blue-Green Deploy',  'api_group_route (blue_uri / green_uri)',                        'Per service group, instant slot swap with zero config changes'],
                ['mTLS',               'MtlsConfig',                                                   'Mutual TLS for downstream service connections'],
                ['Audit Logging',      'audit_log table',                                              'Every POST / PUT / DELETE on /admin/** is recorded with actor, old value, new value'],
              ]}
            />
          </Section>
        </div>
      </div>
    </div>
  );
}
