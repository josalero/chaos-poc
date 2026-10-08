<script setup>
import { computed, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { getService, listCatalog, saveCatalog, submitCommand } from '../api/relayClient.js';
import { commandFromForm, leaseExpiresAt } from '../lib/commands.js';
import { errorMessages } from '../lib/errors.js';
import { showErrors } from '../lib/notices.js';
import { presetsFor } from '../presets/catalog.js';

const props = defineProps({
  applicationName: { type: String, required: true },
  catalogId: { type: String, default: '' },
});

const router = useRouter();
const actions = ['CONFIGURE_AND_ENABLE', 'CONFIGURE', 'ENABLE', 'DISABLE'];
const publishing = ref(false);
const parseError = ref('');
const catalog = ref([]);
const selectedCatalogId = ref('');
const saveToCatalog = ref(false);
const saveLabel = ref('');
const upInstanceIds = ref([]);

const form = ref(emptyForm(props.applicationName));

const availablePresets = computed(() => presetsFor(props.applicationName));
const selectedPreset = computed(
  () => availablePresets.value.find((preset) => preset.id === form.value.presetId) || null,
);

watch(
  () => props.applicationName,
  async (applicationName) => {
    form.value = emptyForm(applicationName);
    selectedCatalogId.value = '';
    saveToCatalog.value = false;
    saveLabel.value = '';
    await loadCatalog();
    await loadInstances(applicationName);
  },
  { immediate: true },
);

watch(
  () => catalogEntry(props.catalogId),
  (entry) => {
    if (entry) {
      fillFromCatalog(entry);
    }
  },
);

function emptyForm(applicationName) {
  return {
    targetApplication: applicationName,
    action: 'CONFIGURE_AND_ENABLE',
    issuedBy: 'chaos-console',
    correlationId: '',
    expiresAt: leaseExpiresAt(),
    presetId: '',
    assaultJson: '',
    instanceSelection: 'ALL',
    instanceIds: [],
  };
}

function catalogEntry(catalogId) {
  return catalog.value.find((entry) => entry.catalogId === catalogId) || null;
}

async function loadCatalog() {
  catalog.value = await listCatalog(props.applicationName).catch(() => []);
  const entry = catalogEntry(props.catalogId);
  if (entry) {
    fillFromCatalog(entry);
  }
}

async function loadInstances(applicationName) {
  form.value.instanceIds = [];
  try {
    const service = await getService(applicationName);
    upInstanceIds.value = service.upInstanceIds || [];
  } catch {
    upInstanceIds.value = [];
  }
}

function onPreset() {
  const preset = selectedPreset.value;
  if (!preset) {
    return;
  }
  selectedCatalogId.value = '';
  form.value.action = preset.action;
  form.value.assaultJson = preset.assault ? JSON.stringify(preset.assault, null, 2) : '';
  form.value.expiresAt = preset.action === 'DISABLE' ? '' : leaseExpiresAt();
}

function onCatalogChange() {
  const entry = catalogEntry(selectedCatalogId.value);
  if (entry) {
    fillFromCatalog(entry);
  }
}

function fillFromCatalog(entry) {
  selectedCatalogId.value = entry.catalogId;
  form.value.presetId = '';
  form.value.action = entry.action;
  form.value.assaultJson = entry.assault ? JSON.stringify(entry.assault, null, 2) : '';
  form.value.expiresAt = entry.action === 'DISABLE' ? '' : leaseExpiresAt();
  saveLabel.value = entry.label;
}

async function publish() {
  parseError.value = '';
  if (form.value.instanceSelection === 'SOME' && form.value.instanceIds.length === 0) {
    parseError.value = 'Select at least one instance.';
    return;
  }
  if (saveToCatalog.value && !saveLabel.value.trim()) {
    parseError.value = 'Enter a name to save this assault on the service.';
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
    if (saveToCatalog.value) {
      await saveCatalog(props.applicationName, {
        label: saveLabel.value.trim(),
        action: command.action,
        assault: command.assault,
      });
    }
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
  <p v-if="parseError" class="alert alert-error" role="alert">{{ parseError }}</p>
  <form class="panel panel-pad" @submit.prevent="publish">
    <div class="split">
      <div class="stack">
        <label class="field" for="saved-assault">Saved assault
          <select id="saved-assault" v-model="selectedCatalogId" @change="onCatalogChange">
            <option value="">Manual assault</option>
            <option v-for="entry in catalog" :key="entry.catalogId" :value="entry.catalogId">{{ entry.label }}</option>
          </select>
        </label>
        <label class="field" for="presetId">Preset
          <select id="presetId" v-model="form.presetId" @change="onPreset">
            <option value="">Custom assault JSON below</option>
            <option v-for="preset in availablePresets" :key="preset.id" :value="preset.id">{{ preset.label }}</option>
          </select>
        </label>
        <label class="field" for="action">Action
          <select id="action" v-model="form.action" required>
            <option v-for="action in actions" :key="action" :value="action">{{ action }}</option>
          </select>
        </label>
        <label class="field" for="assaultJson">Assault JSON
          <textarea id="assaultJson" v-model="form.assaultJson" rows="8" class="mono" placeholder='{"level":1,"exceptionsActive":true}'></textarea>
        </label>
      </div>
      <div class="stack">
        <p>Target <strong class="mono">{{ applicationName }}</strong></p>
        <fieldset>
          <legend>Instances</legend>
          <div class="button-row">
            <label class="check-row"><input v-model="form.instanceSelection" type="radio" value="ALL" /> All UP instances</label>
            <label class="check-row"><input v-model="form.instanceSelection" type="radio" value="SOME" /> Some instances</label>
          </div>
          <div v-if="form.instanceSelection === 'SOME'" class="stack">
            <p v-if="!upInstanceIds.length" class="meta">No UP instances are registered for this target.</p>
            <label v-for="instanceId in upInstanceIds" :key="instanceId" class="check-row mono">
              <input v-model="form.instanceIds" type="checkbox" :value="instanceId" />
              {{ instanceId }}
            </label>
          </div>
        </fieldset>
        <label class="field" for="issuedBy">Issued by
          <input id="issuedBy" v-model="form.issuedBy" required autocomplete="off" />
        </label>
        <label class="field" for="correlationId">Correlation ID
          <input id="correlationId" v-model="form.correlationId" autocomplete="off" placeholder="Optional" />
        </label>
        <label class="field" for="expiresAt">Expires at
          <input id="expiresAt" v-model="form.expiresAt" placeholder="2026-10-06T18:00:00Z" />
        </label>
        <fieldset>
          <label class="check-row">
            <input v-model="saveToCatalog" type="checkbox" />
            Save to this service
          </label>
          <p class="meta">The same name replaces the stored action and assault.</p>
          <label v-if="saveToCatalog" class="field" for="save-label">Name
            <input id="save-label" v-model="saveLabel" />
          </label>
        </fieldset>
      </div>
    </div>
    <button type="submit" class="btn btn-primary" :disabled="publishing">
      {{ publishing ? 'Publishing…' : 'Apply assault' }}
    </button>
  </form>
</template>
