import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { commandFromForm, commandFromPreset } from './commands.js';

describe('commandFromPreset', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-06T12:00:00.000Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('sets a two-hour lease and keeps the preset assault', () => {
    const command = commandFromPreset({
      id: 'latency-success-path',
      targetApplication: 'chaos-poc-demo',
      action: 'CONFIGURE_AND_ENABLE',
      assault: { level: 1, latencyActive: true },
    });

    expect(command).toEqual({
      environment: 'test',
      targetApplication: 'chaos-poc-demo',
      action: 'CONFIGURE_AND_ENABLE',
      assault: { level: 1, latencyActive: true },
      expiresAt: '2026-10-06T14:00:00.000Z',
      issuedBy: 'chaos-console',
      correlationId: 'quick-latency-success-path',
    });
  });

  it('omits the assault and lease when the preset disables Chaos Monkey', () => {
    const command = commandFromPreset({
      id: 'disable',
      environment: 'test',
      targetApplication: 'chaos-poc-demo',
      action: 'DISABLE',
      assault: { level: 1 },
    });

    expect(command.assault).toBeNull();
    expect(command.expiresAt).toBeNull();
    expect(command.issuedBy).toBe('chaos-console');
    expect(command.correlationId).toBe('quick-disable');
  });
});

describe('commandFromForm', () => {
  it('uses custom assault JSON ahead of the selected preset', () => {
    const command = commandFromForm({
      targetApplication: 'chaos-poc-demo',
      action: 'CONFIGURE',
      issuedBy: ' operator ',
      correlationId: '  ',
      expiresAt: '',
      assaultJson: '{"level":3}',
      preset: { assault: { level: 1 } },
    });

    expect(command.assault).toEqual({ level: 3 });
    expect(command.issuedBy).toBe('operator');
    expect(command.correlationId).toBeNull();
    expect(command.expiresAt).toBeNull();
    expect(command.environment).toBe('test');
  });

  it('drops the assault when the action is DISABLE', () => {
    const command = commandFromForm({
      targetApplication: 'chaos-poc-downstream',
      action: 'DISABLE',
      issuedBy: 'chaos-console',
      correlationId: 'manual-1',
      expiresAt: '2026-10-06T18:00:00Z',
      assaultJson: '{"level":1}',
      preset: null,
    });

    expect(command.assault).toBeNull();
    expect(command.correlationId).toBe('manual-1');
  });
});
