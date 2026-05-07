<script setup>
import SseChatRoom from '../components/SseChatRoom.vue'
import { buildManusStreamUrl } from '../api/sseUrls.js'

function buildStreamUrl(message) {
  return buildManusStreamUrl(message)
}
</script>

<template>
  <SseChatRoom
    title="AI 超级智能体"
    subtitle="面向复杂任务的智能助手，会实时返回执行过程和阶段性结果，适合多步骤问题处理。"
    empty-title="把任务交给智能体开始处理"
    empty-description="你可以直接输入一个复杂需求，例如调研、分析、整理方案或调用后端能力。"
    placeholder="比如：帮我整理一个 Spring Boot + Vue 项目的功能规划"
    :build-stream-url="buildStreamUrl"
  />
</template>
