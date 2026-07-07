import { useEffect, useMemo, useState } from 'react';
import { callDemoApi } from '../api/chaosClient';
import Button from './ui/Button';
import Card, { CardBody, CardHeader } from './ui/Card';

const MAX_POINTS = 60;
const CHART_WIDTH = 760;
const CHART_HEIGHT = 220;
const CHART_PADDING = 38;

function percentile(values, percentage) {
  if (values.length === 0) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  const index = Math.min(
    sorted.length - 1,
    Math.max(0, Math.ceil((percentage / 100) * sorted.length) - 1),
  );
  return sorted[index];
}

function statusClass(status) {
  if (status >= 500 || status == null) return '5xx';
  if (status >= 400) return '4xx';
  return '2xx';
}

function statusColor(status) {
  if (status >= 500 || status == null) return '#e11d48';
  if (status >= 400) return '#d97706';
  return '#059669';
}

function Metric({ label, value, detail }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2.5">
      <p className="text-xs font-medium text-slate-500">{label}</p>
      <p className="mt-0.5 text-lg font-bold tabular-nums text-slate-900">{value}</p>
      {detail && <p className="mt-0.5 text-xs text-slate-500">{detail}</p>}
    </div>
  );
}

function LatencyChart({ results, selectedResult, onSelect, supportsLiveProbe }) {
  const points = results.slice(-MAX_POINTS);
  const maxDuration = Math.max(500, ...points.map((result) => result.durationMs || 0));
  const yMax = Math.ceil(maxDuration / 500) * 500;
  const plotWidth = CHART_WIDTH - CHART_PADDING * 2;
  const plotHeight = CHART_HEIGHT - CHART_PADDING * 2;
  const coordinates = points.map((result, index) => {
    const x = CHART_PADDING
      + (points.length <= 1 ? plotWidth / 2 : (index / (points.length - 1)) * plotWidth);
    const y = CHART_PADDING + plotHeight - ((result.durationMs || 0) / yMax) * plotHeight;
    return { x, y, result };
  });
  const path = coordinates.map(({ x, y }) => `${x},${y}`).join(' ');

  return (
    <div>
      <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
        <div>
          <h3 className="text-sm font-semibold text-slate-800">Response latency</h3>
          <p className="text-xs text-slate-500">
            Rolling window — newest point on the right. Click a point to inspect its JSON.
          </p>
        </div>
        <div className="flex gap-3 text-xs text-slate-600">
          <span><span className="mr-1 inline-block h-2 w-2 rounded-full bg-emerald-600" />2xx</span>
          <span><span className="mr-1 inline-block h-2 w-2 rounded-full bg-amber-600" />4xx</span>
          <span><span className="mr-1 inline-block h-2 w-2 rounded-full bg-rose-600" />5xx/error</span>
        </div>
      </div>
      <div className="overflow-hidden rounded-lg border border-slate-200 bg-slate-50">
        {points.length === 0 ? (
          <div className="flex h-52 items-center justify-center px-4 text-center text-sm text-slate-500">
            Run a playground action{supportsLiveProbe ? ' or start a live probe' : ''} to populate the chart.
          </div>
        ) : (
          <svg
            viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`}
            className="h-52 w-full"
            data-testid="latency-chart"
            role="img"
            aria-label={`Response latency chart with ${points.length} samples`}
          >
            {[0, 0.5, 1].map((fraction) => {
              const y = CHART_PADDING + plotHeight * fraction;
              const value = Math.round(yMax * (1 - fraction));
              return (
                <g key={fraction}>
                  <line
                    x1={CHART_PADDING}
                    x2={CHART_WIDTH - CHART_PADDING}
                    y1={y}
                    y2={y}
                    stroke="#cbd5e1"
                    strokeDasharray="4 5"
                  />
                  <text x="4" y={y + 4} fill="#64748b" fontSize="11">{value}ms</text>
                </g>
              );
            })}
            {coordinates.length > 1 && (
              <polyline
                points={path}
                fill="none"
                stroke="#2563eb"
                strokeWidth="2.5"
                strokeLinejoin="round"
                strokeLinecap="round"
              />
            )}
            {coordinates.map(({ x, y, result }, index) => (
              <g
                key={`${result.at}-${index}`}
                role="button"
                tabIndex="0"
                data-testid="latency-point"
                aria-label={`${result.label}: HTTP ${result.status ?? 'error'}, ${result.durationMs}ms. View response JSON.`}
                className="cursor-pointer outline-none"
                onClick={() => onSelect(result)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault();
                    onSelect(result);
                  }
                }}
              >
                <circle cx={x} cy={y} r="11" fill="transparent" />
                <circle
                  cx={x}
                  cy={y}
                  r={selectedResult === result ? 6 : 4.5}
                  fill={statusColor(result.status)}
                  stroke={selectedResult === result ? '#0f172a' : 'white'}
                  strokeWidth={selectedResult === result ? 3 : 2}
                  pointerEvents="none"
                />
                <title>{`${result.label}: HTTP ${result.status ?? 'error'}, ${result.durationMs}ms`}</title>
              </g>
            ))}
          </svg>
        )}
      </div>
    </div>
  );
}

function ResponseDetails({ result, onClose }) {
  if (!result) return null;

  const responseJson = result.body ?? (result.error ? { error: result.error } : null);
  const status = result.status ?? 'Network error';
  const statusClasses = result.status >= 500 || result.status == null
    ? 'bg-rose-50 text-rose-700 ring-rose-200'
    : result.status >= 400
      ? 'bg-amber-50 text-amber-800 ring-amber-200'
      : 'bg-emerald-50 text-emerald-700 ring-emerald-200';

  return (
    <div
      className="rounded-lg border border-blue-200 bg-blue-50/50 p-4"
      data-testid="selected-response-details"
    >
      <div className="mb-3 flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-700">
            Selected response
          </p>
          <h3 className="mt-1 text-sm font-semibold text-slate-900">{result.label}</h3>
          <p className="mt-0.5 text-xs text-slate-500">
            {new Date(result.at).toLocaleString()} · {result.durationMs}ms
          </p>
        </div>
        <div className="flex items-center gap-2">
          <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ring-inset ${statusClasses}`}>
            HTTP {status}
          </span>
          <Button variant="ghost" size="sm" onClick={onClose}>Close details</Button>
        </div>
      </div>
      {result.error && (
        <p className="mb-2 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700">
          {result.error}
        </p>
      )}
      <p className="mb-1.5 text-xs font-medium text-slate-600">Response JSON</p>
      <pre
        className="max-h-72 overflow-auto rounded-lg border border-slate-200 bg-slate-950 p-3 text-xs leading-relaxed text-slate-100"
        data-testid="selected-response-json"
      >
        {responseJson == null ? 'No response body' : JSON.stringify(responseJson, null, 2)}
      </pre>
    </div>
  );
}

function StatusDistribution({ counts, total }) {
  const rows = [
    { key: '2xx', label: 'Success (2xx)', color: 'bg-emerald-500' },
    { key: '4xx', label: 'Client errors (4xx)', color: 'bg-amber-500' },
    { key: '5xx', label: 'Server errors (5xx)', color: 'bg-rose-500' },
  ];

  return (
    <div>
      <h3 className="mb-3 text-sm font-semibold text-slate-800">Status distribution</h3>
      <div className="space-y-3">
        {rows.map((row) => {
          const percentage = total ? Math.round((counts[row.key] / total) * 100) : 0;
          return (
            <div key={row.key}>
              <div className="mb-1 flex justify-between text-xs text-slate-600">
                <span>{row.label}</span>
                <span className="tabular-nums">{counts[row.key]} · {percentage}%</span>
              </div>
              <div className="h-2 overflow-hidden rounded-full bg-slate-100">
                <div
                  className={`h-full rounded-full transition-all duration-300 ${row.color}`}
                  style={{ width: `${percentage}%` }}
                />
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

export default function LiveTelemetryPanel({
  demoUrl,
  variant = 'demo',
  results,
  onResult,
  onClear,
  disabled,
  actuatorStatus,
  actuatorAssaults,
}) {
  const supportsLiveProbe = variant === 'demo';
  const [running, setRunning] = useState(false);
  const [probeMode, setProbeMode] = useState('create');
  const [intervalMs, setIntervalMs] = useState(1500);
  const [selectedResult, setSelectedResult] = useState(null);

  useEffect(() => {
    if (disabled || !supportsLiveProbe) setRunning(false);
  }, [disabled, supportsLiveProbe]);

  useEffect(() => {
    if (selectedResult && !results.includes(selectedResult)) setSelectedResult(null);
  }, [results, selectedResult]);

  useEffect(() => {
    if (!running || !supportsLiveProbe) return undefined;

    let cancelled = false;
    let timerId;

    const recordRequest = async (label, method, path, body) => {
      const started = performance.now();
      try {
        const response = await callDemoApi(demoUrl, method, path, body);
        if (!cancelled) {
          onResult({
            label,
            status: response.status,
            durationMs: Math.round(performance.now() - started),
            body: response.body,
            error: null,
            at: new Date().toISOString(),
            source: 'live-probe',
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
          });
        }
        return null;
      }
    };

    const runProbe = async () => {
      const sku = `LIVE-${crypto.randomUUID()}`;
      const created = await recordRequest(
        probeMode === 'submit' ? 'Live probe — create phase' : 'Live probe — create',
        'POST',
        '/api/v1/orders',
        { sku, quantity: 1 },
      );

      if (probeMode === 'submit' && created?.body?.id && !cancelled) {
        await recordRequest(
          'Live probe — submit phase',
          'POST',
          `/api/v1/orders/${created.body.id}/submit`,
        );
      }

      if (!cancelled) timerId = window.setTimeout(runProbe, intervalMs);
    };

    runProbe();
    return () => {
      cancelled = true;
      window.clearTimeout(timerId);
    };
  }, [demoUrl, intervalMs, onResult, probeMode, running, supportsLiveProbe]);

  const orderedResults = useMemo(
    () => results.slice(0, MAX_POINTS).reverse(),
    [results],
  );
  const metrics = useMemo(() => {
    const durations = orderedResults.map((result) => result.durationMs || 0);
    const counts = { '2xx': 0, '4xx': 0, '5xx': 0 };
    orderedResults.forEach((result) => {
      counts[statusClass(result.status)] += 1;
    });
    const failures = counts['4xx'] + counts['5xx'];
    return {
      p50: percentile(durations, 50),
      p95: percentile(durations, 95),
      errorRate: orderedResults.length
        ? Math.round((failures / orderedResults.length) * 100)
        : 0,
      counts,
    };
  }, [orderedResults]);

  const enabled = actuatorStatus?.enabled === true;
  const assaultLabel = actuatorAssaults?.exceptionsActive
    ? 'Exception'
    : actuatorAssaults?.latencyActive
      ? 'Latency'
      : 'None';

  return (
    <Card data-testid="live-telemetry">
      <CardHeader
        title="Live telemetry"
        description={
          supportsLiveProbe
            ? 'Real API responses from manual actions and optional repeating probes'
            : 'Charts manual playground responses for this downstream target'
        }
        actions={supportsLiveProbe ? (
          <div className="flex items-center gap-2">
            {running && (
              <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-emerald-700">
                <span className="h-2 w-2 animate-pulse rounded-full bg-emerald-500" />
                Live
              </span>
            )}
            <Button
              variant={running ? 'danger' : 'primary'}
              size="sm"
              data-testid="live-probe-toggle"
              disabled={disabled}
              onClick={() => setRunning((current) => !current)}
            >
              {running ? 'Stop probe' : 'Start live probe'}
            </Button>
          </div>
        ) : null}
      />
      <CardBody className="space-y-5">
        {supportsLiveProbe ? (
          <div className="flex flex-wrap items-end gap-3 rounded-lg border border-slate-200 bg-slate-50 p-3">
            <label className="text-xs font-medium text-slate-600">
              Probe path
              <select
                value={probeMode}
                disabled={running || disabled}
                onChange={(event) => setProbeMode(event.target.value)}
                className="mt-1 block rounded-md border border-slate-300 bg-white px-2.5 py-1.5 text-sm text-slate-800"
              >
                <option value="create">Create only</option>
                <option value="submit">Create + submit</option>
              </select>
            </label>
            <label className="text-xs font-medium text-slate-600">
              Pause between probes
              <select
                value={intervalMs}
                disabled={running || disabled}
                onChange={(event) => setIntervalMs(Number(event.target.value))}
                className="mt-1 block rounded-md border border-slate-300 bg-white px-2.5 py-1.5 text-sm text-slate-800"
              >
                <option value={1000}>1 second</option>
                <option value={1500}>1.5 seconds</option>
                <option value={3000}>3 seconds</option>
              </select>
            </label>
            <div className="ml-auto flex items-center gap-3 pb-1 text-xs text-slate-600">
              <span>Chaos Monkey: <strong className={enabled ? 'text-orange-700' : 'text-slate-700'}>{enabled ? 'Enabled' : 'Disabled'}</strong></span>
              <span>Assault: <strong className="text-slate-700">{assaultLabel}</strong></span>
            </div>
          </div>
        ) : (
          <p className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-600" role="status">
            Live probe is available for <strong>chaos-poc-demo</strong> only. Use the playground actions to populate telemetry for downstream.
          </p>
        )}

        <div className="grid grid-cols-2 gap-2 lg:grid-cols-4">
          <div data-testid="telemetry-sample-count">
            <Metric label="Samples" value={orderedResults.length} detail={`Last ${MAX_POINTS} max`} />
          </div>
          <Metric label="p50 latency" value={`${metrics.p50}ms`} detail="Median response" />
          <Metric label="p95 latency" value={`${metrics.p95}ms`} detail="Slow-tail response" />
          <Metric label="Error rate" value={`${metrics.errorRate}%`} detail="4xx + 5xx + network" />
        </div>

        <LatencyChart
          results={orderedResults}
          selectedResult={selectedResult}
          onSelect={setSelectedResult}
          supportsLiveProbe={supportsLiveProbe}
        />

        <ResponseDetails result={selectedResult} onClose={() => setSelectedResult(null)} />

        <div className="grid gap-5 border-t border-slate-100 pt-5 md:grid-cols-[1fr_auto]">
          <StatusDistribution counts={metrics.counts} total={orderedResults.length} />
          <div className="flex items-end">
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                setSelectedResult(null);
                onClear();
              }}
              disabled={results.length === 0}
            >
              Clear telemetry
            </Button>
          </div>
        </div>
      </CardBody>
    </Card>
  );
}
