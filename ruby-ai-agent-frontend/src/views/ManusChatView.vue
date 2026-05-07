<script setup>
import SseChatRoom from '../components/SseChatRoom.vue'
import { buildManusStreamUrl } from '../api/sseUrls.js'

function buildStreamUrl(message) {
  return buildManusStreamUrl(message)
}
</script>

<template>
  <SseChatRoom
    title="面试题拆解智能体"
    subtitle="给一道 Java 面试题，实时输出结构化拆解：考点定位 / 答题框架 / 核心知识点 / 易错点追问 / 一句话总结。"
    empty-title="贴一道面试题，看 AI 怎么拆"
    empty-description="支持八股、源码题、场景设计题、算法题。例如「设计一个秒杀系统」「synchronized 和 ReentrantLock 的区别」。"
    placeholder="比如：请拆解一下 ConcurrentHashMap 在 JDK1.8 的实现原理"
    :build-stream-url="buildStreamUrl"
  />
</template>
