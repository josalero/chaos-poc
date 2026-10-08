import { describe, expect, it } from 'vitest';
import { presetsFor } from './catalog.js';

describe('presetsFor', () => {
  it('includes templates for an allowlisted name that has no preset of its own', () => {
    const presets = presetsFor('orders');
    const ids = presets.map((preset) => preset.id);

    expect(ids).toContain('template-latency');
    expect(ids).toContain('template-exception');
    expect(ids).toContain('template-disable');
    expect(presets.every((preset) => !preset.targetApplication || preset.targetApplication === 'orders')).toBe(
      true,
    );
  });

  it('keeps a service preset beside the templates', () => {
    const ids = presetsFor('chaos-poc-demo').map((preset) => preset.id);

    expect(ids).toContain('latency-success-path');
    expect(ids).toContain('template-latency');
  });
});
