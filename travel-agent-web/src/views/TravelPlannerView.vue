<script setup>
import {computed, onMounted, ref} from 'vue'
import SseChatRoom from '../components/SseChatRoom.vue'
import {buildTravelAgentStreamUrl, fetchChatHistory, fetchChatSessions} from '../api/sseUrls.js'
import {useAuthStore} from '../stores/auth.js'

const auth = useAuthStore()
const chatId = ref('')
const chatRoomRef = ref(null)
const sessions = ref([])

const storageKey = computed(() => {
  const userId = auth.state.loginUser?.id || 'anonymous'
  return `travel_planner_chat_id:${userId}`
})

const displaySessions = computed(() => {
  const list = Array.isArray(sessions.value) ? [...sessions.value] : []
  if (!chatId.value) {
    return list
  }
  if (list.some(item => item.chatId === chatId.value)) {
    return list
  }
  return [{chatId: chatId.value, title: '新会话', lastMessagePreview: '', updatedAt: null}, ...list]
})

onMounted(async () => {
  await auth.ensureAuthLoaded()
  await refreshSessions()
  let storedId = localStorage.getItem(storageKey.value)
  if (!storedId && sessions.value.length > 0) {
    storedId = sessions.value[0].chatId
  }
  if (!storedId) {
    storedId = createChatId()
    localStorage.setItem(storageKey.value, storedId)
  }
  await selectSession(storedId)
})

function buildStreamUrl(message) {
  return buildTravelAgentStreamUrl(message, chatId.value)
}

function createChatId() {
  return globalThis.crypto?.randomUUID?.() || `planner-${Date.now()}`
}

async function refreshSessions() {
  try {
    const {data} = await fetchChatSessions('travel_agent')
    sessions.value = Array.isArray(data) ? data : []
  } catch (e) {
    console.warn('加载规划会话失败', e)
  }
}

async function loadHistory(targetChatId) {
  try {
    const {data} = await fetchChatHistory(targetChatId)
    chatRoomRef.value?.loadHistory(data || [])
  } catch (e) {
    console.warn('加载规划历史失败', e)
    chatRoomRef.value?.loadHistory([])
  }
}

async function selectSession(targetChatId) {
  chatId.value = targetChatId
  localStorage.setItem(storageKey.value, targetChatId)
  await loadHistory(targetChatId)
}

function handleNewChat() {
  const nextId = createChatId()
  localStorage.setItem(storageKey.value, nextId)
  chatId.value = nextId
  chatRoomRef.value?.clearMessages()
}

async function handleStreamComplete() {
  await refreshSessions()
}

function formatUpdatedAt(value) {
  if (!value) return '刚刚创建'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '最近更新'
  return `${date.getMonth() + 1}-${date.getDate()} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}
</script>

<template>
  <div class="planner-page">
    <aside class="session-panel">
      <div class="session-panel-header">
        <div>
          <div class="session-kicker">规划智能体会话</div>
          <h2 class="session-title">多聊天室</h2>
        </div>
        <button class="session-create-btn" type="button" @click="handleNewChat">新建</button>
      </div>

      <div class="session-list">
        <button
            v-for="item in displaySessions"
            :key="item.chatId"
            :data-active="item.chatId === chatId"
            class="session-item"
            type="button"
            @click="selectSession(item.chatId)"
        >
          <span class="session-item-title">{{ item.title || '新会话' }}</span>
          <span class="session-item-preview">{{ item.lastMessagePreview || '暂未开始规划' }}</span>
          <span class="session-item-time">{{ formatUpdatedAt(item.updatedAt) }}</span>
        </button>
      </div>
    </aside>

    <main class="planner-chat-panel">
      <SseChatRoom
          ref="chatRoomRef"
          :build-stream-url="buildStreamUrl"
          :session-label="chatId"
          empty-description="例如：定制多日国内行程、对比住宿与交通方案、整合攻略信息后输出结构化计划。"
          empty-title="把复杂的旅行规划交给智能体"
          placeholder="比如：帮我规划一份 8 月去云南 7 天 6 晚的行程，预算 8000 元，整理成完整行程手册"
          subtitle="面向复杂行程需求的 ReAct 规划智能体：可结合工具能力完成攻略整合、路线编排、预算估算，并在你明确需要时生成 PDF 行程手册。"
          title="行旅 AI · 规划智能体"
          @new-chat="handleNewChat"
          @stream-complete="handleStreamComplete"
      />
    </main>
  </div>
</template>

<style scoped>
.planner-page {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 18px;
  min-height: 100vh;
  max-width: 1400px;
  margin: 0 auto;
  padding: 20px;
}

.session-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px;
  border: 1px solid var(--border-strong);
  border-radius: 28px;
  background: rgba(248, 245, 238, 0.92);
  box-shadow: var(--card-shadow);
  max-height: calc(100vh - 40px);
}

.session-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.session-kicker {
  font-size: 0.76rem;
  letter-spacing: 0.12em;
  color: var(--muted);
}

.session-title {
  margin: 8px 0 0;
  font-size: 1.35rem;
  font-family: 'STKaiti', 'KaiTi', 'Songti SC', 'Noto Serif SC', serif;
  color: var(--text);
}

.session-create-btn {
  height: 40px;
  padding: 0 16px;
  border-radius: 14px;
  border: 1px solid var(--border-strong);
  background: linear-gradient(135deg, var(--accent), var(--accent-strong));
  color: var(--surface);
  cursor: pointer;
}

.session-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  overflow-y: auto;
  padding-right: 4px;
}

.session-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
  padding: 14px 14px 12px;
  border-radius: 18px;
  border: 1px solid rgba(var(--gold-rgb), 0.22);
  background: rgba(255, 255, 255, 0.64);
  color: var(--text);
  text-align: left;
  cursor: pointer;
}

.session-item[data-active='true'] {
  border-color: rgba(var(--gold-rgb), 0.52);
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 0 16px rgba(var(--gold-rgb), 0.12);
}

.session-item-title {
  font-weight: 700;
}

.session-item-preview,
.session-item-time {
  font-size: 0.82rem;
  color: var(--muted);
}

.session-item-preview {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.planner-chat-panel {
  min-width: 0;
}

:deep(.planner-chat-panel .chat-root) {
  max-width: none;
  padding: 0;
}

@media (max-width: 960px) {
  .planner-page {
    grid-template-columns: 1fr;
    padding: 12px;
  }

  .session-panel {
    max-height: none;
  }
}
</style>
