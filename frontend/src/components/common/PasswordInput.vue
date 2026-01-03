<script setup>
import { ref, toRefs } from 'vue';

const props = defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '' },
  id: { type: String, default: null }
});
const { modelValue, placeholder, id } = toRefs(props);
const emit = defineEmits(['update:modelValue', 'blur', 'focus']);

const show = ref(false);
function toggle() {
  show.value = !show.value;
}
function onInput(e) {
  emit('update:modelValue', e.target.value);
}
function onBlur(e) {
  emit('blur', e);
}
function onFocus(e) {
  emit('focus', e);
}
</script>

<template>
  <div class="password-wrapper">
    <input
      :id="id"
      :value="modelValue"
      :type="show ? 'text' : 'password'"
      :placeholder="placeholder"
      @input="onInput"
      @blur="onBlur"
      @focus="onFocus"
      v-bind="$attrs"
    />

    <button
      type="button"
      class="password-toggle"
      :class="{ 'show-password': show }"
      :aria-pressed="show"
      @click="toggle"
      :aria-label="show ? 'Hide password' : 'Show password'"
      :title="show ? 'Hide password' : 'Show password'"
    >
      <span v-if="!show" aria-hidden="true">
        <!-- eye icon -->
        <i class="bi bi-eye"></i>
      </span>
      <span v-else aria-hidden="true">
        <!-- eye-slash icon -->
        <i class="bi bi-eye-slash"></i>
      </span>
    </button>
  </div>
</template>

<style scoped>
.password-wrapper {
  position: relative;
}

/* input styles copied to match UserLogin/UserRegistration styles */
.password-wrapper input {
  width: 100%;
  padding: 0.75rem 1rem;
  padding-right: 2.75rem; /* space for toggle */
  border: 1px solid #ddd;
  border-radius: 8px;
  font-size: 0.95rem;
  transition: border-color 0.2s, box-shadow 0.2s;
  box-sizing: border-box;
  background: #fff;
}

.password-wrapper input:focus {
  outline: none;
  border-color: #ff0000;
  box-shadow: 0 0 0 3px rgba(255, 0, 0, 0.1);
}

.password-wrapper input::placeholder {
  color: #aaa;
}

.password-wrapper.input-error input {
  border-color: #ff4444 !important;
}

.password-wrapper.input-error input:focus {
  box-shadow: 0 0 0 3px rgba(255, 68, 68, 0.1) !important;
}

.password-toggle {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  background: transparent;
  border: none;
  padding: 4px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #666;
  cursor: pointer;
}

.password-toggle:focus {
  outline: none;
  border-radius: 6px;
}

.password-toggle.show-password {
  color: #e60000; /* stronger red when active */
}
</style>
