import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { fetchChaosService, fetchChaosServices } from './api/chaosClient';
import ArchitectureMap from './components/ArchitectureMap';
import RuntimeStatus from './components/RuntimeStatus';
import OutcomeGuide from './components/OutcomeGuide';
import ApiPlayground from './components/ApiPlayground';
import LiveTelemetryStrip from './components/LiveTelemetryStrip';
import { telemetryByService } from './utils/telemetry';

const OPERATOR_CONSOLE_URL = import.meta.env.VITE_OPERATOR_CONSOLE_URL || 'http://localhost:18090/chaos';

const TARGETS = {
  'chaos-poc-demo': {
    apiBase: import.meta.env.VITE_DEMO_URL || '/api/demo',
    variant: 'demo',
    friendlyName: 'Order service',
  },
  'chaos-poc-downstream': {
    apiBase: import.meta.env.VITE_DOWNSTREAM_URL || '/api/downstream',
    variant: 'downstream',
    friendlyName: 'Inventory + Auth',
  },
};

const RESET_RECOVERY_SCOPE = {
  'chaos-poc-demo': ['chaos-poc-demo'],
  'chaos-poc-downstream': ['chaos-poc-downstream', 'chaos-poc-demo'],
};

const THEME_STORAGE_KEY = 'chaos-observatory-theme';

function readStoredTheme() {
  try {
    const stored = window.localStorage.getItem(THEME_STORAGE_KEY);
    return stored === 'dark' ? 'dark' : 'light';
  } catch {
    return 'light';
  }
}

export default function App() {
  const [theme, setTheme] = useState(readStoredTheme);
  const [services, setServices] = useState([]);
  const [serviceDetails, setServiceDetails] = useState({});
  const [selectedService, setSelectedService] = useState('chaos-poc-demo');
  const [loading, setLoading] = useState(true);
  const [connectionError, setConnectionError] = useState('');
  const [apiResults, setApiResults] = useState([]);
  const handledResetCommands = useRef(new Set());
  const selectedTarget = TARGETS[selectedService] || TARGETS['chaos-poc-demo'];

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
    try {
      window.localStorage.setItem(THEME_STORAGE_KEY, theme);
    } catch {
      // ignore storage failures (private mode)
    }
  }, [theme]);

  const refreshTopology = useCallback(async ({ silent = false } = {}) => {
    if (!silent) setLoading(true);
    try {
      const summaries = await fetchChaosServices();
      const detailPairs = await Promise.all(summaries.map(async (summary) => {
        const detail = await fetchChaosService(summary.applicationName);
        return [summary.applicationName, detail];
      }));
      setServices(summaries);
      setServiceDetails(Object.fromEntries(detailPairs));
      setConnectionError('');
    } catch (error) {
      setConnectionError(error.message);
    } finally {
      if (!silent) setLoading(false);
    }
  }, []);

  useEffect(() => { refreshTopology(); }, [refreshTopology]);
  useEffect(() => {
    const timer = window.setInterval(() => refreshTopology({ silent: true }), 3000);
    return () => window.clearInterval(timer);
  }, [refreshTopology]);

  useEffect(() => {
    const resetServices = Object.values(serviceDetails)
      .filter((detail) => (
        detail?.lastCommandId
        && detail.lastCommandAction === 'DISABLE'
        && detail.lastCommandStatus === 'APPLIED'
        && detail.cmEnabled === false
        && !handledResetCommands.current.has(detail.lastCommandId)
      ));

    if (!resetServices.length) return;

    resetServices.forEach((detail) => handledResetCommands.current.add(detail.lastCommandId));
    const resetApplications = new Set(
      resetServices.flatMap((detail) => (
        RESET_RECOVERY_SCOPE[detail.applicationName] || [detail.applicationName]
      )),
    );
    setApiResults((current) => current.filter((result) => {
      const resultTarget = result.target || 'chaos-poc-demo';
      return !resetApplications.has(resultTarget);
    }));
  }, [serviceDetails]);

  const recordResult = useCallback((result) => {
    const tagged = { ...result, target: result.target || selectedService };
    setApiResults((current) => [tagged, ...current].slice(0, 80));
  }, [selectedService]);

  const clearSelectedResults = useCallback(() => {
    setApiResults((current) => current.filter((result) => result.target && result.target !== selectedService));
  }, [selectedService]);

  const selectedDetail = serviceDetails[selectedService];
  const nodeTelemetry = useMemo(() => telemetryByService(apiResults), [apiResults]);
  const selectedTraffic = nodeTelemetry[selectedService];
  const replicaTotal = services.reduce((sum, service) => sum + (service.eurekaUpCount || 0), 0);
  const failingCount = services.filter((service) => {
    const traffic = nodeTelemetry[service.applicationName];
    return service.configState === 'FAILED'
      || (service.eurekaUpCount ?? 0) < (service.expectedInstances ?? 1)
      || (traffic?.count && traffic.errorRate >= 40);
  }).length;
  const chaosCount = services.filter((service) => service.cmEnabled).length;
  const healthLabel = connectionError
    ? 'TOPOLOGY OFFLINE'
    : failingCount
      ? `${failingCount} FAILING`
      : chaosCount
        ? `${chaosCount} DEGRADED`
        : 'ALL GREEN';

  return (
    <div className="app-shell">
      <header className="command-header">
        <div className="brand-lockup">
          <span className="brand-mark">CM</span>
          <div>
            <strong>Chaos Observatory</strong>
            <small>Live mesh · customer traffic · telemetry</small>
          </div>
        </div>
        <nav className="observatory-nav" aria-label="Quick links">
          <a href={OPERATOR_CONSOLE_URL} target="_blank" rel="noreferrer">Operator console ↗</a>
        </nav>
        <div className="header-actions">
          <span className={`system-pill ${failingCount ? 'error' : chaosCount ? 'chaos' : connectionError ? 'error' : 'healthy'}`}>
            <i />
            {healthLabel}
          </span>
          <button
            type="button"
            className="theme-toggle"
            aria-label={theme === 'light' ? 'Switch to dark theme' : 'Switch to light theme'}
            onClick={() => setTheme((current) => (current === 'light' ? 'dark' : 'light'))}
          >
            {theme === 'light' ? 'Dark' : 'Light'}
          </button>
        </div>
      </header>

      <main>
        <section className="hero-strip">
          <div>
            <p className="eyebrow">VERIFY / OBSERVE</p>
            <h1>{failingCount ? 'Mesh is reporting failures' : chaosCount ? 'Fault injection is visible on the mesh' : 'System looks healthy'}</h1>
            <p>
              Place real orders against the APIs. Patch Chaos Monkey in the
              {' '}
              <a href={OPERATOR_CONSOLE_URL} target="_blank" rel="noreferrer">Operator console</a>
              {' '}
              — this view only observes services, replicas, and traffic.
            </p>
          </div>
          <div className="hero-stats">
            <div><span>Services</span><strong>{services.length || '—'}</strong></div>
            <div><span>Replicas up</span><strong>{replicaTotal || '—'}</strong></div>
            <div><span>Captured calls</span><strong>{apiResults.length}</strong></div>
          </div>
        </section>

        {connectionError && (
          <div className="global-alert">
            <strong>Relay unavailable for topology.</strong>
            {' '}
            {connectionError}
            {' '}
            Order APIs may still work via the gateway.
          </div>
        )}

        <div className="observatory-layout">
          <div className="observatory-main">
            <ArchitectureMap
              services={services}
              serviceDetails={serviceDetails}
              selectedService={selectedService}
              onSelectService={setSelectedService}
              controlPlaneOnline={!connectionError}
              telemetryByService={nodeTelemetry}
              apiResults={apiResults}
            />
            <LiveTelemetryStrip
              target={selectedTarget}
              selectedService={selectedService}
              results={apiResults}
              onResult={recordResult}
              onClear={clearSelectedResults}
              disabled={false}
            />
          </div>

          <aside className="observatory-side">
            <RuntimeStatus
              service={selectedDetail || services.find((entry) => entry.applicationName === selectedService)}
              loading={loading}
              traffic={selectedTraffic}
              friendlyName={selectedTarget.friendlyName}
            />
            <section className="run-panel" aria-labelledby="run-panel-title">
              <div className="run-panel-header">
                <div>
                  <p className="eyebrow">CUSTOMER TRAFFIC</p>
                  <h2 id="run-panel-title">
                    {selectedTarget.variant === 'demo' ? 'Place & look up orders' : 'Call inventory / auth'}
                  </h2>
                  <p>Traffic updates the mesh colors and the live strip.</p>
                </div>
                <div className="target-chips" role="group" aria-label="Target service">
                  {Object.entries(TARGETS).map(([name, target]) => (
                    <button
                      key={name}
                      type="button"
                      className={selectedService === name ? 'active' : ''}
                      onClick={() => setSelectedService(name)}
                    >
                      {target.friendlyName.split(' ')[0]}
                    </button>
                  ))}
                </div>
              </div>
              <ApiPlayground
                apiBaseUrl={selectedTarget.apiBase}
                variant={selectedTarget.variant}
                targetLabel={selectedService}
                disabled={false}
                results={apiResults.filter((result) => !result.target || result.target === selectedService)}
                onResult={recordResult}
              />
            </section>
          </aside>

          <OutcomeGuide service={selectedDetail} />
        </div>
      </main>
    </div>
  );
}
