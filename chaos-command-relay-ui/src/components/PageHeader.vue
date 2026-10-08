<script setup>
defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
  crumbs: { type: Array, default: () => [] },
});
</script>

<template>
  <header class="page-head">
    <nav v-if="crumbs.length" aria-label="Breadcrumb">
      <ol class="crumbs">
        <li v-for="(crumb, index) in crumbs" :key="`${crumb.label}-${index}`">
          <RouterLink v-if="crumb.to" :to="crumb.to">{{ crumb.label }}</RouterLink>
          <span v-else aria-current="page">{{ crumb.label }}</span>
        </li>
      </ol>
    </nav>
    <div class="page-head-row">
      <div>
        <h1>{{ title }}</h1>
        <p v-if="description">{{ description }}</p>
      </div>
      <div v-if="$slots.actions" class="page-actions">
        <slot name="actions" />
      </div>
    </div>
  </header>
</template>
