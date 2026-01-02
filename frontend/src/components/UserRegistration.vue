<script setup>
import { ref, nextTick, computed } from 'vue';
import { useRouter } from 'vue-router';
import { registerUser } from '../services/UserService';
import { Toast } from 'bootstrap';

const router = useRouter();

const loading = ref(false);

// Track which fields have been touched (blurred)
const touched = ref({
  username: false,
  email: false,
  password: false,
  passwordConfirm: false
});

// Toast notifications (for API errors only)
const toasts = ref([]);
let toastId = 0;

function showToast(message, type = 'error') {
  const id = ++toastId;
  toasts.value.push({ id, message, type });

  nextTick(() => {
    const toastEl = document.getElementById(`toast-${id}`);
    if (toastEl) {
      const toast = new Toast(toastEl, { delay: 5000 });
      toast.show();

      toastEl.addEventListener('hidden.bs.toast', () => {
        toasts.value = toasts.value.filter(t => t.id !== id);
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

const form = ref({
  username: '',
  email: '',
  firstName: '',
  lastName: '',
  address: '',
  password: '',
  passwordConfirm: ''
});

// Live validation errors
const errors = computed(() => ({
  username: !form.value.username ? 'Username is required.' : '',
  email: !form.value.email ? 'Email is required.' :
         !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.value.email) ? 'Please enter a valid email.' : '',
  password: !form.value.password ? 'Password is required.' :
            form.value.password.length < 8 ? 'Password must be at least 8 characters.' : '',
  passwordConfirm: !form.value.passwordConfirm ? 'Please confirm your password.' :
                   form.value.password !== form.value.passwordConfirm ? 'Passwords do not match.' : ''
}));

// Check if form is valid
const isFormValid = computed(() =>
  !errors.value.username &&
  !errors.value.email &&
  !errors.value.password &&
  !errors.value.passwordConfirm
);

function markAllTouched() {
  touched.value.username = true;
  touched.value.email = true;
  touched.value.password = true;
  touched.value.passwordConfirm = true;
}

async function onSubmit() {
  markAllTouched();
  if (!isFormValid.value) return;

  loading.value = true;
  try {
    const payload = {
      username: form.value.username,
      password: form.value.password,
      email: form.value.email,
      firstName: form.value.firstName,
      lastName: form.value.lastName,
      address: form.value.address
    };

    await registerUser(payload);
    showSuccess('Registration successful! Please verify your email.');
    setTimeout(() => {
      router.push({ path: '/verify', query: { email: form.value.email } });
    }, 1500);
  } catch (e) {
    showError(
      e?.response?.data?.message ||
      e?.response?.statusText ||
      e?.message ||
      'Registration failed.'
    );
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="register-container">
    <div class="card">
      <h2>Create account</h2>
      <p class="subtitle">Join us today and get started.</p>

      <form @submit.prevent="onSubmit" novalidate>
        <div class="form-group">
          <label>Username</label>
          <input
            v-model="form.username"
            required
            placeholder="Enter your username"
            :class="{ 'input-error': touched.username && errors.username }"
            @blur="touched.username = true"
          />
          <span v-if="touched.username && errors.username" class="error-text">{{ errors.username }}</span>
        </div>

        <div class="form-group">
          <label>Email</label>
          <input
            v-model="form.email"
            type="email"
            required
            placeholder="Enter your email"
            :class="{ 'input-error': touched.email && errors.email }"
            @blur="touched.email = true"
          />
          <span v-if="touched.email && errors.email" class="error-text">{{ errors.email }}</span>
        </div>

        <div class="name-grid">
          <div class="form-group">
            <label>First name</label>
            <input v-model="form.firstName" placeholder="First name" />
          </div>

          <div class="form-group">
            <label>Last name</label>
            <input v-model="form.lastName" placeholder="Last name" />
          </div>
        </div>

        <div class="form-group">
          <label>Address</label>
          <input v-model="form.address" placeholder="Enter your address" />
        </div>

        <div class="form-group">
          <label>Password</label>
          <input
            v-model="form.password"
            type="password"
            required
            minlength="8"
            placeholder="Create a password"
            :class="{ 'input-error': touched.password && errors.password }"
            @blur="touched.password = true"
          />
          <span v-if="touched.password && errors.password" class="error-text">{{ errors.password }}</span>
        </div>

        <div class="form-group">
          <label>Confirm password</label>
          <input
            v-model="form.passwordConfirm"
            type="password"
            required
            placeholder="Confirm your password"
            :class="{ 'input-error': touched.passwordConfirm && errors.passwordConfirm }"
            @blur="touched.passwordConfirm = true"
          />
          <span v-if="touched.passwordConfirm && errors.passwordConfirm" class="error-text">{{ errors.passwordConfirm }}</span>
        </div>

        <button type="submit" :disabled="loading" class="submit-btn">
          <span v-if="!loading">Create Account</span>
          <span v-else>Creating…</span>
        </button>

        <p class="footer-text">
          Already have an account? <router-link to="/login" class="footer-link">Sign in</router-link>
        </p>
      </form>
    </div>

    <!-- Bootstrap Toast Container -->
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
        <div class="toast-header" :class="toast.type === 'success' ? 'text-bg-success' : 'text-bg-danger'">
          <strong class="me-auto">{{ toast.type === 'success' ? '✓ Success' : '✕ Error' }}</strong>
          <button type="button" class="btn-close btn-close-white" data-bs-dismiss="toast" aria-label="Close"></button>
        </div>
        <div class="toast-body">
          {{ toast.message }}
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.register-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
  background: #f8f8f8;
}

.card {
  background: #fff;
  border-radius: 12px;
  padding: 2.5rem;
  max-width: 450px;
  width: 100%;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
}

h2 {
  margin: 0 0 0.5rem;
  font-size: 1.5rem;
  font-weight: 600;
  color: #111;
  text-align: center;
}

.subtitle {
  color: #666;
  margin-bottom: 2rem;
  text-align: center;
  font-size: 0.95rem;
}

.form-group {
  margin-bottom: 1rem;
  text-align: left;
}

.form-group label {
  display: block;
  font-weight: 500;
  color: #333;
  margin-bottom: 0.5rem;
  font-size: 0.9rem;
}

input {
  width: 100%;
  padding: 0.75rem 1rem;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-size: 0.95rem;
  transition: border-color 0.2s, box-shadow 0.2s;
  box-sizing: border-box;
  background: #fff;
}

input:focus {
  outline: none;
  border-color: #ff0000;
  box-shadow: 0 0 0 3px rgba(255, 0, 0, 0.1);
}

input::placeholder {
  color: #aaa;
}

.input-error {
  border-color: #ff4444 !important;
}

.input-error:focus {
  box-shadow: 0 0 0 3px rgba(255, 68, 68, 0.1) !important;
}

.error-text {
  display: block;
  color: #ff4444;
  font-size: 0.8rem;
  margin-top: 0.35rem;
}

.name-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}

.submit-btn {
  width: 100%;
  padding: 0.875rem 1.5rem;
  margin-top: 0.5rem;
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

.footer-text {
  text-align: center;
  margin-top: 1.5rem;
  color: #666;
  font-size: 0.9rem;
}

.footer-link {
  color: #ff0000;
  text-decoration: none;
  font-weight: 600;
}

.footer-link:hover {
  text-decoration: underline;
}
</style>
