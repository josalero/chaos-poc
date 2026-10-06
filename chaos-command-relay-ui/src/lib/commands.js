const TWO_HOURS_MS = 2 * 60 * 60 * 1000;

export function leaseExpiresAt(from = new Date()) {
  return new Date(from.getTime() + TWO_HOURS_MS).toISOString();
}

/**
 * The POST body reconstructed from a stored command. Environment is always test.
 * SOME includes instanceIds; ALL omits them.
 */
export function commandRequestJson(entry) {
  const instanceSelection = entry.instanceSelection || 'ALL';
  const request = {
    commandId: entry.commandId,
    environment: 'test',
    targetApplication: entry.targetApplication,
    action: entry.action,
    assault: entry.assault ?? null,
    expiresAt: entry.expiresAt ?? null,
    issuedBy: entry.issuedBy ?? null,
    correlationId: entry.correlationId ?? null,
    instanceSelection,
  };
  if (instanceSelection === 'SOME') {
    request.instanceIds = entry.instanceIds || [];
  }
  return JSON.stringify(request, null, 2);
}

/** Quick-scenario payload. A two-hour lease is set unless the preset disables Chaos Monkey. */
export function commandFromPreset(preset, now = new Date()) {
  const disable = preset.action === 'DISABLE';
  return {
    environment: preset.environment || 'test',
    targetApplication: preset.targetApplication,
    action: preset.action,
    assault: disable ? null : (preset.assault ?? null),
    expiresAt: disable ? null : leaseExpiresAt(now),
    issuedBy: 'chaos-console',
    correlationId: `quick-${preset.id}`,
  };
}

/** Advanced publish form. Custom assault JSON wins over a selected preset. */
export function commandFromForm(form) {
  const disable = form.action === 'DISABLE';
  let assault = null;
  if (!disable && form.assaultJson && form.assaultJson.trim()) {
    assault = JSON.parse(form.assaultJson);
  } else if (!disable && form.preset) {
    assault = form.preset.assault ?? null;
  }
  const expiresAt = form.expiresAt && form.expiresAt.trim() ? form.expiresAt.trim() : null;
  const correlationId =
    form.correlationId && form.correlationId.trim() ? form.correlationId.trim() : null;
  const instanceSelection = form.instanceSelection === 'SOME' ? 'SOME' : 'ALL';
  const command = {
    environment: 'test',
    targetApplication: form.targetApplication,
    action: form.action,
    assault,
    expiresAt,
    issuedBy: form.issuedBy.trim(),
    correlationId,
    instanceSelection,
  };
  if (instanceSelection === 'SOME') {
    command.instanceIds = [...(form.instanceIds || [])];
  }
  return command;
}
