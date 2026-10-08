/** Pure list, selection, and history-limit helpers for the operator console. */

export function matchesFilters(service, filters) {
  const query = (filters.query || '').trim().toLowerCase();
  const title = (service.title || '').toLowerCase();
  const name = (service.applicationName || '').toLowerCase();
  if (query && !title.includes(query) && !name.includes(query)) {
    return false;
  }
  if (filters.cmOn && service.cmEnabled !== true) {
    return false;
  }
  if (filters.status && service.lastCommandStatus !== filters.status) {
    return false;
  }
  if (filters.zeroReplicas && service.eurekaUpCount !== 0) {
    return false;
  }
  return true;
}

export function selectionSummary(selectedNames, shownNames, total) {
  const shown = new Set(shownNames);
  const hidden = selectedNames.filter((name) => !shown.has(name)).length;
  return {
    selected: selectedNames.length,
    hidden,
    shown: shownNames.length,
    total,
  };
}

export function selectAllShown(selectedNames, shownNames, checked) {
  const next = new Set(selectedNames);
  for (const name of shownNames) {
    if (checked) {
      next.add(name);
    } else {
      next.delete(name);
    }
  }
  return [...next];
}

export function nextHistoryLimit(current, step = 50, max = 200) {
  return Math.min(max, current + step);
}

export function checkedAgo(iso, now = Date.now()) {
  if (!iso) {
    return 'unknown';
  }
  const seconds = Math.max(0, Math.round((now - Date.parse(iso)) / 1000));
  return `checked ${seconds}s ago`;
}
