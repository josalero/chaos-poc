<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { listServices, verifyUiUrl } from '../api/relayClient.js';
import { serviceTitle } from '../lib/labels.js';

const route = useRoute();
const collectionUrl = `${import.meta.env.BASE_URL}chaos-command-relay.postman_collection.json`;
const services = ref([]);
let refreshTimer;

const chaosActiveCount = computed(
  () => services.value.filter((service) => service.cmEnabled === true).length,
);
const onService = computed(() => route.name === 'service');
const serviceLabel = computed(() => serviceTitle(String(route.params.applicationName || '')));

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
});

async function refresh() {
  try {
    services.value = await listServices();
  } catch {
    services.value = [];
  }
}
</script>

<template>
  <aside class="rail">
    <a href="#main-content" class="sr-only focus:not-sr-only focus:absolute focus:left-3 focus:top-3 focus:z-30 focus:bg-white focus:px-3 focus:py-2">
      Skip to main content
    </a>
    <div class="rail-top">
      <RouterLink to="/" custom v-slot="{ href, navigate }">
        <a :href="href" class="brand" @click="navigate">
          <strong>Chaos Operator</strong>
          <small>Allowlisted services</small>
        </a>
      </RouterLink>
      <p class="status-pill" :data-hot="chaosActiveCount > 0">
        {{ chaosActiveCount > 0 ? `${chaosActiveCount} under chaos` : 'System normal' }}
      </p>
    </div>
    <nav aria-label="Console">
      <RouterLink
        to="/"
        class="nav-link"
        :class="{ 'is-current': route.name === 'services', 'is-section': onService }"
        :aria-current="route.name === 'services' ? 'page' : undefined"
      >Services</RouterLink>
      <RouterLink
        v-if="onService"
        class="nav-sub"
        aria-current="page"
        :to="{ name: 'service', params: { applicationName: route.params.applicationName }, query: route.query }"
      >{{ serviceLabel }}</RouterLink>
      <RouterLink
        to="/commands"
        class="nav-link"
        :class="{
          'is-current': route.name === 'commands',
          'is-section': route.name === 'command',
        }"
        :aria-current="route.name === 'commands' ? 'page' : undefined"
      >Commands</RouterLink>
      <RouterLink
        to="/saved"
        class="nav-link"
        :class="{ 'is-current': route.name === 'saved' }"
        :aria-current="route.name === 'saved' ? 'page' : undefined"
      >Saved</RouterLink>
    </nav>
    <div class="rail-foot">
      <a class="verify-link" :href="verifyUiUrl" target="_blank" rel="noopener noreferrer">
        Verify UI
        <span class="sr-only">(opens in a new tab)</span>
      </a>
      <a class="verify-link" :href="collectionUrl" download="chaos-command-relay.postman_collection.json">
        Postman collection
      </a>
    </div>
  </aside>
</template>
