function yamlQuote(value) {
  return `'${String(value ?? '').replaceAll("'", "''")}'`;
}

function assaultYaml(assault) {
  const hasAssault = assault != null;
  let yaml = '    assaults:\n';
  yaml += `      level: ${hasAssault && assault.level != null ? assault.level : 1}\n`;
  yaml += `      deterministic: ${!hasAssault || assault.deterministic == null || assault.deterministic}\n`;
  yaml += `      latency-active: ${hasAssault && assault.latencyActive === true}\n`;
  yaml += `      latency-range-start: ${
    hasAssault && assault.latencyRangeStart != null ? assault.latencyRangeStart : 1000
  }\n`;
  yaml += `      latency-range-end: ${
    hasAssault && assault.latencyRangeEnd != null ? assault.latencyRangeEnd : 3000
  }\n`;
  yaml += `      exceptions-active: ${hasAssault && assault.exceptionsActive === true}\n`;
  yaml += '      watched-custom-services:';

  const watched = hasAssault ? assault.watchedCustomServices : null;
  if (!watched || watched.length === 0) {
    yaml += ' []\n';
  } else {
    yaml += '\n';
    watched.forEach((service) => {
      yaml += `        - ${yamlQuote(service)}\n`;
    });
  }

  const exception = hasAssault ? assault.exception : null;
  if (exception == null) {
    yaml += '      exception:\n';
    yaml += "        type: 'java.lang.RuntimeException'\n";
    yaml += "        method: '<init>'\n";
    yaml += '        arguments:\n';
    yaml += "          - type: 'java.lang.String'\n";
    yaml += "            value: 'Chaos Monkey - RuntimeException'\n";
    return yaml;
  }

  yaml += '      exception:\n';
  yaml += `        type: ${yamlQuote(exception.type)}\n`;
  yaml += `        method: ${yamlQuote(exception.method)}\n`;
  yaml += '        arguments:';
  const argumentsList = exception.arguments;
  if (!Array.isArray(argumentsList) || argumentsList.length === 0) {
    yaml += ' []\n';
    return yaml;
  }
  yaml += '\n';
  argumentsList.forEach((argument) => {
    yaml += `          - type: ${yamlQuote(argument.type)}\n`;
    yaml += `            value: ${yamlQuote(argument.value)}\n`;
  });
  return yaml;
}

/** Chaos Monkey YAML the operator console shows for one published command. */
export function chaosMonkeyYaml(entry) {
  let yaml = 'chaos:\n  monkey:\n';
  switch (entry.action) {
    case 'DISABLE':
      yaml += '    enabled: false\n';
      yaml += assaultYaml(null);
      break;
    case 'ENABLE':
      yaml += '    enabled: true\n';
      break;
    case 'CONFIGURE':
      yaml += '    # enabled state unchanged\n';
      yaml += assaultYaml(entry.assault);
      break;
    case 'CONFIGURE_AND_ENABLE':
      yaml += '    enabled: true\n';
      yaml += assaultYaml(entry.assault);
      break;
    default:
      throw new Error(`Unexpected action ${entry.action}`);
  }
  return yaml.replace(/\s+$/, '');
}
