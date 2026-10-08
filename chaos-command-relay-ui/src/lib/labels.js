const LABELS = {
  'bean-interceptor': 'Bean interceptor exception',
  disable: 'Disable Chaos Monkey',
  'downstream-disable': 'Disable Chaos Monkey',
  'downstream-inventory-exception': 'Inventory lookup fails',
  'downstream-inventory-latency': 'Inventory lookup latency',
  'downstream-reserve-exception': 'Reserve inventory fails',
  'exception-http-403': 'HTTP 403 forbidden',
  'exception-http-404': 'HTTP 404 not found',
  'exception-http-409': 'HTTP 409 conflict',
  'exception-http-500-create': 'HTTP 500 on create',
  'exception-http-500-submit': 'HTTP 500 on submit',
  'high-pressure-latency': 'High-pressure latency',
  'latency-success-path': 'Latency on success path',
  'service-to-service-latency': 'Service-to-service latency',
  'template-latency': 'Latency template',
  'template-exception': 'Exception template',
  'template-disable': 'Disable template',
};

const DESCRIPTIONS = {
  'bean-interceptor': 'Throws on a watched bean method in the order service.',
  disable: 'Turns Chaos Monkey off on the order service.',
  'downstream-disable': 'Turns Chaos Monkey off on inventory/auth.',
  'downstream-inventory-exception': 'Makes inventory lookups fail so demo calls surface errors.',
  'downstream-inventory-latency': 'Adds latency to inventory lookups on the downstream service.',
  'downstream-reserve-exception': 'Fails reserve inventory so submit/create paths break.',
  'exception-http-403': 'Forces a 403 response on a watched demo path.',
  'exception-http-404': 'Forces a 404 response on create/order lookup.',
  'exception-http-409': 'Forces a 409 conflict on create.',
  'exception-http-500-create': 'Forces a 500 when creating an order.',
  'exception-http-500-submit': 'Forces a 500 when submitting an order.',
  'high-pressure-latency': 'Injects heavy latency so p95 climbs under load.',
  'latency-success-path': 'Adds latency while still returning success — best first demo.',
  'service-to-service-latency': 'Slows downstream calls from the order service.',
  'template-latency': 'Adds 200–800 ms of latency on the selected service.',
  'template-exception': 'Turns exceptions on for the selected service.',
  'template-disable': 'Turns Chaos Monkey off on the selected service.',
};

const SERVICE_TITLES = {
  'chaos-poc-demo': 'Order service',
  'chaos-poc-downstream': 'Inventory + Auth',
};

export function labelFor(id) {
  return LABELS[id] || id.replaceAll('-', ' ');
}

export function descriptionFor(id, action, targetApplication) {
  if (DESCRIPTIONS[id]) {
    return DESCRIPTIONS[id];
  }
  if (action === 'DISABLE') {
    return `Disables Chaos Monkey on ${targetApplication}.`;
  }
  return `Applies a chaos assault on ${targetApplication}.`;
}

export function serviceTitle(applicationName) {
  return SERVICE_TITLES[applicationName] || applicationName;
}
