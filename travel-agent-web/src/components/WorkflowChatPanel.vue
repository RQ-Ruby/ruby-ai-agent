<script setup>
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {marked} from 'marked'
import {buildWorkflowPlanUrl, fetchChatHistory} from '../api/sseUrls.js'
import {useAuthStore} from '../stores/auth.js'

marked.setOptions({breaks: true, gfm: true})

const auth = useAuthStore()

// 用登录用户 id 给 storage key 命名空间，避免不同账号在同一浏览器串档
const storageKey = computed(() => {
  const userId = auth.state.loginUser?.id || 'anonymous'
  return `workflow_chat_id:${userId}`
})

const stageList = [
  {key: 'intent', title: '意图识别', desc: '判断是旅行规划还是普通闲聊'},
  {key: 'extract', title: '参数抽取', desc: '提取目的地、天数、预算、偏好等'},
  {key: 'validate', title: '参数校验', desc: '检查目的地、天数等必填项'},
  {key: 'clarify', title: '缺参反问', desc: '缺少关键信息时主动追问补全'},
  {key: 'rag', title: 'RAG 检索', desc: '查询本地景点、美食、住宿和攻略'},
  {key: 'mcp', title: 'MCP 增强', desc: '调用高德 MCP 获取天气和真实 POI'},
  {key: 'generate', title: '行程生成', desc: '结合知识库 + MCP 信息生成方案'},
  {key: 'memory', title: '记忆保存', desc: '保存会话上下文，支持后续修改'},
]

const examplePrompts = [
  '我想去青岛玩',
  '帮我规划青岛 3 天 2 晚行程',
  '预算调到 2000',
  '换个便宜一点的路线',
]

const chatId = ref('')
const input = ref('')
const turns = ref([])
const loading = ref(false)
const pageError = ref('')
const listRef = ref(null)
const rootRef = ref(null)
let activeController = null
let turnIdSeed = 0
let revealObserver = null

function createTurn(payload = {}) {
  turnIdSeed += 1
  return {
    id: `turn_${Date.now()}_${turnIdSeed}`,
    user: '',
    assistant: '',
    progress: [],
    error: '',
    status: 'done',
    restored: false,
    ...payload,
  }
}

async function ensureChatId() {
  await auth.ensureAuthLoaded()
  let stored = localStorage.getItem(storageKey.value)
  if (!stored) {
    stored = globalThis.crypto?.randomUUID?.() || `workflow-${Date.now()}`
    localStorage.setItem(storageKey.value, stored)
  }
  chatId.value = stored
}

function abortCurrent() {
  activeController?.abort()
  activeController = null
  loading.value = false
}

function startNewSession() {
  abortCurrent()
  const nextId = globalThis.crypto?.randomUUID?.() || `workflow-${Date.now()}`
  localStorage.setItem(storageKey.value, nextId)
  chatId.value = nextId
  turns.value = []
  pageError.value = ''
}

function enhanceLinks(html) {
  if (!html) return ''
  return html.replace(/<a\s+href="([^"]+)"([^>]*)>(.*?)<\/a>/gi, (match, href, attrs, text) => {
    const isPdf = href.includes('/api/files/pdf/') || href.toLowerCase().endsWith('.pdf')
    const safeAttrs = attrs || ''
    const targetAttr = ' target="_blank" rel="noopener noreferrer"'
    const downloadAttr = isPdf ? ' download' : ''
    return `<a href="${href}"${safeAttrs}${targetAttr}${downloadAttr}>${text}</a>`
  })
}

function renderMarkdown(text) {
  if (!text) return ''
  try {
    return enhanceLinks(marked.parse(text))
  } catch {
    return text
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (listRef.value) {
      listRef.value.scrollTop = listRef.value.scrollHeight
    }
  })
}

watch(turns, scrollToBottom, {deep: true})

function normalizeHistory(history) {
  const normalized = []
  let pending = null

  for (const item of history || []) {
    const role = String(item?.role || '').toLowerCase()
    const content = String(item?.content || '').trim()
    if (!content) continue

    if (role === 'user') {
      if (pending) normalized.push(pending)
      pending = createTurn({user: content, restored: true, status: 'done'})
      continue
    }

    if (!pending) {
      normalized.push(createTurn({assistant: content, restored: true, status: 'done'}))
      continue
    }

    pending.assistant = pending.assistant ? `${pending.assistant}\n\n${content}` : content
    normalized.push(pending)
    pending = null
  }

  if (pending) normalized.push(pending)
  return normalized
}

async function loadHistory() {
  try {
    const {data} = await fetchChatHistory(chatId.value)
    turns.value = normalizeHistory(data)
  } catch (error) {
    console.warn('加载工作流历史失败', error)
  }
}

function parseSseBlock(block) {
  const normalized = block.replace(/\r\n/g, '\n').replace(/\r/g, '\n')
  const lines = normalized.split('\n')
  let event = 'message'
  let data = ''

  for (const line of lines) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim() || 'message'
      continue
    }
    if (line.startsWith('data:')) {
      const payload = line.startsWith('data: ') ? line.slice(6) : line.slice(5)
      data += (data ? '\n' : '') + payload
    }
  }

  return {event, data}
}

async function consumeWorkflowSse(url, {signal, onEvent}) {
  const res = await fetch(url, {
    signal,
    headers: {Accept: 'text/event-stream'},
  })

  if (!res.ok) {
    const text = await res.text().catch(() => '')
    throw new Error(text?.trim() || `请求失败 (${res.status})`)
  }

  const reader = res.body?.getReader()
  if (!reader) {
    throw new Error('无法读取工作流响应流')
  }

  const decoder = new TextDecoder()
  let buffer = ''

  try {
    while (true) {
      const {done, value} = await reader.read()
      if (value) buffer += decoder.decode(value, {stream: true})

      let splitIndex = buffer.indexOf('\n\n')
      while (splitIndex !== -1) {
        const rawBlock = buffer.slice(0, splitIndex)
        buffer = buffer.slice(splitIndex + 2)
        if (rawBlock.trim()) {
          const parsed = parseSseBlock(rawBlock)
          onEvent(parsed)
        }
        splitIndex = buffer.indexOf('\n\n')
      }

      if (done) {
        if (buffer.trim()) {
          onEvent(parseSseBlock(buffer))
        }
        break
      }
    }
  } finally {
    reader.releaseLock()
  }
}

function resolveStageKey(progress = []) {
  const latest = progress.at(-1) || ''
  if (!latest) return 'intent'
  if (latest.includes('意图')) return 'intent'
  if (latest.includes('参数抽取')) return 'extract'
  if (latest.includes('校验')) return 'validate'
  if (latest.includes('反问') || latest.includes('缺失')) return 'clarify'
  if (latest.includes('RAG') || latest.includes('知识库')) return 'rag'
  if (latest.includes('MCP') || latest.includes('天气') || latest.includes('POI')) return 'mcp'
  if (latest.includes('行程方案') || latest.includes('行程') || latest.includes('生成')) return 'generate'
  if (latest.includes('记忆') || latest.includes('保存')) return 'memory'
  return 'intent'
}

const currentStageKey = computed(() => {
  const latestTurn = [...turns.value].reverse().find(item => item.progress.length > 0) || null
  return latestTurn ? resolveStageKey(latestTurn.progress) : 'intent'
})

const currentStageIndex = computed(() => {
  return Math.max(stageList.findIndex(item => item.key === currentStageKey.value), 0)
})

function stageState(stageKey, index) {
  if (!loading.value) {
    const latestTurn = turns.value.at(-1)
    if (!latestTurn?.progress?.length) return 'idle'
    const latestIndex = stageList.findIndex(item => item.key === resolveStageKey(latestTurn.progress))
    if (latestTurn.status === 'done' && latestTurn.assistant) {
      return index <= latestIndex ? 'done' : 'idle'
    }
    if (index < latestIndex) return 'done'
    if (stageKey === currentStageKey.value) return 'active'
    return 'idle'
  }

  if (index < currentStageIndex.value) return 'done'
  if (stageKey === currentStageKey.value) return 'active'
  return 'idle'
}

function usePrompt(prompt) {
  input.value = prompt
}

function turnBadge(turn) {
  if (turn.error) return {text: '执行失败', tone: 'error'}
  if (turn.status === 'streaming') return {text: '执行中', tone: 'active'}
  if (turn.assistant.includes('再确认几个信息') || turn.assistant.includes('大概玩几天')) {
    return {text: '等待补充参数', tone: 'pending'}
  }
  if (turn.restored) return {text: '历史记录', tone: 'restored'}
  return {text: '已完成', tone: 'done'}
}

async function sendMessage() {
  const text = input.value.trim()
  if (!text || loading.value) return

  pageError.value = ''
  loading.value = true

  const turn = createTurn({user: text, status: 'streaming'})
  turns.value.push(turn)
  input.value = ''

  const controller = new AbortController()
  activeController = controller
  const url = buildWorkflowPlanUrl(text, chatId.value)

  try {
    await consumeWorkflowSse(url, {
      signal: controller.signal,
      onEvent: ({event, data}) => {
        if (!data) return

        if (event === 'status' || event === 'progress') {
          turn.progress.push(data)
          return
        }

        if (event === 'result') {
          turn.assistant = data
          turn.status = 'done'
          return
        }

        if (event === 'error') {
          turn.error = data
          turn.status = 'error'
          pageError.value = data
        }
      },
    })

    if (turn.status === 'streaming') {
      turn.status = turn.error ? 'error' : 'done'
      if (!turn.assistant && !turn.error) {
        turn.assistant = '（本轮工作流已结束，但没有返回可展示内容，请再试一次）'
      }
    }
  } catch (error) {
    if (error?.name === 'AbortError') {
      turn.status = 'done'
      if (!turn.assistant) {
        turn.assistant = '（你已手动停止本轮执行）'
      }
      return
    }

    const message = error instanceof Error ? error.message : String(error)
    turn.error = message
    turn.status = 'error'
    pageError.value = message
  } finally {
    if (activeController === controller) {
      activeController = null
    }
    loading.value = false
  }
}

function onKeydown(event) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    sendMessage()
  }
}

onMounted(async () => {
  await ensureChatId()
  await loadHistory()

  const nodes = rootRef.value?.querySelectorAll('.ink-reveal') || []
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
      {threshold: 0.12, rootMargin: '0px 0px -36px 0px'},
  )

  nodes.forEach((node, index) => {
    node.style.transitionDelay = `${Math.min(index * 80, 320)}ms`
    revealObserver.observe(node)
  })
})

onBeforeUnmount(() => {
  abortCurrent()
  revealObserver?.disconnect()
  revealObserver = null
})
</script>

<template>
  <div ref="rootRef" class="workflow-page">
    <header class="workflow-hero ink-reveal">
      <div class="workflow-hero-copy">
        <div class="workflow-kicker">Spring AI Alibaba Graph 工作流</div>
        <h1 class="workflow-title">行旅 AI · 工作流规划</h1>
        <p class="workflow-subtitle">
          把旅游规划拆成可追踪的节点：识别意图、补齐参数、检索知识库，再生成可修改的多轮行程。
        </p>
        <div class="workflow-meta">
          <span class="workflow-session">会话 ID：{{ chatId }}</span>
          <span :data-loading="loading" class="workflow-status">{{ loading ? '工作流执行中' : '已就绪' }}</span>
        </div>
      </div>
      <div class="workflow-hero-actions">
        <router-link class="ghost-link" to="/">返回首页</router-link>
        <button :disabled="loading" class="ghost-btn" type="button" @click="startNewSession">新建会话</button>
      </div>
    </header>

    <div class="workflow-layout">
      <aside class="workflow-sidebar">
        <section class="sidebar-card ink-reveal">
          <div class="sidebar-card-head">
            <h2>标准节点</h2>
            <span class="sidebar-chip">8 个节点</span>
          </div>
          <ol class="stage-list">
            <li
                v-for="(stage, index) in stageList"
                :key="stage.key"
                :data-state="stageState(stage.key, index)"
                class="stage-item"
            >
              <span class="stage-index">{{ String(index + 1).padStart(2, '0') }}</span>
              <div class="stage-copy">
                <strong>{{ stage.title }}</strong>
                <p>{{ stage.desc }}</p>
              </div>
            </li>
          </ol>
        </section>

        <section class="sidebar-card ink-reveal">
          <div class="sidebar-card-head">
            <h2>快捷试用</h2>
            <span class="sidebar-chip">常用提示</span>
          </div>
          <div class="prompt-grid">
            <button
                v-for="prompt in examplePrompts"
                :key="prompt"
                :disabled="loading"
                class="prompt-chip"
                type="button"
                @click="usePrompt(prompt)"
            >
              {{ prompt }}
            </button>
          </div>
        </section>
      </aside>

      <section class="workflow-chat-card ink-reveal">
        <div ref="listRef" class="workflow-message-list">
          <div v-if="turns.length === 0" class="workflow-empty">
            <div class="workflow-empty-card ink-reveal">
              <span class="workflow-empty-badge">就绪</span>
              <h2>开始一段工作流对话</h2>
              <p>
                你可以先说“我想去青岛玩”，系统会主动追问天数、预算和偏好；后面再说“改成两天”或“预算调低一点”，会自动沿用历史上下文继续规划。
              </p>
            </div>
          </div>

          <div v-for="turn in turns" :key="turn.id" class="turn-block">
            <div v-if="turn.user" class="message-row user-row">
              <div class="message-bubble user-bubble">
                <span class="message-role">你</span>
                <div class="message-text">{{ turn.user }}</div>
              </div>
            </div>

            <div class="message-row assistant-row">
              <div class="message-bubble assistant-bubble">
                <div class="assistant-head">
                  <span class="message-role">工作流助手</span>
                  <span :data-tone="turnBadge(turn).tone" class="turn-badge">{{ turnBadge(turn).text }}</span>
                </div>

                <details v-if="turn.progress.length > 0" :open="turn.status === 'streaming'" class="turn-progress">
                  <summary>查看本轮节点进度（{{ turn.progress.length }}）</summary>

                  <li v-for="(item, index) in turn.progress" :key="`${turn.id}_${index}`">{{ item }}
                  </li>
                </details>

                <div v-if="turn.error" class="turn-error">{{ turn.error }}</div>

                <div
                    v-if="turn.assistant"
                    class="message-text markdown-body"
                    v-html="renderMarkdown(turn.assistant)"
                />

                <div v-else-if="turn.status === 'streaming'" class="assistant-thinking">
                  正在执行节点并等待结果返回…
                </div>
              </div>
            </div>
          </div>
        </div>

        <p v-if="pageError" class="page-error">{{ pageError }}</p>

        <footer class="workflow-composer">
          <textarea
              v-model="input"
              :disabled="loading"
              class="workflow-input"
              placeholder="继续对话，例如：我想去青岛玩 / 改成两天 / 预算调到 2000 / 多加点海边景点"
              rows="3"
              @keydown="onKeydown"
          />
          <div class="workflow-composer-actions">
            <button v-if="loading" class="stop-btn" type="button" @click="abortCurrent">停止执行</button>
            <button :disabled="loading || !input.trim()" class="send-btn" type="button" @click="sendMessage">
              {{ loading ? '执行中…' : '发送' }}
            </button>
          </div>
        </footer>
      </section>
    </div>
  </div>
</template>

<style scoped>
.workflow-page {
  max-width: 1280px;
  margin: 0 auto;
  padding: 24px;
  min-height: 100vh;
}

.workflow-hero {
  position: relative;
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 28px 30px;
  border: 1px solid var(--border-strong);
  border-radius: 32px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.3), rgba(255, 255, 255, 0.06)),
  var(--surface);
  box-shadow: var(--card-shadow);
  overflow: hidden;
}

.workflow-hero::before,
.workflow-hero::after {
  content: '';
  position: absolute;
  pointer-events: none;
}

.workflow-hero::before {
  inset: 12px;
  background: linear-gradient(rgba(var(--gold-rgb), 0.82), rgba(var(--gold-rgb), 0.82)) 18px 18px / 24px 1px no-repeat,
  linear-gradient(rgba(var(--gold-rgb), 0.82), rgba(var(--gold-rgb), 0.82)) 18px 18px / 1px 24px no-repeat,
  linear-gradient(rgba(var(--gold-rgb), 0.82), rgba(var(--gold-rgb), 0.82)) calc(100% - 18px) 18px / 24px 1px no-repeat,
  linear-gradient(rgba(var(--gold-rgb), 0.82), rgba(var(--gold-rgb), 0.82)) calc(100% - 18px) 18px / 1px 24px no-repeat,
  linear-gradient(rgba(var(--gold-rgb), 0.82), rgba(var(--gold-rgb), 0.82)) 18px calc(100% - 18px) / 24px 1px no-repeat,
  linear-gradient(rgba(var(--gold-rgb), 0.82), rgba(var(--gold-rgb), 0.82)) 18px calc(100% - 18px) / 1px 24px no-repeat,
  linear-gradient(rgba(var(--gold-rgb), 0.82), rgba(var(--gold-rgb), 0.82)) calc(100% - 18px) calc(100% - 18px) / 24px 1px no-repeat,
  linear-gradient(rgba(var(--gold-rgb), 0.82), rgba(var(--gold-rgb), 0.82)) calc(100% - 18px) calc(100% - 18px) / 1px 24px no-repeat;
  border: 1px solid rgba(var(--gold-rgb), 0.28);
  border-radius: 24px;
  opacity: 0.88;
}

.workflow-hero::after {
  inset: 0;
  background: radial-gradient(circle at 14% 18%, rgba(var(--gold-rgb), 0.08), transparent 22%),
  radial-gradient(circle at 84% 16%, rgba(var(--accent-rgb), 0.06), transparent 18%),
  linear-gradient(116deg, transparent 0 24%, rgba(var(--gold-rgb), 0.05) 24% 24.2%, transparent 24.2% 100%);
  opacity: 0.46;
}

.workflow-hero-copy {
  min-width: 0;
  position: relative;
  z-index: 1;
}

.workflow-kicker {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid rgba(var(--gold-rgb), 0.42);
  background: rgba(248, 245, 238, 0.94);
  font-size: 0.76rem;
  letter-spacing: 0.12em;
  color: var(--text);
  font-weight: 700;
}

.workflow-title {
  margin: 10px 0 0;
  font-size: 2rem;
  font-weight: 800;
  color: var(--text);
  font-family: 'STKaiti', 'KaiTi', 'Songti SC', 'Noto Serif SC', serif;
}

.workflow-subtitle {
  margin: 10px 0 0;
  color: var(--muted);
  line-height: 1.84;
  max-width: 760px;
}

.workflow-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 14px;
}

.workflow-session,
.workflow-status {
  display: inline-flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 999px;
  font-size: 0.8rem;
}

.workflow-session {
  background: rgba(248, 245, 238, 0.94);
  border: 1px solid rgba(var(--gold-rgb), 0.42);
  color: var(--muted);
  word-break: break-all;
}

.workflow-status {
  color: var(--success);
  background: rgba(248, 245, 238, 0.94);
  border: 1px solid rgba(var(--gold-rgb), 0.42);
  font-weight: 700;
}

.workflow-status[data-loading='true'] {
  color: var(--highlight);
  background: rgba(var(--highlight-rgb), 0.12);
}

.workflow-hero-actions {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  flex-shrink: 0;
}

.ghost-link,
.ghost-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 42px;
  padding: 0 18px;
  border-radius: 16px;
  font-size: 0.9rem;
  text-decoration: none;
}

.ghost-link {
  color: var(--text);
  border: 1px solid var(--border-strong);
  background: rgba(248, 245, 238, 0.94);
  box-shadow: 0 10px 20px rgba(7, 14, 22, 0.08);
}

.ghost-btn {
  border: 1px solid var(--border-strong);
  background: rgba(248, 245, 238, 0.94);
  color: var(--text);
  cursor: pointer;
  box-shadow: 0 10px 20px rgba(7, 14, 22, 0.08);
}

.ghost-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.workflow-layout {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 20px;
  margin-top: 20px;
}

.workflow-sidebar {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.sidebar-card,
.workflow-chat-card {
  position: relative;
  border: 1px solid var(--border-strong);
  border-radius: 28px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.3), rgba(255, 255, 255, 0.06)),
  var(--surface-elevated);
  box-shadow: var(--card-shadow);
  overflow: hidden;
}

.sidebar-card::before,
.workflow-chat-card::before {
  content: '';
  position: absolute;
  inset: 14px;
  border: 1px solid rgba(var(--gold-rgb), 0.24);
  border-radius: 22px;
  pointer-events: none;
}

.sidebar-card {
  padding: 20px;
}

.sidebar-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.sidebar-card-head h2 {
  margin: 0;
  font-size: 1rem;
}

.sidebar-chip {
  display: inline-flex;
  align-items: center;
  height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(248, 245, 238, 0.94);
  border: 1px solid rgba(var(--gold-rgb), 0.42);
  color: var(--text);
  font-size: 0.74rem;
  font-weight: 700;
}

.stage-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.stage-item {
  position: relative;
  display: flex;
  gap: 12px;
  padding: 14px;
  border-radius: 20px;
  border: 1px solid rgba(var(--gold-rgb), 0.24);
  background: rgba(248, 245, 238, 0.92);
  transition: border-color 0.24s ease, transform 0.24s ease, background 0.24s ease,
  box-shadow 0.24s ease;
}

.stage-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 14px 26px rgba(7, 14, 22, 0.12);
}

.stage-item[data-state='active'] {
  border-color: rgba(var(--gold-rgb), 0.46);
  background: rgba(var(--highlight-rgb), 0.08);
  transform: translateY(-2px);
}

.stage-item[data-state='done'] {
  border-color: rgba(var(--gold-rgb), 0.34);
  background: rgba(var(--accent-rgb), 0.06);
}

.stage-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 14px;
  background: rgba(var(--gold-rgb), 0.18);
  color: var(--accent-strong);
  font-size: 0.8rem;
  font-weight: 800;
  flex-shrink: 0;
}

.stage-copy strong {
  display: block;
  font-size: 0.92rem;
}

.stage-copy p {
  margin: 4px 0 0;
  font-size: 0.82rem;
  color: var(--muted);
  line-height: 1.55;
}

.prompt-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.prompt-chip {
  border: 1px solid var(--border-strong);
  background: rgba(248, 245, 238, 0.92);
  color: var(--text);
  border-radius: 999px;
  padding: 10px 12px;
  font-size: 0.84rem;
  cursor: pointer;
  text-align: left;
}

.prompt-chip:hover:not(:disabled) {
  border-color: rgba(var(--gold-rgb), 0.46);
  background: rgba(var(--highlight-rgb), 0.08);
  transform: translateY(-1px);
}

.prompt-chip:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.workflow-chat-card {
  display: flex;
  flex-direction: column;
  min-height: 720px;
  overflow: hidden;
}

.workflow-message-list {
  flex: 1;
  overflow-y: auto;
  padding: 26px;
  background: radial-gradient(circle at 12% 14%, rgba(var(--gold-rgb), 0.08), transparent 20%),
  radial-gradient(circle at 88% 18%, rgba(var(--accent-rgb), 0.05), transparent 18%),
  rgba(248, 245, 238, 0.88);
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.workflow-empty {
  margin: auto;
  width: min(620px, 100%);
}

.workflow-empty-card {
  position: relative;
  padding: 30px;
  border-radius: 28px;
  text-align: center;
  border: 1px solid rgba(var(--gold-rgb), 0.44);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.4), rgba(255, 255, 255, 0.08)),
  rgba(248, 245, 238, 0.96);
  overflow: hidden;
}

.workflow-empty-card::before {
  content: '';
  position: absolute;
  inset: 14px;
  border: 1px solid rgba(var(--gold-rgb), 0.24);
  border-radius: 22px;
  pointer-events: none;
}

.workflow-empty-badge {
  display: inline-flex;
  align-items: center;
  height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(248, 245, 238, 0.94);
  border: 1px solid rgba(var(--gold-rgb), 0.42);
  color: var(--highlight);
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.14em;
}

.workflow-empty-card h2 {
  margin: 14px 0 0;
  font-size: 1.26rem;
  font-family: 'STKaiti', 'KaiTi', 'Songti SC', 'Noto Serif SC', serif;
}

.workflow-empty-card p {
  margin: 10px 0 0;
  color: var(--muted);
  line-height: 1.8;
}

.turn-block {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.message-row {
  display: flex;
  width: 100%;
}

.user-row {
  justify-content: flex-end;
}

.assistant-row {
  justify-content: flex-start;
}

.message-bubble {
  position: relative;
  max-width: min(760px, 92%);
  padding: 16px 18px;
  border-radius: 22px;
  box-shadow: 0 14px 28px rgba(7, 14, 22, 0.1);
  transition: transform 0.28s ease, box-shadow 0.28s ease;
}

.user-bubble {
  background: var(--bubble-user);
  color: var(--surface);
  border: 1px solid rgba(var(--gold-rgb), 0.34);
  border-bottom-right-radius: 8px;
}

.assistant-bubble {
  background: var(--bubble-ai);
  color: var(--text);
  border: 1px solid var(--border-strong);
  border-bottom-left-radius: 8px;
}

.message-bubble:hover {
  transform: translateY(-2px);
  box-shadow: 0 0 18px rgba(var(--gold-rgb), 0.12), 0 18px 34px rgba(7, 14, 22, 0.12);
}

.message-role {
  display: inline-block;
  font-size: 0.74rem;
  font-weight: 700;
  opacity: 0.9;
  margin-bottom: 8px;
}

.user-bubble .message-role {
  width: 100%;
  text-align: right;
}

.message-text {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.68;
}

.assistant-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.turn-badge {
  display: inline-flex;
  align-items: center;
  height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 0.76rem;
  font-weight: 700;
}

.turn-badge[data-tone='active'] {
  color: var(--highlight);
  background: rgba(var(--highlight-rgb), 0.12);
}

.turn-badge[data-tone='done'] {
  color: var(--success);
  background: rgba(var(--accent-rgb), 0.08);
}

.turn-badge[data-tone='pending'] {
  color: #a06a2a;
  background: rgba(233, 186, 73, 0.16);
}

.turn-badge[data-tone='error'] {
  color: var(--danger);
  background: rgba(192, 86, 61, 0.12);
}

.turn-badge[data-tone='restored'] {
  color: var(--muted);
  background: rgba(var(--gold-rgb), 0.18);
}

.turn-progress {
  margin-bottom: 12px;
  border-radius: 16px;
  border: 1px solid rgba(var(--gold-rgb), 0.34);
  background: rgba(var(--accent-rgb), 0.06);
  overflow: hidden;
}

.turn-progress summary {
  cursor: pointer;
  list-style: none;
  padding: 12px 14px;
  font-size: 0.84rem;
  color: var(--accent-strong);
  font-weight: 700;
}

.turn-progress summary::-webkit-details-marker {
  display: none;
}

.turn-progress ul {
  margin: 0;
  padding: 0 16px 14px 30px;
  color: var(--muted);
  font-size: 0.84rem;
  line-height: 1.65;
}

.turn-progress li + li {
  margin-top: 6px;
}

.turn-error,
.page-error {
  padding: 12px 14px;
  border-radius: 14px;
  color: var(--danger);
  background: rgba(var(--highlight-rgb), 0.1);
}

.turn-error {
  margin-bottom: 10px;
}

.assistant-thinking {
  color: var(--muted);
  font-style: italic;
}

.markdown-body {
  white-space: normal;
  font-size: 0.95rem;
  line-height: 1.72;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  margin: 14px 0 8px;
  color: var(--accent-strong);
  font-weight: 800;
  font-family: 'STKaiti', 'KaiTi', 'Songti SC', 'Noto Serif SC', serif;
}

.markdown-body :deep(p) {
  margin: 8px 0;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 8px 0;
  padding-left: 22px;
}

.markdown-body :deep(blockquote) {
  margin: 10px 0;
  padding: 8px 12px;
  border-left: 3px solid var(--highlight);
  background: rgba(var(--highlight-rgb), 0.08);
  border-radius: 0 10px 10px 0;
  color: var(--muted);
}

.markdown-body :deep(hr) {
  border: none;
  border-top: 1px dashed rgba(var(--gold-rgb), 0.45);
  margin: 16px 0;
}

.markdown-body :deep(code) {
  padding: 2px 6px;
  border-radius: 6px;
  background: rgba(var(--gold-rgb), 0.18);
}

.markdown-body :deep(pre) {
  overflow-x: auto;
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(var(--accent-rgb), 0.05);
}

.workflow-composer {
  position: relative;
  display: flex;
  gap: 12px;
  align-items: flex-end;
  padding: 18px 20px 20px;
  border-top: 1px solid var(--border-strong);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.26), rgba(255, 255, 255, 0.06)),
  var(--surface);
  overflow: hidden;
}

.workflow-composer::before {
  content: '';
  position: absolute;
  inset: 10px 12px 12px;
  border: 1px solid rgba(var(--gold-rgb), 0.24);
  border-radius: 20px;
  pointer-events: none;
}

.workflow-input {
  flex: 1;
  min-height: 92px;
  padding: 14px 16px;
  border-radius: 20px;
  border: 1px solid var(--border-strong);
  background: rgba(255, 255, 255, 0.72);
  color: var(--text);
  resize: none;
}

.workflow-input:focus {
  outline: 2px solid rgba(var(--gold-rgb), 0.22);
  border-color: var(--highlight);
}

.workflow-input:disabled {
  opacity: 0.7;
}

.workflow-composer-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.send-btn,
.stop-btn {
  min-width: 124px;
  height: 46px;
  padding: 0 16px;
  border-radius: 16px;
  font-weight: 700;
  cursor: pointer;
}

.send-btn {
  border: 1px solid var(--border-strong);
  background: linear-gradient(135deg, var(--accent), var(--accent-strong));
  color: var(--surface);
  box-shadow: 0 16px 30px rgba(7, 14, 22, 0.2);
}

.send-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.stop-btn {
  border: 1px solid var(--border-strong);
  background: rgba(248, 245, 238, 0.94);
  color: var(--text);
}

@media (max-width: 1080px) {
  .workflow-layout {
    grid-template-columns: 1fr;
  }

  .workflow-sidebar {
    order: 2;
  }

  .workflow-chat-card {
    order: 1;
  }
}

@media (max-width: 768px) {
  .workflow-page {
    padding: 12px;
  }

  .workflow-hero {
    flex-direction: column;
    padding: 20px;
    border-radius: 24px;
  }

  .workflow-hero-actions {
    width: 100%;
    justify-content: space-between;
  }

  .workflow-message-list {
    padding: 16px;
  }

  .message-bubble {
    max-width: 100%;
  }

  .workflow-composer {
    flex-direction: column;
    align-items: stretch;
  }

  .workflow-composer-actions {
    flex-direction: row;
  }

  .send-btn,
  .stop-btn {
    flex: 1;
    min-width: 0;
  }
}
</style>
