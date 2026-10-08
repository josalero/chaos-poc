<script setup>
import { computed, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  clearDemoData,
  consoleActor,
  disableService,
  enableService,
  getActuator,
  getHistory,
  getService,
  listServices,
  resetService,
} from '../api/relayClient.js';
import Notices from '../components/Notices.vue';
import PageHeader from '../components/PageHeader.vue';
import StatusBadge from '../components/StatusBadge.vue';
import CommandFormView from './CommandFormView.vue';
import { commandRequestJson, leaseExpiresAt } from '../lib/commands.js';
import { errorMessages } from '../lib/errors.js';
import { showErrors, showSuccess } from '../lib/notices.js';
import { serviceTitle } from '../lib/labels.js';
import { checkedAgo, nextHistoryLimit } from '../lib/serviceList.js';
import { cmLabel } from '../lib/serviceTable.js';
import { chaosMonkeyYaml } from '../lib/yaml.js';

const TABS = [
  { id: 'overview', label: 'Overview' },
  { id: 'commands', label: 'History' },
  { id: 'actuator', label: 'Actuator' },
  { id: 'apply', label: 'Apply' },
];

const route = useRoute();
const router = useRouter();
const status = ref(null);
const history = ref([]);
const historyLimit = ref(50);
const actuator = ref(null);
const loading = ref(true);
const loadError = ref('');
const expiresAt = ref(leaseExpiresAt());
const busy = ref('');
const dialogText = ref('');
const dialogTitle = ref('');
const dialogHint = ref('');
const dialog = ref(null);
let actuatorTimer;

const applicationName = computed(() => route.params.applicationName);
const title = computed(() => serviceTitle(applicationName.value));
const crumbs = computed(() => [
  { label: 'Services', to: '/' },
  { label: title.value },
]);
const tab = computed(() =>
  TABS.some((item) => item.id === route.query.tab) ? route.query.tab : 'overview',
);
const catalogId = computed(() =>
  typeof route.query.catalogId === 'string' ? route.query.catalogId : '',
);

function pretty(value) {
  return value == null ? '—' : JSON.stringify(value, null, 2);
}

function formatInstant(value) {
  if (!value) return '—';
  return new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
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
      instances: [],
      errorMessage: errorMessages(error, 'Could not read the actuator')[0],
    };
  }
}

function stopActuatorPoll() {
  clearInterval(actuatorTimer);
  actuatorTimer = null;
}

function startActuatorPoll() {
  stopActuatorPoll();
  if (tab.value !== 'actuator' || !status.value) {
    return;
  }
  loadActuator();
  actuatorTimer = setInterval(() => {
    if (document.visibilityState === 'visible' && tab.value === 'actuator') {
      loadActuator();
    }
  }, 5000);
}

async function load() {
  loading.value = true;
  loadError.value = '';
  try {
    const [service, commands, services] = await Promise.all([
      getService(applicationName.value),
      getHistory(applicationName.value, historyLimit.value),
      listServices().catch(() => []),
    ]);
    const summary = services.find((item) => item.applicationName === applicationName.value);
    status.value = { ...service, cmCheckedAt: summary?.cmCheckedAt ?? null };
    history.value = commands;
  } catch (error) {
    status.value = null;
    loadError.value =
      error.status === 404
        ? `Unknown service: ${applicationName.value}`
        : errorMessages(error, 'Could not load this service')[0];
  } finally {
    loading.value = false;
    startActuatorPoll();
  }
}

async function loadMore() {
  historyLimit.value = nextHistoryLimit(historyLimit.value);
  history.value = await getHistory(applicationName.value, historyLimit.value);
}

watch(applicationName, load, { immediate: true });
watch(tab, startActuatorPoll);
onUnmounted(stopActuatorPoll);

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
  <PageHeader
    :title="title"
    :description="applicationName"
    :crumbs="crumbs"
  />
  <p v-if="loading" class="empty">Loading service.</p>
  <p v-else-if="loadError" class="alert alert-error" role="alert">{{ loadError }}</p>

  <template v-else-if="status">
    <Notices />

    <div class="tabs" role="tablist" aria-label="Service sections">
      <RouterLink
        v-for="item in TABS"
        :key="item.id"
        custom
        :to="{ name: 'service', params: { applicationName: status.applicationName }, query: { tab: item.id } }"
        v-slot="{ href, navigate }"
      >
        <a
          :id="`tab-${item.id}`"
          :href="href"
          role="tab"
          class="tab"
          :aria-selected="tab === item.id"
          :aria-controls="`${item.id}-panel`"
          @click="navigate"
        >
          {{ item.label }}
          <span v-if="item.id === 'commands' && history.length" class="tab-count">{{ history.length }}</span>
        </a>
      </RouterLink>
    </div>

    <section v-if="tab === 'overview'" id="overview-panel" class="stack" role="tabpanel" aria-labelledby="tab-overview">
      <div class="panel panel-pad">
        <h2>Service summary</h2>
        <dl class="facts cols-4">
          <div>
            <dt>Registry</dt>
            <dd>{{ status.registryEnabled ? 'Enabled' : 'Disabled' }}</dd>
          </div>
          <div>
            <dt>Config</dt>
            <dd>{{ status.configState }}</dd>
          </div>
          <div>
            <dt>Chaos Monkey</dt>
            <dd>{{ cmLabel(status) }} <small class="sub">{{ checkedAgo(status.cmCheckedAt) }}</small></dd>
          </div>
          <div>
            <dt>Replicas</dt>
            <dd class="nums">{{ status.eurekaUpCount }} UP / {{ status.expectedInstances }} expected</dd>
          </div>
          <div v-if="status.lastCommandId">
            <dt>Last command</dt>
            <dd>
              <RouterLink class="row-link" :to="{ name: 'command', params: { commandId: status.lastCommandId } }">{{ String(status.lastCommandId).slice(0, 8) }}</RouterLink>
              <StatusBadge :status="status.lastCommandStatus" />
              <small class="sub">{{ formatInstant(status.lastPublishedAt) }} · {{ status.lastIssuedBy || '—' }}</small>
            </dd>
          </div>
        </dl>
        <div v-if="status.upInstanceIds?.length">
          <h3>UP instances</h3>
          <ul class="id-list">
            <li v-for="instanceId in status.upInstanceIds" :key="instanceId" class="mono">{{ instanceId }}</li>
          </ul>
        </div>
        <div class="button-row">
          <RouterLink custom :to="{ query: { tab: 'apply' } }" v-slot="{ href, navigate }">
            <a :href="href" class="btn btn-primary" @click="navigate">Apply an assault</a>
          </RouterLink>
          <RouterLink custom :to="{ query: { tab: 'actuator' } }" v-slot="{ href, navigate }">
            <a :href="href" class="btn" @click="navigate">View live actuator</a>
          </RouterLink>
          <button type="button" class="btn btn-danger" :disabled="busy !== ''" @click="reset">Reset</button>
        </div>
      </div>
      <div class="panel panel-pad">
        <h2>Maintenance</h2>
        <p class="meta">Reset waits until the disable command is applied. Clear demo data resets in-memory orders and inventory.</p>
        <div class="button-row">
          <button type="button" class="btn" :disabled="busy !== ''" @click="turnOff">Disable</button>
          <button type="button" class="btn" :disabled="busy !== ''" @click="clearData">Clear demo data</button>
        </div>
        <form class="narrow" @submit.prevent="enable">
          <h3>Enable Chaos Monkey</h3>
          <label class="field" for="expires-at">Expires at (ISO-8601)
            <input id="expires-at" v-model="expiresAt" />
          </label>
          <button type="submit" class="btn btn-primary" :disabled="busy !== ''">Enable</button>
        </form>
        <div class="split">
          <div>
            <h3>Desired assault</h3>
            <pre class="mono code-plain">{{ pretty(status.desiredAssault) }}</pre>
          </div>
          <div>
            <h3>Applied assault</h3>
            <pre class="mono code-plain">{{ pretty(status.appliedAssault) }}</pre>
          </div>
        </div>
      </div>
    </section>

    <section v-else-if="tab === 'actuator'" id="actuator-panel" class="stack" role="tabpanel" aria-labelledby="tab-actuator">
      <div class="page-head-row">
        <div>
          <h2>Live Chaos Monkey</h2>
          <p class="meta">Each UP instance is read again every 5 seconds while this section is open.</p>
        </div>
        <button type="button" class="btn" @click="loadActuator">Refresh</button>
      </div>
      <p v-if="!actuator" class="empty">Loading actuator.</p>
      <p v-else-if="!actuator.configured" class="alert alert-error" role="alert">
        No UP instances of this service are registered in discovery.
      </p>
      <p v-else-if="!actuator.reachable" class="alert alert-error" role="alert">
        Could not reach the Chaos Monkey actuator. {{ actuator.errorMessage }}
      </p>
      <template v-else>
        <p>Any instance on: <strong>{{ actuator.enabled ? 'Yes' : 'No' }}</strong></p>
        <article v-for="instance in actuator.instances || []" :key="instance.instanceId" class="panel panel-pad">
          <h3 class="mono">{{ instance.instanceId }}</h3>
          <p v-if="instance.error" class="alert alert-error" role="alert">{{ instance.error }}</p>
          <template v-else>
            <p>Enabled: <strong>{{ instance.enabled ? 'Yes' : 'No' }}</strong></p>
            <div class="split">
            <div>
              <h4>Status</h4>
              <pre class="mono code">{{ instance.statusJson || '—' }}</pre>
            </div>
            <div>
              <h4>Assaults</h4>
              <pre class="mono code">{{ instance.assaultsJson || '—' }}</pre>
            </div>
            </div>
          </template>
        </article>
      </template>
    </section>

    <section v-else-if="tab === 'commands'" id="commands-panel" class="stack" role="tabpanel" aria-labelledby="tab-commands">
      <h2>Command history</h2>
      <p class="meta">Newest command first.</p>
      <div v-if="history.length === 0" class="empty">
        <h2>No commands yet</h2>
        <p><RouterLink :to="{ query: { tab: 'apply' } }">Apply the first assault</RouterLink>.</p>
      </div>
      <div v-else class="panel">
        <div class="table-wrap">
          <table class="data">
            <thead>
              <tr>
                <th scope="col">Published</th>
                <th scope="col">Action</th>
                <th scope="col">Status</th>
                <th scope="col">Issued by</th>
                <th scope="col">Results</th>
                <th scope="col">Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="entry in history" :key="entry.commandId">
                <td>{{ formatInstant(entry.publishedAt) }}</td>
                <td class="mono">{{ entry.action }}</td>
                <td><StatusBadge :status="entry.status" /></td>
                <td>{{ entry.issuedBy || '—' }}</td>
                <td class="nums">{{ entry.successCount }} ok / {{ entry.failureCount }} failed</td>
                <td>
                  <div class="button-row">
                    <RouterLink class="btn" :to="{ name: 'command', params: { commandId: entry.commandId } }">Details</RouterLink>
                    <button type="button" class="btn" @click="openRequest(entry)">JSON</button>
                    <button type="button" class="btn" @click="openYaml(entry)">YAML</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="history.length >= historyLimit && historyLimit < 200" class="pager">
          <button type="button" class="btn" @click="loadMore">Load more</button>
        </div>
      </div>
    </section>

    <section v-else id="apply-panel" class="stack" role="tabpanel" aria-labelledby="tab-apply">
      <h2>Apply an assault</h2>
      <p class="meta">Build it by hand, start from a preset, or fill it from a saved entry. Saving stores it on this service only.</p>
      <CommandFormView :application-name="status.applicationName" :catalog-id="catalogId" />
    </section>
  </template>

  <dialog ref="dialog" class="sheet">
    <header>
      <div>
        <strong>{{ dialogTitle }}</strong>
        <p>{{ dialogHint }}</p>
      </div>
      <form method="dialog"><button type="submit" class="btn">Close</button></form>
    </header>
    <pre class="mono">{{ dialogText }}</pre>
  </dialog>
</template>
