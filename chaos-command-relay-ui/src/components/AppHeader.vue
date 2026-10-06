<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { consoleActor, listServices, resetAll, verifyUiUrl } from '../api/relayClient.js';
import { errorMessages } from '../lib/errors.js';
import { showErrors, showOutcome, showSuccess } from '../lib/notices.js';

const route = useRoute();
const router = useRouter();
const services = ref([]);
const resetting = ref(false);

const chaosActiveCount = computed(
  () => services.value.filter((service) => service.cmEnabled === true).length,
);

onMounted(async () => {
  try {
    services.value = await listServices();
  } catch {
    services.value = [];
  }
});

async function resetAllServices() {
  if (!window.confirm('Turn off Chaos Monkey on every service?')) {
    return;
  }
  resetting.value = true;
  try {
    const result = await resetAll(consoleActor());
    const failed = (result.services || []).filter((service) => service.commandStatus !== 'APPLIED');
    if (failed.length === 0) {
      showSuccess(
        `Chaos Monkey configuration reset for ${result.totalCount} services (assaults disabled).`,
      );
    } else {
      const errors = failed.flatMap((service) =>
        (service.errors || []).map(
          (item) => `${service.applicationName}: ${item.field}: ${item.message}`,
        ),
      );
      showOutcome({
        success:
          result.successCount > 0
            ? `Partial reset: ${result.successCount} of ${result.totalCount} services reset.`
            : null,
        errors,
      });
    }
    if (route.name !== 'scenarios') {
      await router.push({ name: 'scenarios' });
    }
  } catch (error) {
    showErrors(errorMessages(error, 'Reset all failed'));
  } finally {
    resetting.value = false;
  }
}
</script>

<template>
  <header class="border-b border-stone-200 bg-white/90">
    <a href="#main-content" class="sr-only focus:not-sr-only focus:absolute focus:left-3 focus:top-3 focus:z-10 focus:bg-white focus:px-3 focus:py-2">
      Skip to main content
    </a>
    <div class="mx-auto flex max-w-6xl flex-wrap items-center gap-4 px-4 py-3">
      <RouterLink to="/" class="flex items-center gap-3">
        <span class="grid h-10 w-10 place-items-center rounded-lg bg-orange-100 font-bold text-orange-800" aria-hidden="true">CM</span>
        <span>
          <strong class="block leading-tight">Chaos Operator</strong>
          <small class="text-stone-500">Patch scenarios · history · reset</small>
        </span>
      </RouterLink>
      <nav class="flex gap-1" aria-label="Console navigation">
        <RouterLink
          to="/"
          class="rounded-md px-3 py-2 text-sm font-medium"
          :class="route.name === 'scenarios' ? 'bg-stone-900 text-white' : 'text-stone-600'"
        >Scenarios</RouterLink>
        <RouterLink
          to="/commands/new"
          class="rounded-md px-3 py-2 text-sm font-medium"
          :class="route.name === 'publish' ? 'bg-stone-900 text-white' : 'text-stone-600'"
        >Publish</RouterLink>
      </nav>
      <div class="ml-auto flex items-center gap-3">
        <p
          class="rounded-full px-3 py-1 text-xs font-semibold"
          :class="chaosActiveCount > 0 ? 'bg-orange-100 text-orange-900' : 'bg-emerald-100 text-emerald-900'"
        >
          {{ chaosActiveCount > 0 ? `${chaosActiveCount} UNDER CHAOS` : 'SYSTEM NORMAL' }}
        </p>
        <details class="relative">
          <summary class="cursor-pointer list-none rounded-md border border-stone-300 px-3 py-1.5 text-sm">Actions</summary>
          <div class="absolute right-0 z-20 mt-2 w-64 rounded-lg border border-stone-200 bg-white p-2 shadow-lg">
            <a class="block rounded-md px-3 py-2 hover:bg-stone-50" :href="verifyUiUrl" target="_blank" rel="noopener noreferrer">
              <strong class="block text-sm">Open Verify UI</strong>
              <small class="text-stone-500">Observe traffic and telemetry</small>
            </a>
            <RouterLink class="block rounded-md px-3 py-2 hover:bg-stone-50" to="/commands/new">
              <strong class="block text-sm">Advanced publish</strong>
              <small class="text-stone-500">Send custom assault JSON</small>
            </RouterLink>
            <button
              type="button"
              class="w-full rounded-md px-3 py-2 text-left text-red-800 hover:bg-red-50 disabled:opacity-60"
              :disabled="resetting"
              @click="resetAllServices"
            >
              <strong class="block text-sm">Reset all services</strong>
              <small>Disable assaults and restore defaults</small>
            </button>
          </div>
        </details>
      </div>
    </div>
  </header>
</template>
