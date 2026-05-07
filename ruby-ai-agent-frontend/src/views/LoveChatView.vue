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
    title="Java 面试陪练官"
    subtitle="一对一模拟 Java 面试：考官出题 → 你作答 → 实时点评并追问。进入页面会自动生成独立会话 ID，每场面试互不干扰。"
    :session-label="chatId"
    empty-title="开始一场新的模拟面试"
    empty-description="可以先告诉考官你想模拟的方向，例如：JVM 与并发 / Spring 源码 / MySQL 索引 / 秒杀场景设计。说「结束面试」可获取整场评价。"
    placeholder="例如：我想模拟 Java 后端 3 年经验的面试，先从 JVM 开始"
    :build-stream-url="buildStreamUrl"
  />
</template>
