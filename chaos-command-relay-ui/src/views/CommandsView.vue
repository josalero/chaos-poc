<script setup>
import { onMounted, ref, watch } from 'vue';
import { listCommands, listServices } from '../api/relayClient.js';
import Notices from '../components/Notices.vue';
import PageHeader from '../components/PageHeader.vue';
import StatusBadge from '../components/StatusBadge.vue';
import { errorMessages } from '../lib/errors.js';
import { serviceTitle } from '../lib/labels.js';

const ACTIONS = ['CONFIGURE_AND_ENABLE', 'CONFIGURE', 'ENABLE', 'DISABLE'];
const STATUSES = ['APPLIED', 'FAILED', 'TIMED_OUT', 'PENDING', 'PARTIAL', 'PUBLISHED'];

const services = ref([]);
const page = ref(null);
const loading = ref(true);
const loadError = ref('');
const application = ref('');
const status = ref('');
const action = ref('');
const pageIndex = ref(0);

onMounted(async () => {
  try {
    services.value = await listServices();
  } catch {
    services.value = [];
  }
  await load();
});

watch([application, status, action], () => {
  pageIndex.value = 0;
  load();
});

async function load() {
  loading.value = true;
  loadError.value = '';
  try {
    page.value = await listCommands({
      page: pageIndex.value,
      size: 50,
      application: application.value || undefined,
      status: status.value || undefined,
      action: action.value || undefined,
    });
  } catch (error) {
    page.value = null;
    loadError.value = errorMessages(error, 'Could not load commands')[0];
  } finally {
    loading.value = false;
  }
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

function go(next) {
  pageIndex.value = next;
  load();
}
</script>

<template>
  <Notices />
  <PageHeader
    title="Commands"
    description="Every command the relay has applied, newest first. Open a row to see replica results."
  />
  <p v-if="loadError" class="alert alert-error" role="alert">{{ loadError }}</p>

  <div class="toolbar cols-3">
    <label class="field" for="command-service">Service
      <select id="command-service" v-model="application">
        <option value="">All services</option>
        <option v-for="service in services" :key="service.applicationName" :value="service.applicationName">
          {{ serviceTitle(service.applicationName) }}
        </option>
      </select>
    </label>
    <label class="field" for="command-status">Status
      <select id="command-status" v-model="status">
        <option value="">Any</option>
        <option v-for="item in STATUSES" :key="item" :value="item">{{ item }}</option>
      </select>
    </label>
    <label class="field" for="command-action">Action
      <select id="command-action" v-model="action">
        <option value="">Any</option>
        <option v-for="item in ACTIONS" :key="item" :value="item">{{ item }}</option>
      </select>
    </label>
  </div>

  <p v-if="loading" class="empty">Loading commands.</p>
  <div v-else-if="page && page.totalElements === 0" class="empty">
    <h2>No commands match</h2>
    <p>Clear a filter, or apply an assault from a service.</p>
  </div>
  <div v-else-if="page" class="panel">
    <div class="table-wrap">
      <table class="data">
        <thead>
          <tr>
            <th scope="col">Published</th>
            <th scope="col">Service</th>
            <th scope="col">Action</th>
            <th scope="col">Status</th>
            <th scope="col">Issued by</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="entry in page.content" :key="entry.commandId">
            <td>
              <RouterLink class="row-link" :to="{ name: 'command', params: { commandId: entry.commandId } }">
                {{ formatInstant(entry.publishedAt) }}
              </RouterLink>
            </td>
            <td>
              <RouterLink class="row-link" :to="{ name: 'service', params: { applicationName: entry.targetApplication } }">
                {{ serviceTitle(entry.targetApplication) }}
              </RouterLink>
            </td>
            <td class="mono">{{ entry.action }}</td>
            <td><StatusBadge :status="entry.status" /></td>
            <td>{{ entry.issuedBy || '—' }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="pager">
      <p class="nums">{{ page.totalElements }} commands · page {{ page.page + 1 }}</p>
      <div class="button-row">
        <button type="button" class="btn" :disabled="page.page === 0" @click="go(page.page - 1)">Previous</button>
        <button type="button" class="btn" :disabled="(page.page + 1) * page.size >= page.totalElements" @click="go(page.page + 1)">Next</button>
      </div>
    </div>
  </div>
</template>
