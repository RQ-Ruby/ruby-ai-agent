import { http } from './http.js'

/** Java 面试陪练官 SSE（默认走 RAG 检索增强）：GET /ai/love_app/chat/sse/rag */
export function buildLoveChatStreamUrl(message, chatId) {
  return http.getUri({
    url: '/ai/love_app/chat/sse/rag',
    params: { message, chatId },
  })
}

/** 不带 RAG 的面试陪练官 SSE，调试用：GET /ai/love_app/chat/sse */
export function buildLoveChatStreamUrlPlain(message, chatId) {
  return http.getUri({
    url: '/ai/love_app/chat/sse',
    params: { message, chatId },
  })
}

/** Manus SSE：GET /ai/manus/chat */
export function buildManusStreamUrl(message) {
  return http.getUri({
    url: '/ai/manus/chat',
    params: { message },
  })
}
