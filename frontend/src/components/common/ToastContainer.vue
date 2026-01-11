<script setup>
import { useToast } from '@/composables/useToast.js';

const { toasts } = useToast();
</script>

<template>
  <div class="toast-container position-fixed top-0 end-0 p-3">
    <div
      v-for="toast in toasts"
      :key="toast.id"
      :id="`toast-${toast.id}`"
      class="toast"
      :class="toast.type === 'success' ? 'text-bg-success' : 'text-bg-danger'"
      role="alert"
      aria-live="assertive"
      aria-atomic="true"
    >
      <div
        class="toast-header"
        :class="toast.type === 'success' ? 'text-bg-success' : 'text-bg-danger'"
      >
        <strong class="me-auto">
          {{ toast.type === 'success' ? '✓ Success' : '✕ Error' }}
        </strong>
        <button
          type="button"
          class="btn-close btn-close-white"
          data-bs-dismiss="toast"
          aria-label="Close"
        ></button>
      </div>
      <div class="toast-body">{{ toast.message }}</div>
    </div>
  </div>
</template>

