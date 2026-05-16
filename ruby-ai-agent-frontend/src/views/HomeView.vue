<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth.js'

const router = useRouter()
const auth = useAuthStore()
const homeRef = ref(null)
let revealObserver = null

const navItems = [
  { label: '首页', to: '/' },
  { label: '旅行咨询', to: '/travel' },
  { label: '规划智能体', to: '/planner' },
  { label: '工作流规划', to: '/workflow' },
  { label: '用户管理', to: '/admin/users' },
]

const heroStats = [
  { value: '3', label: '核心 AI 模块' },
  { value: '国内', label: '文旅场景聚焦' },
  { value: 'SSE', label: '流式输出体验' },
]

const featureCards = [
  {
    key: 'chat',
    title: '旅行咨询',
    subtitle: '纯对话 AI',
    to: '/travel',
    desc: '面向国内文旅目的地的对话咨询入口，结合 RAG 攻略库与流式回复能力，适合询问景点、美食、住宿、交通、避坑与出行建议。',
    tags: ['RAG 攻略库', 'SSE 实时流式', '多轮咨询', '目的地问答'],
  },
  {
    key: 'agent',
    title: '规划智能体',
    subtitle: 'Agent 智能体',
    to: '/planner',
    desc: '面向复杂行程需求的 ReAct 规划智能体，可整合攻略信息、编排行程路线、估算预算，并在你明确需要时生成 PDF 国风行程手册。',
    tags: ['自主规划', '预算核算', '攻略整合', 'PDF 行程手册'],
  },
  {
    key: 'workflow',
    title: '工作流规划',
    subtitle: 'Graph 工作流',
    to: '/workflow',
    desc: '基于 Spring AI Alibaba Graph 的多步骤规划能力，对需求进行解析、补参、检索与节点式生成，输出完整可调整的出行方案。',
    tags: ['多节点定制', '需求解析', '深度规划', '完整方案'],
  },
]

const userDisplayName = computed(() => {
  const user = auth.state.loginUser
  if (!user) return ''
  return user.userName || user.userAccount || '用户'
})

const visibleNavItems = computed(() => {
  if (auth.isAdmin.value) {
    return navItems
  }
  return navItems.slice(0, 4)
})

const pageStyle = computed(() => ({
  '--home-primary': '#2A4747',
  '--home-primary-rgb': '42, 71, 71',
  '--home-surface': '#F8F5EE',
  '--home-surface-strong': '#FCFBF7',
  '--home-soft': '#EFE9DD',
  '--home-accent': '#A65C41',
  '--home-accent-rgb': '166, 92, 65',
  '--home-muted': '#5E6D6D',
}))

async function handleLogout() {
  await auth.logout()
  router.push('/login')
}

onMounted(() => {
  const nodes = homeRef.value?.querySelectorAll('.ink-reveal') || []
  if (!nodes.length) return

  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    nodes.forEach((node) => node.classList.add('is-visible'))
    return
  }

  revealObserver = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-visible')
          revealObserver?.unobserve(entry.target)
        }
      })
    },
    { threshold: 0.14, rootMargin: '0px 0px -40px 0px' },
  )

  nodes.forEach((node, index) => {
    node.style.transitionDelay = `${Math.min(index * 90, 360)}ms`
    revealObserver.observe(node)
  })
})

onBeforeUnmount(() => {
  revealObserver?.disconnect()
  revealObserver = null
})
</script>

<template>
  <div ref="homeRef" class="home-page" :style="pageStyle">
    <div class="home-page-bg"></div>

    <div class="home-shell">
      <header class="home-nav ink-reveal">
        <router-link to="/" class="home-logo">
          <span class="home-logo-mark">行旅 AI</span>
          <span class="home-logo-sub">TOUR WITH INTELLIGENCE</span>
        </router-link>

        <nav class="home-nav-menu" aria-label="首页导航">
          <router-link
            v-for="item in visibleNavItems"
            :key="item.to"
            :to="item.to"
            class="home-nav-link"
          >
            {{ item.label }}
          </router-link>
        </nav>

        <div class="home-nav-actions">
          <template v-if="auth.isLoggedIn.value">
            <router-link to="/user/center" class="home-user-chip">{{ userDisplayName }}</router-link>
            <button type="button" class="home-nav-btn home-nav-btn-solid" @click="handleLogout">退出</button>
          </template>
          <template v-else>
            <router-link to="/login" class="home-nav-btn home-nav-btn-ghost">登录</router-link>
            <router-link to="/register" class="home-nav-btn home-nav-btn-solid">注册</router-link>
          </template>
        </div>
      </header>

      <main class="home-main">
        <section class="hero-section ink-reveal">
          <div class="hero-copy-card">
            <div class="hero-card-grid">
              <div class="hero-copy-main">
                <span class="hero-kicker">国内文旅专属 AI 旅游助手</span>
                <h1 class="hero-title">行旅 AI・以智绘山河，赴万千山海</h1>
                <p class="hero-subtitle">
                  对话咨询、Agent 智能规划、工作流深度定制一站式服务，把山水出行、路线安排与智能规划融为一体，服务国内文旅目的地决策与出行体验。
                </p>

                <div class="hero-actions">
                  <router-link to="/travel" class="hero-cta hero-cta-solid">立即开始咨询</router-link>
                </div>

                <div class="hero-stats">
                  <div v-for="item in heroStats" :key="item.label" class="hero-stat-item">
                    <strong>{{ item.value }}</strong>
                    <span>{{ item.label }}</span>
                  </div>
                </div>
              </div>

              <article class="hero-visual ink-reveal">
                <div class="visual-map-layer"></div>
                <div class="visual-route route-one"></div>
                <div class="visual-route route-two"></div>
                <div class="visual-poi poi-one"></div>
                <div class="visual-poi poi-two"></div>
                <div class="visual-poi poi-three"></div>
                <div class="visual-mountain mountain-one"></div>
                <div class="visual-mountain mountain-two"></div>
                <div class="visual-scroll-tag">山水文旅 · 智能行旅</div>
              </article>
            </div>
          </div>
        </section>

        <section class="feature-section">
          <div class="section-heading ink-reveal">
            <span class="section-kicker">核心三大 AI 模块</span>
            <h2>从文旅问答到深度定制，一站进入更懂山河的智能服务</h2>
            <p>三种能力各有侧重，分别覆盖纯对话问答、Agent 自主规划与 Graph 工作流深度定制。</p>
          </div>

          <div class="feature-grid">
            <article
              v-for="feature in featureCards"
              :key="feature.key"
              class="feature-card ink-reveal"
              :data-variant="feature.key"
            >
              <div class="feature-visual" :data-variant="feature.key">
                <svg v-if="feature.key === 'chat'" viewBox="0 0 160 120" class="feature-icon" aria-hidden="true">
                  <path d="M26 34c0-9 7-16 16-16h43c9 0 16 7 16 16v18c0 9-7 16-16 16H63l-16 12v-12H42c-9 0-16-7-16-16V34Z" fill="none" stroke="currentColor" stroke-width="4" stroke-linejoin="round"/>
                  <path d="M90 56c0-8 6-14 14-14h16c8 0 14 6 14 14v14c0 8-6 14-14 14h-8l-10 8v-8h-2c-8 0-14-6-14-14V56Z" fill="none" stroke="currentColor" stroke-width="4" stroke-linejoin="round" opacity=".72"/>
                  <path d="M18 106c14-18 26-30 37-35 7 6 12 12 16 19 9-14 18-24 29-31 11 10 24 26 40 47" fill="none" stroke="currentColor" stroke-width="4" stroke-linecap="round"/>
                </svg>
                <svg v-else-if="feature.key === 'agent'" viewBox="0 0 160 120" class="feature-icon" aria-hidden="true">
                  <circle cx="84" cy="56" r="26" fill="none" stroke="currentColor" stroke-width="4"/>
                  <path d="M84 24v10M84 78v10M52 56h10M106 56h10" stroke="currentColor" stroke-width="4" stroke-linecap="round"/>
                  <path d="M84 40l10 20-20 10 10-30Z" fill="currentColor" opacity=".78"/>
                  <path d="M20 98c18-12 34-20 49-22 10 6 17 12 22 20 9-10 22-18 39-24" fill="none" stroke="currentColor" stroke-width="4" stroke-linecap="round"/>
                  <circle cx="36" cy="92" r="4" fill="currentColor"/>
                  <circle cx="124" cy="72" r="4" fill="currentColor"/>
                </svg>
                <svg v-else viewBox="0 0 160 120" class="feature-icon" aria-hidden="true">
                  <path d="M34 20h68c9 0 16 7 16 16v50c0 9-7 16-16 16H34" fill="none" stroke="currentColor" stroke-width="4" stroke-linejoin="round"/>
                  <path d="M34 20c-8 0-14 6-14 14v54c0 8 6 14 14 14" fill="none" stroke="currentColor" stroke-width="4"/>
                  <path d="M48 40h42M48 56h50M48 72h34" stroke="currentColor" stroke-width="4" stroke-linecap="round" opacity=".82"/>
                  <path d="M90 88c8-8 15-12 22-12 7 0 14 4 24 14" fill="none" stroke="currentColor" stroke-width="4" stroke-linecap="round"/>
                  <circle cx="90" cy="88" r="4" fill="currentColor"/>
                  <circle cx="136" cy="90" r="4" fill="currentColor"/>
                </svg>
              </div>

              <div class="feature-body">
                <h3>{{ feature.title }}</h3>
                <p>{{ feature.desc }}</p>

                <div class="feature-tags">
                  <span v-for="tag in feature.tags" :key="tag" class="feature-tag">{{ tag }}</span>
                </div>

                <router-link :to="feature.to" class="feature-link">进入 {{ feature.title }}</router-link>
              </div>
            </article>
          </div>
        </section>
      </main>
    </div>
  </div>
</template>

<style scoped>
.home-page {
  --page-shadow: 0 24px 60px rgba(var(--home-primary-rgb), 0.12);
  --card-shadow: 0 18px 42px rgba(var(--home-primary-rgb), 0.1);
  --card-shadow-hover: 0 28px 64px rgba(var(--home-primary-rgb), 0.16);
  min-height: 100vh;
  position: relative;
  overflow: hidden;
  color: var(--home-primary);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.42), rgba(255, 255, 255, 0)),
    linear-gradient(180deg, var(--home-surface-strong), var(--home-surface));
}

.home-page-bg,
.home-page::before,
.home-page::after {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.home-page::before {
  background:
    radial-gradient(circle at 10% 12%, rgba(var(--home-primary-rgb), 0.12), transparent 22%),
    radial-gradient(circle at 84% 18%, rgba(var(--home-accent-rgb), 0.1), transparent 20%),
    linear-gradient(112deg, transparent 0 18%, rgba(var(--home-primary-rgb), 0.05) 18% 18.2%, transparent 18.2% 100%),
    linear-gradient(90deg, transparent 0 6%, rgba(var(--home-primary-rgb), 0.035) 6% 6.1%, transparent 6.1% 100%),
    linear-gradient(180deg, transparent 0 12%, rgba(var(--home-accent-rgb), 0.025) 12% 12.1%, transparent 12.1% 100%);
  opacity: 0.9;
}

.home-page::after {
  background:
    radial-gradient(circle at 18% 30%, rgba(255, 255, 255, 0.32) 0 2px, transparent 2px),
    radial-gradient(circle at 76% 20%, rgba(255, 255, 255, 0.22) 0 2px, transparent 2px),
    linear-gradient(135deg, transparent 0 44%, rgba(var(--home-primary-rgb), 0.03) 44% 44.2%, transparent 44.2% 100%);
  opacity: 0.68;
}

.home-page-bg {
  background:
    radial-gradient(circle at 20% 82%, rgba(var(--home-primary-rgb), 0.08), transparent 24%),
    radial-gradient(circle at 86% 76%, rgba(var(--home-accent-rgb), 0.08), transparent 20%),
    linear-gradient(0deg, rgba(var(--home-primary-rgb), 0.04), transparent 30%);
}

.home-shell {
  position: relative;
  z-index: 1;
  width: min(1280px, calc(100% - 32px));
  margin: 0 auto;
  padding: 24px 0 72px;
}

.home-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 14px 18px;
  border-radius: 28px;
  border: 1px solid rgba(var(--home-primary-rgb), 0.16);
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.56), rgba(255, 255, 255, 0.1)),
    rgba(255, 255, 255, 0.48);
  backdrop-filter: blur(14px);
  box-shadow: var(--page-shadow);
}

.home-logo {
  display: inline-flex;
  flex-direction: column;
  gap: 2px;
  text-decoration: none;
  color: inherit;
}

.home-logo-mark {
  display: inline-flex;
  align-items: center;
  min-height: 42px;
  padding: 0 18px;
  border-radius: 999px;
  border: 1px solid rgba(var(--home-primary-rgb), 0.14);
  background: rgba(255, 255, 255, 0.52);
  font-size: 1.04rem;
  font-weight: 800;
  letter-spacing: 0.16em;
}

.home-logo-sub {
  padding-left: 8px;
  font-size: 0.66rem;
  letter-spacing: 0.2em;
  color: var(--home-muted);
}

.home-nav-menu {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  flex: 1;
  flex-wrap: wrap;
}

.home-nav-link {
  padding: 10px 14px;
  border-radius: 999px;
  color: var(--home-muted);
  text-decoration: none;
  font-size: 0.92rem;
  transition: transform 0.28s ease, background 0.28s ease, color 0.28s ease, box-shadow 0.28s ease;
}

.home-nav-link:hover {
  color: var(--home-primary);
  background: rgba(var(--home-primary-rgb), 0.08);
  transform: translateY(-1px);
}

.home-nav-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.home-user-chip,
.home-nav-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 40px;
  padding: 0 16px;
  border-radius: 999px;
  font-size: 0.88rem;
  font-weight: 700;
  text-decoration: none;
}

.home-user-chip {
  color: var(--home-primary);
  border: 1px solid rgba(var(--home-primary-rgb), 0.12);
  background: rgba(255, 255, 255, 0.48);
}

.home-nav-btn-ghost {
  color: var(--home-accent);
  border: 1px solid rgba(var(--home-accent-rgb), 0.34);
  background: rgba(255, 255, 255, 0.42);
}

.home-nav-btn-solid {
  color: #fff;
  border: 1px solid rgba(var(--home-primary-rgb), 0.12);
  background: linear-gradient(135deg, var(--home-primary), rgba(var(--home-primary-rgb), 0.88));
  box-shadow: 0 14px 28px rgba(var(--home-primary-rgb), 0.22);
}

.home-main {
  margin-top: 24px;
  display: flex;
  flex-direction: column;
  gap: 28px;
}

.hero-section {
  display: block;
}

.hero-copy-card,
.hero-visual,
.feature-card {
  position: relative;
  overflow: hidden;
  border-radius: 32px;
  border: 1px solid rgba(var(--home-primary-rgb), 0.14);
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.58), rgba(255, 255, 255, 0.16)),
    rgba(255, 255, 255, 0.42);
  box-shadow: var(--card-shadow);
  backdrop-filter: blur(14px);
}

.hero-copy-card::before,
.hero-visual::before,
.feature-card::before {
  content: '';
  position: absolute;
  inset: 14px;
  border-radius: 24px;
  border: 1px solid rgba(var(--home-primary-rgb), 0.08);
  pointer-events: none;
}

.hero-copy-card {
  padding: 34px;
}

.hero-card-grid {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(300px, 0.82fr);
  gap: 24px;
  align-items: center;
}

.hero-copy-main {
  min-width: 0;
}

.hero-copy-card::after {
  content: '';
  position: absolute;
  left: 24px;
  right: 24px;
  bottom: 18px;
  height: 110px;
  border-radius: 999px 999px 18px 18px;
  background:
    radial-gradient(circle at 22% 100%, rgba(var(--home-primary-rgb), 0.16), transparent 36%),
    radial-gradient(circle at 74% 100%, rgba(var(--home-accent-rgb), 0.12), transparent 32%);
  opacity: 0.82;
}

.hero-kicker,
.section-kicker,
.theme-panel-kicker {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 0 14px;
  border-radius: 999px;
  background: rgba(var(--home-primary-rgb), 0.08);
  border: 1px solid rgba(var(--home-primary-rgb), 0.12);
  color: var(--home-primary);
  font-size: 0.76rem;
  letter-spacing: 0.14em;
  font-weight: 700;
}

.hero-title {
  margin: 18px 0 0;
  font-size: clamp(2.5rem, 5vw, 4.2rem);
  line-height: 1.08;
  letter-spacing: 0.02em;
  font-weight: 800;
}

.hero-subtitle {
  margin: 18px 0 0;
  max-width: 760px;
  color: var(--home-muted);
  font-size: 1.02rem;
  line-height: 1.92;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 24px;
}

.hero-cta {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 48px;
  padding: 0 22px;
  border-radius: 999px;
  font-weight: 700;
  text-decoration: none;
  transition: transform 0.28s ease, box-shadow 0.28s ease, background 0.28s ease;
}

.hero-cta:hover,
.feature-card:hover {
  transform: translateY(-4px);
}

.hero-cta-solid {
  color: #fff;
  background: linear-gradient(135deg, var(--home-primary), rgba(var(--home-primary-rgb), 0.88));
  box-shadow: 0 16px 32px rgba(var(--home-primary-rgb), 0.24);
}

.hero-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 26px;
}

.hero-stat-item {
  position: relative;
  z-index: 1;
  padding: 18px 16px;
  border-radius: 22px;
  border: 1px solid rgba(var(--home-primary-rgb), 0.1);
  background: rgba(255, 255, 255, 0.42);
}

.hero-stat-item strong {
  display: block;
  font-size: 1.28rem;
  color: var(--home-primary);
}

.hero-stat-item span {
  display: block;
  margin-top: 6px;
  font-size: 0.88rem;
  color: var(--home-muted);
}

.hero-visual {
  min-height: 320px;
  padding: 22px;
  border-radius: 28px;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.62), rgba(255, 255, 255, 0.18)),
    rgba(255, 255, 255, 0.36);
  box-shadow: 0 16px 34px rgba(var(--home-primary-rgb), 0.08);
}

.visual-map-layer,
.visual-route,
.visual-poi,
.visual-mountain,
.visual-scroll-tag {
  position: absolute;
}

.visual-map-layer {
  inset: 18px;
  border-radius: 24px;
  background:
    radial-gradient(circle at 18% 22%, rgba(var(--home-primary-rgb), 0.12), transparent 22%),
    radial-gradient(circle at 82% 26%, rgba(var(--home-accent-rgb), 0.1), transparent 20%),
    linear-gradient(105deg, transparent 0 24%, rgba(var(--home-primary-rgb), 0.05) 24% 24.3%, transparent 24.3% 100%),
    linear-gradient(0deg, transparent 0 58%, rgba(var(--home-accent-rgb), 0.04) 58% 58.2%, transparent 58.2% 100%);
}

.visual-route {
  border-top: 2px dashed rgba(var(--home-primary-rgb), 0.34);
  border-radius: 999px;
}

.route-one {
  width: 54%;
  height: 90px;
  left: 14%;
  top: 24%;
  transform: rotate(-12deg);
}

.route-two {
  width: 48%;
  height: 100px;
  right: 12%;
  bottom: 22%;
  transform: rotate(12deg);
}

.visual-poi {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--home-accent);
  box-shadow: 0 0 0 8px rgba(var(--home-accent-rgb), 0.12);
}

.poi-one {
  left: 20%;
  top: 38%;
}

.poi-two {
  right: 22%;
  top: 30%;
}

.poi-three {
  right: 28%;
  bottom: 28%;
}

.visual-mountain {
  bottom: 12%;
  border-radius: 100% 100% 0 0;
  background: linear-gradient(180deg, rgba(var(--home-primary-rgb), 0.16), rgba(var(--home-primary-rgb), 0.04));
}

.mountain-one {
  left: 10%;
  width: 42%;
  height: 110px;
}

.mountain-two {
  right: 8%;
  width: 48%;
  height: 136px;
}

.visual-scroll-tag {
  left: 28px;
  top: 24px;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 0 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.58);
  border: 1px solid rgba(var(--home-primary-rgb), 0.1);
  font-size: 0.74rem;
  letter-spacing: 0.14em;
  font-weight: 700;
}

.feature-section {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.section-heading {
  max-width: 760px;
}

.section-heading h2 {
  margin: 14px 0 0;
  font-size: clamp(1.8rem, 3vw, 2.5rem);
  line-height: 1.18;
}

.section-heading p {
  margin: 12px 0 0;
  color: var(--home-muted);
  line-height: 1.86;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.feature-card {
  display: flex;
  flex-direction: column;
  min-height: 100%;
  transition: transform 0.32s ease, box-shadow 0.32s ease, border-color 0.32s ease;
}

.feature-card:hover {
  box-shadow: var(--card-shadow-hover);
}

.feature-card::after {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 84% 18%, rgba(var(--home-accent-rgb), 0.12), transparent 18%),
    radial-gradient(circle at 16% 86%, rgba(var(--home-primary-rgb), 0.12), transparent 20%);
  opacity: 0.72;
  pointer-events: none;
}

.feature-visual,
.feature-body {
  position: relative;
  z-index: 1;
}

.feature-visual {
  min-height: 190px;
  padding: 24px 24px 18px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.feature-icon {
  width: 100%;
  max-width: 180px;
  height: 120px;
  color: var(--home-primary);
}

.feature-card[data-variant='agent'] .feature-icon {
  color: var(--home-accent);
}

.feature-card[data-variant='workflow'] .feature-icon {
  color: rgba(var(--home-primary-rgb), 0.92);
}

.feature-body {
  padding: 0 24px 24px;
  display: flex;
  flex-direction: column;
  flex: 1;
}

.feature-body h3 {
  margin: 0;
  font-size: 1.4rem;
}

.feature-body p {
  margin: 12px 0 0;
  color: var(--home-muted);
  line-height: 1.82;
}

.feature-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 18px;
}

.feature-tag {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid rgba(var(--home-primary-rgb), 0.12);
  background: rgba(255, 255, 255, 0.42);
  font-size: 0.8rem;
  color: var(--home-primary);
}

.feature-link {
  margin-top: auto;
  padding-top: 18px;
  color: var(--home-accent);
  font-weight: 700;
  text-decoration: none;
}

@media (max-width: 1120px) {
  .hero-card-grid,
  .feature-grid {
    grid-template-columns: 1fr;
  }

  .hero-visual {
    min-height: 280px;
  }
}

@media (max-width: 900px) {
  .home-nav {
    flex-wrap: wrap;
    justify-content: center;
  }

  .home-nav-actions {
    width: 100%;
    justify-content: center;
  }

  .hero-stats,
  .feature-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .home-shell {
    width: min(100%, calc(100% - 20px));
    padding: 12px 0 48px;
  }

  .home-nav,
  .hero-copy-card,
  .hero-visual,
  .feature-card {
    border-radius: 24px;
  }

  .hero-copy-card {
    padding: 20px;
  }

  .hero-visual {
    min-height: 280px;
    padding: 20px;
  }

  .feature-visual,
  .feature-body {
    padding-left: 20px;
    padding-right: 20px;
  }

  .feature-body {
    padding-bottom: 20px;
  }

  .hero-title {
    font-size: 2.2rem;
  }
}
</style>
