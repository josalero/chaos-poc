<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { getCommand, verifyUiUrl } from '../api/relayClient.js';
import StatusBadge from '../components/StatusBadge.vue';
import { errorMessages } from '../lib/errors.js';

const route = useRoute();
const status = ref(null);
const loadError = ref('');
let timer;

const waiting = computed(
  () => status.value && (status.value.status === 'PENDING' || status.value.status === 'PARTIAL'),
);
const headline = computed(() => {
  if (!status.value) return 'Loading patch…';
  if (status.value.status === 'APPLIED') return 'Patch applied';
  if (status.value.status === 'FAILED' || status.value.status === 'TIMED_OUT') return 'Patch failed';
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
  <RouterLink class="text-sm text-stone-600 underline" to="/">Back to scenarios</RouterLink>
  <p v-if="loadError" class="mt-4 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-900" role="alert">{{ loadError }}</p>

  <section class="mt-4 flex flex-wrap items-start justify-between gap-4 rounded-xl border border-stone-200 bg-white p-5">
    <div>
      <p class="text-xs font-semibold tracking-widest text-orange-800">PATCH STATUS</p>
      <h1 class="text-3xl font-semibold">{{ headline }}</h1>
      <p v-if="status?.status === 'APPLIED'" class="mt-2 text-stone-600">All expected replicas reported success. Open Verify UI and place orders.</p>
      <p v-else-if="status?.status === 'FAILED' || status?.status === 'TIMED_OUT'" class="mt-2 text-stone-600">Something went wrong. Check pod results or try another scenario.</p>
      <p v-else-if="waiting" class="mt-2 text-stone-600">Refreshing every 2 seconds until Applied or Failed.</p>
    </div>
    <StatusBadge v-if="status" :status="status.status" />
  </section>

  <section v-if="status" class="mt-4 rounded-xl border border-stone-200 bg-white p-5">
    <dl class="grid gap-4 sm:grid-cols-3">
      <div><dt class="text-sm text-stone-500">Target</dt><dd>{{ status.targetApplication }}</dd></div>
      <div><dt class="text-sm text-stone-500">Replicas</dt><dd>{{ status.successCount }} / {{ status.expectedInstances }}</dd></div>
      <div><dt class="text-sm text-stone-500">Action</dt><dd class="mono">{{ status.action }}</dd></div>
    </dl>
  </section>

  <section v-if="status?.instances?.length" class="mt-4 rounded-xl border border-stone-200 bg-white p-5">
    <h2 class="text-lg font-semibold">Per-pod results</h2>
    <div class="mt-3 grid gap-3 sm:grid-cols-2">
      <article
        v-for="instance in status.instances"
        :key="instance.podName"
        class="rounded-lg border p-3"
        :class="instance.outcome === 'SUCCESS' ? 'border-emerald-200 bg-emerald-50' : 'border-red-200 bg-red-50'"
      >
        <strong class="mono block">{{ instance.podName }}</strong>
        <span>{{ instance.outcome }}</span>
        <small class="block text-stone-600">
          {{ instance.httpStatus != null ? `HTTP ${instance.httpStatus}` : instance.failedStep || '—' }}
        </small>
      </article>
    </div>
  </section>

  <div v-if="status?.status === 'APPLIED'" class="mt-4 flex flex-wrap gap-2">
    <a class="rounded-md bg-stone-900 px-4 py-2 text-sm font-semibold text-white" :href="verifyUiUrl" target="_blank" rel="noopener noreferrer">Open Verify UI</a>
    <RouterLink class="rounded-md border border-stone-300 px-4 py-2 text-sm" :to="{ name: 'service', params: { applicationName: status.targetApplication }, query: { tab: 'commands' } }">History</RouterLink>
    <RouterLink class="rounded-md border border-stone-300 px-4 py-2 text-sm" to="/">More scenarios</RouterLink>
  </div>
  <div v-else-if="status?.status === 'FAILED' || status?.status === 'TIMED_OUT'" class="mt-4 flex flex-wrap gap-2">
    <RouterLink class="rounded-md bg-stone-900 px-4 py-2 text-sm font-semibold text-white" to="/">Try another scenario</RouterLink>
    <RouterLink class="rounded-md border border-stone-300 px-4 py-2 text-sm" :to="{ name: 'service', params: { applicationName: status.targetApplication }, query: { tab: 'commands' } }">History</RouterLink>
  </div>
</template>
