<script setup>
import { ref, onMounted } from 'vue'
import SseChatRoom from '../components/SseChatRoom.vue'
import { buildTravelManusStreamUrl } from '../api/sseUrls.js'

const chatId = ref('')

onMounted(() => {
  chatId.value = globalThis.crypto?.randomUUID?.() || `planner-${Date.now()}`
})

function buildStreamUrl(message) {
  return buildTravelManusStreamUrl(message, chatId.value)
}
</script>

<template>
  <SseChatRoom
    title="行旅 AI · 规划智能体"
    subtitle="面向复杂旅游任务的智能体：自主搜索、抓取攻略、生成结构化行程、核算预算、必要时生成 PDF 行程手册。"
    empty-title="把复杂的旅行规划交给智能体"
    empty-description="例如：定制多日多城市行程、对比酒店与交通方案、抓取最新攻略整合后输出。"
    placeholder="比如：帮我规划一份 8 月份去日本关西 7 天 6 晚的行程，预算 1.5 万，生成完整行程手册"
    :build-stream-url="buildStreamUrl"
  />
</template>
