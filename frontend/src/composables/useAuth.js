import { ref, computed } from 'vue';

const token = ref(localStorage.getItem('authToken') || sessionStorage.getItem('authToken') || null);
const user = ref(JSON.parse(localStorage.getItem('authUser') || sessionStorage.getItem('authUser') || 'null'));

export function useAuth() {
  const isLoggedIn = computed(() => !!token.value);

  function setToken(newToken, remember = false) {
    token.value = newToken;
    if (remember) {
      localStorage.setItem('authToken', newToken);
    } else {
      sessionStorage.setItem('authToken', newToken);
    }
  }

  function setUser(userData, remember = false) {
    user.value = userData;
    const userJson = JSON.stringify(userData);
    if (remember) {
      localStorage.setItem('authUser', userJson);
    } else {
      sessionStorage.setItem('authUser', userJson);
    }
  }

  function logout() {
    token.value = null;
    user.value = null;
    localStorage.removeItem('authToken');
    sessionStorage.removeItem('authToken');
    localStorage.removeItem('authUser');
    sessionStorage.removeItem('authUser');
  }

  function getToken() {
    return token.value;
  }

  return {
    isLoggedIn,
    token,
    user,
    setToken,
    setUser,
    logout,
    getToken
  };
}

