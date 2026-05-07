<script setup>
const apps = [
  {
    name: 'Java 面试陪练官',
    desc: '一对一模拟面试：考官提问 → 你作答 → 点评 + 追问。自动生成会话 ID记录面试过程，支持多轮流式对话，还原真实面试节奏。',
    to: '/love',
    accent: 'love',
    tag: '模拟面试',
  },
  {
    name: '面试题拆解智能体',
    desc: '针对一道题输出结构化拆解：考点定位 / 答题框架 / 核心知识点 / 易错点追问 / 一句话总结。适合八股、场景题、架构设计。',
    to: '/manus',
    accent: 'manus',
    tag: '题目拆解',
  },
]
</script>

<template>
  <div class="home">
    <section class="hero-card">
      <div class="hero-copy">
        <span class="hero-badge">Ruby AI 工作台</span>
        <h1 class="home-title">Java 面试一站式备战工作台</h1>
        <p class="home-sub">
          两个 AI 能力覆盖面试全流程：一个负责跟你一轮轮模拟面试、点评追问，另一个负责把题拆成可背可写的结构化答案。
        </p>
        <div class="hero-meta">
          <div class="meta-item">
            <strong>2</strong>
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
          <p class="panel-text">想练“临场反应 + 表达”选面试陪练官；想快速吃透一道面试题的考点与答题框架，选题目拆解智能体。</p>
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
    grid-template-columns: 1fr 1fr;
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
