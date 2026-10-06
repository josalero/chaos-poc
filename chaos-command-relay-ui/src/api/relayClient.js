import { RelayError } from '../lib/errors.js';

const apiBase = import.meta.env.VITE_RELAY_API_URL || '/api/relay';

export const verifyUiUrl = import.meta.env.VITE_VERIFY_UI_URL || 'http://localhost:18000';

async function request(path, options = {}) {
  const response = await fetch(`${apiBase}${path}`, {
    ...options,
    headers: {
      Accept: 'application/json',
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...options.headers,
    },
  });
  const text = await response.text();
  const body = text ? JSON.parse(text) : null;
  if (!response.ok) {
    throw new RelayError(response.status, body);
  }
  return body;
}

export function listServices() {
  return request('/internal/v1/chaos/services');
}

export function getService(applicationName) {
  return request(`/internal/v1/chaos/services/${encodeURIComponent(applicationName)}`);
}

export function getHistory(applicationName) {
  return request(`/internal/v1/chaos/services/${encodeURIComponent(applicationName)}/history`);
}

export function getActuator(applicationName) {
  return request(`/internal/v1/chaos/services/${encodeURIComponent(applicationName)}/actuator`);
}

export function submitCommand(command) {
  return request('/internal/v1/chaos/commands', {
    method: 'POST',
    body: JSON.stringify(command),
  });
}

export function getCommand(commandId) {
  return request(`/internal/v1/chaos/commands/${encodeURIComponent(commandId)}`);
}

function maintenance(applicationName, action, body) {
  return request(`/internal/v1/chaos/services/${encodeURIComponent(applicationName)}/${action}`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function enableService(applicationName, body) {
  return maintenance(applicationName, 'enable', body);
}

export function disableService(applicationName, body) {
  return maintenance(applicationName, 'disable', body);
}

export function resetService(applicationName, body) {
  return maintenance(applicationName, 'reset', body);
}

export function clearDemoData(applicationName) {
  return request(
    `/internal/v1/chaos/services/${encodeURIComponent(applicationName)}/clear-demo-data`,
    { method: 'POST' },
  );
}

export function resetAll(body) {
  return request('/internal/v1/chaos/services/reset-all', {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function consoleActor(expiresAt = null) {
  return {
    issuedBy: 'chaos-console',
    correlationId: null,
    expiresAt,
  };
}
