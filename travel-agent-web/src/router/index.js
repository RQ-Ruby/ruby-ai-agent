import {createRouter, createWebHistory} from 'vue-router'
import HomeView from '../views/HomeView.vue'
import TravelChatView from '../views/TravelChatView.vue'
import TravelPlannerView from '../views/TravelPlannerView.vue'
import WorkflowPlanView from '../views/WorkflowPlanView.vue'
import UserAuthView from '../views/UserAuthView.vue'
import UserCenterView from '../views/UserCenterView.vue'
import AdminUserManageView from '../views/AdminUserManageView.vue'
import {useAuthStore} from '../stores/auth.js'

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes: [
        {path: '/', name: 'home', component: HomeView, meta: {title: '应用首页'}},
        {
            path: '/travel',
            name: 'travel',
            component: TravelChatView,
            meta: {title: '行旅 AI · 旅行咨询', requiresAuth: true}
        },
        {
            path: '/planner',
            name: 'planner',
            component: TravelPlannerView,
            meta: {title: '行旅 AI · 规划智能体', requiresAuth: true}
        },
        {
            path: '/workflow',
            name: 'workflow',
            component: WorkflowPlanView,
            meta: {title: '行旅 AI · 工作流规划', requiresAuth: true}
        },
        {path: '/login', name: 'login', component: UserAuthView, props: {mode: 'login'}, meta: {title: '用户登录'}},
        {
            path: '/register',
            name: 'register',
            component: UserAuthView,
            props: {mode: 'register'},
            meta: {title: '用户注册'}
        },
        {
            path: '/user/center',
            name: 'user-center',
            component: UserCenterView,
            meta: {title: '用户中心', requiresAuth: true}
        },
        {
            path: '/admin/users',
            name: 'admin-users',
            component: AdminUserManageView,
            meta: {title: '用户管理', requiresAuth: true, requiresAdmin: true}
        },
    ],
})

router.beforeEach(async (to) => {
    const auth = useAuthStore()
    await auth.ensureAuthLoaded()

    if (to.meta?.requiresAuth && !auth.isLoggedIn.value) {
        return {
            path: '/login',
            query: {redirect: to.fullPath},
        }
    }

    if ((to.path === '/login' || to.path === '/register') && auth.isLoggedIn.value) {
        return '/'
    }

    if (to.meta?.requiresAdmin && !auth.isAdmin.value) {
        return '/'
    }

    return true
})

router.afterEach((to) => {
    if (to.meta?.title) {
        document.title = `${to.meta.title} · 行旅 AI`
    }
})

export default router
