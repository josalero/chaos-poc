import { useState } from 'react';
import { callDemoApi } from '../api/chaosClient';
import Card, { CardBody, CardHeader } from './ui/Card';

function uniqueSku(prefix) {
  return `${prefix}-${crypto.randomUUID().slice(0, 8)}`;
}

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
  const [lastOrderId, setLastOrderId] = useState('');
  const [sku, setSku] = useState(() => uniqueSku('WIDGET'));
  const [quantity, setQuantity] = useState(1);
  const manualResults = results.filter((result) => result.source !== 'live-probe');

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

  const placeOrder = async () => {
    const result = await run('Place order', 'POST', '/api/v1/orders', { sku, quantity: Number(quantity) || 1 });
    if (result?.body?.id) {
      setLastOrderId(String(result.body.id));
      setSku(uniqueSku('WIDGET'));
    }
    return result;
  };

  const lookUpOrder = async () => {
    if (!lastOrderId) return null;
    return run('Look up order', 'GET', `/api/v1/orders/${lastOrderId}`);
  };

  const submitOrder = async () => {
    if (!lastOrderId) return null;
    return run('Submit order', 'POST', `/api/v1/orders/${lastOrderId}/submit`);
  };

  const placeAndSubmit = async () => {
    const created = await placeOrder();
    const id = created?.body?.id;
    if (!id) return;
    await run('Submit order', 'POST', `/api/v1/orders/${id}/submit`);
  };

  if (variant === 'downstream') {
    return (
      <Card>
        <CardHeader
          title="Inventory & auth client"
          description="Direct calls a partner system would make to chaos-poc-downstream"
        />
        <CardBody className="space-y-4">
          <div className="flex flex-wrap gap-2">
            <button type="button" disabled={disabled} className="action-tile" onClick={() => run('Get stock', 'GET', '/v1/inventory/WIDGET-001')}>
              <span>Check stock</span>
              <small>GET /v1/inventory/WIDGET-001</small>
            </button>
            <button
              type="button"
              disabled={disabled}
              className="action-tile"
              onClick={() => run('Reserve inventory', 'POST', '/v1/inventory/reserve', { sku: uniqueSku('RESERVE'), quantity: 1 })}
            >
              <span>Reserve inventory</span>
              <small>POST /v1/inventory/reserve</small>
            </button>
            <button type="button" disabled={disabled} className="action-tile" onClick={() => run('Auth granted', 'GET', '/v1/auth/reports')}>
              <span>Authorize reports</span>
              <small>GET /v1/auth/reports</small>
            </button>
            <button type="button" disabled={disabled} className="action-tile" onClick={() => run('Auth forbidden', 'GET', '/v1/auth/restricted')}>
              <span>Restricted resource</span>
              <small>GET /v1/auth/restricted → 403</small>
            </button>
          </div>
          <ResponseList results={manualResults} disabled={disabled} emptyHint="Call inventory or auth to capture responses." />
        </CardBody>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader
        title="Order client"
        description="Act like a shopper: place an order, look it up, then submit — same APIs a real client would call"
      />
      <CardBody className="space-y-5">
        {disabled && (
          <p className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-600" role="status">
            Waiting for <strong>{targetLabel}</strong> before customer actions are enabled.
          </p>
        )}

        <div className="order-form-grid">
          <label>
            <span>SKU</span>
            <input value={sku} onChange={(event) => setSku(event.target.value)} disabled={disabled} />
          </label>
          <label>
            <span>Qty</span>
            <input
              type="number"
              min="1"
              value={quantity}
              onChange={(event) => setQuantity(event.target.value)}
              disabled={disabled}
            />
          </label>
        </div>

        <div className="flex flex-wrap gap-2">
          <button type="button" disabled={disabled} className="action-tile primary" onClick={placeOrder}>
            <span>Place order</span>
            <small>POST /api/v1/orders</small>
          </button>
          <button type="button" disabled={disabled || !lastOrderId} className="action-tile" onClick={lookUpOrder}>
            <span>Look up order</span>
            <small>{lastOrderId ? `GET …/${lastOrderId}` : 'Place an order first'}</small>
          </button>
          <button type="button" disabled={disabled || !lastOrderId} className="action-tile" onClick={submitOrder}>
            <span>Submit order</span>
            <small>{lastOrderId ? `POST …/${lastOrderId}/submit` : 'Place an order first'}</small>
          </button>
          <button type="button" disabled={disabled} className="action-tile" onClick={placeAndSubmit}>
            <span>Place + submit</span>
            <small>Full happy path</small>
          </button>
        </div>

        <details className="edge-cases">
          <summary>Edge cases (optional probes)</summary>
          <div className="flex flex-wrap gap-2 mt-3">
            <button
              type="button"
              disabled={disabled}
              className="action-tile"
              onClick={() => run('Downstream 404', 'POST', '/api/v1/orders', { sku: 'NOT-FOUND', quantity: 1 })}
            >
              <span>Unknown SKU</span>
              <small>→ 404</small>
            </button>
            <button
              type="button"
              disabled={disabled}
              className="action-tile"
              onClick={() => run('Downstream 403', 'GET', '/api/v1/inventory/FORBIDDEN')}
            >
              <span>Forbidden inventory</span>
              <small>→ 403</small>
            </button>
          </div>
        </details>

        <ResponseList
          results={manualResults}
          disabled={disabled}
          emptyHint="Place an order to capture HTTP status, latency, and response bodies."
        />
      </CardBody>
    </Card>
  );
}

function ResponseList({ results, disabled, emptyHint }) {
  if (results.length === 0 && !disabled) {
    return (
      <p className="rounded-lg border border-dashed border-slate-300 bg-slate-50 px-4 py-5 text-center text-sm text-slate-600">
        {emptyHint}
      </p>
    );
  }
  if (results.length === 0) return null;

  return (
    <div className="border-t border-slate-100 pt-4">
      <h3 className="mb-3 text-sm font-semibold text-slate-800">Recent responses</h3>
      <ul className="space-y-2" aria-live="polite">
        {results.slice(0, 8).map((r, i) => (
          <li key={`${r.label}-${r.at}-${i}`} className="rounded-lg border border-slate-200 bg-slate-50 p-3">
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-sm font-medium text-slate-900">{r.label}</span>
              {r.error ? (
                <span className="rounded-full bg-rose-50 px-2 py-0.5 text-xs font-medium text-rose-700">Error</span>
              ) : (
                <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${statusTone(r.status)}`}>
                  HTTP {r.status}
                </span>
              )}
              <span className="text-xs text-slate-500">{r.durationMs}ms</span>
            </div>
            {r.error && <p className="mt-1 text-sm text-rose-700">{r.error}</p>}
            {r.body && (
              <pre className="mt-2 max-h-32 overflow-auto rounded border border-slate-200 bg-white p-2 text-xs text-slate-600">
                {JSON.stringify(r.body, null, 2)}
              </pre>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}
