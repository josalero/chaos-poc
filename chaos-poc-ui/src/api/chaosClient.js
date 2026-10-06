function joinUrl(base, path) {
  return `${base.replace(/\/$/, '')}${path}`;
}

export async function fetchChaosMonkeyStatus(demoBaseUrl) {
  const response = await fetch(joinUrl(demoBaseUrl, '/actuator/chaosmonkey/status'));
  if (!response.ok) {
    throw new Error(`Chaos Monkey status failed (${response.status})`);
  }
  return response.json();
}

export async function fetchChaosMonkeyAssaults(demoBaseUrl) {
  const response = await fetch(joinUrl(demoBaseUrl, '/actuator/chaosmonkey/assaults'));
  if (!response.ok) {
    throw new Error(`Chaos Monkey assaults failed (${response.status})`);
  }
  return response.json();
}

export async function callDemoApi(demoBaseUrl, method, path, body) {
  const response = await fetch(joinUrl(demoBaseUrl, path), {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await response.text();
  let parsed;
  try {
    parsed = text ? JSON.parse(text) : null;
  } catch {
    parsed = text;
  }
  return { status: response.status, body: parsed };
}

export async function fetchDemoHealth(demoBaseUrl) {
  const response = await fetch(joinUrl(demoBaseUrl, '/actuator/health'));
  if (!response.ok) {
    throw new Error(`Demo health failed (${response.status})`);
  }
  return response.json();
}

const RELAY_BASE = import.meta.env.VITE_RELAY_URL || '/api/relay';

async function relayRequest(path, options) {
  const response = await fetch(joinUrl(RELAY_BASE, path), options);
  const text = await response.text();
  let body = null;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    throw new Error('The chaos relay is not reachable');
  }
  if (!response.ok) {
    const message = body?.errors?.map((error) => error.message).join(', ');
    throw new Error(message || `Relay request failed (${response.status})`);
  }
  return body;
}

export function fetchChaosServices() {
  return relayRequest('/internal/v1/chaos/services');
}

export function fetchChaosService(applicationName) {
  return relayRequest(`/internal/v1/chaos/services/${applicationName}`);
}
