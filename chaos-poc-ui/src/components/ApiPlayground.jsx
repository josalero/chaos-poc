import { callDemoApi } from '../api/chaosClient';
import Card, { CardBody, CardHeader } from './ui/Card';

function uniqueSku(prefix) {
  return `${prefix}-${crypto.randomUUID()}`;
}

const demoGroups = [
  {
    title: 'Happy paths',
    description: 'Verify normal responses before and after assaults',
    actions: [
      {
        label: 'Create order',
        detail: 'Probe create endpoint',
        run: (run) => run(
          'Create order',
          'POST',
          '/api/v1/orders',
          { sku: uniqueSku('WIDGET'), quantity: 2 },
        ),
      },
      {
        label: 'Create + submit',
        detail: 'Probe create, then submit',
        run: async (run, createAndSubmit) => createAndSubmit(uniqueSku('SUBMIT')),
      },
    ],
  },
  {
    title: 'Error paths',
    description: 'Downstream failures the demo maps to HTTP status codes',
    actions: [
      {
        label: 'Duplicate order',
        detail: 'POST → 409',
        run: async (run) => {
          const sku = uniqueSku('DUPLICATE');
          const created = await run(
            'Create duplicate prerequisite',
            'POST',
            '/api/v1/orders',
            { sku, quantity: 1 },
          );
          if (created?.status !== 201) return;
          await run('Duplicate (409)', 'POST', '/api/v1/orders', { sku, quantity: 1 });
        },
      },
      {
        label: 'Not found',
        detail: 'Feign → 404',
        run: (run) => run('Feign 404', 'POST', '/api/v1/orders', { sku: 'NOT-FOUND', quantity: 1 }),
      },
      {
        label: 'Forbidden',
        detail: 'GET → 403',
        run: (run) => run('Feign 403', 'GET', '/api/v1/inventory/FORBIDDEN'),
      },
      {
        label: 'Reserve conflict',
        detail: 'Submit → 409',
        run: async (run) => {
          const sku = uniqueSku('RESERVE');
          const created = await run(
            'Create reserve prerequisite',
            'POST',
            '/api/v1/orders',
            { sku, quantity: 1 },
          );
          if (!created?.body?.id) return;
          const path = `/api/v1/orders/${created.body.id}/submit`;
          const reserved = await run('Initial submit (200)', 'POST', path);
          if (reserved?.status !== 200) return;
          await run('Submit → reserve 409', 'POST', path);
        },
      },
    ],
  },
];

const downstreamGroups = [
  {
    title: 'Inventory',
    description: 'Direct calls to chaos-poc-downstream',
    actions: [
      {
        label: 'Get stock',
        detail: 'GET /v1/inventory/{sku}',
        run: (run) => run('Get stock', 'GET', '/v1/inventory/WIDGET-001'),
      },
      {
        label: 'Reserve',
        detail: 'POST /v1/inventory/reserve',
        run: (run) => run(
          'Reserve inventory',
          'POST',
          '/v1/inventory/reserve',
          { sku: uniqueSku('RESERVE'), quantity: 1 },
        ),
      },
    ],
  },
  {
    title: 'Auth',
    description: 'Downstream auth probe endpoints',
    actions: [
      {
        label: 'Granted',
        detail: 'GET → 200',
        run: (run) => run('Auth granted', 'GET', '/v1/auth/reports'),
      },
      {
        label: 'Forbidden',
        detail: 'GET → 403',
        run: (run) => run('Auth forbidden', 'GET', '/v1/auth/restricted'),
      },
    ],
  },
];

function statusTone(status) {
  if (!status) return 'text-rose-700 bg-rose-50';
  if (status >= 500) return 'text-rose-700 bg-rose-50';
  if (status >= 400) return 'text-amber-800 bg-amber-50';
  return 'text-emerald-800 bg-emerald-50';
}

export default function ApiPlayground({
  apiBaseUrl,
  variant = 'demo',
  targetLabel,
  disabled,
  results,
  onResult,
}) {
  const groups = variant === 'downstream' ? downstreamGroups : demoGroups;
  const manualResults = results.filter((result) => result.source !== 'live-probe');
  const title = variant === 'downstream' ? 'Downstream API playground' : 'Demo API playground';

  const run = async (label, method, path, body) => {
    const started = performance.now();
    try {
      const result = await callDemoApi(apiBaseUrl, method, path, body);
      onResult({
        label,
        status: result.status,
        durationMs: Math.round(performance.now() - started),
        body: result.body,
        error: null,
        at: new Date().toISOString(),
      });
      return result;
    } catch (err) {
      onResult({
        label,
        status: null,
        durationMs: Math.round(performance.now() - started),
        body: null,
        error: err.message,
        at: new Date().toISOString(),
      });
      return null;
    }
  };

  const createAndSubmit = async (sku) => {
    const created = await run(
      'Create + submit — create phase',
      'POST',
      '/api/v1/orders',
      { sku, quantity: 1 },
    );
    if (!created?.body?.id) return;
    await run(
      'Create + submit — submit phase',
      'POST',
      `/api/v1/orders/${created.body.id}/submit`,
    );
  };

  return (
    <Card>
      <CardHeader
        title={title}
        description="Exercise endpoints to verify assault effects after applying a scenario in the operator console"
      />
      <CardBody className="space-y-5">
        {disabled && (
          <p className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-600" role="status">
            Waiting for <strong>{targetLabel}</strong> to become reachable before API actions are enabled.
          </p>
        )}

        {groups.map((group) => (
          <div key={group.title}>
            <h3 className="text-sm font-semibold text-slate-800">{group.title}</h3>
            <p className="mb-3 text-xs text-slate-500">{group.description}</p>
            <div className="flex flex-wrap gap-2">
              {group.actions.map((action) => (
                <button
                  key={action.label}
                  type="button"
                  disabled={disabled}
                  onClick={() => action.run(run, createAndSubmit)}
                  className="group rounded-lg border border-slate-200 bg-white px-3 py-2 text-left shadow-sm transition hover:border-slate-300 hover:shadow focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  <span className="block text-sm font-medium text-slate-800 group-hover:text-blue-700">
                    {action.label}
                  </span>
                  <span className="block text-xs text-slate-500">{action.detail}</span>
                </button>
              ))}
            </div>
          </div>
        ))}

        {manualResults.length === 0 && !disabled && (
          <p className="rounded-lg border border-dashed border-slate-300 bg-slate-50 px-4 py-5 text-center text-sm text-slate-600">
            Run an action above to capture HTTP status, latency, and response bodies here.
          </p>
        )}

        {manualResults.length > 0 && (
          <div className="border-t border-slate-100 pt-4">
            <h3 className="mb-3 text-sm font-semibold text-slate-800">Recent responses</h3>
            <ul className="space-y-2" aria-live="polite">
              {manualResults.slice(0, 8).map((r, i) => (
                <li
                  key={`${r.label}-${r.at}-${i}`}
                  className="rounded-lg border border-slate-200 bg-slate-50 p-3"
                >
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-sm font-medium text-slate-900">{r.label}</span>
                    {r.error ? (
                      <span className="rounded-full bg-rose-50 px-2 py-0.5 text-xs font-medium text-rose-700">
                        Error
                      </span>
                    ) : (
                      <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${statusTone(r.status)}`}>
                        HTTP {r.status}
                      </span>
                    )}
                    <span className="text-xs text-slate-500">{r.durationMs}ms</span>
                  </div>
                  {r.error && (
                    <p className="mt-1 text-sm text-rose-700">{r.error}</p>
                  )}
                  {r.body && (
                    <pre className="mt-2 max-h-32 overflow-auto rounded border border-slate-200 bg-white p-2 text-xs text-slate-600">
                      {JSON.stringify(r.body, null, 2)}
                    </pre>
                  )}
                </li>
              ))}
            </ul>
          </div>
        )}
      </CardBody>
    </Card>
  );
}
