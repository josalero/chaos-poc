import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  fetchChaosMonkeyAssaults,
  fetchChaosMonkeyStatus,
} from './api/chaosClient';
import ActuatorPanel from './components/ActuatorPanel';
import ApiPlayground from './components/ApiPlayground';
import ConnectionStatusBar from './components/ConnectionStatusBar';
import LiveTelemetryPanel from './components/LiveTelemetryPanel';
import Button from './components/ui/Button';
import { TabNav, TabPanel } from './components/ui/Tabs';

const OPERATOR_CONSOLE_URL =
  import.meta.env.VITE_OPERATOR_CONSOLE_URL || 'http://localhost:18090/chaos';

const VERIFY_TARGETS = {
  demo: {
    label: 'chaos-poc-demo',
    apiBase: import.meta.env.VITE_DEMO_URL || '/api/demo',
    playground: 'demo',
  },
  downstream: {
    label: 'chaos-poc-downstream',
    apiBase: import.meta.env.VITE_DOWNSTREAM_URL || '/api/downstream',
    playground: 'downstream',
  },
};

export default function App() {
  const [targetKey, setTargetKey] = useState('demo');
  const [activeTab, setActiveTab] = useState('status');
  const target = VERIFY_TARGETS[targetKey];
  const [apiBaseUrl, setApiBaseUrl] = useState(target.apiBase);
  const [actuatorStatus, setActuatorStatus] = useState(null);
  const [actuatorAssaults, setActuatorAssaults] = useState(null);
  const [actuatorError, setActuatorError] = useState('');
  const [actuatorLoading, setActuatorLoading] = useState(false);
  const [apiResults, setApiResults] = useState([]);

  useEffect(() => {
    setApiBaseUrl(target.apiBase);
    setApiResults([]);
    setActiveTab('status');
  }, [targetKey, target.apiBase]);

  const targetOk = !actuatorError && !actuatorLoading;
  const cmEnabled = actuatorStatus?.enabled === true;

  const refreshActuator = useCallback(async ({ silent = false } = {}) => {
    if (!silent) {
      setActuatorLoading(true);
      setActuatorError('');
    }
    try {
      const [status, assaults] = await Promise.all([
        fetchChaosMonkeyStatus(apiBaseUrl),
        fetchChaosMonkeyAssaults(apiBaseUrl),
      ]);
      setActuatorError('');
      setActuatorStatus(status);
      setActuatorAssaults(assaults);
    } catch (err) {
      setActuatorError(err.message);
      setActuatorStatus(null);
      setActuatorAssaults(null);
    } finally {
      if (!silent) setActuatorLoading(false);
    }
  }, [apiBaseUrl]);

  useEffect(() => {
    refreshActuator();
  }, [refreshActuator]);

  useEffect(() => {
    const intervalId = window.setInterval(
      () => refreshActuator({ silent: true }),
      3000,
    );
    return () => window.clearInterval(intervalId);
  }, [refreshActuator]);

  const recordApiResult = useCallback((result) => {
    setApiResults((previous) => [result, ...previous].slice(0, 60));
  }, []);

  const statusBadge = useMemo(() => {
    if (actuatorLoading) return '…';
    if (actuatorError) return 'Error';
    if (cmEnabled) return 'On';
    return null;
  }, [actuatorError, actuatorLoading, cmEnabled]);

  const tabs = useMemo(() => [
    {
      id: 'status',
      label: 'Chaos status',
      description: 'Actuator metrics and raw JSON',
      badge: statusBadge,
    },
    {
      id: 'playground',
      label: 'API playground',
      description: 'Manual endpoint exercises',
      badge: apiResults.filter((r) => r.source !== 'live-probe').length || null,
    },
    {
      id: 'telemetry',
      label: 'Live telemetry',
      description: target.playground === 'demo' ? 'Charts and live probe' : 'Response charts',
      badge: apiResults.length || null,
    },
  ], [apiResults, statusBadge, target.playground]);

  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-50 border-b border-slate-200 bg-white/95 shadow-sm backdrop-blur">
        <div className="mx-auto max-w-5xl px-4 py-5 sm:px-6">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div className="flex items-start gap-3">
              <div
                className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-orange-100 text-lg font-bold text-orange-600"
                aria-hidden
              >
                C
              </div>
              <div>
                <h1 className="text-xl font-bold tracking-tight text-slate-900 sm:text-2xl">
                  Chaos POC — Observe &amp; Verify
                </h1>
                <p className="mt-0.5 max-w-xl text-sm text-slate-600">
                  Monitor Chaos Monkey on a target service and exercise APIs to confirm assault
                  effects. Apply scenarios from the{' '}
                  <a
                    href={OPERATOR_CONSOLE_URL}
                    target="_blank"
                    rel="noreferrer"
                    className="font-medium text-blue-700 hover:text-blue-900"
                  >
                    operator console
                  </a>
                  .
                </p>
              </div>
            </div>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => window.open(OPERATOR_CONSOLE_URL, '_blank', 'noopener,noreferrer')}
            >
              Open operator console
            </Button>
          </div>

          <div className="mt-4 flex flex-wrap items-center gap-4 border-t border-slate-100 pt-4">
            <div>
              <label htmlFor="verify-target" className="block text-xs font-medium text-slate-600">
                Verify target
              </label>
              <select
                id="verify-target"
                value={targetKey}
                onChange={(event) => setTargetKey(event.target.value)}
                className="mt-1 rounded-md border border-slate-300 px-2 py-1.5 text-sm text-slate-800 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
              >
                {Object.entries(VERIFY_TARGETS).map(([key, entry]) => (
                  <option key={key} value={key}>
                    {entry.label}
                  </option>
                ))}
              </select>
            </div>
            <ConnectionStatusBar
              targetLabel={target.label}
              targetOk={targetOk}
              loading={actuatorLoading}
              onRefresh={refreshActuator}
            />
            <div>
              <label htmlFor="api-base-url" className="block text-xs font-medium text-slate-600">
                API base URL
              </label>
              <input
                id="api-base-url"
                type="url"
                value={apiBaseUrl}
                onChange={(event) => setApiBaseUrl(event.target.value)}
                className="mt-1 w-48 rounded-md border border-slate-300 px-2 py-1.5 text-sm text-slate-800 sm:w-64 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
              />
            </div>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-5xl px-4 py-6 sm:px-6" id="main-content">
        {!targetOk && !actuatorLoading && (
          <div
            role="status"
            className="mb-5 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900"
          >
            <p className="font-medium">Target unreachable — playground and live probe are paused.</p>
            <p className="mt-1 text-amber-800">
              Apply a scenario from the operator console first, then refresh status or fix the API base URL.
            </p>
          </div>
        )}

        <TabNav
          activeId={activeTab}
          onChange={setActiveTab}
          tabs={tabs}
          aria-label="Verify workflow sections"
        />

        <div className="mt-5">
          <TabPanel id="status" activeId={activeTab}>
            <ActuatorPanel
              status={actuatorStatus}
              assaults={actuatorAssaults}
              error={actuatorError}
              loading={actuatorLoading}
            />
          </TabPanel>

          <TabPanel id="playground" activeId={activeTab}>
            <ApiPlayground
              apiBaseUrl={apiBaseUrl}
              variant={target.playground}
              targetLabel={target.label}
              disabled={!targetOk}
              results={apiResults}
              onResult={recordApiResult}
            />
          </TabPanel>

          <TabPanel id="telemetry" activeId={activeTab}>
            <LiveTelemetryPanel
              demoUrl={apiBaseUrl}
              variant={target.playground}
              results={apiResults}
              onResult={recordApiResult}
              onClear={() => setApiResults([])}
              disabled={!targetOk}
              actuatorStatus={actuatorStatus}
              actuatorAssaults={actuatorAssaults}
            />
          </TabPanel>
        </div>
      </main>

      <footer className="border-t border-slate-200 bg-white py-4 text-center text-xs text-slate-500">
        Verify surface only — operator control plane lives on chaos-command-relay
      </footer>
    </div>
  );
}
