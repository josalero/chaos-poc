const RECENT_MS = 6000;

export function percentile(values, percentage) {
  if (values.length === 0) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  const index = Math.min(
    sorted.length - 1,
    Math.max(0, Math.ceil((percentage / 100) * sorted.length) - 1),
  );
  return sorted[index];
}

export function summarizeResults(results) {
  const durations = results.map((result) => result.durationMs || 0);
  let ok = 0;
  let clientError = 0;
  let serverError = 0;
  results.forEach((result) => {
    if (result.status == null || result.status >= 500) {
      serverError += 1;
    } else if (result.status >= 400) {
      clientError += 1;
    } else {
      ok += 1;
    }
  });
  const failures = clientError + serverError;
  return {
    count: results.length,
    p50: percentile(durations, 50),
    p95: percentile(durations, 95),
    errorRate: results.length ? Math.round((failures / results.length) * 100) : 0,
    ok,
    clientError,
    serverError,
  };
}

export function resultsForTarget(results, target) {
  return results.filter((result) => !result.target || result.target === target);
}

export function telemetryByService(results) {
  return {
    'chaos-poc-demo': summarizeResults(resultsForTarget(results, 'chaos-poc-demo')),
    'chaos-poc-downstream': summarizeResults(resultsForTarget(results, 'chaos-poc-downstream')),
  };
}

export function recentResults(results, withinMs = RECENT_MS) {
  const cutoff = Date.now() - withinMs;
  return results.filter((result) => {
    const at = Date.parse(result.at || '');
    return Number.isFinite(at) && at >= cutoff;
  });
}

/** Route ids that should pulse from recent traffic / errors. */
export function hotRoutes(results, serviceDetails) {
  const recent = recentResults(results);
  const hot = new Set();
  if (recent.some((result) => result.target === 'chaos-poc-demo' || !result.target)) {
    hot.add('request');
  }
  if (
    recent.some(
      (result) =>
        (result.target === 'chaos-poc-demo' || !result.target)
        && /submit|inventory|downstream|auth/i.test(result.label || ''),
    )
    || recent.some((result) => result.target === 'chaos-poc-downstream')
  ) {
    hot.add('inventory');
  }

  const demo = serviceDetails['chaos-poc-demo'];
  const downstream = serviceDetails['chaos-poc-downstream'];
  if (demo?.cmEnabled) hot.add('fanout-demo');
  if (downstream?.cmEnabled) hot.add('fanout-downstream');
  if (demo?.cmEnabled || downstream?.cmEnabled) hot.add('publish');

  return hot;
}

export function routeTrafficTone(routeId, results) {
  const recent = recentResults(results);
  const relevant = recent.filter((result) => {
    if (routeId === 'request') {
      return result.target === 'chaos-poc-demo' || !result.target;
    }
    if (routeId === 'inventory') {
      return (
        result.target === 'chaos-poc-downstream'
        || /submit|inventory|downstream|auth/i.test(result.label || '')
      );
    }
    return false;
  });
  if (!relevant.length) return null;
  if (relevant.some((result) => result.status == null || result.status >= 500)) return 'error';
  if (relevant.some((result) => result.status >= 400)) return 'warn';
  return 'ok';
}
