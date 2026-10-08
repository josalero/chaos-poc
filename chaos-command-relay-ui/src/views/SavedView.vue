<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { deleteCatalog, listSavedCatalog, listServices } from '../api/relayClient.js';
import Notices from '../components/Notices.vue';
import PageHeader from '../components/PageHeader.vue';
import { errorMessages } from '../lib/errors.js';
import { serviceTitle } from '../lib/labels.js';
import { showErrors } from '../lib/notices.js';
import { pageServices } from '../lib/serviceTable.js';

const services = ref([]);
const entries = ref([]);
const loading = ref(true);
const loadError = ref('');
const application = ref('');
const page = ref(0);

const table = computed(() => pageServices(entries.value, page.value, 20));

onMounted(async () => {
  try {
    services.value = await listServices();
  } catch {
    services.value = [];
  }
  await load();
});

watch(application, () => {
  page.value = 0;
  load();
});

async function load() {
  loading.value = true;
  loadError.value = '';
  try {
    entries.value = await listSavedCatalog(application.value || undefined);
  } catch (error) {
    entries.value = [];
    loadError.value = errorMessages(error, 'Could not load saved assaults')[0];
  } finally {
    loading.value = false;
  }
}

async function remove(entry) {
  const name = `${entry.label} on ${serviceTitle(entry.targetApplication)}`;
  if (!window.confirm(`Delete saved assault "${name}"?`)) {
    return;
  }
  try {
    await deleteCatalog(entry.targetApplication, entry.catalogId);
    entries.value = entries.value.filter((item) => item.catalogId !== entry.catalogId);
  } catch (error) {
    showErrors(errorMessages(error));
  }
}

function formatInstant(value) {
  if (!value) return '—';
  return new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value));
}
</script>

<template>
  <Notices />
  <PageHeader
    title="Saved assaults"
    description="Assaults stored on each service. Apply opens that service with the form already filled."
  />
  <p v-if="loadError" class="alert alert-error" role="alert">{{ loadError }}</p>

  <label class="field narrow" for="saved-service">Service
    <select id="saved-service" v-model="application">
      <option value="">All services</option>
      <option v-for="service in services" :key="service.applicationName" :value="service.applicationName">
        {{ serviceTitle(service.applicationName) }}
      </option>
    </select>
  </label>

  <p v-if="loading" class="empty">Loading saved assaults.</p>
  <div v-else-if="table.total === 0" class="empty">
    <h2>Nothing saved yet</h2>
    <p>
      Open a service, build an assault, and check “Save to this service” before you apply it.
      <RouterLink to="/">Go to services</RouterLink>.
    </p>
  </div>
  <div v-else class="panel">
    <div class="table-wrap">
      <table class="data">
        <thead>
          <tr>
            <th scope="col">Service</th>
            <th scope="col">Label</th>
            <th scope="col">Action</th>
            <th scope="col">Created</th>
            <th scope="col">Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="entry in table.content" :key="entry.catalogId">
            <td>
              <RouterLink class="row-link" :to="{ name: 'service', params: { applicationName: entry.targetApplication } }">
                {{ serviceTitle(entry.targetApplication) }}
              </RouterLink>
            </td>
            <td>{{ entry.label }}</td>
            <td class="mono">{{ entry.action }}</td>
            <td>{{ formatInstant(entry.createdAt) }}</td>
            <td>
              <div class="button-row">
                <RouterLink
                  class="btn btn-primary"
                  :to="{ name: 'service', params: { applicationName: entry.targetApplication }, query: { tab: 'apply', catalogId: entry.catalogId } }"
                >Apply</RouterLink>
                <button type="button" class="btn btn-danger" @click="remove(entry)">Delete</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="pager">
      <p class="nums">Page {{ table.page + 1 }} of {{ table.pages }}</p>
      <div class="button-row">
        <button type="button" class="btn" :disabled="table.page === 0" @click="page = table.page - 1">Previous</button>
        <button type="button" class="btn" :disabled="table.page >= table.pages - 1" @click="page = table.page + 1">Next</button>
      </div>
    </div>
  </div>
</template>
