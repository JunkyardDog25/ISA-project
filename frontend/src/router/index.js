import { createRouter, createWebHistory } from 'vue-router';

import { useAuth } from '@/composables/useAuth.js';
import Home from '@/components/Home.vue';
import UserLogin from '@/components/UserLogin.vue';
import UserRegistration from '@/components/UserRegistration.vue';
import UserVerification from '@/components/UserVerification.vue';
import VideoPlayer from '@/components/VideoPlayer.vue';
import CreateVideo from '@/components/CreateVideo.vue';
import UserProfile from '@/components/UserProfile.vue';
import PerformanceDashboard from '@/components/PerformanceDashboard.vue';
import SimulationDashboard from '@/components/SimulationDashboard.vue';

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
    path: '/user/:id',
    name: 'user-profile',
    component: UserProfile
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
  },
  {
    path: '/create-video',
    name: 'create-video',
    component: CreateVideo,
    meta: { requiresAuth: true }
  },
  {
    path: '/performance',
    name: 'performance',
    component: PerformanceDashboard
  },
  {
    path: '/simulation',
    name: 'simulation',
    component: SimulationDashboard
  }
];

// ----- Router Instance -----

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
});

// ----- Navigation Guards -----

/**
 * Navigation guards:
 * - Redirect logged-in users away from guest-only pages (login, register, verify).
 * - Redirect unauthenticated users away from protected pages (create-video).
 */
router.beforeEach((to, from, next) => {
  const { isLoggedIn } = useAuth();

  // Redirect logged-in users away from guest-only pages
  if (to.meta.guestOnly && isLoggedIn.value) {
    next({ name: 'home' });
    return;
  }

  // Redirect unauthenticated users away from protected pages
  if (to.meta.requiresAuth && !isLoggedIn.value) {
    next({ name: 'login' });
    return;
  }

  next();
});

export default router;
