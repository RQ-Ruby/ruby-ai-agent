import { http } from './http.js'

/** 行旅 AI 旅行咨询 SSE：GET /ai/travel_app/chat/sse */
export function buildTravelChatStreamUrl(message, chatId) {
  return http.getUri({
    url: '/ai/travel_app/chat/sse/emitter',
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

/**
 * 获取对话历史：GET /ai/chat/history
 * 后端返回 BaseResponse<List<{role,content}>>，统一解包后返回 { data: [...] } 兼容老调用方。
 */
export async function fetchChatHistory(chatId) {
  const resp = await http.get('/ai/chat/history', { params: { chatId } })
  const body = resp?.data
  // 兼容两种结构：{ code, data, message } 或直接数组
  const list = Array.isArray(body) ? body : (body?.data ?? [])
  return { data: list }
}

export async function fetchChatSessions(scene) {
  const resp = await http.get('/ai/chat/sessions', { params: { scene } })
  const body = resp?.data
  const list = Array.isArray(body) ? body : (body?.data ?? [])
  return { data: list }
}

/** 行旅 AI 工作流规划 SSE：GET /ai/workflow/plan */
export function buildWorkflowPlanUrl(message, chatId) {
  return http.getUri({
    url: '/ai/workflow/plan',
    params: { message, chatId },
  })
}
