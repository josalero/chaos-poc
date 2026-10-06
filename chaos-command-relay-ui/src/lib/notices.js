import { reactive } from 'vue';

export const notices = reactive({
  errors: [],
  success: null,
  commandId: null,
});

export function showOutcome({ success = null, commandId = null, errors = [] } = {}) {
  notices.success = success;
  notices.commandId = commandId;
  notices.errors = errors;
}

export function showErrors(errors) {
  showOutcome({ errors: errors?.length ? errors : ['Request failed'] });
}

export function showSuccess(message, commandId = null) {
  showOutcome({ success: message, commandId });
}

export function clearNotices() {
  notices.errors = [];
  notices.success = null;
  notices.commandId = null;
}
