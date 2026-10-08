import { describe, expect, it } from 'vitest';
import {
  checkedAgo,
  matchesFilters,
  nextHistoryLimit,
  selectAllShown,
  selectionSummary,
} from './serviceList.js';

const services = [
  { applicationName: 'orders', title: 'Order service', cmEnabled: true, lastCommandStatus: 'APPLIED', eurekaUpCount: 2 },
  { applicationName: 'billing', title: 'Billing', cmEnabled: false, lastCommandStatus: 'FAILED', eurekaUpCount: 0 },
  { applicationName: 'gateway', title: 'Edge', cmEnabled: null, lastCommandStatus: null, eurekaUpCount: 1 },
];

describe('matchesFilters', () => {
  it('hides rows that miss the search, chaos filter, status, or replica filter', () => {
    expect(services.filter((service) => matchesFilters(service, { query: 'order' }))).toHaveLength(1);
    expect(services.filter((service) => matchesFilters(service, { cmOn: true }))).toEqual([services[0]]);
    expect(services.filter((service) => matchesFilters(service, { status: 'FAILED' }))).toEqual([services[1]]);
    expect(services.filter((service) => matchesFilters(service, { zeroReplicas: true }))).toEqual([services[1]]);
  });
});

describe('selection', () => {
  it('keeps hidden selections and selects only the rows that are shown', () => {
    const shown = ['orders'];
    const selected = selectAllShown(['billing'], shown, true);

    expect(selected).toEqual(['billing', 'orders']);
    expect(selectionSummary(selected, shown, 3)).toEqual({
      selected: 2,
      hidden: 1,
      shown: 1,
      total: 3,
    });
    expect(selectAllShown(selected, shown, false)).toEqual(['billing']);
  });
});

describe('history limit', () => {
  it('raises the limit and stops at 200', () => {
    expect(nextHistoryLimit(50)).toBe(100);
    expect(nextHistoryLimit(200)).toBe(200);
  });

  it('formats an unknown check and a recent check', () => {
    expect(checkedAgo(null)).toBe('unknown');
    expect(checkedAgo('2026-10-07T12:00:00.000Z', Date.parse('2026-10-07T12:00:05.000Z'))).toBe(
      'checked 5s ago',
    );
  });
});
