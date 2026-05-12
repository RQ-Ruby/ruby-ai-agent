<script setup>
import { ref, onMounted } from 'vue'
import SseChatRoom from '../components/SseChatRoom.vue'
import { buildTravelChatStreamUrl } from '../api/sseUrls.js'

const chatId = ref('')

onMounted(() => {
  chatId.value = globalThis.crypto?.randomUUID?.() || `travel-${Date.now()}`
})

function buildStreamUrl(message) {
  return buildTravelChatStreamUrl(message, chatId.value)
}
</script>

<template>
  <SseChatRoom
    title="行旅 AI · 旅行咨询"
    subtitle="你的专属旅游规划师。可咨询行程、景点、酒店、签证、预算与避坑攻略，自动生成独立会话用于多轮记忆。"
    :session-label="chatId"
    empty-title="开始你的旅行咨询"
    empty-description="输入目的地、出行时间、人数与预算，行旅 AI 会以 SSE 流式输出建议。"
    placeholder="比如：5 月想带父母去成都玩 4 天，预算 6000 元，请帮我推荐行程"
    :build-stream-url="buildStreamUrl"
  />
</template>
