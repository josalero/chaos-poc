<script setup>
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getService, submitCommand } from '../api/relayClient.js';
import Notices from '../components/Notices.vue';
import { commandFromForm, leaseExpiresAt } from '../lib/commands.js';
import { errorMessages } from '../lib/errors.js';
import { showErrors } from '../lib/notices.js';
import { presets } from '../presets/catalog.js';

const route = useRoute();
const router = useRouter();
const targets = [...new Set(presets.map((preset) => preset.targetApplication))];
const actions = ['CONFIGURE_AND_ENABLE', 'CONFIGURE', 'ENABLE', 'DISABLE'];
const publishing = ref(false);
const parseError = ref('');

const form = ref({
  targetApplication: route.query.applicationName || targets[0] || '',
  action: 'CONFIGURE_AND_ENABLE',
  issuedBy: 'chaos-console',
  correlationId: '',
  expiresAt: leaseExpiresAt(),
  presetId: '',
  assaultJson: '',
  instanceSelection: route.query.instanceSelection === 'SOME' ? 'SOME' : 'ALL',
  instanceIds: [],
});
const upInstanceIds = ref([]);

const selectedPreset = computed(() => presets.find((preset) => preset.id === form.value.presetId) || null);

watch(selectedPreset, (preset) => {
  if (!preset || form.value.assaultJson.trim()) return;
  form.value.action = preset.action;
  form.value.targetApplication = preset.targetApplication;
});

watch(
  () => form.value.targetApplication,
  async (applicationName) => {
    form.value.instanceIds = [];
    if (!applicationName) {
      upInstanceIds.value = [];
      return;
    }
    try {
      const service = await getService(applicationName);
      upInstanceIds.value = service.upInstanceIds || [];
    } catch {
      upInstanceIds.value = [];
    }
  },
  { immediate: true },
);

async function publish() {
  parseError.value = '';
  if (form.value.instanceSelection === 'SOME' && form.value.instanceIds.length === 0) {
    parseError.value = 'Select at least one instance.';
    return;
  }
  let command;
  try {
    command = commandFromForm({ ...form.value, preset: selectedPreset.value });
  } catch {
    parseError.value = 'Assault JSON is not valid JSON.';
    return;
  }
  publishing.value = true;
  try {
    const submitted = await submitCommand(command);
    await router.push({ name: 'command', params: { commandId: submitted.commandId } });
  } catch (error) {
    showErrors(errorMessages(error));
  } finally {
    publishing.value = false;
  }
}
</script>

<template>
  <RouterLink class="text-sm text-stone-600 underline" to="/">Back to scenarios</RouterLink>
  <header class="mt-4">
    <p class="text-xs font-semibold tracking-widest text-orange-800">ADVANCED</p>
    <h1 class="text-3xl font-semibold">Manual patch</h1>
    <p class="mt-1 text-stone-600">Prefer scenarios on the home page unless you need custom assault JSON.</p>
  </header>
  <Notices />
  <p v-if="parseError" class="mb-4 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-900" role="alert">{{ parseError }}</p>

  <form class="rounded-xl border border-stone-200 bg-white p-5" @submit.prevent="publish">
    <div class="grid gap-4">
      <label class="text-sm font-medium" for="targetApplication">Target
        <select id="targetApplication" v-model="form.targetApplication" required class="mt-1 w-full rounded-md border border-stone-300 px-3 py-2">
          <option v-for="target in targets" :key="target" :value="target">{{ target }}</option>
        </select>
      </label>
      <fieldset class="text-sm font-medium">
        <legend>Instances</legend>
        <div class="mt-1 flex gap-4 font-normal">
          <label><input v-model="form.instanceSelection" type="radio" value="ALL" /> All UP instances</label>
          <label><input v-model="form.instanceSelection" type="radio" value="SOME" /> Some instances</label>
        </div>
        <div v-if="form.instanceSelection === 'SOME'" class="mt-2 grid gap-1 font-normal">
          <p v-if="!upInstanceIds.length" class="text-stone-500">No UP instances are registered for this target.</p>
          <label v-for="instanceId in upInstanceIds" :key="instanceId" class="font-mono text-xs">
            <input v-model="form.instanceIds" type="checkbox" :value="instanceId" />
            {{ instanceId }}
          </label>
        </div>
      </fieldset>
      <label class="text-sm font-medium" for="action">Action
        <select id="action" v-model="form.action" required class="mt-1 w-full rounded-md border border-stone-300 px-3 py-2">
          <option v-for="action in actions" :key="action" :value="action">{{ action }}</option>
        </select>
      </label>
      <label class="text-sm font-medium" for="issuedBy">Issued by
        <input id="issuedBy" v-model="form.issuedBy" required autocomplete="off" class="mt-1 w-full rounded-md border border-stone-300 px-3 py-2" />
      </label>
      <label class="text-sm font-medium" for="correlationId">Correlation ID (optional)
        <input id="correlationId" v-model="form.correlationId" autocomplete="off" class="mt-1 w-full rounded-md border border-stone-300 px-3 py-2" />
      </label>
      <label class="text-sm font-medium" for="expiresAt">Expires at (ISO-8601)
        <input id="expiresAt" v-model="form.expiresAt" placeholder="2026-10-06T18:00:00Z" class="mt-1 w-full rounded-md border border-stone-300 px-3 py-2" />
      </label>
      <label class="text-sm font-medium" for="presetId">Preset (optional)
        <select id="presetId" v-model="form.presetId" class="mt-1 w-full rounded-md border border-stone-300 px-3 py-2">
          <option value="">Custom assault JSON below</option>
          <option v-for="preset in presets" :key="preset.id" :value="preset.id">{{ preset.label }}</option>
        </select>
      </label>
      <label class="text-sm font-medium" for="assaultJson">Assault JSON
        <textarea id="assaultJson" v-model="form.assaultJson" rows="8" class="mono mt-1 w-full rounded-md border border-stone-300 px-3 py-2" placeholder='{"level":1,"exceptionsActive":true}'></textarea>
      </label>
    </div>
    <div class="mt-4 flex gap-2">
      <button type="submit" class="rounded-md bg-stone-900 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50" :disabled="publishing">
        {{ publishing ? 'Publishing…' : 'Publish' }}
      </button>
      <RouterLink class="rounded-md border border-stone-300 px-4 py-2 text-sm" to="/">Cancel</RouterLink>
    </div>
  </form>
</template>
