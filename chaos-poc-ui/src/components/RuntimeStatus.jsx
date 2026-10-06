const OPERATOR_CONSOLE_URL = import.meta.env.VITE_OPERATOR_CONSOLE_URL || 'http://localhost:18090/chaos';

function meshLabel(service, traffic) {
  if (!service) return { tone: 'unknown', label: 'Unknown', detail: 'Select a service on the map.' };
  if (service.configState === 'FAILED' || service.lastCommandStatus === 'FAILED') {
    return { tone: 'failing', label: 'Failing', detail: 'Last patch failed — check Operator console history.' };
  }
  if ((service.eurekaUpCount ?? 0) < (service.expectedInstances ?? 1)) {
    return { tone: 'failing', label: 'Replica gap', detail: `${service.eurekaUpCount ?? 0} of ${service.expectedInstances ?? 1} replicas up.` };
  }
  if (traffic?.count && traffic.errorRate >= 40) {
    return { tone: 'failing', label: 'Unhealthy traffic', detail: `${traffic.errorRate}% errors in recent calls — mesh red.` };
  }
  if (service.cmEnabled) {
    return { tone: 'degraded', label: 'Fault injected', detail: 'Chaos Monkey is on. Expect latency or errors on watched paths.' };
  }
  if (traffic?.count && traffic.errorRate > 0) {
    return { tone: 'degraded', label: 'Partial errors', detail: 'Some recent calls failed; most traffic may still succeed.' };
  }
  return { tone: 'healthy', label: 'Healthy', detail: 'All greens — no active fault injection observed.' };
}

export default function RuntimeStatus({ service, loading, traffic, friendlyName }) {
  const mesh = meshLabel(service, traffic);
  const up = service?.eurekaUpCount ?? 0;
  const expected = service?.expectedInstances ?? 0;

  return (
    <section className="runtime-card" aria-labelledby="runtime-title">
      <div className="runtime-header">
        <div>
          <p className="eyebrow">RUNTIME STATUS</p>
          <h2 id="runtime-title">{friendlyName || service?.applicationName || 'Select a service'}</h2>
          <p className="muted mono">{service?.applicationName || '—'}</p>
        </div>
        <span className={`mesh-pill ${mesh.tone}`}><i />{mesh.label}</span>
      </div>

      {loading && <div className="inspector-loading">Reading live service status…</div>}

      {!loading && (
        <>
          <p className="runtime-detail">{mesh.detail}</p>

          <dl className="runtime-metrics">
            <div className="runtime-replicas">
              <dt>Replicas</dt>
              <dd>
                <span className="replica-count">{up} / {expected || '—'} pods running</span>
                <div className="replica-cluster runtime">
                  {Array.from({ length: Math.max(expected, up, 0) }, (_, index) => {
                    const isUp = index < up;
                    const tone = !isUp ? 'down' : (mesh.tone === 'failing' || mesh.tone === 'degraded' ? mesh.tone : 'healthy');
                    return (
                      <div key={index} className={`replica-pod mesh-${tone}`}>
                        <span className="replica-pod-icon" aria-hidden>▣</span>
                        <span className="replica-pod-copy">
                          <strong>replica-{index + 1}</strong>
                          <small>{!isUp ? 'DOWN' : mesh.tone === 'failing' ? 'ERRORS' : mesh.tone === 'degraded' ? 'CHAOS' : 'UP'}</small>
                        </span>
                      </div>
                    );
                  })}
                </div>
              </dd>
            </div>
            <div>
              <dt>Recent calls</dt>
              <dd>{traffic?.count ? `${traffic.count} · err ${traffic.errorRate}%` : 'No traffic yet'}</dd>
            </div>
            <div>
              <dt>p95 latency</dt>
              <dd>{traffic?.count ? `${traffic.p95}ms` : '—'}</dd>
            </div>
            <div>
              <dt>Fault injection</dt>
              <dd className={service?.cmEnabled ? 'warn-text' : 'success-text'}>
                {service?.cmEnabled ? 'Active (see Operator)' : 'Off'}
              </dd>
            </div>
          </dl>

          <a className="operator-link" href={OPERATOR_CONSOLE_URL} target="_blank" rel="noreferrer">
            Patch scenarios &amp; history in Operator console ↗
          </a>
        </>
      )}
    </section>
  );
}
