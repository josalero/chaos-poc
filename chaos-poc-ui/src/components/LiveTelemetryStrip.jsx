import { useEffect, useMemo, useState } from 'react';
import { callDemoApi, fetchChaosMonkeyAssaults, fetchChaosMonkeyStatus } from '../api/chaosClient';
import { resultsForTarget, summarizeResults } from '../utils/telemetry';
import Button from './ui/Button';
import LiveTelemetryPanel from './LiveTelemetryPanel';

const SPARK_W = 220;
const SPARK_H = 48;

function Sparkline({ results }) {
  const points = results.slice(0, 40).reverse();
  if (!points.length) {
    return <div className="telemetry-spark empty">No samples yet — run an API action or start a live probe.</div>;
  }
  const max = Math.max(100, ...points.map((result) => result.durationMs || 0));
  const coordinates = points.map((result, index) => {
    const x = points.length <= 1 ? SPARK_W / 2 : (index / (points.length - 1)) * SPARK_W;
    const y = SPARK_H - ((result.durationMs || 0) / max) * (SPARK_H - 6) - 3;
    return { x, y };
  });
  const path = coordinates.map(({ x, y }) => `${x},${y}`).join(' ');
  const lastPoint = coordinates[coordinates.length - 1];
  const last = points[points.length - 1];
  const tone = last.status == null || last.status >= 500 ? '#e11d48' : last.status >= 400 ? '#d97706' : '#059669';

  return (
    <svg viewBox={`0 0 ${SPARK_W} ${SPARK_H}`} className="telemetry-spark" role="img" aria-label={`Latency sparkline, ${points.length} samples`}>
      <polyline points={path} fill="none" stroke="#2563eb" strokeWidth="2" strokeLinejoin="round" strokeLinecap="round" />
      <circle cx={lastPoint.x} cy={lastPoint.y} r="3.5" fill={tone} />
    </svg>
  );
}

function statusTone(status) {
  if (status == null || status >= 500) return 'bad';
  if (status >= 400) return 'warn';
  return 'ok';
}

export default function LiveTelemetryStrip({
  target,
  selectedService,
  results,
  onResult,
  onClear,
  disabled,
}) {
  const [expanded, setExpanded] = useState(false);
  const [running, setRunning] = useState(false);
  const [status, setStatus] = useState(null);
  const [assaults, setAssaults] = useState(null);
  const scoped = useMemo(() => resultsForTarget(results, selectedService), [results, selectedService]);
  const metrics = useMemo(() => summarizeResults(scoped.slice(0, 60).reverse()), [scoped]);
  const supportsLiveProbe = target.variant === 'demo';

  useEffect(() => {
    let active = true;
    const refresh = async () => {
      try {
        const [nextStatus, nextAssaults] = await Promise.all([
          fetchChaosMonkeyStatus(target.apiBase),
          fetchChaosMonkeyAssaults(target.apiBase),
        ]);
        if (active) {
          setStatus(nextStatus);
          setAssaults(nextAssaults);
        }
      } catch {
        if (active) {
          setStatus(null);
          setAssaults(null);
        }
      }
    };
    refresh();
    const timer = window.setInterval(refresh, 3000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [target.apiBase]);

  useEffect(() => {
    setRunning(false);
  }, [selectedService]);

  useEffect(() => {
    if (expanded) setRunning(false);
  }, [expanded]);

  useEffect(() => {
    if (!running || !supportsLiveProbe) return undefined;
    let cancelled = false;
    let timerId;

    const recordRequest = async (label, method, path, body) => {
      const started = performance.now();
      try {
        const response = await callDemoApi(target.apiBase, method, path, body);
        if (!cancelled) {
          onResult({
            label,
            status: response.status,
            durationMs: Math.round(performance.now() - started),
            body: response.body,
            error: null,
            at: new Date().toISOString(),
            source: 'live-probe',
            target: selectedService,
          });
        }
        return response;
      } catch (error) {
        if (!cancelled) {
          onResult({
            label,
            status: null,
            durationMs: Math.round(performance.now() - started),
            body: null,
            error: error.message,
            at: new Date().toISOString(),
            source: 'live-probe',
            target: selectedService,
          });
        }
        return null;
      }
    };

    const runProbe = async () => {
      const sku = `LIVE-${crypto.randomUUID()}`;
      const created = await recordRequest('Live probe — create', 'POST', '/api/v1/orders', { sku, quantity: 1 });
      if (created?.body?.id && !cancelled) {
        await recordRequest('Live probe — submit', 'POST', `/api/v1/orders/${created.body.id}/submit`);
      }
      if (!cancelled) timerId = window.setTimeout(runProbe, 1200);
    };

    runProbe();
    return () => {
      cancelled = true;
      window.clearTimeout(timerId);
    };
  }, [onResult, running, selectedService, supportsLiveProbe, target.apiBase]);

  const recent = scoped.slice(0, 6);

  return (
    <section className="telemetry-strip" aria-labelledby="telemetry-strip-title">
      <div className="telemetry-strip-header">
        <div>
          <p className="eyebrow">LIVE TELEMETRY</p>
          <h2 id="telemetry-strip-title">Traffic for {selectedService === 'chaos-poc-demo' ? 'Order service' : 'Inventory + Auth'}</h2>
        </div>
        <div className="telemetry-strip-actions">
          {supportsLiveProbe && (
            <Button
              size="sm"
              variant={running ? 'danger' : 'primary'}
              disabled={disabled}
              onClick={() => setRunning((value) => !value)}
            >
              {running ? 'Stop probe' : 'Start live probe'}
            </Button>
          )}
          <Button size="sm" variant="secondary" disabled={!scoped.length} onClick={onClear}>Clear</Button>
          <Button size="sm" variant="secondary" onClick={() => setExpanded((value) => !value)}>
            {expanded ? 'Hide chart' : 'Full chart'}
          </Button>
        </div>
      </div>

      <div className="telemetry-strip-body">
        <div className="telemetry-kpis" aria-label="Live metrics">
          <div><span>Calls</span><strong>{metrics.count}</strong></div>
          <div><span>p50</span><strong>{metrics.count ? `${metrics.p50}ms` : '—'}</strong></div>
          <div><span>p95</span><strong>{metrics.count ? `${metrics.p95}ms` : '—'}</strong></div>
          <div><span>Error rate</span><strong className={metrics.errorRate ? 'danger-text' : ''}>{metrics.count ? `${metrics.errorRate}%` : '—'}</strong></div>
          <div><span>Observed CM</span><strong>{status?.enabled ? assaults?.exceptionsActive ? 'Exception' : assaults?.latencyActive ? 'Latency' : 'On' : 'Off'}</strong></div>
        </div>
        <Sparkline results={scoped} />
        <div className="telemetry-recent" aria-label="Recent calls">
          {recent.length === 0 ? (
            <p className="empty-copy">Run an experiment action above to light up the map and this strip.</p>
          ) : recent.map((result, index) => (
            <span key={`${result.at}-${index}`} className={`call-chip ${statusTone(result.status)}`}>
              <strong>{result.status ?? 'ERR'}</strong>
              <small>{result.durationMs}ms</small>
              <em>{result.label}</em>
            </span>
          ))}
        </div>
      </div>

      {expanded && (
        <div className="telemetry-expanded">
          <LiveTelemetryPanel
            demoUrl={target.apiBase}
            variant={target.variant}
            results={scoped}
            onResult={(result) => onResult({ ...result, target: selectedService })}
            onClear={onClear}
            disabled={disabled}
            actuatorStatus={status}
            actuatorAssaults={assaults}
          />
        </div>
      )}
    </section>
  );
}
