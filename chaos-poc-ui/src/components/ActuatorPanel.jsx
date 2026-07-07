import Card, { CardBody, CardHeader } from './ui/Card';

function Metric({ label, value, active }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
      <p className="text-xs font-medium text-slate-500">{label}</p>
      <p className={`mt-0.5 text-sm font-semibold ${active ? 'text-orange-700' : 'text-slate-800'}`}>
        {value}
      </p>
    </div>
  );
}

function MetricSkeleton() {
  return (
    <div className="animate-pulse rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
      <div className="h-3 w-16 rounded bg-slate-200" />
      <div className="mt-2 h-4 w-10 rounded bg-slate-200" />
    </div>
  );
}

export default function ActuatorPanel({ status, assaults, error, loading }) {
  const enabled = status?.enabled === true;
  const latencyActive = assaults?.latencyActive === true;
  const exceptionsActive = assaults?.exceptionsActive === true;
  const hasData = Boolean(status || assaults);

  return (
    <Card>
      <CardHeader
        title="Chaos Monkey state"
        description="Live actuator on the selected target — refreshes every 3s after the operator console applies a scenario"
        actions={(
          <span
            className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-semibold ring-1 ring-inset ${
              loading
                ? 'bg-slate-100 text-slate-500 ring-slate-200'
                : enabled
                  ? 'bg-orange-50 text-orange-800 ring-orange-200'
                  : 'bg-slate-100 text-slate-600 ring-slate-200'
            }`}
            role="status"
          >
            {loading ? 'Loading…' : enabled ? 'Enabled' : 'Disabled'}
          </span>
        )}
      />
      <CardBody>
        {error && (
          <div
            role="alert"
            className="mb-4 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800"
          >
            <p className="font-medium">Could not read Chaos Monkey actuator</p>
            <p className="mt-1">{error}</p>
            <p className="mt-2 text-xs text-rose-700">
              Check the API base URL, confirm the target is running, and ensure actuator exposure includes
              <code className="mx-1 rounded bg-rose-100 px-1">chaosmonkey</code>.
            </p>
          </div>
        )}

        {loading && !hasData && !error && (
          <div className="mb-5 grid grid-cols-2 gap-2 sm:grid-cols-4" aria-busy="true" aria-label="Loading actuator metrics">
            <MetricSkeleton />
            <MetricSkeleton />
            <MetricSkeleton />
            <MetricSkeleton />
          </div>
        )}

        {!loading && !error && !hasData && (
          <p className="rounded-lg border border-dashed border-slate-300 bg-slate-50 px-4 py-6 text-center text-sm text-slate-600">
            No actuator data yet. Select a reachable target or wait for the first refresh.
          </p>
        )}

        {assaults && (
          <div className="mb-5 grid grid-cols-2 gap-2 sm:grid-cols-4">
            <Metric label="Enabled" value={enabled ? 'Yes' : 'No'} active={enabled} />
            <Metric label="Latency assault" value={latencyActive ? 'Active' : 'Off'} active={latencyActive} />
            <Metric
              label="Exception assault"
              value={exceptionsActive ? 'Active' : 'Off'}
              active={exceptionsActive}
            />
            <Metric
              label="Assault level"
              value={assaults.level != null ? String(assaults.level) : '—'}
              active={assaults.level > 0}
            />
          </div>
        )}

        {assaults?.watchedCustomServices?.length > 0 && (
          <div className="mb-5">
            <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">
              Watched methods
            </p>
            <div className="flex flex-wrap gap-1.5">
              {assaults.watchedCustomServices.map((svc) => (
                <code
                  key={svc}
                  className="rounded-md bg-blue-50 px-2 py-1 text-xs font-medium text-blue-800 ring-1 ring-blue-100"
                >
                  {svc}
                </code>
              ))}
            </div>
          </div>
        )}

        {hasData && (
          <details className="group">
            <summary className="cursor-pointer text-sm font-medium text-slate-700 hover:text-blue-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600">
              View raw actuator JSON
            </summary>
            <div className="mt-3 grid gap-4 lg:grid-cols-2">
              <div>
                <p className="mb-1.5 text-xs font-medium text-slate-500">Status</p>
                <pre className="max-h-48 overflow-auto rounded-lg border border-slate-200 bg-slate-50 p-3 text-xs text-slate-700">
                  {status ? JSON.stringify(status, null, 2) : 'No data'}
                </pre>
              </div>
              <div>
                <p className="mb-1.5 text-xs font-medium text-slate-500">Assaults config</p>
                <pre className="max-h-48 overflow-auto rounded-lg border border-slate-200 bg-slate-50 p-3 text-xs text-slate-700">
                  {assaults ? JSON.stringify(assaults, null, 2) : 'No data'}
                </pre>
              </div>
            </div>
          </details>
        )}
      </CardBody>
    </Card>
  );
}
