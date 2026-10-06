<script setup>
import AppHeader from './components/AppHeader.vue';
</script>

<template>
  <AppHeader />
  <main id="main-content" class="mx-auto w-full max-w-6xl px-4 py-6">
    <router-view />
  </main>
</template>
