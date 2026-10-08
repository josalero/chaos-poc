import { describe, expect, it } from 'vitest';
import { cmLabel, filterServices, pageServices, sortServices } from './serviceTable.js';

const services = [
  { applicationName: 'orders', title: 'Order service', cmEnabled: true, configState: 'APPLIED', eurekaUpCount: 2, lastCommandStatus: 'APPLIED' },
  { applicationName: 'billing', title: 'Billing', cmEnabled: false, configState: 'FAILED', eurekaUpCount: 0, lastCommandStatus: 'FAILED' },
  { applicationName: 'gateway', title: 'Edge', cmEnabled: null, configState: 'DEFAULT', eurekaUpCount: 1, lastCommandStatus: null },
];

describe('filterServices', () => {
  it('filters by name, chaos monkey state, and config state', () => {
    expect(filterServices(services, { query: 'order' })).toEqual([services[0]]);
    expect(filterServices(services, { cm: 'on' })).toEqual([services[0]]);
    expect(filterServices(services, { cm: 'off' })).toEqual([services[1]]);
    expect(filterServices(services, { cm: 'unknown' })).toEqual([services[2]]);
    expect(filterServices(services, { configState: 'DEFAULT' })).toEqual([services[2]]);
    expect(filterServices(services, { cm: 'any' })).toHaveLength(3);
  });
});

describe('sortServices', () => {
  it('sorts by name, chaos monkey, config state, replicas, and last status', () => {
    expect(sortServices(services, { key: 'name', direction: 'asc' }).map((row) => row.applicationName)).toEqual([
      'billing',
      'gateway',
      'orders',
    ]);
    expect(sortServices(services, { key: 'name', direction: 'desc' })[0].applicationName).toBe('orders');
    expect(sortServices(services, { key: 'cm' })[0].applicationName).toBe('orders');
    expect(sortServices(services, { key: 'up', direction: 'desc' })[0].applicationName).toBe('orders');
    expect(sortServices(services, { key: 'configState' })[0].configState).toBe('APPLIED');
    expect(sortServices(services, { key: 'lastStatus', direction: 'desc' })[0].lastCommandStatus).toBe('FAILED');
  });
});

describe('pageServices', () => {
  it('returns one page and clamps a page past the end', () => {
    const rows = Array.from({ length: 25 }, (_, index) => ({ applicationName: `svc-${index}` }));
    const first = pageServices(rows, 0, 20);

    expect(first.content).toHaveLength(20);
    expect(first.pages).toBe(2);
    expect(pageServices(rows, 5, 20).page).toBe(1);
    expect(pageServices([], 0, 20)).toMatchObject({ page: 0, total: 0, pages: 1, content: [] });
  });
});

describe('cmLabel', () => {
  it('labels on, off, and unknown', () => {
    expect(cmLabel(services[0])).toBe('On');
    expect(cmLabel(services[1])).toBe('Off');
    expect(cmLabel(services[2])).toBe('Unknown');
  });
});
