<!-- File: ISA-project/frontend/src/components/UserRegistration.vue -->
<!-- vue -->
<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { registerUser } from '../services/UserService';

const router = useRouter();

const loading = ref(false);
const error = ref('');
const success = ref('');

const form = ref({
  username: '',
  email: '',
  first_name: '',
  last_name: '',
  address: '',
  password: '',
  passwordConfirm: ''
});

function validate() {
  if (!form.value.username || !form.value.email || !form.value.password) {
    error.value = 'Username, email and password are required.';
    return false;
  }
  if (form.value.password.length < 6) {
    error.value = 'Password must be at least 6 characters.';
    return false;
  }
  if (form.value.password !== form.value.passwordConfirm) {
    error.value = 'Passwords do not match.';
    return false;
  }
  return true;
}

async function onSubmit() {
  error.value = '';
  success.value = '';
  if (!validate()) return;

  loading.value = true;
  try {
    // The backend User model expects `password_hash`; send plaintext here
    // and let the server hash it (or change to send a pre-hashed value).
    const payload = {
      username: form.value.username,
      password_hash: form.value.password,
      email: form.value.email,
      first_name: form.value.first_name,
      last_name: form.value.last_name,
      address: form.value.address
    };

    const res = await registerUser(payload);
    success.value = 'Registration successful.';
    // Optionally redirect to login or home
    router.push('/login').catch(() => router.push('/'));
  } catch (e) {
    error.value =
      e?.response?.data?.message ||
      e?.response?.statusText ||
      e?.message ||
      'Registration failed.';
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="card" style="max-width:560px;margin:1rem auto;padding:1rem;">
    <h2>Create account</h2>

    <form @submit.prevent="onSubmit" novalidate>
      <label>
        Username
        <input v-model="form.username" required placeholder="Username" />
      </label>

      <label>
        Email
        <input v-model="form.email" type="email" required placeholder="you@example.com" />
      </label>

      <div style="display:grid;grid-template-columns:1fr 1fr;gap:0.5rem;">
        <label>
          First name
          <input v-model="form.first_name" placeholder="First name" />
        </label>

        <label>
          Last name
          <input v-model="form.last_name" placeholder="Last name" />
        </label>
      </div>

      <label>
        Address
        <input v-model="form.address" placeholder="123 Main St" />
      </label>

      <label>
        Password
        <input v-model="form.password" type="password" required minlength="6" placeholder="••••••" />
      </label>

      <label>
        Confirm password
        <input v-model="form.passwordConfirm" type="password" required placeholder="••••••" />
      </label>

      <div style="display:flex;gap:0.5rem;align-items:center;margin-top:0.5rem;">
        <button type="submit" :disabled="loading">
          <span v-if="!loading">Register</span>
          <span v-else>Registering…</span>
        </button>

        <router-link to="/login" style="margin-left:auto;">Already have an account? Log in</router-link>
      </div>

      <p v-if="error" style="color:#b91c1c;margin-top:0.5rem;">{{ error }}</p>
      <p v-if="success" style="color:#059669;margin-top:0.5rem;">{{ success }}</p>
    </form>
  </div>
</template>

<style scoped>
label {
  display: block;
  margin-top: 0.5rem;
}
input, select {
  width: 100%;
  padding: 0.5rem;
  margin-top: 0.25rem;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}
button {
  background: #2563eb;
  color: #fff;
  border: none;
  padding: 0.5rem 0.75rem;
  border-radius: 6px;
  cursor: pointer;
}
button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
