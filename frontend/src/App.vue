<script setup>
import { RouterLink, RouterView, useRoute, useRouter } from "vue-router";
import { computed, ref, onMounted, onUnmounted } from "vue";
import { useAuth } from "@/composables/useAuth.js";
import { useToast } from "@/composables/useToast.js";
import ToastContainer from "@/components/common/ToastContainer.vue";

const route = useRoute();
const router = useRouter();
const { isLoggedIn, logout, user } = useAuth();
const { showSuccess } = useToast();

// Dropdown state
const dropdownOpen = ref(false);

// Hide navbar on auth pages
const hideNavbar = computed(() => {
  const authRoutes = ['/login', '/register', '/verify'];
  return authRoutes.includes(route.path);
});

function goToCreateVideo() {
  closeDropdown();
  router.push('/create-video');
}

function toggleDropdown() {
  dropdownOpen.value = !dropdownOpen.value;
}

function closeDropdown() {
  dropdownOpen.value = false;
}

function handleLogout() {
  closeDropdown();
  logout();
  showSuccess('You have been logged out successfully');
  router.push('/');
}

// Close dropdown when clicking outside
function handleClickOutside(event) {
  const dropdown = document.querySelector('.profile-dropdown');
  if (dropdown && !dropdown.contains(event.target)) {
    closeDropdown();
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside);
});

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside);
});
</script>

<template>
  <nav v-if="!hideNavbar" class="navbar navbar-expand-lg bg-body-tertiary">
    <div class="container-fluid">
      <a class="navbar-brand" href="#">
        <img src="../logo.png" alt="Bootstrap" width="60" height="48">
        Jutjubic
      </a>
      <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#navbarSupportedContent" aria-controls="navbarSupportedContent" aria-expanded="false" aria-label="Toggle navigation">
        <span class="navbar-toggler-icon"></span>
      </button>
      <div class="collapse navbar-collapse" id="navbarSupportedContent">
        <ul class="navbar-nav me-auto mb-2 mb-lg-0">
          <li class="nav-item">
            <RouterLink to="/" class="nav-link">Home</RouterLink>
          </li>
          <li v-if="isLoggedIn" class="nav-item">
            <RouterLink to="/create-video" class="nav-link">Create Video</RouterLink>
          </li>
        </ul>
        <form class="d-flex" role="search">
          <RouterLink v-if="!isLoggedIn" to="/login" class="nav-link nav-btn">Sign In</RouterLink>
          <div v-else class="profile-dropdown">
            <button class="profile-btn" type="button" @click.stop="toggleDropdown">
              <svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"/>
                <circle cx="12" cy="7" r="4"/>
              </svg>
              <span class="username">{{ user?.username || 'User' }}</span>
            </button>
            <ul class="dropdown-menu" :class="{ show: dropdownOpen }">
              <li><a class="dropdown-item" href="#" @click.prevent="goToCreateVideo"><svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="dropdown-icon"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="12" y1="18" x2="12" y2="12"/><line x1="9" y1="15" x2="15" y2="15"/></svg> Create Video</a></li>
              <li><a class="dropdown-item" href="#" @click.prevent="closeDropdown"><svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="dropdown-icon"><path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg> Profile</a></li>
              <li><a class="dropdown-item" href="#" @click.prevent="closeDropdown"><svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="dropdown-icon"><path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z"/><circle cx="12" cy="12" r="3"/></svg> Settings</a></li>
              <li><hr class="dropdown-divider"></li>
              <li><a class="dropdown-item dropdown-item-logout" href="#" @click.prevent="handleLogout"><svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="dropdown-icon"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg> Logout</a></li>
            </ul>
          </div>
        </form>
      </div>
    </div>
  </nav>

  <main>
    <RouterView />
  </main>

  <ToastContainer />
</template>

<style scoped>
.navbar {
  background: linear-gradient(135deg, #ff0000 0%, #cc0000 100%);
  padding: 0.75rem 1.5rem;
  box-shadow: 0 4px 20px -5px rgba(0, 0, 0, 0.2);
  position: sticky;
  top: 0;
  z-index: 100;
}

.navbar-brand {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  text-decoration: none;
  color: #fff;
  font-weight: 700;
  font-size: 1.25rem;
}

.nav-link {
  position: relative;
  color: white !important;
  text-decoration: none;
  font-size: 16px;
  padding-bottom: 4px;
}

.nav-link:hover {
  color: #fff !important;
}

.nav-link::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  width: 0;
  height: 2px;
  background: #fff;
  transition: width 0.2s;
}

.nav-link:hover::after {
  width: 100%;
}

.nav-btn {
  background: rgba(255, 255, 255, 0.15);
  padding: 0.5rem 1.25rem;
  border-radius: 8px;
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  transition: all 0.2s;
  cursor: pointer;
}

.nav-btn::after {
  display: none;
}

.nav-btn:hover {
  background: rgba(255, 255, 255, 0.25);
  transform: translateY(-1px);
}

/* Profile Dropdown */
.profile-dropdown {
  position: relative;
}

.profile-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  border-radius: 25px;
  background: rgba(255, 255, 255, 0.15);
  border: 1px solid rgba(255, 255, 255, 0.3);
  color: white;
  cursor: pointer;
  transition: all 0.2s;
}

.username {
  font-size: 0.9rem;
  font-weight: 500;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-btn::after {
  display: none; /* Hide dropdown arrow */
}

.profile-btn:hover {
  background: rgba(255, 255, 255, 0.25);
  transform: scale(1.05);
}

.dropdown-menu {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: 0.5rem;
  border: none;
  border-radius: 10px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.15);
  padding: 0.5rem;
  min-width: 180px;
  display: none;
  background: white;
  z-index: 1000;
}

.dropdown-menu.show {
  display: block;
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.6rem 1rem;
  border-radius: 6px;
  font-size: 0.9rem;
  color: #333;
  transition: all 0.15s;
}

.dropdown-item:hover {
  background: #f5f5f5;
  color: #111;
}

.dropdown-icon {
  opacity: 0.7;
}

.dropdown-divider {
  margin: 0.5rem 0;
  border-color: #eee;
}

.dropdown-item-logout {
  color: #dc3545;
}

.dropdown-item-logout:hover {
  background: #fff5f5;
  color: #dc3545;
}

.dropdown-item-logout .dropdown-icon {
  color: #dc3545;
}
</style>
