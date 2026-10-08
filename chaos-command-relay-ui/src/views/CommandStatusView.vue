<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { getCommand, verifyUiUrl } from '../api/relayClient.js';
import PageHeader from '../components/PageHeader.vue';
import StatusBadge from '../components/StatusBadge.vue';
import { errorMessages } from '../lib/errors.js';
import { serviceTitle } from '../lib/labels.js';

const route = useRoute();
const status = ref(null);
const loadError = ref('');
let timer;

const waiting = computed(
  () => status.value && (status.value.status === 'PENDING' || status.value.status === 'PARTIAL'),
);
const headline = computed(() => {
  if (!status.value) return 'Loading patch…';
  if (status.value.status === 'APPLIED' && status.value.instanceSelection === 'SOME') {
    return 'Patch applied to selected replicas';
  }
  if (status.value.status === 'APPLIED') return 'Patch applied';
  if (status.value.status === 'FAILED' || status.value.status === 'TIMED_OUT') return 'Patch did not apply';
  return 'Waiting for replicas…';
});

async function load() {
  try {
    status.value = await getCommand(route.params.commandId);
    loadError.value = '';
  } catch (error) {
    loadError.value = errorMessages(error, 'Could not load this command')[0];
  }
}

function startPolling() {
  stopPolling();
  timer = setInterval(load, 2000);
}

function stopPolling() {
  if (timer) {
    clearInterval(timer);
    timer = null;
  }
}

watch(waiting, (isWaiting) => {
  if (isWaiting) startPolling();
  else stopPolling();
});

onMounted(load);
onUnmounted(stopPolling);
</script>

<template>
  <PageHeader
    :title="headline"
    :crumbs="[
      { label: 'Commands', to: '/commands' },
      { label: String(route.params.commandId).slice(0, 8) },
    ]"
  >
    <template v-if="status" #actions>
      <StatusBadge :status="status.status" />
    </template>
  </PageHeader>
  <p v-if="loadError" class="alert alert-error" role="alert">{{ loadError }}</p>
  <p v-else-if="status?.status === 'APPLIED' && status?.instanceSelection === 'SOME'" class="meta">
    Only the selected replicas were changed. The others keep their current assault.
  </p>
  <p v-else-if="status?.status === 'APPLIED'" class="meta">
    All expected replicas reported success. Open Verify UI and place orders.
  </p>
  <p v-else-if="status?.status === 'FAILED' || status?.status === 'TIMED_OUT'" class="meta">
    Check the replica results, then open this service history and apply again.
  </p>
  <p v-else-if="waiting" class="meta">Refreshing every 2 seconds until the command is applied or fails.</p>

  <section v-if="status" class="panel panel-pad">
    <h2 class="sr-only">Command</h2>
    <dl class="facts cols-4">
      <div>
        <dt>Target</dt>
        <dd>
          <RouterLink class="row-link" :to="{ name: 'service', params: { applicationName: status.targetApplication } }">
            {{ serviceTitle(status.targetApplication) }}
          </RouterLink>
        </dd>
      </div>
      <div>
        <dt>Replicas</dt>
        <dd class="nums">{{ status.successCount }} / {{ status.expectedInstances }}</dd>
      </div>
      <div>
        <dt>Action</dt>
        <dd class="mono">{{ status.action }}</dd>
      </div>
      <div>
        <dt>Scope</dt>
        <dd class="mono">{{ status.instanceSelection || 'ALL' }}<span v-if="status.instanceIds?.length"> · {{ status.instanceIds.join(', ') }}</span></dd>
      </div>
    </dl>
  </section>

  <section v-if="status?.instances?.length" class="stack">
    <h2>Replica results</h2>
    <div class="split">
      <article
        v-for="instance in status.instances"
        :key="instance.podName"
        class="panel panel-pad"
      >
        <strong class="mono">{{ instance.podName }}</strong>
        <span :class="instance.outcome === 'SUCCESS' ? '' : 'bad'">{{ instance.outcome }}</span>
        <small class="sub">
          {{ instance.httpStatus != null ? `HTTP ${instance.httpStatus}` : instance.failedStep || '—' }}
        </small>
      </article>
    </div>
  </section>

  <div v-if="status?.status === 'APPLIED'" class="button-row">
    <a class="btn btn-primary" :href="verifyUiUrl" target="_blank" rel="noopener noreferrer">Open Verify UI</a>
    <RouterLink class="btn" :to="{ name: 'service', params: { applicationName: status.targetApplication }, query: { tab: 'commands' } }">Service history</RouterLink>
  </div>
  <div v-else-if="status?.status === 'FAILED' || status?.status === 'TIMED_OUT'" class="button-row">
    <RouterLink class="btn btn-primary" :to="{ name: 'service', params: { applicationName: status.targetApplication }, query: { tab: 'apply' } }">Apply again</RouterLink>
    <RouterLink class="btn" :to="{ name: 'service', params: { applicationName: status.targetApplication }, query: { tab: 'commands' } }">Service history</RouterLink>
  </div>
</template>
