<script setup>
import { ref, computed, watch, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { useAuth } from '@/composables/useAuth.js';
import { useToast } from '@/composables/useToast.js';
import { loginUser } from '@/services/UserService.js';
import PasswordInput from '@/components/common/PasswordInput.vue';
import ToastContainer from '@/components/common/ToastContainer.vue';

// Router and auth
const router = useRouter();
const { setToken, setUser } = useAuth();
const { showError } = useToast();

// Form state
const loading = ref(false);
const rememberedEmail = localStorage.getItem('rememberedEmail') || '';

const form = ref({
  email: rememberedEmail,
  password: '',
  remember: !!rememberedEmail
});

// Track which fields have been touched (blurred)
const touched = ref({
  email: false,
  password: false
});

// Inline API-level error (e.g. rate limiting or auth errors)
const apiError = ref('');

// Rate limiting state
const rateLimitRemaining = ref(0);
let rateLimitInterval = null;

// Location consent state
const locationConsentAsked = ref(false); // whether we've already shown the consent UI
const showLocationConsentModal = ref(false);

// ----- Validation -----

const errors = computed(() => ({
  email: !form.value.email
    ? 'Email is required.'
    : !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.value.email)
      ? 'Please enter a valid email.'
      : '',
  password: !form.value.password ? 'Password is required.' : ''
}));

const isFormValid = computed(() => !errors.value.email && !errors.value.password);

// Clear inline api error when user edits fields
watch(
  [() => form.value.email, () => form.value.password],
  () => {
    if (!rateLimitRemaining.value) {
      apiError.value = '';
    }
  }
);

// ----- Rate Limiting -----

function startRateLimitCountdown(seconds) {
  clearRateLimitCountdown();
  rateLimitRemaining.value = Math.max(0, Math.floor(seconds));
  apiError.value = `Too many attempts. Try again in ${rateLimitRemaining.value}s.`;

  rateLimitInterval = setInterval(() => {
    rateLimitRemaining.value -= 1;
    if (rateLimitRemaining.value <= 0) {
      clearRateLimitCountdown();
      apiError.value = '';
    } else {
      apiError.value = `Too many attempts. Try again in ${rateLimitRemaining.value}s.`;
    }
  }, 1000);
}

function clearRateLimitCountdown() {
  if (rateLimitInterval) {
    clearInterval(rateLimitInterval);
    rateLimitInterval = null;
  }
  rateLimitRemaining.value = 0;
}

onUnmounted(() => {
  clearRateLimitCountdown();
});

// ----- Form Submission -----

function markAllTouched() {
  touched.value.email = true;
  touched.value.password = true;
}

// Promise wrapper for browser geolocation with timeout
function getBrowserLocation(timeoutMs = 5000) {
  return new Promise((resolve) => {
    if (!navigator.geolocation) return resolve(null);
    let resolved = false;
    const timer = setTimeout(() => {
      if (!resolved) {
        resolved = true;
        resolve(null);
      }
    }, timeoutMs);

    navigator.geolocation.getCurrentPosition(
      (pos) => {
        if (resolved) return;
        resolved = true;
        clearTimeout(timer);
        resolve({ lat: pos.coords.latitude, lon: pos.coords.longitude });
      },
      () => {
        if (resolved) return;
        resolved = true;
        clearTimeout(timer);
        resolve(null);
      },
      { enableHighAccuracy: false, timeout: timeoutMs }
    );
  });
}

// The core login logic (performs the API call). Accepts an optional location string "lat,lon".
async function performLogin(locationStr = null) {
  if (rateLimitRemaining.value > 0) return;
  loading.value = true;

  try {
    const payload = {
      email: form.value.email,
      password: form.value.password
    };

    if (locationStr) payload.location = locationStr;

    const res = await loginUser(payload);
    const data = res.data || {};

    // Store token
    if (data.token) {
      setToken(data.token, form.value.remember);
    }

    // Store user data including location preference
    if (data.id || data.username || data.email) {
      // Save user data with location preference
      // If locationStr was provided, user allowed location; otherwise they skipped
      const userData = {
        id: data.id,
        username: data.username,
        email: data.email,
        locationAllowed: !!locationStr,
        latitude: data.latitude || null,
        longitude: data.longitude || null
      };

      // If we obtained browser location, store it
      if (locationStr) {
        const [lat, lon] = locationStr.split(',');
        userData.latitude = parseFloat(lat);
        userData.longitude = parseFloat(lon);
      }

      setUser(userData, form.value.remember);
    }

    // Handle "Remember me"
    if (form.value.remember) {
      localStorage.setItem('rememberedEmail', form.value.email);
    } else {
      localStorage.removeItem('rememberedEmail');
    }

    apiError.value = '';

    setTimeout(() => {
      router.push('/');
    }, 1500);
  } catch (e) {
    handleLoginError(e);
  } finally {
    loading.value = false;
  }
}

// Entry point for the form submit.
// If we haven't asked for location consent yet, show modal; otherwise proceed.
async function onSubmit() {
  markAllTouched();
  apiError.value = '';

  if (!isFormValid.value) return;
  if (rateLimitRemaining.value > 0) return;

  // If we haven't asked for location consent on this session, show the modal first.
  if (!locationConsentAsked.value) {
    showLocationConsentModal.value = true;
    return;
  }

  // If consent was already asked and user chose to allow earlier, we would have included location when performing login.
  // Fallback: perform login without location.
  await performLogin();
}

// Called when user agrees to share location from the modal
async function handleAllowLocation() {
  locationConsentAsked.value = true;
  showLocationConsentModal.value = false;

  // Try to get browser location (with short timeout), then perform login with result (or without if null)
  const loc = await getBrowserLocation(4000);
  if (loc) {
    await performLogin(`${loc.lat},${loc.lon}`);
  } else {
    // If user denied at browser level or it failed, just proceed without location and backend will approximate via IP
    await performLogin();
  }
}

// Called when user explicitly skips location sharing
async function handleSkipLocation() {
  locationConsentAsked.value = true;
  showLocationConsentModal.value = false;
  await performLogin();
}

function handleLoginError(e) {
  const status = e?.response?.status;

  // Rate limit (429)
  if (e?.isRateLimit || status === 429) {
    const retry =
      e?.retryAfter ||
      e?.response?.data?.retryAfterSeconds ||
      e?.response?.headers['retry-after'];
    startRateLimitCountdown(Number(retry) || 60);
    return;
  }

  // Auth error (401/403)
  if (e?.isAuthError) {
    apiError.value =
      e?.userMessage || 'Please check your password and email and try again.';
    return;
  }

  // Other errors: show toast
  const serverMessage = e?.response?.data?.message;
  showError(
    serverMessage || e?.response?.statusText || e?.message || 'Login failed.'
  );
}
</script>

<template>
  <div class="login-container">
    <div class="card">
      <h2>Welcome back</h2>
      <p class="subtitle">Please enter your details to sign in.</p>

      <form @submit.prevent="onSubmit" novalidate>
        <!-- Email -->
        <div class="form-group">
          <label for="email">Email</label>
          <input
            id="email"
            v-model="form.email"
            type="email"
            required
            placeholder="Enter your email"
            :class="{ 'input-error': touched.email && errors.email }"
            @blur="touched.email = true"
          />
          <span v-if="touched.email && errors.email" class="error-text">
            {{ errors.email }}
          </span>
        </div>

        <!-- Password -->
        <div class="form-group">
          <label for="password">Password</label>
          <PasswordInput
            id="password"
            v-model="form.password"
            placeholder="Enter your password"
            :class="{ 'input-error': touched.password && errors.password }"
            @blur="touched.password = true"
          />
          <span v-if="touched.password && errors.password" class="error-text">
            {{ errors.password }}
          </span>
        </div>

        <!-- Options row -->
        <div class="options-row">
          <label class="remember-label">
            <input v-model="form.remember" type="checkbox" />
            <span>Remember me</span>
          </label>
          <a href="#" class="forgot-link">Forgot password?</a>
        </div>

        <!-- Inline API error -->
        <span v-if="apiError" class="error-text api-error">
          {{ apiError }}
        </span>

        <!-- Submit button -->
        <button
          type="submit"
          :disabled="loading || rateLimitRemaining > 0"
          class="submit-btn"
        >
          <span v-if="!loading">Sign in</span>
          <span v-else>Signing in…</span>
        </button>

        <!-- Footer -->
        <p class="footer-text">
          Don't have an account?
          <router-link to="/register" class="footer-link">Sign up</router-link>
        </p>
      </form>
    </div>

    <!-- Toast Container -->
    <ToastContainer />

    <!-- Location consent modal -->
    <div v-if="showLocationConsentModal" class="location-modal-overlay">
      <div class="location-modal">
        <h3>Share your location?</h3>
        <p>We'd like to use your location to improve results and approximate content. Allow sharing your location?</p>
        <div class="modal-actions">
          <button @click="handleSkipLocation" class="btn-secondary">Skip</button>
          <button @click="handleAllowLocation" class="btn-primary">Allow</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Container */
.login-container {
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
  max-width: 400px;
  width: 100%;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
}

/* Typography */
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

/* Form groups */
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

/* Inputs */
input[type='email'],
input[type='text'] {
  width: 100%;
  padding: 0.75rem 1rem;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-size: 0.95rem;
  transition: border-color 0.2s, box-shadow 0.2s;
  box-sizing: border-box;
  background: #fff;
}

input[type='email']:focus,
input[type='text']:focus {
  outline: none;
  border-color: #ff0000;
  box-shadow: 0 0 0 3px rgba(255, 0, 0, 0.1);
}

input::placeholder {
  color: #aaa;
}

/* Error state */
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

.api-error {
  margin-bottom: 12px;
}

/* Options row */
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

.remember-label input[type='checkbox'] {
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

/* Footer */
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

/* Modal styles */
.location-modal-overlay {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background: rgba(0,0,0,0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1200;
}

.location-modal {
  background: #fff;
  padding: 1.5rem;
  border-radius: 10px;
  max-width: 420px;
  width: 90%;
  box-shadow: 0 8px 30px rgba(0,0,0,0.12);
  text-align: center;
}

.location-modal h3 { margin: 0 0 8px 0; }
.location-modal p { color: #555; margin-bottom: 16px; }

.modal-actions { display:flex; gap:12px; justify-content:center; }
.btn-primary { background:#ff0000; color:#fff; padding:0.6rem 1.1rem; border-radius:8px; border:none; }
.btn-secondary { background:transparent; color:#333; padding:0.6rem 1.1rem; border-radius:8px; border:1px solid #ddd; }
</style>
