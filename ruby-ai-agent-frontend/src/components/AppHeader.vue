<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth.js'

const router = useRouter()
const auth = useAuthStore()
const logoutLoading = ref(false)

const navItems = [
  { label: '首页', to: '/' },
  { label: '旅行咨询', to: '/travel' },
  { label: '规划智能体', to: '/planner' },
  { label: '工作流规划', to: '/workflow' },
]

const visibleNavItems = computed(() => {
  if (auth.isAdmin.value) {
    return [...navItems, { label: '用户管理', to: '/admin/users' }]
  }
  return navItems
})

const userDisplayName = computed(() => {
  const user = auth.state.loginUser
  if (!user) return ''
  return user.userName || user.userAccount || '用户'
})

const avatarText = computed(() => userDisplayName.value?.slice(0, 1) || '游')

async function handleLogout() {
  if (logoutLoading.value) return
  logoutLoading.value = true
  try {
    await auth.logout()
    router.push('/login')
  } finally {
    logoutLoading.value = false
  }
}
</script>

<template>
  <header class="app-header">
    <div class="app-header-inner">
      <router-link to="/" class="brand-link">
        <span class="brand-main">行旅 AI</span>
        <span class="brand-sub">TOUR WITH INTELLIGENCE</span>
      </router-link>

      <nav class="header-nav">
        <router-link
          v-for="item in visibleNavItems"
          :key="item.to"
          :to="item.to"
          class="header-nav-link"
        >
          {{ item.label }}
        </router-link>
      </nav>

      <div class="header-user-area">
        <template v-if="auth.isLoggedIn.value">
          <router-link to="/user/center" class="user-brief">
            <span class="user-avatar">{{ avatarText }}</span>
            <span class="user-name">{{ userDisplayName }}</span>
          </router-link>
          <button class="header-logout-btn" type="button" @click="handleLogout">
            {{ logoutLoading ? '退出中...' : '退出' }}
          </button>
        </template>

        <template v-else>
          <router-link to="/login" class="header-auth-link header-auth-link-ghost">登录</router-link>
          <router-link to="/register" class="header-auth-link header-auth-link-solid">注册</router-link>
        </template>
      </div>
    </div>
  </header>
</template>

<style scoped>
.app-header {
  position: sticky;
  top: 0;
  z-index: 20;
  padding: 16px 16px 0;
}

.app-header-inner {
  position: relative;
  max-width: 1180px;
  margin: 0 auto;
  min-height: 72px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 20px;
  border-radius: 28px;
  border: 1px solid var(--border-strong);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.34), rgba(255, 255, 255, 0.08)),
    var(--surface-strong);
  box-shadow: var(--card-shadow);
  backdrop-filter: blur(14px);
  overflow: hidden;
}

.app-header-inner::before,
.app-header-inner::after {
  content: '';
  position: absolute;
  pointer-events: none;
}

.app-header-inner::before {
  inset: 10px;
  background:
    linear-gradient(rgba(var(--gold-rgb), 0.82), rgba(var(--gold-rgb), 0.82)) 18px 18px / 24px 1px no-repeat,
    linear-gradient(rgba(var(--gold-rgb), 0.82), rgba(var(--gold-rgb), 0.82)) 18px 18px / 1px 24px no-repeat,
    linear-gradient(rgba(var(--gold-rgb), 0.82), rgba(var(--gold-rgb), 0.82)) calc(100% - 18px) 18px / 24px 1px no-repeat,
    linear-gradient(rgba(var(--gold-rgb), 0.82), rgba(var(--gold-rgb), 0.82)) calc(100% - 18px) 18px / 1px 24px no-repeat,
    linear-gradient(rgba(var(--gold-rgb), 0.82), rgba(var(--gold-rgb), 0.82)) 18px calc(100% - 18px) / 24px 1px no-repeat,
    linear-gradient(rgba(var(--gold-rgb), 0.82), rgba(var(--gold-rgb), 0.82)) 18px calc(100% - 18px) / 1px 24px no-repeat,
    linear-gradient(rgba(var(--gold-rgb), 0.82), rgba(var(--gold-rgb), 0.82)) calc(100% - 18px) calc(100% - 18px) / 24px 1px no-repeat,
    linear-gradient(rgba(var(--gold-rgb), 0.82), rgba(var(--gold-rgb), 0.82)) calc(100% - 18px) calc(100% - 18px) / 1px 24px no-repeat;
  border: 1px solid rgba(var(--gold-rgb), 0.28);
  border-radius: 20px;
  opacity: 0.86;
}

.app-header-inner::after {
  inset: 0;
  background:
    radial-gradient(circle at 12% 18%, rgba(var(--accent-rgb), 0.08), transparent 22%),
    radial-gradient(circle at 88% 20%, rgba(var(--gold-rgb), 0.08), transparent 20%);
  opacity: 0.5;
}

.brand-link {
  position: relative;
  z-index: 1;
  flex-shrink: 0;
  display: inline-flex;
  flex-direction: column;
  gap: 4px;
  text-decoration: none;
  color: var(--text);
}

.brand-main {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  min-height: 42px;
  padding: 0 18px;
  border-radius: 999px;
  background: rgba(248, 245, 238, 0.96);
  border: 1px solid var(--border-strong);
  font-family: 'STKaiti', 'KaiTi', 'Songti SC', 'Noto Serif SC', serif;
  font-size: 1.02rem;
  font-weight: 800;
  letter-spacing: 0.18em;
}

.brand-sub {
  padding-left: 8px;
  font-size: 0.66rem;
  letter-spacing: 0.22em;
  color: var(--muted);
}

.header-nav {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  flex-wrap: wrap;
}

.header-nav-link {
  text-decoration: none;
  color: var(--text);
  padding: 9px 16px;
  border-radius: 999px;
  font-size: 0.9rem;
  border: 1px solid transparent;
  transition: background 0.24s ease, color 0.24s ease, border-color 0.24s ease,
    transform 0.24s ease;
}

.header-nav-link:hover {
  color: var(--text);
  border-color: rgba(var(--gold-rgb), 0.4);
  background: rgba(var(--accent-rgb), 0.06);
  transform: translateY(-1px);
}

.header-nav-link.router-link-active {
  background: var(--accent-soft);
  color: var(--text);
  border-color: rgba(var(--gold-rgb), 0.42);
  font-weight: 700;
}

.header-user-area {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  min-width: 0;
}

.header-auth-link,
.header-logout-btn,
.user-brief {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  height: 38px;
  padding: 0 14px;
  font-size: 0.88rem;
  font-weight: 700;
  text-decoration: none;
  transition: transform 0.24s ease, box-shadow 0.24s ease, border-color 0.24s ease,
    background 0.24s ease;
}

.header-auth-link-ghost,
.header-logout-btn {
  border: 1px solid var(--border-strong);
  background: rgba(248, 245, 238, 0.92);
  color: var(--highlight);
}

.header-auth-link-solid {
  border: 1px solid var(--border-strong);
  background: linear-gradient(135deg, var(--accent), var(--accent-strong));
  color: var(--surface);
  box-shadow: 0 14px 24px rgba(7, 14, 22, 0.2);
}

.header-logout-btn {
  cursor: pointer;
}

.header-auth-link:hover,
.header-logout-btn:hover,
.user-brief:hover {
  transform: translateY(-1px);
  box-shadow: 0 0 18px rgba(var(--gold-rgb), 0.12), 0 12px 24px rgba(7, 14, 22, 0.16);
}

.user-brief {
  max-width: 220px;
  gap: 10px;
  padding-left: 10px;
  padding-right: 14px;
  color: var(--text);
  border: 1px solid var(--border-strong);
  background: rgba(248, 245, 238, 0.94);
}

.user-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(var(--gold-rgb), 0.22);
  color: var(--accent-strong);
  font-size: 0.84rem;
  font-weight: 800;
  flex-shrink: 0;
}

.user-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 980px) {
  .app-header-inner {
    flex-wrap: wrap;
    justify-content: center;
  }

  .header-user-area {
    width: 100%;
    justify-content: center;
  }
}

@media (max-width: 640px) {
  .app-header {
    padding: 12px 12px 0;
  }

  .app-header-inner {
    padding: 14px;
    border-radius: 22px;
  }

  .header-nav {
    gap: 8px;
  }

  .header-nav-link {
    padding: 7px 10px;
    font-size: 0.84rem;
  }

  .brand-sub {
    display: none;
  }
}
</style>
