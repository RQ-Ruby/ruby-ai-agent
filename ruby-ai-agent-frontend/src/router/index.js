import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import TravelChatView from '../views/TravelChatView.vue'
import TravelPlannerView from '../views/TravelPlannerView.vue'
import WorkflowPlanView from '../views/WorkflowPlanView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', name: 'home', component: HomeView, meta: { title: '应用首页' } },
    { path: '/travel', name: 'travel', component: TravelChatView, meta: { title: '行旅 AI · 旅行咨询' } },
    { path: '/planner', name: 'planner', component: TravelPlannerView, meta: { title: '行旅 AI · 规划智能体' } },
    { path: '/workflow', name: 'workflow', component: WorkflowPlanView, meta: { title: '行旅 AI · 工作流规划' } },
  ],
})

router.afterEach((to) => {
  if (to.meta?.title) {
    document.title = `${to.meta.title} · 行旅 AI`
  }
})

export default router
