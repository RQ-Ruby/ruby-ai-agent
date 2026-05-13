<script setup>
import { computed, ref, onMounted } from 'vue'
import SseChatRoom from '../components/SseChatRoom.vue'
import { buildTravelChatStreamUrl, fetchChatHistory } from '../api/sseUrls.js'
import { useAuthStore } from '../stores/auth.js'

const auth = useAuthStore()

// 用登录用户 id 给 storage key 命名空间，避免不同账号在同一浏览器串档
const storageKey = computed(() => {
  const userId = auth.state.loginUser?.id || 'anonymous'
  return `travel_chat_id:${userId}`
})

const chatId = ref('')
const chatRoomRef = ref(null)

onMounted(async () => {
  await auth.ensureAuthLoaded()

  let storedId = localStorage.getItem(storageKey.value)
  if (!storedId) {
    storedId = globalThis.crypto?.randomUUID?.() || `travel-${Date.now()}`
    localStorage.setItem(storageKey.value, storedId)
  }
  chatId.value = storedId

  try {
    const { data } = await fetchChatHistory(chatId.value)
    if (data && data.length > 0) {
      chatRoomRef.value?.loadHistory(data)
    }
  } catch (e) {
    console.warn('加载历史消息失败', e)
  }
})

function buildStreamUrl(message) {
  return buildTravelChatStreamUrl(message, chatId.value)
}

function handleNewChat() {
  const newId = globalThis.crypto?.randomUUID?.() || `travel-${Date.now()}`
  localStorage.setItem(storageKey.value, newId)
  chatId.value = newId
}
</script>

<template>
  <SseChatRoom
    ref="chatRoomRef"
    title="行旅 AI · 旅行咨询"
    subtitle="你的专属旅游规划师。可咨询行程、景点、酒店、签证、预算与避坑攻略，自动生成独立会话用于多轮记忆。"
    :session-label="chatId"
    empty-title="开始你的旅行咨询"
    empty-description="输入目的地、出行时间、人数与预算，行旅 AI 会以 SSE 流式输出建议。"
    placeholder="比如：5 月想带父母去成都玩 4 天，预算 6000 元，请帮我推荐行程"
    :build-stream-url="buildStreamUrl"
    @new-chat="handleNewChat"
  />
</template>
