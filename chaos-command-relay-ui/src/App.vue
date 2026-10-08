<script setup>
import AppHeader from './components/AppHeader.vue';
</script>

<template>
  <div class="shell">
    <AppHeader />
    <main id="main-content" class="shell-main">
      <router-view />
    </main>
  </div>
</template>
