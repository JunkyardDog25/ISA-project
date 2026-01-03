import { createRouter, createWebHistory } from 'vue-router';

import { useAuth } from '@/composables/useAuth.js';
import Home from '@/components/Home.vue';
import UserLogin from '@/components/UserLogin.vue';
import UserRegistration from '@/components/UserRegistration.vue';
import UserVerification from '@/components/UserVerification.vue';
import VideoPlayer from '@/components/VideoPlayer.vue';

// ----- Route Definitions -----

const routes = [
  {
    path: '/',
    name: 'home',
    component: Home
  },
  {
    path: '/video/:id',
    name: 'video',
    component: VideoPlayer
  },
  {
    path: '/login',
    name: 'login',
    component: UserLogin,
    meta: { guestOnly: true }
  },
  {
    path: '/register',
    name: 'register',
    component: UserRegistration,
    meta: { guestOnly: true }
  },
  {
    path: '/verify',
    name: 'verify',
    component: UserVerification,
    meta: { guestOnly: true }
  }
];

// ----- Router Instance -----

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
});

// ----- Navigation Guards -----

/**
 * Redirect logged-in users away from guest-only pages (login, register, verify).
 */
router.beforeEach((to, from, next) => {
  const { isLoggedIn } = useAuth();

  if (to.meta.guestOnly && isLoggedIn.value) {
    next({ name: 'home' });
  } else {
    next();
  }
});

export default router;
