import { http } from './http.js'

/** 行旅 AI 旅行咨询 SSE：GET /ai/travel_app/chat/sse */
export function buildTravelChatStreamUrl(message, chatId) {
  return http.getUri({
    url: '/ai/travel_app/chat/sse',
    params: { message, chatId },
  })
}

/** 行旅 AI 规划智能体 SSE：GET /ai/travel_manus/chat */
export function buildTravelManusStreamUrl(message, chatId) {
  return http.getUri({
    url: '/ai/travel_manus/chat',
    params: { message, chatId },
  })
}

/** 获取对话历史：GET /ai/travel_app/chat/history */
export function fetchChatHistory(chatId) {
  return http.get('/ai/travel_app/chat/history', { params: { chatId } })
}

/** 行旅 AI 工作流规划 SSE：GET /ai/workflow/plan */
export function buildWorkflowPlanUrl(message, chatId) {
  return http.getUri({
    url: '/ai/workflow/plan',
    params: { message, chatId },
  })
}
