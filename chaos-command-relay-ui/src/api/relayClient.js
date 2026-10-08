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

export function listCatalog(applicationName) {
  return request(`/internal/v1/chaos/services/${encodeURIComponent(applicationName)}/catalog`);
}

export function listSavedCatalog(application) {
  const query = application ? `?application=${encodeURIComponent(application)}` : '';
  return request(`/internal/v1/chaos/catalog${query}`);
}

export function listCommands({ page = 0, size = 50, application, status, action } = {}) {
  const params = new URLSearchParams();
  params.set('page', String(page));
  params.set('size', String(size));
  if (application) {
    params.set('application', application);
  }
  if (status) {
    params.set('status', status);
  }
  if (action) {
    params.set('action', action);
  }
  return request(`/internal/v1/chaos/commands?${params}`);
}

export function saveCatalog(applicationName, entry) {
  return request(`/internal/v1/chaos/services/${encodeURIComponent(applicationName)}/catalog`, {
    method: 'POST',
    body: JSON.stringify(entry),
  });
}

export function deleteCatalog(applicationName, catalogId) {
  return request(
    `/internal/v1/chaos/services/${encodeURIComponent(applicationName)}/catalog/${encodeURIComponent(catalogId)}`,
    { method: 'DELETE' },
  );
}

export function getHistory(applicationName, limit = 50) {
  const bounded = Math.min(200, Math.max(1, limit));
  return request(
    `/internal/v1/chaos/services/${encodeURIComponent(applicationName)}/history?limit=${bounded}`,
  );
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

export function resetSelected(applicationNames) {
  return request('/internal/v1/chaos/services/reset', {
    method: 'POST',
    body: JSON.stringify({ issuedBy: 'chaos-console', applicationNames }),
  });
}

export function consoleActor(expiresAt = null) {
  return {
    issuedBy: 'chaos-console',
    correlationId: null,
    expiresAt,
  };
}
