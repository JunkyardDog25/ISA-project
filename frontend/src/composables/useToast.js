import { ref, nextTick } from 'vue';
import { Toast } from 'bootstrap';

// ----- Shared State -----

const toasts = ref([]);
let toastId = 0;

// ----- Composable -----

/**
 * Toast notification composable.
 * Provides methods to show success/error toasts with auto-dismiss.
 */
export function useToast() {
  // ----- Toast Methods -----

  /**
   * Show a toast notification.
   * @param {string} message - Toast message
   * @param {('success'|'error')} type - Toast type
   * @param {number} delay - Auto-dismiss delay in ms (default: 5000)
   */
  function showToast(message, type = 'error', delay = 5000) {
    const id = ++toastId;
    toasts.value.push({ id, message, type });

    nextTick(() => {
      const toastEl = document.getElementById(`toast-${id}`);
      if (toastEl) {
        const toast = new Toast(toastEl, { delay });
        toast.show();
        toastEl.addEventListener('hidden.bs.toast', () => {
          removeToast(id);
        });
      }
    });
  }

  /**
   * Show an error toast.
   * @param {string} message - Error message
   */
  function showError(message) {
    showToast(message, 'error');
  }

  /**
   * Show a success toast.
   * @param {string} message - Success message
   */
  function showSuccess(message) {
    showToast(message, 'success');
  }

  /**
   * Show an info toast.
   * @param {string} message - Info message
   */
  function showInfo(message) {
    showToast(message, 'info');
  }

  /**
   * Remove a toast by ID.
   * @param {number} id - Toast ID
   */
  function removeToast(id) {
    toasts.value = toasts.value.filter((t) => t.id !== id);
  }

  /**
   * Clear all toasts.
   */
  function clearToasts() {
    toasts.value = [];
  }

  // ----- Return Public API -----

  return {
    // State
    toasts,

    // Methods
    showToast,
    showError,
    showSuccess,
    showInfo,
    removeToast,
    clearToasts
  };
}

