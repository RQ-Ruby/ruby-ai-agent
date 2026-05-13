<script setup>
import { ref, onMounted } from 'vue'
import SseChatRoom from '../components/SseChatRoom.vue'
import { buildTravelChatStreamUrl, fetchChatHistory } from '../api/sseUrls.js'

const STORAGE_KEY = 'travel_chat_id'

const chatId = ref('')
const chatRoomRef = ref(null)

onMounted(async () => {
  // 从 localStorage 读取或生成 chatId
  let storedId = localStorage.getItem(STORAGE_KEY)
  if (!storedId) {
    storedId = globalThis.crypto?.randomUUID?.() || `travel-${Date.now()}`
    localStorage.setItem(STORAGE_KEY, storedId)
  }
  chatId.value = storedId

  // 拉取历史消息并渲染
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
  localStorage.setItem(STORAGE_KEY, newId)
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
