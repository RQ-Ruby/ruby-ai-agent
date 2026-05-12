<script setup>
import { ref, watch, nextTick, onBeforeUnmount } from 'vue'
import { marked } from 'marked'
import { fetchSse } from '../utils/sseStream'

marked.setOptions({ breaks: true, gfm: true })

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
  } catch (e) {
    return text
  }
}

const props = defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  /** 会话标识（例如恋爱大师的 chatId），展示在标题旁 */
  sessionLabel: { type: String, default: '' },
  /** (message: string) => 完整请求 URL（含 query） */
  buildStreamUrl: { type: Function, required: true },
  emptyTitle: { type: String, default: '开始一段新的对话' },
  emptyDescription: { type: String, default: '输入消息后发送，AI 回复将以流式方式实时显示。' },
  placeholder: { type: String, default: '输入消息，Enter 发送，Shift+Enter 换行' },
  userLabel: { type: String, default: '我' },
  assistantLabel: { type: String, default: 'AI' },
})

const input = ref('')
const messages = ref([])
const streaming = ref(false)
const errorText = ref('')
let abortController = null

const listEl = ref(null)

watch(
  () => messages.value.map((m) => m.content),
  () => scrollToBottom(),
  { deep: true },
)

function resetMessages() {
  messages.value = []
  errorText.value = ''
}

function scrollToBottom() {
  nextTick(() => {
    const el = listEl.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

function abortStream() {
  abortController?.abort()
  abortController = null
  streaming.value = false
}

onBeforeUnmount(() => {
  abortStream()
})

async function send() {
  const text = input.value.trim()
  if (!text || streaming.value) return

  errorText.value = ''
  messages.value.push({ role: 'user', content: text })
  messages.value.push({ role: 'assistant', content: '' })
  input.value = ''

  const assistantIndex = messages.value.length - 1
  streaming.value = true

  abortController = new AbortController()
  const url = props.buildStreamUrl(text)

  try {
    await fetchSse(url, {
      signal: abortController.signal,
      onMessage: (chunk) => {
        messages.value[assistantIndex].content += chunk
      },
    })
  } catch (e) {
    if (e?.name === 'AbortError') return
    const msg = e instanceof Error ? e.message : String(e)
    errorText.value = msg
    if (!messages.value[assistantIndex].content) {
      messages.value[assistantIndex].content = `（出错）${msg}`
    }
  } finally {
    streaming.value = false
    abortController = null
  }
}

function onKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}
</script>

<template>
  <div class="chat-root">
    <header class="chat-header">
      <div class="chat-header-inner">
        <div class="chat-kicker">实时对话界面</div>
        <h1 class="chat-title">{{ title }}</h1>
        <p v-if="subtitle" class="chat-subtitle">{{ subtitle }}</p>
        <div class="chat-meta-row">
          <p v-if="sessionLabel" class="chat-session">会话 ID：{{ sessionLabel }}</p>
          <span class="chat-status" :data-streaming="streaming">{{ streaming ? '生成中' : '已就绪' }}</span>
        </div>
      </div>
      <div class="chat-header-actions">
        <button type="button" class="ghost-btn" :disabled="streaming || messages.length === 0" @click="resetMessages">清空会话</button>
        <router-link class="back-link" to="/">返回首页</router-link>
      </div>
    </header>

    <div ref="listEl" class="chat-messages">
      <div v-if="messages.length === 0" class="chat-empty">
        <div class="chat-empty-card">
          <span class="chat-empty-badge">READY</span>
          <h2 class="chat-empty-title">{{ emptyTitle }}</h2>
          <p class="chat-empty-desc">{{ emptyDescription }}</p>
        </div>
      </div>
      <div
        v-for="(m, i) in messages"
        :key="i"
        class="msg-row"
        :data-role="m.role"
      >
        <div class="bubble">
          <span class="bubble-label">{{ m.role === 'user' ? userLabel : assistantLabel }}</span>
          <div
            v-if="m.role === 'assistant'"
            class="bubble-text markdown-body"
            v-html="m.content ? renderMarkdown(m.content) : '<span class=&quot;thinking-dots&quot;>思考中<span>.</span><span>.</span><span>.</span></span>'"
          />
          <div v-else class="bubble-text">{{ m.content }}</div>
        </div>
      </div>
    </div>

    <p v-if="errorText" class="chat-error">{{ errorText }}</p>

    <footer class="chat-input-bar">
      <textarea
        v-model="input"
        class="chat-textarea"
        rows="3"
        :placeholder="placeholder"
        :disabled="streaming"
        @keydown="onKeydown"
      />
      <div class="chat-actions">
        <button
          v-if="streaming"
          type="button"
          class="stop-btn"
          @click="abortStream"
        >
          停止生成
        </button>
        <button
          type="button"
          class="send-btn"
          :disabled="streaming || !input.trim()"
          @click="send"
        >
          {{ streaming ? '生成中…' : '发送消息' }}
        </button>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.chat-root {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  max-width: 1120px;
  margin: 0 auto;
  padding: 24px;
}

.chat-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 24px 26px;
  border: 1px solid var(--border);
  border-radius: 28px 28px 0 0;
  background: linear-gradient(180deg, rgba(255, 252, 248, 0.92), rgba(249, 242, 234, 0.85));
  box-shadow: var(--card-shadow);
}

.chat-header-inner {
  min-width: 0;
}

.chat-kicker {
  font-size: 0.78rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--accent-strong);
  font-weight: 700;
}

.chat-title {
  margin: 10px 0 0;
  font-size: 1.8rem;
  font-weight: 800;
  color: var(--text);
}

.chat-subtitle {
  margin: 10px 0 0;
  font-size: 0.96rem;
  line-height: 1.7;
  color: var(--muted);
}

.chat-meta-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
}

.chat-session {
  margin: 0;
  font-size: 0.8rem;
  color: var(--muted);
  word-break: break-all;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.52);
  border: 1px solid rgba(112, 87, 67, 0.08);
}

.chat-status {
  display: inline-flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 999px;
  font-size: 0.8rem;
  color: var(--success);
  background: rgba(95, 122, 98, 0.12);
  font-weight: 700;
}

.chat-status[data-streaming="true"] {
  color: var(--accent-strong);
  background: rgba(140, 90, 60, 0.14);
}

.chat-header-actions {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-shrink: 0;
}

.ghost-btn {
  height: 40px;
  padding: 0 14px;
  border: 1px solid var(--border);
  border-radius: 12px;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.62);
  color: var(--text);
}

.ghost-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.back-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 40px;
  padding: 0 14px;
  border-radius: 12px;
  font-size: 0.9rem;
  color: var(--accent-strong);
  text-decoration: none;
  background: var(--accent-soft);
}

.back-link:hover {
  filter: brightness(0.98);
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  background: rgba(255, 251, 247, 0.64);
  border-left: 1px solid var(--border);
  border-right: 1px solid var(--border);
}

.chat-empty {
  margin: auto;
  width: min(560px, 100%);
}

.chat-empty-card {
  padding: 28px;
  border-radius: 24px;
  text-align: center;
  border: 1px dashed rgba(112, 87, 67, 0.18);
  background: rgba(255, 255, 255, 0.58);
}

.chat-empty-badge {
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

.chat-empty-title {
  margin: 14px 0 0;
  font-size: 1.28rem;
}

.chat-empty-desc {
  margin: 10px 0 0;
  font-size: 0.95rem;
  color: var(--muted);
  line-height: 1.75;
}

.msg-row {
  display: flex;
  width: 100%;
}

.msg-row[data-role='user'] {
  justify-content: flex-end;
}

.msg-row[data-role='assistant'] {
  justify-content: flex-start;
}

.bubble {
  max-width: min(640px, 88%);
  padding: 14px 16px;
  border-radius: 18px;
  line-height: 1.55;
  font-size: 0.95rem;
  box-shadow: 0 8px 24px rgba(77, 53, 38, 0.06);
}

.msg-row[data-role='user'] .bubble {
  background: var(--bubble-user);
  color: #fff;
  border-bottom-right-radius: 6px;
}

.msg-row[data-role='assistant'] .bubble {
  background: var(--bubble-ai);
  color: var(--text);
  border: 1px solid var(--border);
  border-bottom-left-radius: 6px;
}

.bubble-label {
  display: block;
  font-size: 0.72rem;
  opacity: 0.85;
  margin-bottom: 6px;
  font-weight: 700;
}

.msg-row[data-role='user'] .bubble-label {
  text-align: right;
}

.bubble-text {
  white-space: pre-wrap;
  word-break: break-word;
}

.markdown-body {
  white-space: normal;
  font-size: 0.95rem;
  line-height: 1.7;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  margin: 14px 0 8px;
  font-weight: 800;
  color: var(--accent-strong);
}

.markdown-body :deep(h3) { font-size: 1.05rem; }
.markdown-body :deep(h4) { font-size: 0.98rem; }

.markdown-body :deep(p) {
  margin: 6px 0;
}

.markdown-body :deep(hr) {
  margin: 14px 0;
  border: none;
  border-top: 1px dashed rgba(112, 87, 67, 0.25);
}

.markdown-body :deep(code) {
  padding: 2px 6px;
  border-radius: 6px;
  background: rgba(112, 87, 67, 0.1);
  font-size: 0.88em;
}

.markdown-body :deep(pre) {
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(60, 42, 30, 0.06);
  overflow-x: auto;
}

.markdown-body :deep(pre code) {
  background: transparent;
  padding: 0;
}

.markdown-body :deep(blockquote) {
  margin: 8px 0;
  padding: 6px 12px;
  border-left: 3px solid var(--accent);
  background: rgba(192, 86, 61, 0.06);
  color: var(--muted);
  border-radius: 0 8px 8px 0;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 22px;
  margin: 6px 0;
}

.markdown-body :deep(strong) { color: var(--text); }

.thinking-dots {
  color: var(--muted);
  font-style: italic;
}
.thinking-dots span {
  display: inline-block;
  animation: blink 1.2s infinite;
}
.thinking-dots span:nth-child(2) { animation-delay: 0.2s; }
.thinking-dots span:nth-child(3) { animation-delay: 0.4s; }
@keyframes blink {
  0%, 60%, 100% { opacity: 0.2; }
  30% { opacity: 1; }
}

.chat-error {
  margin: 0;
  padding: 12px 18px;
  font-size: 0.88rem;
  color: var(--danger);
  background: rgba(192, 86, 61, 0.08);
  border-left: 1px solid var(--border);
  border-right: 1px solid var(--border);
}

.chat-input-bar {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  padding: 16px 18px 18px;
  border: 1px solid var(--border);
  border-top: none;
  border-radius: 0 0 28px 28px;
  background: rgba(255, 250, 245, 0.92);
  box-shadow: var(--card-shadow);
}

.chat-textarea {
  flex: 1;
  min-height: 88px;
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid var(--border);
  font: inherit;
  background: rgba(255, 255, 255, 0.78);
  color: var(--text);
}

.chat-textarea:focus {
  outline: 2px solid var(--accent-soft);
  border-color: var(--accent);
}

.chat-textarea:disabled {
  opacity: 0.7;
}

.chat-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.send-btn {
  min-width: 120px;
  height: 48px;
  padding: 0 18px;
  border: none;
  border-radius: 14px;
  font-weight: 700;
  cursor: pointer;
  background: var(--accent);
  color: #fff;
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.stop-btn {
  min-width: 120px;
  height: 42px;
  padding: 0 16px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.7);
  color: var(--text);
  cursor: pointer;
}

@media (max-width: 768px) {
  .chat-root {
    padding: 12px;
  }

  .chat-header {
    flex-direction: column;
    border-radius: 22px 22px 0 0;
    padding: 20px;
  }

  .chat-header-actions {
    width: 100%;
    justify-content: space-between;
  }

  .chat-input-bar {
    flex-direction: column;
    align-items: stretch;
    border-radius: 0 0 22px 22px;
  }

  .chat-actions {
    flex-direction: row;
    justify-content: flex-end;
  }

  .send-btn,
  .stop-btn {
    min-width: 0;
    flex: 1;
  }
}
</style>
