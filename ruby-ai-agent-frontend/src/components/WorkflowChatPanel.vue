<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { marked } from 'marked'
import { buildWorkflowPlanUrl, fetchChatHistory } from '../api/sseUrls.js'

marked.setOptions({ breaks: true, gfm: true })

const STORAGE_KEY = 'workflow_chat_id'

const stageList = [
  { key: 'intent', title: '意图识别', desc: '判断是旅行规划还是普通闲聊' },
  { key: 'extract', title: '参数抽取', desc: '提取目的地、天数、预算、偏好等' },
  { key: 'validate', title: '参数校验', desc: '检查目的地、天数等必填项' },
  { key: 'clarify', title: '缺参反问', desc: '缺少关键信息时主动追问补全' },
  { key: 'rag', title: 'RAG 检索', desc: '查询本地景点、美食、住宿和攻略' },
  { key: 'generate', title: '行程生成', desc: '结合知识库内容生成结构化方案' },
  { key: 'memory', title: '记忆保存', desc: '保存会话上下文，支持后续修改' },
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
let activeController = null
let turnIdSeed = 0

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

function ensureChatId() {
  let stored = localStorage.getItem(STORAGE_KEY)
  if (!stored) {
    stored = globalThis.crypto?.randomUUID?.() || `workflow-${Date.now()}`
    localStorage.setItem(STORAGE_KEY, stored)
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
  localStorage.setItem(STORAGE_KEY, nextId)
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

watch(turns, scrollToBottom, { deep: true })

function normalizeHistory(history) {
  const normalized = []
  let pending = null

  for (const item of history || []) {
    const role = String(item?.role || '').toLowerCase()
    const content = String(item?.content || '').trim()
    if (!content) continue

    if (role === 'user') {
      if (pending) normalized.push(pending)
      pending = createTurn({ user: content, restored: true, status: 'done' })
      continue
    }

    if (!pending) {
      normalized.push(createTurn({ assistant: content, restored: true, status: 'done' }))
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
    const { data } = await fetchChatHistory(chatId.value)
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

  return { event, data }
}

async function consumeWorkflowSse(url, { signal, onEvent }) {
  const res = await fetch(url, {
    signal,
    headers: { Accept: 'text/event-stream' },
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
      const { done, value } = await reader.read()
      if (value) buffer += decoder.decode(value, { stream: true })

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
  if (turn.error) return { text: '执行失败', tone: 'error' }
  if (turn.status === 'streaming') return { text: '执行中', tone: 'active' }
  if (turn.assistant.includes('再确认几个信息') || turn.assistant.includes('大概玩几天')) {
    return { text: '等待补充参数', tone: 'pending' }
  }
  if (turn.restored) return { text: '历史记录', tone: 'restored' }
  return { text: '已完成', tone: 'done' }
}

async function sendMessage() {
  const text = input.value.trim()
  if (!text || loading.value) return

  pageError.value = ''
  loading.value = true

  const turn = createTurn({ user: text, status: 'streaming' })
  turns.value.push(turn)
  input.value = ''

  const controller = new AbortController()
  activeController = controller
  const url = buildWorkflowPlanUrl(text, chatId.value)

  try {
    await consumeWorkflowSse(url, {
      signal: controller.signal,
      onEvent: ({ event, data }) => {
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
  ensureChatId()
  await loadHistory()
})

onBeforeUnmount(() => {
  abortCurrent()
})
</script>

<template>
  <div class="workflow-page">
    <header class="workflow-hero">
      <div class="workflow-hero-copy">
        <div class="workflow-kicker">Spring AI Alibaba Graph</div>
        <h1 class="workflow-title">行旅 AI · 工作流规划</h1>
        <p class="workflow-subtitle">
          把旅游规划拆成可追踪的节点：识别意图、补齐参数、检索知识库，再生成可修改的多轮行程。
        </p>
        <div class="workflow-meta">
          <span class="workflow-session">会话 ID：{{ chatId }}</span>
          <span class="workflow-status" :data-loading="loading">{{ loading ? '工作流执行中' : '已就绪' }}</span>
        </div>
      </div>
      <div class="workflow-hero-actions">
        <router-link to="/" class="ghost-link">返回首页</router-link>
        <button type="button" class="ghost-btn" :disabled="loading" @click="startNewSession">新建会话</button>
      </div>
    </header>

    <div class="workflow-layout">
      <aside class="workflow-sidebar">
        <section class="sidebar-card">
          <div class="sidebar-card-head">
            <h2>标准节点</h2>
            <span class="sidebar-chip">7 Steps</span>
          </div>
          <ol class="stage-list">
            <li
              v-for="(stage, index) in stageList"
              :key="stage.key"
              class="stage-item"
              :data-state="stageState(stage.key, index)"
            >
              <span class="stage-index">{{ String(index + 1).padStart(2, '0') }}</span>
              <div class="stage-copy">
                <strong>{{ stage.title }}</strong>
                <p>{{ stage.desc }}</p>
              </div>
            </li>
          </ol>
        </section>

        <section class="sidebar-card">
          <div class="sidebar-card-head">
            <h2>快捷试用</h2>
            <span class="sidebar-chip">Prompt</span>
          </div>
          <div class="prompt-grid">
            <button
              v-for="prompt in examplePrompts"
              :key="prompt"
              type="button"
              class="prompt-chip"
              :disabled="loading"
              @click="usePrompt(prompt)"
            >
              {{ prompt }}
            </button>
          </div>
        </section>
      </aside>

      <section class="workflow-chat-card">
        <div ref="listRef" class="workflow-message-list">
          <div v-if="turns.length === 0" class="workflow-empty">
            <div class="workflow-empty-card">
              <span class="workflow-empty-badge">READY</span>
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
                  <span class="turn-badge" :data-tone="turnBadge(turn).tone">{{ turnBadge(turn).text }}</span>
                </div>

                <details v-if="turn.progress.length > 0" class="turn-progress" :open="turn.status === 'streaming'">
                  <summary>查看本轮节点进度（{{ turn.progress.length }}）</summary>
                  <ul>
                    <li v-for="(item, index) in turn.progress" :key="`${turn.id}_${index}`">{{ item }}</li>
                  </ul>
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
            class="workflow-input"
            rows="3"
            :disabled="loading"
            placeholder="继续对话，例如：我想去青岛玩 / 改成两天 / 预算调到 2000 / 多加点海边景点"
            @keydown="onKeydown"
          />
          <div class="workflow-composer-actions">
            <button v-if="loading" type="button" class="stop-btn" @click="abortCurrent">停止执行</button>
            <button type="button" class="send-btn" :disabled="loading || !input.trim()" @click="sendMessage">
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
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 24px 28px;
  border: 1px solid var(--border);
  border-radius: 28px;
  background: linear-gradient(180deg, rgba(255, 252, 248, 0.94), rgba(249, 242, 234, 0.86));
  box-shadow: var(--card-shadow);
}

.workflow-hero-copy {
  min-width: 0;
}

.workflow-kicker {
  font-size: 0.76rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--accent-strong);
  font-weight: 700;
}

.workflow-title {
  margin: 10px 0 0;
  font-size: 1.9rem;
  font-weight: 800;
  color: var(--text);
}

.workflow-subtitle {
  margin: 10px 0 0;
  color: var(--muted);
  line-height: 1.7;
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
  background: rgba(255, 255, 255, 0.62);
  border: 1px solid rgba(112, 87, 67, 0.08);
  color: var(--muted);
  word-break: break-all;
}

.workflow-status {
  color: var(--success);
  background: rgba(95, 122, 98, 0.12);
  font-weight: 700;
}

.workflow-status[data-loading='true'] {
  color: var(--accent-strong);
  background: rgba(140, 90, 60, 0.14);
}

.workflow-hero-actions {
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
  padding: 0 16px;
  border-radius: 14px;
  font-size: 0.9rem;
  text-decoration: none;
}

.ghost-link {
  color: var(--accent-strong);
  background: var(--accent-soft);
}

.ghost-btn {
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.7);
  color: var(--text);
  cursor: pointer;
}

.ghost-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.workflow-layout {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 18px;
  margin-top: 18px;
}

.workflow-sidebar {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.sidebar-card,
.workflow-chat-card {
  border: 1px solid var(--border);
  border-radius: 24px;
  background: var(--surface-elevated);
  box-shadow: var(--card-shadow);
}

.sidebar-card {
  padding: 18px;
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
  background: var(--accent-soft);
  color: var(--accent-strong);
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
  display: flex;
  gap: 12px;
  padding: 12px;
  border-radius: 16px;
  border: 1px solid transparent;
  background: rgba(255, 255, 255, 0.46);
  transition: border-color 0.18s ease, transform 0.18s ease, background 0.18s ease;
}

.stage-item[data-state='active'] {
  border-color: rgba(140, 90, 60, 0.22);
  background: rgba(140, 90, 60, 0.08);
  transform: translateY(-1px);
}

.stage-item[data-state='done'] {
  border-color: rgba(95, 122, 98, 0.16);
  background: rgba(95, 122, 98, 0.08);
}

.stage-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.86);
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
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.68);
  color: var(--text);
  border-radius: 999px;
  padding: 10px 12px;
  font-size: 0.84rem;
  cursor: pointer;
  text-align: left;
}

.prompt-chip:hover:not(:disabled) {
  border-color: rgba(140, 90, 60, 0.22);
  background: rgba(140, 90, 60, 0.08);
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
  padding: 24px;
  background: rgba(255, 251, 247, 0.6);
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.workflow-empty {
  margin: auto;
  width: min(620px, 100%);
}

.workflow-empty-card {
  padding: 30px;
  border-radius: 24px;
  text-align: center;
  border: 1px dashed rgba(112, 87, 67, 0.18);
  background: rgba(255, 255, 255, 0.62);
}

.workflow-empty-badge {
  display: inline-flex;
  align-items: center;
  height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--accent-strong);
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.workflow-empty-card h2 {
  margin: 14px 0 0;
  font-size: 1.26rem;
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
  max-width: min(760px, 92%);
  padding: 14px 16px;
  border-radius: 20px;
  box-shadow: 0 8px 24px rgba(77, 53, 38, 0.06);
}

.user-bubble {
  background: var(--bubble-user);
  color: #fff;
  border-bottom-right-radius: 8px;
}

.assistant-bubble {
  background: var(--bubble-ai);
  color: var(--text);
  border: 1px solid var(--border);
  border-bottom-left-radius: 8px;
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
  color: var(--accent-strong);
  background: rgba(140, 90, 60, 0.12);
}

.turn-badge[data-tone='done'] {
  color: var(--success);
  background: rgba(95, 122, 98, 0.12);
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
  background: rgba(125, 109, 99, 0.12);
}

.turn-progress {
  margin-bottom: 12px;
  border-radius: 14px;
  border: 1px solid rgba(95, 122, 98, 0.14);
  background: rgba(95, 122, 98, 0.08);
  overflow: hidden;
}

.turn-progress summary {
  cursor: pointer;
  list-style: none;
  padding: 12px 14px;
  font-size: 0.84rem;
  color: var(--success);
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
  background: rgba(192, 86, 61, 0.08);
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
  border-left: 3px solid var(--accent);
  background: rgba(140, 90, 60, 0.08);
  border-radius: 0 10px 10px 0;
  color: var(--muted);
}

.markdown-body :deep(hr) {
  border: none;
  border-top: 1px dashed rgba(112, 87, 67, 0.25);
  margin: 16px 0;
}

.markdown-body :deep(code) {
  padding: 2px 6px;
  border-radius: 6px;
  background: rgba(112, 87, 67, 0.1);
}

.markdown-body :deep(pre) {
  overflow-x: auto;
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(60, 42, 30, 0.06);
}

.workflow-composer {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  padding: 18px;
  border-top: 1px solid var(--border);
  background: rgba(255, 250, 245, 0.92);
}

.workflow-input {
  flex: 1;
  min-height: 92px;
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.78);
  color: var(--text);
  resize: none;
}

.workflow-input:focus {
  outline: 2px solid var(--accent-soft);
  border-color: var(--accent);
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
  border-radius: 14px;
  font-weight: 700;
  cursor: pointer;
}

.send-btn {
  border: none;
  background: var(--accent);
  color: #fff;
}

.send-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.stop-btn {
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.7);
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
    border-radius: 22px;
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
