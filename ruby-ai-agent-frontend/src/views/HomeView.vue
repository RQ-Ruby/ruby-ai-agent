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

const featureCards = [
  {
    key: 'chat',
    title: '旅行咨询',
    to: '/travel',
    desc: '面向国内文旅目的地的对话咨询入口，结合 RAG 攻略库与流式回复能力，适合询问景点、美食、住宿、交通、避坑与出行建议。',
    tags: ['RAG 攻略库', 'SSE 实时流式', '多轮咨询', '目的地问答'],
  },
  {
    key: 'agent',
    title: '规划智能体',
    to: '/planner',
    desc: '面向复杂行程需求的 ReAct 规划智能体，可整合攻略信息、编排行程路线、估算预算，并在你明确需要时生成 PDF 国风行程手册。',
    tags: ['自主规划', '预算核算', '攻略整合', 'PDF 行程手册'],
  },
  {
    key: 'workflow',
    title: '工作流规划',
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
  '--home-primary': '#2A4758',
  '--home-primary-rgb': '42, 71, 88',
  '--home-page-bg': '#2A4758',
  '--home-surface': '#F8F5EE',
  '--home-surface-strong': '#FCFAF5',
  '--home-soft': '#EFE7DA',
  '--home-accent': '#915C41',
  '--home-accent-rgb': '145, 92, 65',
  '--home-gold': '#C8B290',
  '--home-gold-rgb': '200, 178, 144',
  '--home-muted': '#6A6E72',
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
  --page-shadow: 0 28px 72px rgba(7, 14, 22, 0.34);
  --card-shadow: 0 24px 52px rgba(7, 14, 22, 0.26);
  --card-shadow-hover: 0 32px 68px rgba(7, 14, 22, 0.34);
  min-height: 100vh;
  position: relative;
  overflow: hidden;
  color: var(--home-surface);
  background:
    radial-gradient(circle at 18% 10%, rgba(248, 245, 238, 0.05), transparent 18%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.02), rgba(0, 0, 0, 0.08)),
    var(--home-page-bg);
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
    radial-gradient(ellipse at 14% 96%, rgba(0, 0, 0, 0.16) 0 16%, transparent 40%),
    radial-gradient(ellipse at 38% 92%, rgba(0, 0, 0, 0.12) 0 14%, transparent 36%),
    radial-gradient(ellipse at 62% 96%, rgba(0, 0, 0, 0.16) 0 18%, transparent 42%),
    linear-gradient(112deg, transparent 0 18%, rgba(var(--home-gold-rgb), 0.08) 18% 18.12%, transparent 18.12% 100%),
    linear-gradient(90deg, transparent 0 8%, rgba(248, 245, 238, 0.04) 8% 8.08%, transparent 8.08% 100%);
  opacity: 0.62;
}

.home-page::after {
  background:
    radial-gradient(circle at 84% 18%, rgba(var(--home-gold-rgb), 0.1), transparent 24%),
    radial-gradient(circle at 18% 22%, rgba(248, 245, 238, 0.05), transparent 20%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.04), transparent 24%);
  opacity: 0.9;
}

.home-page-bg {
  background:
    radial-gradient(circle at 22% 76%, rgba(248, 245, 238, 0.04), transparent 22%),
    radial-gradient(circle at 86% 72%, rgba(var(--home-gold-rgb), 0.08), transparent 18%),
    linear-gradient(135deg, transparent 0 40%, rgba(248, 245, 238, 0.03) 40% 40.08%, transparent 40.08% 100%);
}

.home-shell {
  position: relative;
  z-index: 1;
  width: min(1280px, calc(100% - 32px));
  margin: 0 auto;
  padding: 24px 0 72px;
}

.home-nav,
.hero-copy-card,
.hero-visual,
.feature-card {
  position: relative;
  overflow: hidden;
  border-radius: 30px;
  border: 1px solid rgba(var(--home-gold-rgb), 0.58);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.3), rgba(255, 255, 255, 0.06)),
    var(--home-surface);
  box-shadow: var(--card-shadow);
  color: var(--home-primary);
}

.home-nav::before,
.hero-copy-card::before,
.hero-visual::before,
.feature-card::before {
  content: '';
  position: absolute;
  inset: 10px;
  border-radius: 22px;
  border: 1px solid rgba(var(--home-gold-rgb), 0.28);
  background:
    linear-gradient(rgba(var(--home-gold-rgb), 0.82), rgba(var(--home-gold-rgb), 0.82)) 18px 18px / 24px 1px no-repeat,
    linear-gradient(rgba(var(--home-gold-rgb), 0.82), rgba(var(--home-gold-rgb), 0.82)) 18px 18px / 1px 24px no-repeat,
    linear-gradient(rgba(var(--home-gold-rgb), 0.82), rgba(var(--home-gold-rgb), 0.82)) calc(100% - 18px) 18px / 24px 1px no-repeat,
    linear-gradient(rgba(var(--home-gold-rgb), 0.82), rgba(var(--home-gold-rgb), 0.82)) calc(100% - 18px) 18px / 1px 24px no-repeat,
    linear-gradient(rgba(var(--home-gold-rgb), 0.82), rgba(var(--home-gold-rgb), 0.82)) 18px calc(100% - 18px) / 24px 1px no-repeat,
    linear-gradient(rgba(var(--home-gold-rgb), 0.82), rgba(var(--home-gold-rgb), 0.82)) 18px calc(100% - 18px) / 1px 24px no-repeat,
    linear-gradient(rgba(var(--home-gold-rgb), 0.82), rgba(var(--home-gold-rgb), 0.82)) calc(100% - 18px) calc(100% - 18px) / 24px 1px no-repeat,
    linear-gradient(rgba(var(--home-gold-rgb), 0.82), rgba(var(--home-gold-rgb), 0.82)) calc(100% - 18px) calc(100% - 18px) / 1px 24px no-repeat;
  pointer-events: none;
  opacity: 0.88;
}

.home-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 14px 20px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.34), rgba(255, 255, 255, 0.08)),
    var(--home-surface-strong);
  box-shadow: var(--page-shadow);
}

.home-logo {
  display: inline-flex;
  flex-direction: column;
  gap: 4px;
  text-decoration: none;
  color: inherit;
}

.home-logo-mark,
.hero-title,
.section-heading h2,
.feature-body h3 {
  font-family: 'STKaiti', 'KaiTi', 'Songti SC', 'Noto Serif SC', serif;
}

.home-logo-sub,
.home-nav-link,
.hero-subtitle,
.section-heading p,
.feature-body p,
.feature-tag,
.feature-link,
.home-user-chip,
.home-nav-btn {
  font-family: 'STSong', 'Songti SC', 'Microsoft YaHei', sans-serif;
}

.home-logo-mark {
  display: inline-flex;
  align-items: center;
  min-height: 42px;
  padding: 0 18px;
  border-radius: 999px;
  border: 1px solid rgba(var(--home-gold-rgb), 0.62);
  background: rgba(248, 245, 238, 0.96);
  font-size: 1.04rem;
  font-weight: 800;
  letter-spacing: 0.18em;
}

.home-logo-sub {
  padding-left: 8px;
  font-size: 0.66rem;
  letter-spacing: 0.22em;
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
  padding: 10px 16px;
  border-radius: 999px;
  color: var(--home-primary);
  text-decoration: none;
  font-size: 0.92rem;
  transition: transform 0.28s ease, background 0.28s ease, box-shadow 0.28s ease, color 0.28s ease;
}

.home-nav-link:hover,
.home-nav-link.router-link-active,
.home-nav-link.router-link-exact-active {
  color: var(--home-primary);
  background: rgba(var(--home-primary-rgb), 0.07);
  box-shadow: inset 0 0 0 1px rgba(var(--home-gold-rgb), 0.32);
  transform: translateY(-1px);
}

.home-nav-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.home-user-chip,
.home-nav-btn,
.hero-cta,
.feature-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 42px;
  padding: 0 18px;
  border-radius: 999px;
  font-size: 0.9rem;
  font-weight: 700;
  text-decoration: none;
  transition: transform 0.28s ease, box-shadow 0.28s ease, border-color 0.28s ease, background 0.28s ease;
}

.home-user-chip {
  color: var(--home-primary);
  border: 1px solid rgba(var(--home-gold-rgb), 0.4);
  background: rgba(248, 245, 238, 0.96);
}

.home-nav-btn-ghost {
  color: var(--home-accent);
  border: 1px solid rgba(var(--home-gold-rgb), 0.5);
  background: rgba(248, 245, 238, 0.92);
}

.home-nav-btn-solid,
.hero-cta-solid {
  color: var(--home-surface);
  border: 1px solid rgba(var(--home-gold-rgb), 0.62);
  background: linear-gradient(135deg, rgba(var(--home-primary-rgb), 0.96), var(--home-primary));
  box-shadow:
    inset 0 0 0 1px rgba(var(--home-gold-rgb), 0.18),
    0 14px 28px rgba(7, 14, 22, 0.22);
}

.home-nav-btn:hover,
.hero-cta:hover,
.feature-link:hover {
  transform: translateY(-2px);
  box-shadow:
    inset 0 0 0 1px rgba(var(--home-gold-rgb), 0.28),
    0 0 22px rgba(var(--home-gold-rgb), 0.16),
    0 18px 30px rgba(7, 14, 22, 0.24);
}

.home-main {
  margin-top: 24px;
  display: flex;
  flex-direction: column;
  gap: 34px;
}

.hero-section {
  display: block;
}

.hero-copy-card {
  padding: 34px;
}

.hero-copy-card::after {
  content: '';
  position: absolute;
  inset: auto 30px 18px 30px;
  height: 180px;
  border-radius: 120px 120px 24px 24px;
  background:
    radial-gradient(circle at 18% 100%, rgba(var(--home-primary-rgb), 0.12), transparent 36%),
    radial-gradient(circle at 48% 100%, rgba(var(--home-primary-rgb), 0.08), transparent 34%),
    radial-gradient(circle at 82% 100%, rgba(var(--home-accent-rgb), 0.08), transparent 30%);
  opacity: 0.9;
  pointer-events: none;
}

.hero-card-grid,
.feature-grid {
  position: relative;
  z-index: 1;
}

.hero-card-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.18fr) minmax(320px, 0.82fr);
  gap: 30px;
  align-items: center;
}

.hero-copy-main {
  min-width: 0;
}

.hero-kicker {
  display: inline-flex;
  align-items: center;
  min-height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid rgba(var(--home-gold-rgb), 0.48);
  background: rgba(248, 245, 238, 0.96);
  color: var(--home-primary);
  font-size: 0.76rem;
  letter-spacing: 0.14em;
  font-weight: 700;
}

.hero-title {
  margin: 20px 0 0;
  font-size: clamp(2.7rem, 5vw, 4.5rem);
  line-height: 1.06;
  letter-spacing: 0.04em;
  font-weight: 800;
  color: var(--home-primary);
}

.hero-subtitle {
  margin: 22px 0 0;
  max-width: 760px;
  color: var(--home-muted);
  font-size: 1.02rem;
  line-height: 2;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 28px;
}

.hero-cta {
  min-height: 50px;
  padding: 0 24px;
}

.hero-visual {
  min-height: 360px;
  padding: 0;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.26), rgba(255, 255, 255, 0.04)),
    rgba(248, 245, 238, 0.98);
}

.visual-map-layer,
.visual-route,
.visual-poi,
.visual-mountain {
  position: absolute;
}

.visual-map-layer {
  inset: 18px;
  overflow: hidden;
  border-radius: 22px;
  border: 1px solid rgba(var(--home-gold-rgb), 0.32);
  background:
    radial-gradient(circle at 22% 24%, rgba(var(--home-accent-rgb), 0.12), transparent 16%),
    radial-gradient(circle at 76% 28%, rgba(var(--home-gold-rgb), 0.18), transparent 18%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.34), rgba(255, 255, 255, 0.08)),
    rgba(var(--home-primary-rgb), 0.06);
}

.visual-map-layer::before,
.visual-map-layer::after {
  content: '';
  position: absolute;
  left: -10%;
  right: -10%;
  bottom: -8%;
  pointer-events: none;
}

.visual-map-layer::before {
  height: 62%;
  background:
    radial-gradient(ellipse at 18% 100%, rgba(var(--home-primary-rgb), 0.34) 0 30%, transparent 30%),
    radial-gradient(ellipse at 48% 100%, rgba(var(--home-primary-rgb), 0.22) 0 28%, transparent 28%),
    radial-gradient(ellipse at 80% 100%, rgba(var(--home-primary-rgb), 0.3) 0 32%, transparent 32%);
  opacity: 0.44;
}

.visual-map-layer::after {
  left: 8%;
  right: 8%;
  bottom: 20%;
  height: 24%;
  background:
    radial-gradient(ellipse at 30% 60%, rgba(255, 255, 255, 0.52), transparent 40%),
    radial-gradient(ellipse at 72% 68%, rgba(255, 255, 255, 0.4), transparent 38%);
  filter: blur(12px);
  opacity: 0.84;
}

.visual-route {
  border-top: 1px dashed rgba(var(--home-gold-rgb), 0.52);
  border-radius: 999px;
  opacity: 0.88;
}

.route-one {
  width: 54%;
  height: 90px;
  left: 16%;
  top: 16%;
  transform: rotate(-10deg);
}

.route-two {
  width: 46%;
  height: 96px;
  right: 10%;
  bottom: 24%;
  transform: rotate(10deg);
}

.visual-poi {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--home-accent);
  box-shadow: 0 0 0 7px rgba(var(--home-gold-rgb), 0.22);
}

.poi-one {
  left: 22%;
  top: 34%;
}

.poi-two {
  right: 18%;
  top: 24%;
}

.poi-three {
  right: 24%;
  bottom: 26%;
}

.visual-mountain {
  bottom: 12%;
  border-radius: 100% 100% 0 0;
  background: linear-gradient(180deg, rgba(var(--home-primary-rgb), 0.2), rgba(var(--home-primary-rgb), 0.05));
}

.mountain-one {
  left: 12%;
  width: 40%;
  height: 110px;
}

.mountain-two {
  right: 10%;
  width: 44%;
  height: 132px;
}

.feature-section {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.section-heading {
  max-width: 860px;
  color: var(--home-surface);
}

.section-kicker {
  display: inline-flex;
  align-items: center;
  min-height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid rgba(var(--home-gold-rgb), 0.42);
  background: rgba(248, 245, 238, 0.08);
  color: var(--home-surface);
  font-size: 0.76rem;
  letter-spacing: 0.14em;
  font-weight: 700;
}

.section-heading h2 {
  margin: 16px 0 0;
  font-size: clamp(1.9rem, 3vw, 2.7rem);
  line-height: 1.22;
  color: var(--home-surface);
}

.section-heading p {
  margin: 14px 0 0;
  color: rgba(248, 245, 238, 0.74);
  line-height: 1.92;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 22px;
}

.feature-card {
  display: flex;
  flex-direction: column;
  min-height: 100%;
  transition: transform 0.34s ease, box-shadow 0.34s ease, border-color 0.34s ease;
}

.feature-card::after {
  content: '';
  position: absolute;
  inset: auto -10% -30% auto;
  width: 180px;
  height: 180px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(var(--home-gold-rgb), 0.22), transparent 64%);
  opacity: 0.44;
  transition: opacity 0.34s ease, transform 0.34s ease;
  pointer-events: none;
}

.feature-card:hover {
  transform: translateY(-6px);
  box-shadow:
    0 0 0 1px rgba(var(--home-gold-rgb), 0.2) inset,
    var(--card-shadow-hover),
    0 0 24px rgba(var(--home-gold-rgb), 0.14);
}

.feature-card:hover::after {
  opacity: 0.7;
  transform: scale(1.08);
}

.feature-visual,
.feature-body {
  position: relative;
  z-index: 1;
}

.feature-visual {
  min-height: 210px;
  padding: 28px 26px 20px;
  display: flex;
  align-items: flex-start;
  position: relative;
  overflow: hidden;
}

.feature-visual::before,
.feature-visual::after {
  content: '';
  position: absolute;
  pointer-events: none;
}

.feature-visual::before {
  inset: auto 18px 14px 18px;
  height: 62px;
  border-radius: 100% 100% 0 0;
  background: linear-gradient(180deg, rgba(var(--home-primary-rgb), 0.16), rgba(var(--home-primary-rgb), 0.05));
  opacity: 0.44;
}

.feature-visual[data-variant='chat']::after {
  right: 18px;
  top: 20px;
  width: 94px;
  height: 94px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(var(--home-accent-rgb), 0.1), transparent 70%);
}

.feature-visual[data-variant='agent']::after {
  right: 18px;
  top: 18px;
  width: 108px;
  height: 108px;
  border-radius: 50%;
  border: 1px solid rgba(var(--home-gold-rgb), 0.38);
  box-shadow: 0 0 0 16px rgba(var(--home-gold-rgb), 0.06);
}

.feature-visual[data-variant='workflow']::after {
  right: 20px;
  bottom: 26px;
  width: 120px;
  height: 70px;
  border-radius: 999px;
  border-top: 1px dashed rgba(var(--home-gold-rgb), 0.48);
  border-right: 1px dashed rgba(var(--home-gold-rgb), 0.48);
  transform: rotate(-8deg);
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
  padding: 0 26px 26px;
  display: flex;
  flex-direction: column;
  flex: 1;
}

.feature-body h3 {
  margin: 0;
  font-size: 1.56rem;
  color: var(--home-primary);
}

.feature-body p {
  margin: 14px 0 0;
  color: var(--home-muted);
  line-height: 1.92;
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
  border: 1px solid rgba(var(--home-gold-rgb), 0.4);
  background: rgba(248, 245, 238, 0.92);
  font-size: 0.8rem;
  color: var(--home-primary);
}

.feature-link {
  align-self: flex-start;
  margin-top: auto;
  color: var(--home-accent);
  border: 1px solid rgba(var(--home-gold-rgb), 0.46);
  background: rgba(var(--home-primary-rgb), 0.04);
}

.ink-reveal {
  opacity: 0;
  transform: translateY(28px);
  transition: opacity 0.75s ease, transform 0.75s ease;
}

.ink-reveal.is-visible {
  opacity: 1;
  transform: translateY(0);
}

@media (max-width: 1120px) {
  .hero-card-grid,
  .feature-grid {
    grid-template-columns: 1fr;
  }

  .hero-visual {
    min-height: 320px;
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

  .home-nav::before,
  .hero-copy-card::before,
  .hero-visual::before,
  .feature-card::before {
    inset: 8px;
    border-radius: 18px;
  }

  .hero-copy-card {
    padding: 22px;
  }

  .hero-visual {
    min-height: 280px;
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
    font-size: 2.35rem;
  }
}
</style>
