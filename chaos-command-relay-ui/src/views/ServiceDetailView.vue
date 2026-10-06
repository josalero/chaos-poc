<script setup>
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  clearDemoData,
  consoleActor,
  disableService,
  enableService,
  getActuator,
  getHistory,
  getService,
  resetService,
} from '../api/relayClient.js';
import Notices from '../components/Notices.vue';
import StatusBadge from '../components/StatusBadge.vue';
import { commandRequestJson, leaseExpiresAt } from '../lib/commands.js';
import { errorMessages } from '../lib/errors.js';
import { showErrors, showSuccess } from '../lib/notices.js';
import { chaosMonkeyYaml } from '../lib/yaml.js';

const TABS = [
  { id: 'overview', label: 'Status' },
  { id: 'commands', label: 'History' },
  { id: 'actuator', label: 'Actuator' },
  { id: 'maintenance', label: 'Reset' },
];

const route = useRoute();
const router = useRouter();
const status = ref(null);
const history = ref([]);
const actuator = ref(null);
const loading = ref(true);
const loadError = ref('');
const expiresAt = ref(leaseExpiresAt());
const busy = ref('');
const dialogText = ref('');
const dialogTitle = ref('');
const dialogHint = ref('');
const dialog = ref(null);

const applicationName = computed(() => route.params.applicationName);
const tab = computed(() =>
  TABS.some((item) => item.id === route.query.tab) ? route.query.tab : 'overview',
);

function pretty(value) {
  return value == null ? '—' : JSON.stringify(value, null, 2);
}

function formatInstant(value) {
  if (!value) return '—';
  return new Intl.DateTimeFormat(undefined, {
    month: 'long',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value));
}

async function loadActuator() {
  try {
    actuator.value = await getActuator(applicationName.value);
  } catch (error) {
    actuator.value = {
      configured: false,
      reachable: false,
      errorMessage: errorMessages(error, 'Could not read the actuator')[0],
    };
  }
}

async function load() {
  loading.value = true;
  loadError.value = '';
  try {
    const [service, commands] = await Promise.all([
      getService(applicationName.value),
      getHistory(applicationName.value),
    ]);
    status.value = service;
    history.value = commands;
    if (tab.value === 'actuator') {
      await loadActuator();
    }
  } catch (error) {
    status.value = null;
    loadError.value =
      error.status === 404
        ? `Unknown service: ${applicationName.value}`
        : errorMessages(error, 'Could not load this service')[0];
  } finally {
    loading.value = false;
  }
}

watch(applicationName, load, { immediate: true });
watch(tab, (next) => {
  if (next === 'actuator' && status.value) {
    loadActuator();
  }
});

function openDialog(title, hint, text) {
  dialogTitle.value = title;
  dialogHint.value = hint;
  dialogText.value = text;
  dialog.value?.showModal();
}

function openYaml(entry) {
  openDialog('Applied Chaos Monkey configuration', 'Effective values sent to the actuator', chaosMonkeyYaml(entry));
}

function openRequest(entry) {
  openDialog('Command request JSON', 'Body published to the relay for this service', commandRequestJson(entry));
}

async function turnOff() {
  busy.value = 'disable';
  try {
    const submitted = await disableService(applicationName.value, consoleActor());
    await router.push({ name: 'command', params: { commandId: submitted.commandId } });
  } catch (error) {
    showErrors(errorMessages(error));
  } finally {
    busy.value = '';
  }
}

async function enable() {
  busy.value = 'enable';
  try {
    const submitted = await enableService(
      applicationName.value,
      consoleActor(expiresAt.value || null),
    );
    await router.push({ name: 'command', params: { commandId: submitted.commandId } });
  } catch (error) {
    showErrors(errorMessages(error));
  } finally {
    busy.value = '';
  }
}

async function reset() {
  if (!window.confirm('Reset Chaos Monkey configuration on this service? Assaults will be disabled.')) {
    return;
  }
  busy.value = 'reset';
  try {
    const result = await resetService(applicationName.value, consoleActor());
    showSuccess('Configuration reset complete.', result.commandId);
    await load();
  } catch (error) {
    showErrors(errorMessages(error));
  } finally {
    busy.value = '';
  }
}

async function clearData() {
  if (!window.confirm('Clear in-memory demo orders and inventory for this service?')) {
    return;
  }
  busy.value = 'clear';
  try {
    await clearDemoData(applicationName.value);
    showSuccess(`Demo data cleared for ${applicationName.value}.`);
    await load();
  } catch (error) {
    showErrors(errorMessages(error));
  } finally {
    busy.value = '';
  }
}
</script>

<template>
  <RouterLink class="text-sm text-stone-600 underline" to="/">Back to scenarios</RouterLink>
  <p v-if="loading" class="mt-4 text-sm text-stone-500">Loading service…</p>
  <p v-else-if="loadError" class="mt-4 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-900" role="alert">
    {{ loadError }}
  </p>

  <template v-else-if="status">
    <header class="mt-4">
      <p class="text-xs font-semibold tracking-widest text-orange-800">SERVICE</p>
      <h1 class="mono text-2xl font-semibold">{{ status.applicationName }}</h1>
      <p class="text-stone-600">Status, history, and reset for this target.</p>
    </header>
    <Notices />

    <div class="mt-4 flex flex-wrap gap-2 border-b border-stone-200" role="tablist" aria-label="Service sections">
      <RouterLink
        v-for="item in TABS"
        :key="item.id"
        role="tab"
        class="border-b-2 px-3 py-2 text-sm"
        :class="tab === item.id ? 'border-stone-900 font-semibold' : 'border-transparent text-stone-500'"
        :aria-selected="tab === item.id"
        :to="{ name: 'service', params: { applicationName: status.applicationName }, query: { tab: item.id } }"
      >
        {{ item.label }}
        <span v-if="item.id === 'commands' && history.length" class="ml-1 rounded-full bg-stone-200 px-1.5 text-xs">{{ history.length }}</span>
      </RouterLink>
    </div>

    <section v-if="tab === 'overview'" class="mt-4 rounded-xl border border-stone-200 bg-white p-5" role="tabpanel">
      <h2 class="text-lg font-semibold">Service summary</h2>
      <p class="text-sm text-stone-500">Relay-tracked state vs live actuator when reachable.</p>
      <dl class="mt-4 grid gap-3 sm:grid-cols-4">
        <div class="rounded-lg bg-stone-50 p-3"><dt class="text-sm text-stone-500">Relay state</dt><dd>{{ status.configState }}</dd></div>
        <div class="rounded-lg bg-stone-50 p-3"><dt class="text-sm text-stone-500">CM (actuator)</dt><dd>{{ status.cmEnabled ? 'On' : 'Off' }}</dd></div>
        <div class="rounded-lg bg-stone-50 p-3"><dt class="text-sm text-stone-500">Replicas</dt><dd>{{ status.eurekaUpCount }} / {{ status.expectedInstances }}</dd></div>
        <div v-if="status.lastCommandId" class="rounded-lg bg-stone-50 p-3">
          <dt class="text-sm text-stone-500">Last command</dt>
          <dd>
            <RouterLink class="underline" :to="{ name: 'command', params: { commandId: status.lastCommandId } }">{{ String(status.lastCommandId).slice(0, 8) }}</RouterLink>
            <StatusBadge class="ml-1" :status="status.lastCommandStatus" />
          </dd>
        </div>
      </dl>
      <div v-if="status.upInstanceIds?.length" class="mt-4">
        <h3 class="text-sm font-medium">UP instances</h3>
        <ul class="mt-2 flex flex-wrap gap-2">
          <li v-for="instanceId in status.upInstanceIds" :key="instanceId" class="mono rounded-md bg-stone-100 px-2 py-1 text-xs">{{ instanceId }}</li>
        </ul>
      </div>
      <div class="mt-4 flex flex-wrap gap-2">
        <RouterLink class="rounded-md bg-stone-900 px-4 py-2 text-sm font-semibold text-white" :to="{ name: 'publish', query: { applicationName: status.applicationName } }">Configure new assault</RouterLink>
        <RouterLink class="rounded-md border border-stone-300 px-4 py-2 text-sm" :to="{ name: 'publish', query: { applicationName: status.applicationName, instanceSelection: 'SOME' } }">Apply to some instances</RouterLink>
        <RouterLink class="rounded-md border border-stone-300 px-4 py-2 text-sm" :to="{ query: { tab: 'actuator' } }">View live actuator</RouterLink>
      </div>
    </section>

    <section v-else-if="tab === 'actuator'" class="mt-4 rounded-xl border border-stone-200 bg-white p-5" role="tabpanel">
      <h2 class="text-lg font-semibold">Live Chaos Monkey</h2>
      <p class="text-sm text-stone-500">Read from the target service actuator.</p>
      <p v-if="!actuator" class="mt-3 text-sm text-stone-500">Loading actuator…</p>
      <p v-else-if="!actuator.configured" class="mt-3 rounded-lg border border-red-200 bg-red-50 p-3 text-sm" role="alert">
        No UP instances of this service are registered in discovery.
      </p>
      <p v-else-if="!actuator.reachable" class="mt-3 rounded-lg border border-red-200 bg-red-50 p-3 text-sm" role="alert">
        Could not reach Chaos Monkey actuator: {{ actuator.errorMessage }}
      </p>
      <div v-else class="mt-3 grid gap-4 md:grid-cols-2">
        <p>Enabled: <strong>{{ actuator.enabled ? 'Yes' : 'No' }}</strong></p>
        <div class="md:col-span-2 grid gap-4 md:grid-cols-2">
          <div><h3 class="font-medium">Status</h3><pre class="mono mt-2 overflow-auto rounded-lg bg-stone-900 p-3 text-xs text-stone-100">{{ actuator.statusJson || '—' }}</pre></div>
          <div><h3 class="font-medium">Assaults</h3><pre class="mono mt-2 overflow-auto rounded-lg bg-stone-900 p-3 text-xs text-stone-100">{{ actuator.assaultsJson || '—' }}</pre></div>
        </div>
      </div>
    </section>

    <section v-else-if="tab === 'commands'" class="mt-4 rounded-xl border border-stone-200 bg-white p-5" role="tabpanel">
      <h2 class="text-lg font-semibold">Patch history</h2>
      <p class="text-sm text-stone-500">Newest command first.</p>
      <p v-if="history.length === 0" class="mt-4 text-sm text-stone-600">
        No commands published yet.
        <RouterLink class="underline" :to="{ name: 'publish', query: { applicationName: status.applicationName } }">Publish the first command</RouterLink>.
      </p>
      <div v-else class="mt-4 overflow-x-auto">
        <table class="w-full text-left text-sm">
          <thead>
            <tr class="border-b border-stone-200 text-stone-500">
              <th class="py-2 pr-3 font-medium" scope="col">Published</th>
              <th class="py-2 pr-3 font-medium" scope="col">Action</th>
              <th class="py-2 pr-3 font-medium" scope="col">Status</th>
              <th class="py-2 pr-3 font-medium" scope="col">Issued by</th>
              <th class="py-2 pr-3 font-medium" scope="col">Results</th>
              <th class="py-2 font-medium" scope="col">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="entry in history" :key="entry.commandId" class="border-b border-stone-100">
              <td class="py-2 pr-3">{{ formatInstant(entry.publishedAt) }}</td>
              <td class="mono py-2 pr-3">{{ entry.action }}</td>
              <td class="py-2 pr-3"><StatusBadge :status="entry.status" /></td>
              <td class="py-2 pr-3">{{ entry.issuedBy || '—' }}</td>
              <td class="py-2 pr-3">{{ entry.successCount }} ok / {{ entry.failureCount }} failed</td>
              <td class="py-2">
                <RouterLink class="underline" :to="{ name: 'command', params: { commandId: entry.commandId } }">Details</RouterLink>
                <button type="button" class="ml-3 underline" @click="openRequest(entry)">View JSON</button>
                <button type="button" class="ml-3 underline" @click="openYaml(entry)">View YAML</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-else class="mt-4 space-y-4" role="tabpanel">
      <div class="grid gap-4 md:grid-cols-2">
        <div class="rounded-xl border border-stone-200 bg-white p-5">
          <h2 class="font-semibold">Relay last command — desired assault</h2>
          <pre class="mono mt-3 overflow-auto text-xs">{{ pretty(status.desiredAssault) }}</pre>
        </div>
        <div class="rounded-xl border border-stone-200 bg-white p-5">
          <h2 class="font-semibold">Relay last command — applied assault</h2>
          <pre class="mono mt-3 overflow-auto text-xs">{{ pretty(status.appliedAssault) }}</pre>
        </div>
      </div>
      <div class="rounded-xl border border-stone-200 bg-white p-5">
        <h2 class="font-semibold">Maintenance actions</h2>
        <p class="mt-1 text-sm text-stone-500">Reset CM disables assaults and waits for APPLIED. Clear demo data resets in-memory orders and inventory. It does not change the assault.</p>
        <div class="mt-4 flex flex-wrap gap-2">
          <button type="button" class="rounded-md border border-stone-300 px-3 py-2 text-sm disabled:opacity-50" :disabled="busy !== ''" @click="turnOff">Disable</button>
          <button type="button" class="rounded-md bg-red-700 px-3 py-2 text-sm font-semibold text-white disabled:opacity-50" :disabled="busy !== ''" @click="reset">Reset CM configuration</button>
          <button type="button" class="rounded-md border border-stone-300 px-3 py-2 text-sm disabled:opacity-50" :disabled="busy !== ''" @click="clearData">Clear demo data</button>
          <RouterLink class="rounded-md bg-stone-900 px-3 py-2 text-sm font-semibold text-white" :to="{ name: 'publish', query: { applicationName: status.applicationName } }">Configure new assault</RouterLink>
        </div>
        <form class="mt-6 max-w-md" @submit.prevent="enable">
          <h3 class="font-medium">Enable Chaos Monkey</h3>
          <label class="mt-2 block text-sm" for="expires-at">Expires at (ISO-8601)
            <input id="expires-at" v-model="expiresAt" class="mt-1 w-full rounded-md border border-stone-300 px-3 py-2" />
          </label>
          <button type="submit" class="mt-3 rounded-md bg-stone-900 px-3 py-2 text-sm font-semibold text-white disabled:opacity-50" :disabled="busy !== ''">Enable</button>
        </form>
      </div>
    </section>
  </template>

  <dialog ref="dialog" class="w-full max-w-2xl rounded-xl p-0 backdrop:bg-stone-900/40">
    <header class="flex items-start justify-between gap-4 border-b border-stone-200 px-4 py-3">
      <div>
        <strong>{{ dialogTitle }}</strong>
        <p class="text-sm text-stone-500">{{ dialogHint }}</p>
      </div>
      <form method="dialog"><button type="submit" class="rounded-md border border-stone-300 px-3 py-1 text-sm">Close</button></form>
    </header>
    <pre class="mono max-h-[60vh] overflow-auto px-4 py-3 text-xs">{{ dialogText }}</pre>
  </dialog>
</template>
