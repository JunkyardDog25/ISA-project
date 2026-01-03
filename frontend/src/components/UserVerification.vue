<script setup>
import { ref, nextTick, computed } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { Toast } from 'bootstrap';
import { verifyUser, resendVerificationCode } from '../services/UserService';

// Router
const router = useRouter();
const route = useRoute();

// Form state
const loading = ref(false);
const resending = ref(false);

// Get email from query params (passed from registration)
const email = ref(route.query.email || '');

// Verification code (6 digits)
const CODE_LENGTH = 6;
const digits = ref(Array(CODE_LENGTH).fill(''));
const digitRefs = ref([]);

// Toast notifications
const toasts = ref([]);
let toastId = 0;

// ----- Computed -----

const verificationCode = computed(() => digits.value.join(''));
const isCodeComplete = computed(() => verificationCode.value.length === CODE_LENGTH);

// ----- Digit Input Handling -----

function setDigitRef(el, index) {
  if (el) digitRefs.value[index] = el;
}

function handleInput(index, event) {
  const value = event.target.value;

  // Only allow single digit, take the last character if multiple
  digits.value[index] = value.slice(-1);

  // Move to next input if digit entered
  if (digits.value[index] && index < CODE_LENGTH - 1) {
    nextTick(() => {
      digitRefs.value[index + 1]?.focus();
    });
  }
}

function handleKeydown(index, event) {
  // Handle backspace
  if (event.key === 'Backspace') {
    if (!digits.value[index] && index > 0) {
      event.preventDefault();
      digits.value[index - 1] = '';
      nextTick(() => {
        digitRefs.value[index - 1]?.focus();
      });
    }
  }

  // Handle arrow keys
  if (event.key === 'ArrowLeft' && index > 0) {
    digitRefs.value[index - 1]?.focus();
  }
  if (event.key === 'ArrowRight' && index < CODE_LENGTH - 1) {
    digitRefs.value[index + 1]?.focus();
  }
}

function handlePaste(event) {
  event.preventDefault();
  const pastedData = event.clipboardData
    .getData('text')
    .replace(/\D/g, '')
    .slice(0, CODE_LENGTH);

  for (let i = 0; i < pastedData.length; i++) {
    digits.value[i] = pastedData[i];
  }

  // Focus the next empty input or the last one
  const nextEmptyIndex = digits.value.findIndex((d) => !d);
  const focusIndex = nextEmptyIndex === -1 ? CODE_LENGTH - 1 : nextEmptyIndex;
  nextTick(() => {
    digitRefs.value[focusIndex]?.focus();
  });
}

function clearDigits() {
  digits.value = Array(CODE_LENGTH).fill('');
  nextTick(() => {
    digitRefs.value[0]?.focus();
  });
}

// ----- Toast Notifications -----

function showToast(message, type = 'error') {
  const id = ++toastId;
  toasts.value.push({ id, message, type });

  nextTick(() => {
    const toastEl = document.getElementById(`toast-${id}`);
    if (toastEl) {
      const toast = new Toast(toastEl, { delay: 5000 });
      toast.show();
      toastEl.addEventListener('hidden.bs.toast', () => {
        toasts.value = toasts.value.filter((t) => t.id !== id);
      });
    }
  });
}

function showError(message) {
  showToast(message, 'error');
}

function showSuccess(message) {
  showToast(message, 'success');
}

// ----- Form Submission -----

function validate() {
  if (!isCodeComplete.value) {
    showError('Please enter the complete verification code.');
    return false;
  }
  return true;
}

async function onSubmit() {
  if (!validate()) return;

  loading.value = true;

  try {
    const payload = {
      email: email.value,
      verificationCode: verificationCode.value
    };

    await verifyUser(payload);
    showSuccess('Verification successful! Redirecting to login...');

    setTimeout(() => {
      router.push('/login');
    }, 2000);
  } catch (e) {
    handleVerificationError(e);
  } finally {
    loading.value = false;
  }
}

function handleVerificationError(e) {
  const errorMsg =
    e?.response?.data?.message || e?.response?.data || e?.message || '';

  if (
    errorMsg.toLowerCase().includes('invalid') ||
    errorMsg.toLowerCase().includes('code')
  ) {
    showError('Incorrect verification code entered');
  } else {
    showError(errorMsg || 'Verification failed.');
  }
}

// ----- Resend Code -----

async function onResend() {
  if (!email.value) {
    showError('Email is required to resend verification code.');
    return;
  }

  resending.value = true;

  try {
    await resendVerificationCode(email.value);
    showSuccess('Verification code resent! Check your email.');
    clearDigits();
  } catch (e) {
    handleResendError(e);
  } finally {
    resending.value = false;
  }
}

function handleResendError(e) {
  showError(
    e?.response?.data ||
      e?.response?.statusText ||
      e?.message ||
      'Failed to resend verification code.'
  );
}
</script>

<template>
  <div class="verification-container">
    <div class="card">
      <h2>Verify your email</h2>
      <p class="subtitle">
        We've sent a verification code to your email address. Enter the code
        below to verify your account.
      </p>

      <form @submit.prevent="onSubmit" novalidate>
        <!-- Verification Code Input -->
        <div class="code-label">Verification Code</div>
        <div class="code-inputs" @paste="handlePaste">
          <input
            v-for="(digit, index) in digits"
            :key="index"
            :ref="(el) => setDigitRef(el, index)"
            type="text"
            inputmode="numeric"
            maxlength="1"
            :value="digit"
            class="digit-input"
            :class="{ 'has-value': digit }"
            :aria-label="`Digit ${index + 1}`"
            @input="handleInput(index, $event)"
            @keydown="handleKeydown(index, $event)"
          />
        </div>

        <!-- Submit button -->
        <button type="submit" :disabled="loading" class="submit-btn">
          <span v-if="!loading">Verify Account</span>
          <span v-else>Verifying…</span>
        </button>

        <!-- Resend section -->
        <div class="resend-section">
          <span>Didn't receive a code?</span>
          <button
            type="button"
            :disabled="resending"
            class="resend-btn"
            @click="onResend"
          >
            <span v-if="!resending">Resend Code</span>
            <span v-else>Sending…</span>
          </button>
        </div>

        <!-- Footer -->
        <p class="footer-text">
          <router-link to="/login" class="footer-link">
            ← Back to Sign in
          </router-link>
        </p>
      </form>
    </div>

    <!-- Toast Container -->
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
  </div>
</template>

<style scoped>
/* Container */
.verification-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
  background: #f8f8f8;
}

/* Card */
.card {
  background: #fff;
  border-radius: 12px;
  padding: 2.5rem;
  max-width: 420px;
  width: 100%;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  text-align: center;
}

/* Typography */
h2 {
  margin: 0 0 0.5rem;
  font-size: 1.5rem;
  font-weight: 600;
  color: #111;
}

.subtitle {
  color: #666;
  margin-bottom: 2rem;
  font-size: 0.95rem;
  line-height: 1.5;
}

/* Code input */
.code-label {
  text-align: left;
  font-weight: 500;
  color: #333;
  margin-bottom: 0.5rem;
  font-size: 0.9rem;
}

.code-inputs {
  display: flex;
  gap: 0.5rem;
  justify-content: center;
  margin-bottom: 1.5rem;
}

.digit-input {
  width: 50px;
  height: 60px;
  text-align: center;
  font-size: 1.5rem;
  font-weight: 600;
  border: 1px solid #ddd;
  border-radius: 8px;
  transition: all 0.2s;
  background: #fff;
}

.digit-input:focus {
  outline: none;
  border-color: #ff0000;
  box-shadow: 0 0 0 3px rgba(255, 0, 0, 0.1);
}

.digit-input.has-value {
  border-color: #ff0000;
  background: #fff;
}

/* Submit button */
.submit-btn {
  width: 100%;
  padding: 0.875rem 1.5rem;
  background: #ff0000;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s, transform 0.2s;
}

.submit-btn:hover:not(:disabled) {
  background: #e60000;
}

.submit-btn:active:not(:disabled) {
  transform: scale(0.98);
}

.submit-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

/* Resend section */
.resend-section {
  margin-top: 1.5rem;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  color: #666;
  font-size: 0.9rem;
}

.resend-btn {
  background: transparent;
  color: #ff0000;
  border: none;
  padding: 0;
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  transition: color 0.2s;
}

.resend-btn:hover:not(:disabled) {
  text-decoration: underline;
}

.resend-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* Footer */
.footer-text {
  text-align: center;
  margin-top: 1.5rem;
  padding-top: 1.5rem;
  border-top: 1px solid #eee;
}

.footer-link {
  color: #ff0000;
  text-decoration: none;
  font-weight: 500;
  font-size: 0.9rem;
}

.footer-link:hover {
  text-decoration: underline;
}
</style>
