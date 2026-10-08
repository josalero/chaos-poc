<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import {
  getCommand,
  listServices,
  resetSelected,
} from '../api/relayClient.js';
import Notices from '../components/Notices.vue';
import PageHeader from '../components/PageHeader.vue';
import StatusBadge from '../components/StatusBadge.vue';
import { errorMessages } from '../lib/errors.js';
import { serviceTitle } from '../lib/labels.js';
import { showErrors } from '../lib/notices.js';
import { checkedAgo, selectAllShown, selectionSummary } from '../lib/serviceList.js';
import { cmLabel, filterServices, pageServices, sortServices } from '../lib/serviceTable.js';

const CONFIG_STATES = ['DEFAULT', 'DESIRED', 'APPLIED', 'PARTIAL', 'FAILED'];
const SORTS = [
  { key: 'name', label: 'Service' },
  { key: 'cm', label: 'Chaos Monkey' },
  { key: 'configState', label: 'Config' },
  { key: 'up', label: 'UP' },
  { key: 'lastStatus', label: 'Last command' },
];

const services = ref([]);
const loading = ref(true);
const loadError = ref('');
const query = ref('');
const cm = ref('any');
const configState = ref('');
const sortKey = ref('name');
const sortDirection = ref('asc');
const page = ref(0);
const selected = ref([]);
const resetting = ref(false);
const progress = ref([]);
let refreshTimer;
let pollTimer;

const titled = computed(() =>
  services.value.map((service) => ({
    ...service,
    title: serviceTitle(service.applicationName),
  })),
);
const filtered = computed(() =>
  filterServices(titled.value, {
    query: query.value,
    cm: cm.value,
    configState: configState.value,
  }),
);
const sorted = computed(() =>
  sortServices(filtered.value, { key: sortKey.value, direction: sortDirection.value }),
);
const table = computed(() => pageServices(sorted.value, page.value));
const summary = computed(() =>
  selectionSummary(
    selected.value,
    filtered.value.map((service) => service.applicationName),
    titled.value.length,
  ),
);
const caption = computed(() => {
  const current = summary.value;
  const range = `${current.shown} shown of ${current.total}`;
  if (current.selected === 0) {
    return range;
  }
  if (current.hidden === 0) {
    return `${current.selected} selected · ${range}`;
  }
  return `${current.selected} selected, ${current.hidden} hidden by the filter · ${range}`;
});
const allShownSelected = computed(
  () =>
    filtered.value.length > 0
    && filtered.value.every((service) => selected.value.includes(service.applicationName)),
);

watch([query, cm, configState], () => {
  page.value = 0;
});

onMounted(() => {
  refresh();
  refreshTimer = setInterval(() => {
    if (document.visibilityState === 'visible') {
      refresh();
    }
  }, 15000);
});

onUnmounted(() => {
  clearInterval(refreshTimer);
  clearInterval(pollTimer);
});

async function refresh() {
  try {
    services.value = await listServices();
    loadError.value = '';
  } catch (error) {
    loadError.value = errorMessages(error, 'Could not load services')[0];
  } finally {
    loading.value = false;
  }
}

function toggleSort(key) {
  if (sortKey.value === key) {
    sortDirection.value = sortDirection.value === 'asc' ? 'desc' : 'asc';
  } else {
    sortKey.value = key;
    sortDirection.value = 'asc';
  }
  page.value = 0;
}

function ariaSort(key) {
  if (sortKey.value !== key) {
    return 'none';
  }
  return sortDirection.value === 'asc' ? 'ascending' : 'descending';
}

function toggleShown(checked) {
  selected.value = selectAllShown(
    selected.value,
    filtered.value.map((service) => service.applicationName),
    checked,
  );
}

function toggleOne(name, checked) {
  selected.value = selectAllShown(selected.value, [name], checked);
}

function confirmReset() {
  if (selected.value.length === 0) {
    return;
  }
  const names = selected.value.join('\n');
  if (!window.confirm(`Turn off Chaos Monkey on:\n${names}`)) {
    return;
  }
  startReset();
}

async function startReset() {
  resetting.value = true;
  try {
    const result = await resetSelected(selected.value);
    progress.value = (result.services || []).map((service) => ({
      applicationName: service.applicationName,
      commandId: service.commandId,
      status: service.commandId ? 'PENDING' : 'FAILED',
      errors: service.errors || [],
    }));
    pollProgress();
  } catch (error) {
    showErrors(errorMessages(error));
    resetting.value = false;
  }
}

function pollProgress() {
  clearInterval(pollTimer);
  pollTimer = setInterval(async () => {
    const pending = progress.value.filter((item) => item.commandId && !isTerminal(item.status));
    if (pending.length === 0) {
      clearInterval(pollTimer);
      resetting.value = false;
      await refresh();
      return;
    }
    const updates = await Promise.all(
      pending.map(async (item) => {
        try {
          const command = await getCommand(item.commandId);
          return { commandId: item.commandId, status: command.status };
        } catch {
          return { commandId: item.commandId, status: item.status };
        }
      }),
    );
    progress.value = progress.value.map((item) => {
      const update = updates.find((candidate) => candidate.commandId === item.commandId);
      return update ? { ...item, status: update.status } : item;
    });
  }, 2000);
}

function isTerminal(status) {
  return status === 'APPLIED' || status === 'FAILED' || status === 'TIMED_OUT';
}
</script>

<template>
  <Notices />
  <p v-if="loadError" class="alert alert-error" role="alert">{{ loadError }}</p>

  <PageHeader
    title="Services"
    description="Allowlisted targets, Chaos Monkey state, and the last command. Open a row to inspect or apply an assault."
  >
    <template #actions>
      <button type="button" class="btn" @click="refresh">Refresh</button>
      <button
        type="button"
        class="btn"
        :class="{ 'btn-danger': selected.length > 0 }"
        :disabled="selected.length === 0 || resetting"
        @click="confirmReset"
      >
        Reset selected
      </button>
    </template>
  </PageHeader>

  <section aria-labelledby="targets-title" :aria-busy="loading">
    <h2 id="targets-title" class="sr-only">Allowlist</h2>
    <div class="toolbar cols-3">
      <label class="field" for="service-search">Search
        <input id="service-search" v-model="query" type="search" placeholder="Name or title" />
      </label>
      <label class="field" for="cm-filter">Chaos Monkey
        <select id="cm-filter" v-model="cm">
          <option value="any">Any</option>
          <option value="on">On</option>
          <option value="off">Off</option>
          <option value="unknown">Unknown</option>
        </select>
      </label>
      <label class="field" for="config-filter">Config state
        <select id="config-filter" v-model="configState">
          <option value="">Any</option>
          <option v-for="state in CONFIG_STATES" :key="state" :value="state">{{ state }}</option>
        </select>
      </label>
    </div>

    <p v-if="loading" class="empty">Loading services.</p>
    <div v-else-if="titled.length === 0" class="empty">
      <h2>No services on the allowlist</h2>
      <p>The relay returned no targets this console can command.</p>
    </div>
    <div v-else class="panel">
      <div class="bar">
        <label class="check-row">
          <input type="checkbox" :checked="allShownSelected" @change="toggleShown($event.target.checked)" />
          Select all shown
        </label>
        <p class="nums meta">{{ caption }}</p>
      </div>
      <div class="table-wrap">
        <table class="data">
          <thead>
            <tr>
              <th scope="col"><span class="sr-only">Select</span></th>
              <th
                v-for="column in SORTS"
                :key="column.key"
                scope="col"
                :aria-sort="ariaSort(column.key)"
              >
                <button type="button" class="sort-btn" @click="toggleSort(column.key)">
                  {{ column.label }}
                  <svg v-if="sortKey === column.key" width="12" height="12" viewBox="0 0 12 12" aria-hidden="true">
                    <path
                      v-if="sortDirection === 'asc'"
                      d="M6 2.5 9.2 7H2.8L6 2.5Z"
                      fill="currentColor"
                    />
                    <path v-else d="M6 9.5 2.8 5h6.4L6 9.5Z" fill="currentColor" />
                  </svg>
                </button>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="service in table.content"
              :key="service.applicationName"
              :class="{ 'is-selected': selected.includes(service.applicationName) }"
            >
              <td>
                <input
                  type="checkbox"
                  :checked="selected.includes(service.applicationName)"
                  :aria-label="`Select ${service.applicationName}`"
                  @change="toggleOne(service.applicationName, $event.target.checked)"
                />
              </td>
              <td>
                <RouterLink class="row-link" :to="{ name: 'service', params: { applicationName: service.applicationName } }">
                  {{ service.title }}
                </RouterLink>
                <small class="mono sub">{{ service.applicationName }}</small>
              </td>
              <td :class="{ hot: service.cmEnabled }">
                {{ cmLabel(service) }}
                <small class="sub">{{ checkedAgo(service.cmCheckedAt) }}</small>
              </td>
              <td>{{ service.configState }}</td>
              <td class="nums">{{ service.eurekaUpCount }}</td>
              <td><StatusBadge :status="service.lastCommandStatus" /></td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-if="table.total === 0" class="panel-pad meta">No services match the current search.</p>
      <div v-else class="pager">
        <p class="nums">Page {{ table.page + 1 }} of {{ table.pages }}</p>
        <div class="button-row">
          <button type="button" class="btn" :disabled="table.page === 0" @click="page = table.page - 1">Previous</button>
          <button type="button" class="btn" :disabled="table.page >= table.pages - 1" @click="page = table.page + 1">Next</button>
        </div>
      </div>
    </div>

    <ul v-if="progress.length" class="panel progress-list" aria-live="polite">
      <li v-for="item in progress" :key="item.applicationName">
        <span>{{ serviceTitle(item.applicationName) }}</span>
        <span>
          <StatusBadge :status="item.status" />
          <span v-if="item.errors.length"> {{ item.errors.map((error) => error.message).join(', ') }}</span>
        </span>
      </li>
    </ul>
  </section>
</template>
