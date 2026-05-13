<script setup>
const apps = [
  {
    name: '旅行咨询',
    desc: '多轮对话型旅游助手，结合 RAG 攻略库，实时流式回复。适合咨询行程、签证、避坑、酒店与美食推荐。',
    to: '/travel',
    accent: 'love',
    tag: '旅行咨询',
  },
  {
    name: '规划智能体',
    desc: '面向复杂旅行任务的 ReAct 智能体，自主搜索 / 抓取攻略 / 行程编排 / 预算核算 / 生成 PDF 行程手册。',
    to: '/planner',
    accent: 'manus',
    tag: '规划智能体',
  },
  {
    name: '工作流规划',
    desc: '基于 LangGraph4j 有向图工作流：需求解析 → 天气/景点/酒店/航班信息增强 → 行程编排 → 预算核算 → 完整方案。',
    to: '/workflow',
    accent: 'workflow',
    tag: '工作流',
  },
]
</script>

<template>
  <div class="home">
    <section class="hero-card">
      <div class="hero-copy">
        <div class="hero-topbar">
          <span class="hero-badge">行旅 AI · 工作台</span>
          <div class="hero-auth-actions">
            <router-link to="/login" class="hero-auth-link hero-auth-link-ghost">登录</router-link>
            <router-link to="/register" class="hero-auth-link hero-auth-link-solid">注册</router-link>
          </div>
        </div>
        <h1 class="home-title">你的专属旅游 AI 助手，从咨询到规划一站搞定</h1>
        <p class="home-sub">
          基于 Spring AI 与 ReAct 智能体架构打造的旅游垂直 AI：左侧入口用于多轮旅行咨询（含 RAG 攻略库），右侧入口用于多步行程规划与 PDF 行程手册生成。
        </p>
        <div class="hero-meta">
          <div class="meta-item">
            <strong>3</strong>
            <span>已接入应用</span>
          </div>
          <div class="meta-item">
            <strong>SSE</strong>
            <span>流式输出</span>
          </div>
          <div class="meta-item">
            <strong>Vue 3</strong>
            <span>轻量前端架构</span>
          </div>
        </div>
      </div>

      <div class="hero-panel">
        <div class="panel-card">
          <span class="panel-title">使用建议</span>
          <p class="panel-text">日常旅行咨询、攻略问答 → 选「旅行咨询」；多日复杂行程定制、需要生成 PDF 手册 → 选「规划智能体」；一键生成完整规划方案 → 选「工作流规划」。</p>
        </div>
      </div>
    </section>

    <section class="section-head">
      <div>
        <h2 class="section-title">选择应用</h2>
        <p class="section-desc">点击卡片进入对应对话界面。</p>
      </div>
    </section>

    <ul class="app-grid">
      <li v-for="app in apps" :key="app.to">
        <router-link :to="app.to" class="app-card" :data-accent="app.accent">
          <div class="app-card-top">
            <span class="app-tag">{{ app.tag }}</span>
            <span class="app-arrow">→</span>
          </div>
          <h2 class="app-name">{{ app.name }}</h2>
          <p class="app-desc">{{ app.desc }}</p>
          <div class="app-footer">
            <span class="app-cta">立即进入</span>
            <span class="app-tip">实时对话</span>
          </div>
        </router-link>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.home {
  min-height: 100vh;
  padding: 40px 24px 72px;
  max-width: 1180px;
  margin: 0 auto;
}

.hero-card {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(280px, 0.9fr);
  gap: 24px;
  padding: 28px;
  border: 1px solid var(--border);
  border-radius: 28px;
  background: linear-gradient(180deg, rgba(255, 251, 246, 0.92), rgba(250, 241, 231, 0.78));
  box-shadow: var(--card-shadow);
  backdrop-filter: blur(12px);
  margin-bottom: 28px;
}

.hero-copy {
  min-width: 0;
}

.hero-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--accent-strong);
  font-size: 0.86rem;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.hero-auth-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.hero-auth-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 38px;
  padding: 0 16px;
  border-radius: 999px;
  text-decoration: none;
  font-size: 0.88rem;
  font-weight: 700;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}

.hero-auth-link:hover {
  transform: translateY(-1px);
}

.hero-auth-link-ghost {
  color: var(--accent-strong);
  border: 1px solid rgba(112, 87, 67, 0.16);
  background: rgba(255, 255, 255, 0.52);
}

.hero-auth-link-solid {
  color: #fff;
  background: linear-gradient(135deg, var(--accent), var(--accent-strong));
  box-shadow: 0 14px 24px rgba(110, 68, 45, 0.16);
}

.home-title {
  margin: 16px 0 0;
  font-size: clamp(2rem, 4vw, 3.4rem);
  line-height: 1.12;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--text);
}

.home-sub {
  margin: 16px 0 0;
  color: var(--muted);
  font-size: 1.02rem;
  line-height: 1.75;
  max-width: 720px;
}

.hero-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 24px;
}

.meta-item {
  padding: 16px 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.48);
  border: 1px solid rgba(112, 87, 67, 0.1);
}

.meta-item strong {
  display: block;
  font-size: 1.12rem;
  color: var(--text);
}

.meta-item span {
  display: block;
  margin-top: 4px;
  color: var(--muted);
  font-size: 0.88rem;
}

.hero-panel {
  display: flex;
}

.panel-card {
  width: 100%;
  padding: 22px;
  border-radius: 24px;
  border: 1px solid rgba(112, 87, 67, 0.12);
  background: rgba(255, 254, 252, 0.72);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.55);
}

.panel-title {
  display: block;
  font-size: 0.92rem;
  font-weight: 700;
  color: var(--accent-strong);
}

.panel-text {
  margin: 12px 0 0;
  color: var(--muted);
  line-height: 1.75;
  font-size: 0.95rem;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}

.section-title {
  margin: 0;
  font-size: 1.18rem;
  font-weight: 700;
}

.section-desc {
  margin: 8px 0 0;
  color: var(--muted);
  font-size: 0.92rem;
}

.app-grid {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 22px;
}

@media (min-width: 740px) {
  .app-grid {
    grid-template-columns: 1fr 1fr 1fr;
  }
}

.app-card {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 24px;
  border-radius: 24px;
  text-decoration: none;
  color: inherit;
  background: var(--surface-elevated);
  border: 1px solid var(--border);
  box-shadow: var(--card-shadow);
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}

.app-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--card-shadow-hover);
}

.app-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.app-tag {
  display: inline-flex;
  align-items: center;
  height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  background: var(--surface-soft);
  color: var(--accent-strong);
  font-size: 0.78rem;
  font-weight: 700;
}

.app-arrow {
  font-size: 1.3rem;
  color: var(--muted);
}

.app-card[data-accent='love']:hover {
  border-color: rgba(164, 87, 73, 0.38);
}

.app-card[data-accent='manus']:hover {
  border-color: rgba(91, 122, 97, 0.34);
}

.app-card[data-accent='workflow']:hover {
  border-color: rgba(64, 158, 255, 0.38);
}

.app-name {
  margin: 0 0 10px;
  font-size: 1.3rem;
  font-weight: 700;
}

.app-desc {
  margin: 0 0 24px;
  flex: 1;
  font-size: 0.95rem;
  color: var(--muted);
  line-height: 1.7;
}

.app-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.app-cta {
  font-size: 0.92rem;
  font-weight: 700;
  color: var(--accent-strong);
}

.app-tip {
  color: var(--muted);
  font-size: 0.84rem;
}

@media (max-width: 860px) {
  .hero-card {
    grid-template-columns: 1fr;
  }

  .hero-meta {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .home {
    padding: 20px 16px 40px;
  }

  .hero-card {
    padding: 20px;
    border-radius: 22px;
  }
}
</style>
