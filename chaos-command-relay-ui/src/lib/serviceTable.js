/** Client-side filter, sort, and paging for the allowlisted services table. */

export const PAGE_SIZE = 20;

const CM_RANK = new Map([
  [true, 0],
  [false, 1],
]);

export function filterServices(services, filters) {
  const query = (filters.query || '').trim().toLowerCase();
  return services.filter((service) => {
    const name = (service.applicationName || '').toLowerCase();
    const title = (service.title || '').toLowerCase();
    if (query && !name.includes(query) && !title.includes(query)) {
      return false;
    }
    if (filters.cm === 'on' && service.cmEnabled !== true) {
      return false;
    }
    if (filters.cm === 'off' && service.cmEnabled !== false) {
      return false;
    }
    if (filters.cm === 'unknown' && service.cmEnabled != null) {
      return false;
    }
    if (filters.configState && service.configState !== filters.configState) {
      return false;
    }
    return true;
  });
}

export function sortServices(services, sort) {
  const key = sort.key || 'name';
  const direction = sort.direction === 'desc' ? -1 : 1;
  return [...services].sort((left, right) => direction * compare(left, right, key) || compareName(left, right));
}

export function pageServices(services, page, pageSize = PAGE_SIZE) {
  const total = services.length;
  const pages = Math.max(1, Math.ceil(total / pageSize) || 1);
  const current = Math.min(Math.max(0, page), pages - 1);
  const start = current * pageSize;
  return {
    page: current,
    pageSize,
    total,
    pages,
    content: services.slice(start, start + pageSize),
  };
}

export function cmLabel(service) {
  if (service.cmEnabled === true) {
    return 'On';
  }
  if (service.cmEnabled === false) {
    return 'Off';
  }
  return 'Unknown';
}

function compare(left, right, key) {
  if (key === 'cm') {
    return rank(left.cmEnabled) - rank(right.cmEnabled);
  }
  if (key === 'configState') {
    return text(left.configState).localeCompare(text(right.configState));
  }
  if (key === 'up') {
    return (left.eurekaUpCount || 0) - (right.eurekaUpCount || 0);
  }
  if (key === 'lastStatus') {
    return text(left.lastCommandStatus).localeCompare(text(right.lastCommandStatus));
  }
  return compareName(left, right);
}

function compareName(left, right) {
  return text(left.applicationName).localeCompare(text(right.applicationName));
}

function rank(enabled) {
  return CM_RANK.get(enabled) ?? 2;
}

function text(value) {
  return value || '';
}
