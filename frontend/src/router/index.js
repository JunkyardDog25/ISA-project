import {createRouter, createWebHistory} from "vue-router";

import UserRegistration from "../components/UserRegistration.vue";
import Home from "../components/Home.vue";
import UserLogin from "@/components/UserLogin.vue";
import UserVerification from "@/components/UserVerification.vue";
import { useAuth } from "@/composables/useAuth.js";

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: "/", name: "home", component: Home },
    { path: "/login", name: "login", component: UserLogin, meta: { guestOnly: true } },
    { path: "/register", name: "register", component: UserRegistration, meta: { guestOnly: true } },
    { path: "/verify", name: "verify", component: UserVerification, meta: { guestOnly: true } },
  ]
})

// Navigation guard to redirect logged-in users away from auth pages
router.beforeEach((to, from, next) => {
  const { isLoggedIn } = useAuth();

  if (to.meta.guestOnly && isLoggedIn.value) {
    // User is logged in, redirect to home
    next({ name: 'home' });
  } else {
    next();
  }
});

export default router
