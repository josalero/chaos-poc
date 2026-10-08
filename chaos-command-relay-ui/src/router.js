import { createRouter, createWebHistory } from 'vue-router';
import CommandStatusView from './views/CommandStatusView.vue';
import CommandsView from './views/CommandsView.vue';
import DashboardView from './views/DashboardView.vue';
import SavedView from './views/SavedView.vue';
import ServiceDetailView from './views/ServiceDetailView.vue';

export const router = createRouter({
  history: createWebHistory('/chaos/'),
  scrollBehavior(to, from, saved) {
    if (saved) {
      return saved;
    }
    if (to.path === from.path) {
      return false;
    }
    return { top: 0 };
  },
  routes: [
    { path: '/', name: 'services', component: DashboardView },
    { path: '/commands', name: 'commands', component: CommandsView },
    { path: '/commands/:commandId', name: 'command', component: CommandStatusView },
    { path: '/saved', name: 'saved', component: SavedView },
    { path: '/services/:applicationName', name: 'service', component: ServiceDetailView },
  ],
});
