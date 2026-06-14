import {http} from './http.js'

/** 行旅 AI 旅行咨询 SSE：GET /ai/travel_app/chat/sse/emitter */
export function buildTravelChatStreamUrl(message, chatId) {
    return http.getUri({
        url: '/ai/travel_app/chat/sse/emitter',
        params: {message, chatId},
    })
}

/** 行旅 AI 规划智能体 SSE：GET /ai/travel_agent/chat */
export function buildTravelAgentStreamUrl(message, chatId) {
    return http.getUri({
        url: '/ai/travel_agent/chat',
        params: {message, chatId},
    })
}

/*
 * 获取对话历史：GET /ai/chat/history
 * 后端返回 BaseResponse<List<{role,content}>>，这里统一解包成组件需要的数组。
 */
export async function fetchChatHistory(chatId) {
    const resp = await http.get('/ai/chat/history', {params: {chatId}})
    const body = resp?.data
    const list = Array.isArray(body) ? body : (body?.data ?? [])
    return {data: list}
}

export async function fetchChatSessions(scene) {
    const resp = await http.get('/ai/chat/sessions', {params: {scene}})
    const body = resp?.data
    const list = Array.isArray(body) ? body : (body?.data ?? [])
    return {data: list}
}

/** 行旅 AI 工作流规划 SSE：GET /ai/workflow/plan */
export function buildWorkflowPlanUrl(message, chatId) {
    return http.getUri({
        url: '/ai/workflow/plan',
        params: {message, chatId},
    })
}
