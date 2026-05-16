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
    subtitle="面向复杂行程需求的 ReAct 规划智能体：可结合工具能力完成攻略整合、路线编排、预算估算，并在你明确需要时生成 PDF 行程手册。"
    empty-title="把复杂的旅行规划交给智能体"
    empty-description="例如：定制多日国内行程、对比住宿与交通方案、整合攻略信息后输出结构化计划。"
    placeholder="比如：帮我规划一份 8 月去云南 7 天 6 晚的行程，预算 8000 元，整理成完整行程手册"
    :build-stream-url="buildStreamUrl"
  />
</template>
