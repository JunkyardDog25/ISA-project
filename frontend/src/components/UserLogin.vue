<script setup>
import { ref, nextTick, computed } from 'vue';
import { useRouter } from 'vue-router';
import { loginUser } from '../services/UserService';
import { Toast } from 'bootstrap';
import { useAuth } from '@/composables/useAuth.js';

const router = useRouter();
const { setToken, setUser } = useAuth();

const loading = ref(false);

// Track which fields have been touched (blurred)
const touched = ref({
  email: false,
  password: false
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

// Check for remembered email on mount
const rememberedEmail = localStorage.getItem('rememberedEmail') || '';

const form = ref({
  email: rememberedEmail,
  password: '',
  remember: !!rememberedEmail
});

// Live validation errors
const errors = computed(() => ({
  email: !form.value.email ? 'Email is required.' :
         !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.value.email) ? 'Please enter a valid email.' : '',
  password: !form.value.password ? 'Password is required.' :
            form.value.password.length < 6 ? 'Password must be at least 6 characters.' : ''
}));

// Check if form is valid
const isFormValid = computed(() => !errors.value.email && !errors.value.password);

function markAllTouched() {
  touched.value.email = true;
  touched.value.password = true;
}

async function onSubmit() {
  markAllTouched();
  if (!isFormValid.value) return;

  loading.value = true;
  try {
    const payload = {
      email: form.value.email,
      password: form.value.password
    };

    const res = await loginUser(payload);
    const data = res.data || {};

    if (data.token) {
      setToken(data.token, form.value.remember);
    }

    // Save user data from response
    if (data.username || data.email) {
      setUser({
        username: data.username,
        email: data.email
      }, form.value.remember);
    }

    // Remember or forget the email
    if (form.value.remember) {
      localStorage.setItem('rememberedEmail', form.value.email);
    } else {
      localStorage.removeItem('rememberedEmail');
    }

    setTimeout(() => {
      router.push('/');
    }, 1500);
  } catch (e) {
    showError(
      e?.response?.data?.message ||
      e?.response?.statusText ||
      e?.message ||
      'Login failed.'
    );
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="login-container">
    <div class="card">
      <h2>Welcome back</h2>
      <p class="subtitle">Please enter your details to sign in.</p>

      <form @submit.prevent="onSubmit" novalidate>
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

        <div class="form-group">
          <label>Password</label>
          <input
            v-model="form.password"
            type="password"
            required
            minlength="6"
            placeholder="Enter your password"
            :class="{ 'input-error': touched.password && errors.password }"
            @blur="touched.password = true"
          />
          <span v-if="touched.password && errors.password" class="error-text">{{ errors.password }}</span>
        </div>

        <div class="options-row">
          <label class="remember-label">
            <input v-model="form.remember" type="checkbox" />
            <span>Remember me</span>
          </label>
          <a href="#" class="forgot-link">Forgot password?</a>
        </div>

        <button type="submit" :disabled="loading" class="submit-btn">
          <span v-if="!loading">Sign in</span>
          <span v-else>Signing in…</span>
        </button>

        <p class="footer-text">
          Don't have an account? <router-link to="/register" class="footer-link">Sign up</router-link>
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
.login-container {
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
  max-width: 400px;
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
  margin-bottom: 1.25rem;
  text-align: left;
}

.form-group label {
  display: block;
  font-weight: 500;
  color: #333;
  margin-bottom: 0.5rem;
  font-size: 0.9rem;
}

input[type="email"],
input[type="password"] {
  width: 100%;
  padding: 0.75rem 1rem;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-size: 0.95rem;
  transition: border-color 0.2s, box-shadow 0.2s;
  box-sizing: border-box;
  background: #fff;
}

input[type="email"]:focus,
input[type="password"]:focus {
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

.options-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 1.5rem;
}

.remember-label {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: #555;
  font-size: 0.9rem;
  cursor: pointer;
}

.remember-label input[type="checkbox"] {
  width: 16px;
  height: 16px;
  accent-color: #ff0000;
  cursor: pointer;
}

.forgot-link {
  color: #ff0000;
  text-decoration: none;
  font-size: 0.9rem;
  font-weight: 500;
}

.forgot-link:hover {
  text-decoration: underline;
}

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
