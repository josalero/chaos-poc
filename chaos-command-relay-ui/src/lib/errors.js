export class RelayError extends Error {
  constructor(status, body) {
    super('Relay request failed');
    this.status = status;
    this.body = body;
  }
}

export function errorMessages(error, fallback = 'Request failed') {
  const body = error?.body;
  if (Array.isArray(body?.errors) && body.errors.length > 0) {
    return body.errors.map((item) => `${item.field}: ${item.message}`);
  }
  if (Array.isArray(body?.services)) {
    const messages = body.services.flatMap((service) =>
      (service.errors || []).map(
        (item) => `${service.applicationName}: ${item.field}: ${item.message}`,
      ),
    );
    if (messages.length > 0) {
      return messages;
    }
  }
  if (body?.message) {
    return [body.message];
  }
  if (body?.commandStatus) {
    return [`Reset finished with status ${body.commandStatus}`];
  }
  return [fallback];
}
