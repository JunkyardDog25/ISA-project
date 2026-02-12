<script setup>
import { useToast } from '@/composables/useToast.js';

const { toasts } = useToast();

function getToastClass(type) {
  switch (type) {
    case 'success':
      return 'text-bg-success';
    case 'info':
      return 'text-bg-info';
    default:
      return 'text-bg-danger';
  }
}

function getToastLabel(type) {
  switch (type) {
    case 'success':
      return '✓ Success';
    case 'info':
      return 'ℹ Info';
    default:
      return '✕ Error';
  }
}
</script>

<template>
  <div class="toast-container position-fixed top-0 end-0 p-3">
    <div
      v-for="toast in toasts"
      :key="toast.id"
      :id="`toast-${toast.id}`"
      class="toast"
      :class="getToastClass(toast.type)"
      role="alert"
      aria-live="assertive"
      aria-atomic="true"
    >
      <div
        class="toast-header"
        :class="getToastClass(toast.type)"
      >
        <strong class="me-auto">
          {{ getToastLabel(toast.type) }}
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

