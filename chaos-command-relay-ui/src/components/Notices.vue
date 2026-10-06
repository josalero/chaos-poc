<script setup>
import { clearNotices, notices } from '../lib/notices.js';
</script>

<template>
  <div v-if="notices.errors.length" class="mb-4 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-900" role="alert">
    <p v-for="(error, index) in notices.errors" :key="index">{{ error }}</p>
  </div>
  <div v-if="notices.success" class="mb-4 rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-950" role="status">
    <p>{{ notices.success }}</p>
    <p v-if="notices.commandId" class="mt-1">
      Command:
      <RouterLink class="font-medium underline" :to="`/commands/${notices.commandId}`">{{ notices.commandId }}</RouterLink>
    </p>
    <button type="button" class="mt-2 text-xs underline" @click="clearNotices">Dismiss</button>
  </div>
</template>
