<script setup>
defineProps({
  status: { type: String, default: '' },
});

const tone = {
  APPLIED: 'bg-emerald-100 text-emerald-900',
  FAILED: 'bg-red-100 text-red-900',
  TIMED_OUT: 'bg-red-100 text-red-900',
  PENDING: 'bg-amber-100 text-amber-950',
  PARTIAL: 'bg-amber-100 text-amber-950',
  PUBLISHED: 'bg-sky-100 text-sky-950',
};
</script>

<template>
  <span
    class="inline-flex rounded-full px-2 py-0.5 text-xs font-semibold tracking-wide"
    :class="tone[status] || 'bg-stone-200 text-stone-800'"
  >
    {{ status || '—' }}
  </span>
</template>
