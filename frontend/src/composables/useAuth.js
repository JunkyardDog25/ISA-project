import { ref, computed } from 'vue';

// ----- Storage Keys -----

const STORAGE_KEYS = {
  TOKEN: 'authToken',
  USER: 'authUser'
};

// ----- Reactive State (shared across all components) -----

const token = ref(
  localStorage.getItem(STORAGE_KEYS.TOKEN) ||
    sessionStorage.getItem(STORAGE_KEYS.TOKEN) ||
    null
);

const user = ref(
  JSON.parse(
    localStorage.getItem(STORAGE_KEYS.USER) ||
      sessionStorage.getItem(STORAGE_KEYS.USER) ||
      'null'
  )
);

// ----- Composable -----

/**
 * Authentication composable for managing user auth state.
 * State is shared across all components that use this composable.
 */
export function useAuth() {
  // ----- Computed -----

  const isLoggedIn = computed(() => !!token.value);

  // ----- Token Management -----

  /**
   * Set authentication token.
   * @param {string} newToken - The JWT or auth token
   * @param {boolean} remember - If true, persist in localStorage; otherwise sessionStorage
   */
  function setToken(newToken, remember = false) {
    token.value = newToken;

    if (remember) {
      localStorage.setItem(STORAGE_KEYS.TOKEN, newToken);
    } else {
      sessionStorage.setItem(STORAGE_KEYS.TOKEN, newToken);
    }
  }

  /**
   * Get the current authentication token.
   * @returns {string|null} The current token or null
   */
  function getToken() {
    return token.value;
  }

  // ----- User Management -----

  /**
   * Set user data.
   * @param {Object} userData - User object (e.g., { username, email })
   * @param {boolean} remember - If true, persist in localStorage; otherwise sessionStorage
   */
  function setUser(userData, remember = false) {
    user.value = userData;
    const userJson = JSON.stringify(userData);

    if (remember) {
      localStorage.setItem(STORAGE_KEYS.USER, userJson);
    } else {
      sessionStorage.setItem(STORAGE_KEYS.USER, userJson);
    }
  }

  // ----- Logout -----

  /**
   * Clear all auth state and remove from storage.
   */
  function logout() {
    token.value = null;
    user.value = null;

    // Clear from both storage types
    localStorage.removeItem(STORAGE_KEYS.TOKEN);
    sessionStorage.removeItem(STORAGE_KEYS.TOKEN);
    localStorage.removeItem(STORAGE_KEYS.USER);
    sessionStorage.removeItem(STORAGE_KEYS.USER);
  }

  // ----- Return Public API -----

  return {
    // State
    token,
    user,
    isLoggedIn,

    // Methods
    setToken,
    getToken,
    setUser,
    logout
  };
}
