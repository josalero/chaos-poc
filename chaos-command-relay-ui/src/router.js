import { createRouter, createWebHistory } from 'vue-router';
import CommandFormView from './views/CommandFormView.vue';
import CommandStatusView from './views/CommandStatusView.vue';
import DashboardView from './views/DashboardView.vue';
import ServiceDetailView from './views/ServiceDetailView.vue';

export const router = createRouter({
  history: createWebHistory('/chaos/'),
  routes: [
    { path: '/', name: 'scenarios', component: DashboardView },
    { path: '/commands/new', name: 'publish', component: CommandFormView },
    { path: '/commands/:commandId', name: 'command', component: CommandStatusView },
    { path: '/services/:applicationName', name: 'service', component: ServiceDetailView },
  ],
});
