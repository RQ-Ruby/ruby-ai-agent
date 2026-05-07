import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import LoveChatView from '../views/LoveChatView.vue'
import ManusChatView from '../views/ManusChatView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', name: 'home', component: HomeView, meta: { title: '应用首页' } },
    { path: '/love', name: 'love', component: LoveChatView, meta: { title: 'AI 恋爱大师' } },
    { path: '/manus', name: 'manus', component: ManusChatView, meta: { title: 'AI 超级智能体' } },
  ],
})

router.afterEach((to) => {
  if (to.meta?.title) {
    document.title = `${to.meta.title} · Ruby AI`
  }
})

export default router
