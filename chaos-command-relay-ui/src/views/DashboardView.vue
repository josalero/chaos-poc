<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { consoleActor, disableService, listServices, submitCommand } from '../api/relayClient.js';
import Notices from '../components/Notices.vue';
import StatusBadge from '../components/StatusBadge.vue';
import { commandFromPreset } from '../lib/commands.js';
import { errorMessages } from '../lib/errors.js';
import { serviceTitle } from '../lib/labels.js';
import { showErrors } from '../lib/notices.js';
import { presets } from '../presets/catalog.js';

const router = useRouter();
const services = ref([]);
const loading = ref(true);
const loadError = ref('');
const target = ref(presets[0]?.targetApplication || '');
const scenarioId = ref('');
const applying = ref(false);

const targets = computed(() => [...new Set(presets.map((preset) => preset.targetApplication))]);
const scenarios = computed(() =>
  presets.filter((preset) => preset.targetApplication === target.value && preset.action !== 'DISABLE'),
);
const disablePreset = computed(() =>
  presets.find((preset) => preset.targetApplication === target.value && preset.action === 'DISABLE'),
);
const selected = computed(() => scenarios.value.find((preset) => preset.id === scenarioId.value));

onMounted(async () => {
  try {
    services.value = await listServices();
    if (!target.value && services.value[0]) {
      target.value = services.value[0].applicationName;
    }
  } catch (error) {
    loadError.value = errorMessages(error, 'Could not load services')[0];
  } finally {
    loading.value = false;
  }
});

function onTargetChange() {
  scenarioId.value = '';
}

async function applyPreset(preset) {
  applying.value = true;
  try {
    const submitted = await submitCommand(commandFromPreset(preset));
    await router.push({ name: 'command', params: { commandId: submitted.commandId } });
  } catch (error) {
    showErrors(errorMessages(error));
  } finally {
    applying.value = false;
  }
}

async function turnOff(applicationName) {
  try {
    const submitted = await disableService(applicationName, consoleActor());
    await router.push({ name: 'command', params: { commandId: submitted.commandId } });
  } catch (error) {
    showErrors(errorMessages(error));
  }
}
</script>

<template>
  <Notices />
  <p v-if="loadError" class="mb-4 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-900" role="alert">
    {{ loadError }}
  </p>

  <header class="mb-6 flex flex-wrap items-end justify-between gap-4">
    <div>
      <p class="text-xs font-semibold tracking-widest text-orange-800">CONTROL PLANE</p>
      <h1 class="text-3xl font-semibold">Chaos command relay</h1>
      <p class="mt-1 text-stone-600">Choose a service and scenario, publish the patch, then verify its effect.</p>
    </div>
    <ol class="flex gap-3 text-sm text-stone-600" aria-label="Experiment workflow">
      <li><span class="mr-1 font-semibold text-stone-900">1</span>Target</li>
      <li><span class="mr-1 font-semibold text-stone-900">2</span>Scenario</li>
      <li><span class="mr-1 font-semibold text-stone-900">3</span>Verify</li>
    </ol>
  </header>

  <section class="mb-8" aria-labelledby="targets-title" :aria-busy="loading">
    <div class="mb-3 flex items-baseline justify-between">
      <div>
        <h2 id="targets-title" class="text-lg font-semibold">Services</h2>
        <p class="text-sm text-stone-500">Live state reported by relay and actuator</p>
      </div>
      <span class="text-sm text-stone-500">{{ services.length }} targets</span>
    </div>
    <p v-if="loading" class="text-sm text-stone-500">Loading services…</p>
    <p v-else-if="services.length === 0" class="rounded-lg border border-dashed border-stone-300 p-4 text-sm text-stone-600">
      No allowlisted services were returned.
    </p>
    <div v-else class="grid gap-3 md:grid-cols-2">
      <article
        v-for="service in services"
        :key="service.applicationName"
        class="rounded-xl border bg-white p-4"
        :class="service.cmEnabled ? 'border-orange-300' : 'border-stone-200'"
      >
        <div class="flex items-start justify-between gap-3">
          <div>
            <strong class="block">{{ serviceTitle(service.applicationName) }}</strong>
            <small class="mono text-stone-500">{{ service.applicationName }}</small>
          </div>
          <details class="relative">
            <summary class="cursor-pointer list-none rounded-md px-2 py-1 text-stone-500" :aria-label="`Actions for ${service.applicationName}`">•••</summary>
            <div class="absolute right-0 z-10 mt-1 w-48 rounded-lg border border-stone-200 bg-white p-1 text-sm shadow-lg">
              <RouterLink class="block rounded px-2 py-1 hover:bg-stone-50" :to="{ name: 'service', params: { applicationName: service.applicationName }, query: { tab: 'overview' } }">Status</RouterLink>
              <RouterLink class="block rounded px-2 py-1 hover:bg-stone-50" :to="{ name: 'service', params: { applicationName: service.applicationName }, query: { tab: 'commands' } }">History</RouterLink>
              <RouterLink class="block rounded px-2 py-1 hover:bg-stone-50" :to="{ name: 'service', params: { applicationName: service.applicationName }, query: { tab: 'maintenance' } }">Reset &amp; maintenance</RouterLink>
              <button v-if="service.cmEnabled" type="button" class="block w-full rounded px-2 py-1 text-left hover:bg-stone-50" @click="turnOff(service.applicationName)">Turn off chaos</button>
            </div>
          </details>
        </div>
        <dl class="mt-3 grid grid-cols-3 gap-2 text-sm">
          <div><dt class="text-stone-500">CM</dt><dd :class="service.cmEnabled ? 'font-semibold text-orange-800' : 'text-emerald-800'">{{ service.cmEnabled ? 'ON' : 'OFF' }}</dd></div>
          <div><dt class="text-stone-500">Replicas</dt><dd>{{ service.eurekaUpCount }}/{{ service.expectedInstances }}</dd></div>
          <div><dt class="text-stone-500">Last patch</dt><dd><StatusBadge :status="service.lastCommandStatus" /></dd></div>
        </dl>
      </article>
    </div>
  </section>

  <section aria-labelledby="scenario-title">
    <div class="mb-3 flex items-baseline justify-between">
      <div>
        <h2 id="scenario-title" class="text-lg font-semibold">Run an experiment</h2>
        <p class="text-sm text-stone-500">Commands use a two-hour lease and apply to every expected replica.</p>
      </div>
      <RouterLink class="text-sm text-stone-600 underline" to="/commands/new">Advanced JSON</RouterLink>
    </div>
    <p v-if="presets.length === 0" class="rounded-lg border border-dashed border-stone-300 p-4 text-sm">No scenarios are packaged with this console.</p>
    <form v-else class="rounded-xl border border-stone-200 bg-white p-4" @submit.prevent="selected && applyPreset(selected)">
      <div class="grid gap-4 md:grid-cols-2">
        <label class="block text-sm font-medium" for="target-select">
          Target service
          <select id="target-select" v-model="target" class="mt-1 w-full rounded-md border border-stone-300 bg-white px-3 py-2" @change="onTargetChange">
            <option v-for="name in targets" :key="name" :value="name">{{ serviceTitle(name) }}</option>
          </select>
        </label>
        <label class="block text-sm font-medium" for="scenario-select">
          Scenario
          <select id="scenario-select" v-model="scenarioId" class="mt-1 w-full rounded-md border border-stone-300 bg-white px-3 py-2" required>
            <option value="" disabled>Select a scenario…</option>
            <option v-for="preset in scenarios" :key="preset.id" :value="preset.id">{{ preset.label }}</option>
          </select>
        </label>
      </div>
      <div class="mt-4 rounded-lg bg-stone-50 p-3" aria-live="polite">
        <strong class="block">{{ selected?.label || 'No scenario selected' }}</strong>
        <p class="text-sm text-stone-600">{{ selected?.description || 'Choose a scenario to see the expected behavior before publishing.' }}</p>
      </div>
      <button type="submit" class="mt-4 rounded-md bg-stone-900 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50" :disabled="!selected || applying">
        {{ applying ? 'Publishing…' : 'Apply scenario' }}
      </button>
      <details v-if="disablePreset" class="mt-4 text-sm">
        <summary class="cursor-pointer">Need to stop the experiment?</summary>
        <p class="mt-2 text-stone-600">{{ disablePreset.description }}</p>
        <button type="button" class="mt-2 rounded-md bg-red-700 px-3 py-1.5 text-sm font-semibold text-white disabled:opacity-50" :disabled="applying" @click="applyPreset(disablePreset)">
          Turn off chaos
        </button>
      </details>
    </form>
  </section>
</template>
