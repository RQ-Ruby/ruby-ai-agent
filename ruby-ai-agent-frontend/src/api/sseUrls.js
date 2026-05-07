import { http } from './http.js'

/** 恋爱大师 SSE：GET /ai/love_app/chat/sse */
export function buildLoveChatStreamUrl(message, chatId) {
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
