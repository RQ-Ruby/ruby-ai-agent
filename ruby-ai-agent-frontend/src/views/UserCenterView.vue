<script setup>
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth.js'

const router = useRouter()
const auth = useAuthStore()

const loginUser = computed(() => auth.state.loginUser)
const displayName = computed(() => loginUser.value?.userName || loginUser.value?.userAccount || '未命名用户')
const avatarText = computed(() => displayName.value?.slice(0, 1) || '游')

onMounted(async () => {
  const user = await auth.ensureAuthLoaded()
  if (!user) {
    router.replace('/login')
  }
})
</script>

<template>
  <div class="user-center-page">
    <section class="user-center-shell">
      <div class="user-center-hero">
        <div class="user-avatar-large">{{ avatarText }}</div>
        <div class="user-hero-copy">
          <span class="user-badge">用户中心</span>
          <h1>{{ displayName }}</h1>
          <p>你已登录行旅 AI，后续这里可以继续扩展会话历史、收藏行程、权限管理等能力。</p>
        </div>
      </div>

      <div class="user-grid">
        <article class="info-card">
          <h2>基础信息</h2>
          <dl class="info-list">
            <div>
              <dt>账号</dt>
              <dd>{{ loginUser?.userAccount || '-' }}</dd>
            </div>
            <div>
              <dt>昵称</dt>
              <dd>{{ loginUser?.userName || '-' }}</dd>
            </div>
            <div>
              <dt>角色</dt>
              <dd>{{ loginUser?.userRole || '-' }}</dd>
            </div>
            <div>
              <dt>简介</dt>
              <dd>{{ loginUser?.userProfile || '这个用户还没有填写简介。' }}</dd>
            </div>
          </dl>
        </article>

        <article class="info-card">
          <h2>后续可扩展</h2>
          <ul class="todo-list">
            <li>展示我的旅行会话历史</li>
            <li>保存常用目的地与偏好</li>
            <li>管理工作流规划记录</li>
            <li>管理员后台与角色权限页面</li>
          </ul>
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped>
.user-center-page {
  max-width: 1180px;
  margin: 0 auto;
  padding: 24px 24px 56px;
}

.user-center-shell {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.user-center-hero {
  display: flex;
  align-items: center;
  gap: 22px;
  padding: 28px;
  border-radius: 28px;
  border: 1px solid var(--border);
  background: linear-gradient(180deg, rgba(255, 251, 246, 0.92), rgba(250, 241, 231, 0.78));
  box-shadow: var(--card-shadow);
}

.user-avatar-large {
  width: 84px;
  height: 84px;
  border-radius: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--accent), var(--accent-strong));
  color: #fff;
  font-size: 2rem;
  font-weight: 800;
  box-shadow: 0 18px 30px rgba(110, 68, 45, 0.18);
}

.user-badge {
  display: inline-flex;
  align-items: center;
  height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--accent-strong);
  font-size: 0.82rem;
  font-weight: 700;
}

.user-hero-copy h1 {
  margin: 14px 0 0;
  font-size: clamp(1.8rem, 3.4vw, 2.8rem);
}

.user-hero-copy p {
  margin: 12px 0 0;
  color: var(--muted);
  line-height: 1.75;
}

.user-grid {
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: 22px;
}

.info-card {
  padding: 24px;
  border-radius: 24px;
  border: 1px solid var(--border);
  background: var(--surface-elevated);
  box-shadow: var(--card-shadow);
}

.info-card h2 {
  margin: 0 0 18px;
  font-size: 1.14rem;
}

.info-list {
  margin: 0;
}

.info-list div + div {
  margin-top: 16px;
}

.info-list dt {
  color: var(--muted);
  font-size: 0.86rem;
}

.info-list dd {
  margin: 6px 0 0;
  font-size: 0.98rem;
  line-height: 1.7;
}

.todo-list {
  margin: 0;
  padding-left: 18px;
  color: var(--muted);
}

.todo-list li + li {
  margin-top: 12px;
}

@media (max-width: 860px) {
  .user-grid {
    grid-template-columns: 1fr;
  }

  .user-center-hero {
    flex-direction: column;
    align-items: flex-start;
  }
}

@media (max-width: 640px) {
  .user-center-page {
    padding: 18px 16px 40px;
  }

  .user-center-hero,
  .info-card {
    padding: 20px;
    border-radius: 22px;
  }
}
</style>
