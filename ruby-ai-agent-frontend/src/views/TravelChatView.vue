<script setup>
import { computed, ref, onMounted } from 'vue'
import SseChatRoom from '../components/SseChatRoom.vue'
import { buildTravelChatStreamUrl, fetchChatHistory, fetchChatSessions } from '../api/sseUrls.js'
import { useAuthStore } from '../stores/auth.js'

const auth = useAuthStore()

// 用登录用户 id 给 storage key 命名空间，避免不同账号在同一浏览器串档
const storageKey = computed(() => {
  const userId = auth.state.loginUser?.id || 'anonymous'
  return `travel_chat_id:${userId}`
})

const chatId = ref('')
const chatRoomRef = ref(null)
const sessions = ref([])
const loadingSessions = ref(false)

const displaySessions = computed(() => {
  const list = Array.isArray(sessions.value) ? [...sessions.value] : []
  if (!chatId.value) {
    return list
  }
  if (list.some(item => item.chatId === chatId.value)) {
    return list
  }
  return [{ chatId: chatId.value, title: '新会话', lastMessagePreview: '', updatedAt: null }, ...list]
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
  return buildTravelChatStreamUrl(message, chatId.value)
}

function createChatId() {
  return globalThis.crypto?.randomUUID?.() || `travel-${Date.now()}`
}

async function refreshSessions() {
  loadingSessions.value = true
  try {
    const { data } = await fetchChatSessions('travel_app')
    sessions.value = Array.isArray(data) ? data : []
  } catch (e) {
    console.warn('加载会话列表失败', e)
  } finally {
    loadingSessions.value = false
  }
}

async function loadHistory(targetChatId) {
  try {
    const { data } = await fetchChatHistory(targetChatId)
    chatRoomRef.value?.loadHistory(data || [])
  } catch (e) {
    console.warn('加载历史消息失败', e)
    chatRoomRef.value?.loadHistory([])
  }
}

async function selectSession(targetChatId) {
  chatId.value = targetChatId
  localStorage.setItem(storageKey.value, targetChatId)
  await loadHistory(targetChatId)
}

async function handleNewChat() {
  const newId = createChatId()
  localStorage.setItem(storageKey.value, newId)
  chatId.value = newId
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
  <div class="chat-page">
    <aside class="session-panel">
      <div class="session-panel-header">
        <div>
          <div class="session-kicker">旅行咨询会话</div>
          <h2 class="session-title">多聊天室</h2>
        </div>
        <button type="button" class="session-create-btn" @click="handleNewChat">新建</button>
      </div>

      <div class="session-list" :data-loading="loadingSessions">
        <button
          v-for="item in displaySessions"
          :key="item.chatId"
          type="button"
          class="session-item"
          :data-active="item.chatId === chatId"
          @click="selectSession(item.chatId)"
        >
          <span class="session-item-title">{{ item.title || '新会话' }}</span>
          <span class="session-item-preview">{{ item.lastMessagePreview || '暂未开始对话' }}</span>
          <span class="session-item-time">{{ formatUpdatedAt(item.updatedAt) }}</span>
        </button>
      </div>
    </aside>

    <main class="chat-panel">
      <SseChatRoom
        ref="chatRoomRef"
        title="行旅 AI · 旅行咨询"
        subtitle="面向国内文旅目的地的多轮旅行咨询助手。可咨询行程、景点、美食、住宿、交通、预算与避坑建议，并基于独立会话保留上下文。"
        :session-label="chatId"
        empty-title="开始你的旅行咨询"
        empty-description="输入目的地、出行时间、人数与预算，行旅 AI 会结合多轮记忆与流式输出给你更连贯的建议。"
        placeholder="比如：5 月想带父母去成都玩 4 天，预算 6000 元，请帮我推荐行程"
        :build-stream-url="buildStreamUrl"
        @new-chat="handleNewChat"
        @stream-complete="handleStreamComplete"
      />
    </main>
  </div>
</template>

<style scoped>
.chat-page {
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

.chat-panel {
  min-width: 0;
}

:deep(.chat-panel .chat-root) {
  max-width: none;
  padding: 0;
}

@media (max-width: 960px) {
  .chat-page {
    grid-template-columns: 1fr;
    padding: 12px;
  }

  .session-panel {
    max-height: none;
  }
}
</style>
