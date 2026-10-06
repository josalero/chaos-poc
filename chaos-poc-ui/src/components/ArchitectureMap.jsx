import { hotRoutes, resultsForTarget, routeTrafficTone, summarizeResults } from '../utils/telemetry';

const NODES = [
  { id: 'audience', label: 'Customers', detail: 'HTTP clients', x: 3, y: 30, tone: 'neutral' },
  { id: 'demo', label: 'Order service', detail: 'chaos-poc-demo', x: 26, y: 28, tone: 'service', app: 'chaos-poc-demo' },
  { id: 'downstream', label: 'Inventory + Auth', detail: 'chaos-poc-downstream', x: 56, y: 28, tone: 'service', app: 'chaos-poc-downstream' },
  { id: 'relay', label: 'Operator / relay', detail: 'patch control plane', x: 26, y: 78, tone: 'control' },
  { id: 'discovery', label: 'Eureka', detail: 'UP instances', x: 56, y: 78, tone: 'broker' },
];

const ROUTES = [
  { id: 'request', from: 'audience', to: 'demo', label: 'POST/GET /orders' },
  { id: 'inventory', from: 'demo', to: 'downstream', label: 'HTTP /inventory + /auth' },
  { id: 'publish', from: 'relay', to: 'discovery', label: 'resolve UP' },
  { id: 'fanout-demo', from: 'relay', to: 'demo', label: 'HTTP push' },
  { id: 'fanout-downstream', from: 'relay', to: 'downstream', label: 'HTTP push' },
];

function meshState(node, services, controlPlaneOnline, apiResults) {
  if ((node.id === 'relay' || node.id === 'discovery') && !controlPlaneOnline) return 'unknown';
  if (!node.app) return 'healthy';

  const service = services.find((entry) => entry.applicationName === node.app);
  if (!service) return 'unknown';

  const traffic = summarizeResults(resultsForTarget(apiResults, node.app).slice(0, 20));
  const replicaGap = (service.eurekaUpCount ?? 0) < (service.expectedInstances ?? 1);

  if (service.configState === 'FAILED' || service.lastCommandStatus === 'FAILED' || replicaGap) {
    return 'failing';
  }
  if (traffic.count && traffic.errorRate >= 40) return 'failing';
  if (service.cmEnabled || (traffic.count && traffic.errorRate > 0)) return 'degraded';
  return 'healthy';
}

function formatMetric(metrics) {
  if (!metrics?.count) return null;
  return `p95 ${metrics.p95}ms · err ${metrics.errorRate}%`;
}

function replicaNames(service, serviceDetail) {
  const expected = Math.max(service?.expectedInstances ?? 0, service?.eurekaUpCount ?? 0, 0);
  const fromCommand = serviceDetail?.lastCommandInstances;
  if (Array.isArray(fromCommand) && fromCommand.length) {
    return fromCommand.map((instance, index) => instance.podName || `replica-${index + 1}`);
  }
  return Array.from({ length: expected }, (_, index) => `replica-${index + 1}`);
}

/** Two (or N) visible pod cards under each service — not just a 2/2 counter. */
function ReplicaCluster({ service, serviceDetail, meshTone }) {
  if (!service) return null;
  const up = service.eurekaUpCount ?? 0;
  const names = replicaNames(service, serviceDetail);
  if (!names.length) return null;
  const tone = meshTone === 'failing' || meshTone === 'degraded' ? meshTone : 'healthy';

  return (
    <div className="replica-cluster" aria-label={`${up} of ${names.length} replicas`}>
      {names.map((name, index) => {
        const isUp = index < up;
        const podTone = !isUp ? 'down' : tone;
        return (
          <div key={`${name}-${index}`} className={`replica-pod mesh-${podTone}`}>
            <span className="replica-pod-icon" aria-hidden>▣</span>
            <span className="replica-pod-copy">
              <strong>{name}</strong>
              <small>{!isUp ? 'DOWN' : meshTone === 'failing' ? 'ERRORS' : meshTone === 'degraded' ? 'CHAOS' : 'UP'}</small>
            </span>
          </div>
        );
      })}
    </div>
  );
}

export default function ArchitectureMap({
  services,
  serviceDetails,
  selectedService,
  onSelectService,
  controlPlaneOnline,
  telemetryByService = {},
  apiResults = [],
}) {
  const pulsing = hotRoutes(apiResults, serviceDetails);
  const systemHealthy = services.length > 0
    && services.every((service) => {
      const traffic = summarizeResults(resultsForTarget(apiResults, service.applicationName).slice(0, 20));
      return !service.cmEnabled
        && service.configState !== 'FAILED'
        && (service.eurekaUpCount ?? 0) >= (service.expectedInstances ?? 1)
        && (!traffic.count || traffic.errorRate === 0);
    });

  return (
    <section className="mesh-card" aria-labelledby="architecture-title">
      <div className="mesh-heading">
        <div>
          <p className="eyebrow">LIVE SERVICE MESH</p>
          <h2 id="architecture-title">Services &amp; replicas</h2>
          <p>
            {systemHealthy
              ? 'Each service shows its pods as separate cards — both replicas visible.'
              : 'Each pod card is a replica. Color = healthy / chaos / failing traffic.'}
          </p>
        </div>
        <div className="mesh-legend" aria-label="Topology legend">
          <span><i className="legend-dot healthy" /> Healthy pod</span>
          <span><i className="legend-dot degraded" /> Chaos on pod</span>
          <span><i className="legend-dot failing" /> Failing traffic</span>
          <span><i className="legend-line hot" /> Live traffic</span>
        </div>
      </div>

      <div className="topology topology-with-replicas" role="img" aria-label="Live architecture topology with replica pods">
        <svg viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
          {ROUTES.map((route) => {
            const from = NODES.find((node) => node.id === route.from);
            const to = NODES.find((node) => node.id === route.to);
            const traffic = routeTrafficTone(route.id, apiResults);
            const isHot = pulsing.has(route.id);
            const stateClass = traffic === 'error' || traffic === 'warn' ? 'affected' : 'healthy';
            const classes = [
              'route-line',
              stateClass,
              isHot ? 'hot' : '',
              traffic === 'error' ? 'traffic-error' : traffic === 'warn' ? 'traffic-warn' : traffic === 'ok' ? 'traffic-ok' : '',
            ].filter(Boolean).join(' ');
            return (
              <g key={route.id}>
                <line
                  x1={from.x + 10}
                  y1={from.y + 8}
                  x2={to.x + 10}
                  y2={to.y + 8}
                  className={classes}
                  vectorEffect="non-scaling-stroke"
                />
                <text x={(from.x + to.x) / 2 + 10} y={(from.y + to.y) / 2 + 4} className={`route-label ${stateClass} ${isHot ? 'hot' : ''}`}>
                  {route.label}
                </text>
              </g>
            );
          })}
        </svg>

        {NODES.map((node) => {
          const state = meshState(node, services, controlPlaneOnline, apiResults);
          const selectable = Boolean(node.app);
          const summary = node.app ? services.find((entry) => entry.applicationName === node.app) : null;
          const detail = node.app ? serviceDetails[node.app] : null;
          const metrics = node.app ? telemetryByService[node.app] : null;
          const metricLabel = formatMetric(metrics);
          const stateLabel = state === 'degraded' ? 'DEGRADED' : state === 'failing' ? 'FAILING' : state.toUpperCase();
          return (
            <button
              key={node.id}
              type="button"
              disabled={!selectable}
              onClick={() => selectable && onSelectService(node.app)}
              className={`topology-node ${node.tone} mesh-${state} ${selectedService === node.app ? 'selected' : ''} ${selectable ? 'has-replica-cluster' : ''}`}
              style={{ left: `${node.x}%`, top: `${node.y}%` }}
              aria-label={`${node.label}: ${stateLabel}${summary ? `, ${summary.eurekaUpCount} of ${summary.expectedInstances} replicas` : ''}`}
            >
              <span className="node-icon" aria-hidden>
                {node.id === 'discovery' ? '⇄' : node.id === 'relay' ? '⌁' : node.id === 'audience' ? '◎' : '⬡'}
              </span>
              <span className="node-copy">
                <strong>{node.label}</strong>
                <small>{node.detail}</small>
                {metricLabel && <small className="node-metrics">{metricLabel}</small>}
              </span>
              <span className={`node-state mesh-${state}`}>{stateLabel}</span>
              {selectable && (
                <ReplicaCluster service={summary} serviceDetail={detail} meshTone={state} />
              )}
            </button>
          );
        })}
      </div>
    </section>
  );
}
