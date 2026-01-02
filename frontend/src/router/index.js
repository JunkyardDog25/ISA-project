import {createRouter, createWebHistory} from "vue-router";

import UserRegistration from "../components/UserRegistration.vue";
import Home from "../components/Home.vue";
import UserLogin from "@/components/UserLogin.vue";

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: "/", name: "home", component: Home },
    { path: "/login", name: "login", component: UserLogin},
    { path: "/register", name: "register", component: UserRegistration },
  ]
})

export default router
