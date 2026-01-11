// ----- Styles -----

import './assets/main.css';
import 'bootstrap/dist/css/bootstrap.min.css';
import 'bootstrap/dist/js/bootstrap.bundle.min.js';
import 'bootstrap-icons/font/bootstrap-icons.css';

// ----- Vue App -----

import { createApp } from 'vue';
import App from './App.vue';
import router from '@/router/index.js';

// ----- Create and Mount App -----

createApp(App).use(router).mount('#app');
