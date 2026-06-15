<script setup>
import {computed, onMounted, reactive, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {getLoginUser, userLogin, userRegister} from '../api/user.js'
import {useAuthStore} from '../stores/auth.js'

const props = defineProps({
  mode: {
    type: String,
    default: 'login',
  },
})

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const errorMessage = ref('')
const successMessage = ref('')

const form = reactive({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
})

const isLogin = computed(() => props.mode === 'login')
const pageTitle = computed(() => (isLogin.value ? '欢迎回来' : '创建你的行旅账号'))
const pageDesc = computed(() =>
    isLogin.value
        ? '登录后即可使用多轮咨询、工作流规划与后续用户中心能力。'
        : '注册一个账号，保存你的会话、规划记录与个性化偏好。',
)
const submitText = computed(() => (loading.value ? '提交中...' : isLogin.value ? '登录' : '注册'))
const switchText = computed(() => (isLogin.value ? '还没有账号？' : '已经有账号了？'))
const switchLinkText = computed(() => (isLogin.value ? '去注册' : '去登录'))
const switchTo = computed(() => (isLogin.value ? '/register' : '/login'))
const sideTitle = computed(() => (isLogin.value ? '登录后可继续使用' : '注册后立即解锁'))
const featureList = computed(() => [
  '多轮旅行咨询记录',
  '工作流规划历史',
  '后续用户中心与权限能力',
])

function validate() {
  if (!form.userAccount.trim()) {
    throw new Error('请输入账号')
  }
  if (form.userAccount.trim().length < 4) {
    throw new Error('账号长度不能少于 4 位')
  }
  if (!form.userPassword) {
    throw new Error('请输入密码')
  }
  if (form.userPassword.length < 8) {
    throw new Error('密码长度不能少于 8 位')
  }
  if (!isLogin.value && form.userPassword !== form.checkPassword) {
    throw new Error('两次输入的密码不一致')
  }
}

async function submit() {
  errorMessage.value = ''
  successMessage.value = ''
  try {
    validate()
    loading.value = true
    if (isLogin.value) {
      const loginUser = await userLogin({
        userAccount: form.userAccount.trim(),
        userPassword: form.userPassword,
      })
      auth.setLoginUser(loginUser)
      successMessage.value = `登录成功，欢迎你${loginUser?.userName ? `，${loginUser.userName}` : ''}`
      window.setTimeout(() => {
        router.push(route.query.redirect || '/')
      }, 500)
      return
    }
    await userRegister({
      userAccount: form.userAccount.trim(),
      userPassword: form.userPassword,
      checkPassword: form.checkPassword,
    })
    successMessage.value = '注册成功，请使用新账号登录'
    window.setTimeout(() => {
      router.push('/login')
    }, 700)
  } catch (error) {
    errorMessage.value = error?.message || '操作失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    await getLoginUser()
    router.replace('/')
  } catch {
  }
})
</script>

<template>
  <div class="auth-page">
    <div class="auth-background auth-background-left"></div>
    <div class="auth-background auth-background-right"></div>

    <section class="auth-shell">
      <aside class="auth-brand-panel">
        <router-link class="brand-mark" to="/">行旅 AI</router-link>
        <h1 class="brand-title">{{ pageTitle }}</h1>
        <p class="brand-desc">{{ pageDesc }}</p>

        <div class="brand-card">
          <span class="brand-card-title">{{ sideTitle }}</span>
          <ul class="brand-list">
            <li v-for="item in featureList" :key="item">{{ item }}
            </li>
          </ul>
        </div>
      </aside>

      <main class="auth-form-panel">
        <div class="auth-card">
          <div class="auth-card-head">
            <h2 class="auth-heading">{{ isLogin ? '登录账号' : '创建账号' }}</h2>
            <p class="auth-subheading">{{
                isLogin ? '输入账号和密码即可进入工作台。' : '使用账号和密码快速完成注册。'
              }}</p>
          </div>

          <form class="auth-form" @submit.prevent="submit">
            <label class="field">
              <span class="field-label">账号</span>
              <input
                  v-model="form.userAccount"
                  autocomplete="username"
                  class="field-input"
                  maxlength="64"
                  placeholder="请输入账号"
                  type="text"
              />
            </label>

            <label class="field">
              <span class="field-label">密码</span>
              <input
                  v-model="form.userPassword"
                  autocomplete="current-password"
                  class="field-input"
                  maxlength="64"
                  placeholder="请输入密码（至少 8 位）"
                  type="password"
              />
            </label>

            <label v-if="!isLogin" class="field">
              <span class="field-label">确认密码</span>
              <input
                  v-model="form.checkPassword"
                  autocomplete="new-password"
                  class="field-input"
                  maxlength="64"
                  placeholder="请再次输入密码"
                  type="password"
              />
            </label>

            <p v-if="errorMessage" class="message message-error">{{ errorMessage }}</p>
            <p v-if="successMessage" class="message message-success">{{ successMessage }}</p>

            <button :disabled="loading" class="submit-btn" type="submit">
              {{ submitText }}
            </button>
          </form>

          <div class="auth-footer">
            <span>{{ switchText }}</span>
            <router-link :to="switchTo" class="switch-link">{{ switchLinkText }}</router-link>
          </div>

          <router-link class="back-link" to="/">返回首页</router-link>
        </div>
      </main>
    </section>
  </div>
</template>

<style scoped>
.auth-page {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  padding: 28px;
}

.auth-background {
  position: absolute;
  border-radius: 999px;
  filter: blur(24px);
  opacity: 0.68;
  pointer-events: none;
}

.auth-background-left {
  top: -120px;
  left: -100px;
  width: 320px;
  height: 320px;
  background: rgba(var(--gold-rgb), 0.16);
}

.auth-background-right {
  right: -120px;
  bottom: -120px;
  width: 360px;
  height: 360px;
  background: rgba(var(--accent-rgb), 0.14);
}

.auth-shell {
  position: relative;
  z-index: 1;
  min-height: calc(100vh - 56px);
  max-width: 1160px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(360px, 460px);
  gap: 28px;
  align-items: stretch;
}

.auth-brand-panel {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 28px 12px 28px 4px;
}

.brand-mark {
  width: fit-content;
  min-height: 44px;
  display: inline-flex;
  align-items: center;
  padding: 0 18px;
  border-radius: 999px;
  text-decoration: none;
  color: var(--surface);
  font-weight: 800;
  letter-spacing: 0.18em;
  border: 1px solid rgba(var(--gold-rgb), 0.42);
  background: rgba(248, 245, 238, 0.08);
  font-family: 'STKaiti', 'KaiTi', 'Songti SC', 'Noto Serif SC', serif;
}

.brand-title {
  margin: 18px 0 0;
  font-size: clamp(2rem, 4vw, 3.5rem);
  line-height: 1.08;
  letter-spacing: 0.04em;
  color: var(--surface);
  font-family: 'STKaiti', 'KaiTi', 'Songti SC', 'Noto Serif SC', serif;
}

.brand-desc {
  margin: 16px 0 0;
  max-width: 560px;
  color: rgba(248, 245, 238, 0.8);
  font-size: 1rem;
  line-height: 1.8;
}

.brand-card {
  position: relative;
  margin-top: 28px;
  max-width: 520px;
  padding: 22px;
  border-radius: 26px;
  border: 1px solid var(--border-strong);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.3), rgba(255, 255, 255, 0.06)),
  var(--surface-elevated);
  box-shadow: var(--card-shadow);
  overflow: hidden;
}

.brand-card::before {
  content: '';
  position: absolute;
  inset: 12px;
  border: 1px solid rgba(var(--gold-rgb), 0.24);
  border-radius: 20px;
  pointer-events: none;
}

.brand-card-title {
  display: inline-flex;
  color: var(--highlight);
  font-size: 0.92rem;
  font-weight: 700;
}

.brand-list {
  margin: 16px 0 0;
  padding-left: 18px;
  color: var(--muted);
  position: relative;
  z-index: 1;
}

.brand-list li + li {
  margin-top: 10px;
}

.auth-form-panel {
  display: flex;
  align-items: center;
  justify-content: center;
}

.auth-card {
  position: relative;
  width: 100%;
  padding: 28px;
  border-radius: 28px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.3), rgba(255, 255, 255, 0.06)),
  var(--surface-elevated);
  border: 1px solid var(--border-strong);
  box-shadow: var(--card-shadow);
  backdrop-filter: blur(12px);
  overflow: hidden;
}

.auth-card::before {
  content: '';
  position: absolute;
  inset: 12px;
  border: 1px solid rgba(var(--gold-rgb), 0.24);
  border-radius: 22px;
  pointer-events: none;
}

.auth-card-head {
  margin-bottom: 22px;
  position: relative;
  z-index: 1;
}

.auth-heading {
  margin: 0;
  font-size: 1.7rem;
  line-height: 1.2;
  font-family: 'STKaiti', 'KaiTi', 'Songti SC', 'Noto Serif SC', serif;
}

.auth-subheading {
  margin: 10px 0 0;
  color: var(--muted);
  font-size: 0.95rem;
  line-height: 1.7;
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  position: relative;
  z-index: 1;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field-label {
  font-size: 0.9rem;
  font-weight: 600;
}

.field-input {
  height: 48px;
  border-radius: 16px;
  border: 1px solid var(--border-strong);
  background: rgba(255, 255, 255, 0.72);
  color: var(--text);
  padding: 0 14px;
  font-size: 0.96rem;
  outline: none;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.field-input:focus {
  border-color: var(--highlight);
  box-shadow: 0 0 0 4px rgba(var(--gold-rgb), 0.16);
}

.message {
  margin: 0;
  padding: 12px 14px;
  border-radius: 14px;
  font-size: 0.9rem;
}

.message-error {
  color: var(--danger);
  background: rgba(var(--highlight-rgb), 0.1);
  border: 1px solid rgba(var(--highlight-rgb), 0.18);
}

.message-success {
  color: var(--success);
  background: rgba(var(--accent-rgb), 0.08);
  border: 1px solid rgba(var(--gold-rgb), 0.24);
}

.submit-btn {
  margin-top: 4px;
  height: 50px;
  border: 1px solid var(--border-strong);
  border-radius: 16px;
  background: linear-gradient(135deg, var(--accent), var(--accent-strong));
  color: var(--surface);
  font-size: 0.98rem;
  font-weight: 700;
  cursor: pointer;
  transition: transform 0.16s ease, box-shadow 0.16s ease, opacity 0.16s ease;
  box-shadow: 0 18px 30px rgba(7, 14, 22, 0.2);
}

.submit-btn:hover:not(:disabled) {
  transform: translateY(-1px);
}

.submit-btn:disabled {
  opacity: 0.72;
  cursor: not-allowed;
}

.auth-footer {
  margin-top: 18px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--muted);
  font-size: 0.92rem;
  position: relative;
  z-index: 1;
}

.switch-link,
.back-link {
  color: var(--accent-strong);
  text-decoration: none;
  font-weight: 700;
}

.back-link {
  display: inline-flex;
  margin-top: 18px;
  font-size: 0.92rem;
  position: relative;
  z-index: 1;
}

@media (max-width: 980px) {
  .auth-shell {
    grid-template-columns: 1fr;
  }

  .auth-brand-panel {
    padding: 8px 4px 0;
  }
}

@media (max-width: 640px) {
  .auth-page {
    padding: 16px;
  }

  .auth-shell {
    min-height: auto;
    gap: 18px;
  }

  .auth-card {
    padding: 22px 18px;
    border-radius: 22px;
  }

  .brand-card {
    padding: 18px;
    border-radius: 22px;
  }
}
</style>
