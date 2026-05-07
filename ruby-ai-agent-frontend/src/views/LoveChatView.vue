<script setup>
import { ref, onMounted } from 'vue'
import SseChatRoom from '../components/SseChatRoom.vue'
import { buildLoveChatStreamUrl } from '../api/sseUrls.js'

const chatId = ref('')

onMounted(() => {
  chatId.value = globalThis.crypto?.randomUUID?.() || `love-${Date.now()}`
})

function buildStreamUrl(message) {
  return buildLoveChatStreamUrl(message, chatId.value)
}
</script>

<template>
  <SseChatRoom
    title="AI 恋爱大师"
    subtitle="适合情感交流、恋爱话题和连续聊天场景。进入页面后会自动生成独立会话 ID，用于区分不同对话。"
    :session-label="chatId"
    empty-title="开始今天的专属恋爱对话"
    empty-description="输入你的问题、聊天场景或烦恼，系统会通过 SSE 流式返回回复内容。"
    placeholder="比如：我想和喜欢的人自然开启聊天，第一句怎么说？"
    :build-stream-url="buildStreamUrl"
  />
</template>
