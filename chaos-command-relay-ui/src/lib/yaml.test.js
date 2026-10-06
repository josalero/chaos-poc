import { describe, expect, it } from 'vitest';
import { chaosMonkeyYaml } from './yaml.js';

describe('chaosMonkeyYaml', () => {
  it('writes a disabled default assault', () => {
    const yaml = chaosMonkeyYaml({ action: 'DISABLE' });

    expect(yaml).toContain('enabled: false');
    expect(yaml).toContain('latency-active: false');
    expect(yaml).toContain('watched-custom-services: []');
    expect(yaml).toContain("type: 'java.lang.RuntimeException'");
    expect(yaml.endsWith('\n')).toBe(false);
  });

  it('writes enable without an assault block', () => {
    expect(chaosMonkeyYaml({ action: 'ENABLE' })).toBe('chaos:\n  monkey:\n    enabled: true');
  });

  it('quotes watched services and a custom exception', () => {
    const yaml = chaosMonkeyYaml({
      action: 'CONFIGURE_AND_ENABLE',
      assault: {
        level: 2,
        deterministic: false,
        latencyActive: true,
        latencyRangeStart: 100,
        latencyRangeEnd: 400,
        exceptionsActive: false,
        watchedCustomServices: ["order's.create"],
        exception: {
          type: 'java.lang.IllegalStateException',
          method: '<init>',
          arguments: [{ type: 'java.lang.String', value: "can't" }],
        },
      },
    });

    expect(yaml).toContain('enabled: true');
    expect(yaml).toContain('level: 2');
    expect(yaml).toContain('deterministic: false');
    expect(yaml).toContain('latency-range-start: 100');
    expect(yaml).toContain("        - 'order''s.create'");
    expect(yaml).toContain("value: 'can''t'");
  });
});
