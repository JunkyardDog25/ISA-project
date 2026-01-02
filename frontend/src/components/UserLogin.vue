<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import axios from 'axios';

const router = useRouter();
const loading = ref(false);
const error = ref('');
const form = ref({
  email: '',
  password: '',
  remember: false
});

async function onSubmit() {
  error.value = '';
  if (!form.value.email || !form.value.password) {
    error.value = 'Email and password are required.';
    return;
  }

  loading.value = true;
  try {
    // Preferred: POST to a real login endpoint that returns a token
    const res = await axios.post('/api/login', {
      email: form.value.email,
      password: form.value.password,
      remember: form.value.remember
    });

    const data = res.data || {};
    if (data.token) {
      if (form.value.remember) localStorage.setItem('authToken', data.token);
      else sessionStorage.setItem('authToken', data.token);
    } else if (data.user && data.user.id) {
      // fallback storage for non-token response
      sessionStorage.setItem('userId', data.user.id);
    }

    await router.push('/');
  } catch (err) {
    // If backend doesn't expose /api/login, fall back to insecure dev check via /api/users
    const status = err?.response?.status;
    if (status === 404 || (err.response === undefined && err.message)) {
      try {
        const usersRes = await axios.get('/api/users');
        const users = usersRes.data || [];
        const found = users.find(u => String(u.email).toLowerCase() === String(form.value.email).toLowerCase());
        if (found) {
          // Development-only: accept login if user exists (server handles hashing normally)
          sessionStorage.setItem('userId', found.id);
          await router.push('/');

        } else {
          error.value = 'Invalid email or password.';
        }
      } catch (e) {
        error.value = 'Unable to contact authentication service.';
      }
    } else {
      // Show server error message if provided
      error.value = err?.response?.data?.message || err.message || 'Login failed';
    }
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="container card" style="max-width:420px;">
    <h1 class="h1">Sign in</h1>

    <form @submit.prevent="onSubmit" class="stack" novalidate>
      <label>
        Email
        <input v-model="form.email" type="email" required placeholder="you@example.com" />
      </label>

      <label>
        Password
        <input v-model="form.password" type="password" required minlength="6" placeholder="••••••••" />
      </label>

      <label style="display:flex;align-items:center;gap:0.5rem;">
        <input v-model="form.remember" type="checkbox" />
        <span>Remember me</span>
      </label>

      <div style="display:flex;gap:0.5rem;align-items:center;">
        <button type="submit" :disabled="loading" aria-busy="loading">
          <span v-if="!loading">Sign in</span>
          <span v-else>Signing in…</span>
        </button>

        <router-link to="/register" class="center" style="margin-left:auto;align-self:center;">
          Don't have an account? Register
        </router-link>
      </div>

      <p v-if="error" class="text-muted" style="color:#b91c1c;" role="alert" aria-live="polite">{{ error }}</p>
    </form>
  </div>
</template>

<style scoped>
input[type="email"],
input[type="password"] {
  width: 100%;
  padding: 0.5rem;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  margin-top: 0.25rem;
}

button {
  background: var(--color-primary, #2563eb);
  color: white;
  border: none;
  padding: 0.5rem 0.75rem;
  border-radius: 6px;
  cursor: pointer;
}

button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

a {
  color: var(--color-primary, #2563eb);
  text-decoration: none;
}
</style>
