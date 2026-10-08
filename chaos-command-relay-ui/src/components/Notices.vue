<script setup>
import { clearNotices, notices } from '../lib/notices.js';
</script>

<template>
  <div v-if="notices.errors.length" class="alert alert-error" role="alert">
    <p v-for="(error, index) in notices.errors" :key="index">{{ error }}</p>
  </div>
  <div v-if="notices.success" class="alert alert-ok" role="status">
    <p>{{ notices.success }}</p>
    <p v-if="notices.commandId" class="mt-1">
      Command
      <RouterLink class="font-semibold underline" :to="`/commands/${notices.commandId}`">{{ notices.commandId }}</RouterLink>
    </p>
    <button type="button" class="mt-2 text-xs font-semibold underline" @click="clearNotices">Dismiss</button>
  </div>
</template>
