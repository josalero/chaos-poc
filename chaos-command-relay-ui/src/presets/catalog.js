import { descriptionFor, labelFor } from '../lib/labels.js';

const modules = import.meta.glob('./*.json', { eager: true, import: 'default' });

export const presets = Object.entries(modules)
  .map(([path, preset]) => {
    const id = path.split('/').pop().replace(/\.json$/, '');
    return {
      id,
      ...preset,
      label: labelFor(id),
      description: descriptionFor(id, preset.action, preset.targetApplication),
    };
  })
  .sort(
    (left, right) =>
      left.targetApplication.localeCompare(right.targetApplication) ||
      left.label.localeCompare(right.label),
  );

export function presetsFor(targetApplication) {
  return presets.filter((preset) => preset.targetApplication === targetApplication);
}

export function findPreset(id) {
  return presets.find((preset) => preset.id === id) || null;
}
