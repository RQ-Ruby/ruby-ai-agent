<script setup>
import { ref, nextTick } from 'vue'
import { buildWorkflowPlanUrl } from '../api/sseUrls.js'

const userInput = ref('')
const isLoading = ref(false)
const progressList = ref([])
const resultContent = ref('')
const errorMessage = ref('')

const contentRef = ref(null)

function enhanceLinks(html) {
  if (!html) return ''
  return html.replace(/<a\s+href="([^"]+)"([^>]*)>(.*?)<\/a>/gi, (match, href, attrs, text) => {
    const isPdf = href.includes('/api/files/pdf/') || href.toLowerCase().endsWith('.pdf')
    const safeAttrs = attrs || ''
    const targetAttr = ' target="_blank" rel="noopener noreferrer"'
    const downloadAttr = isPdf ? ' download' : ''
    return `<a href="${href}"${safeAttrs}${targetAttr}${downloadAttr}>${text}</a>`
  })
}

function renderMarkdown(text) {
  if (!text) return ''
  return enhanceLinks(text
    .replace(/^### (.+)$/gm, '<h3>$1</h3>')
    .replace(/^## (.+)$/gm, '<h2>$1</h2>')
    .replace(/^# (.+)$/gm, '<h1>$1</h1>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.+?)\*/g, '<em>$1</em>')
    .replace(/\n/g, '<br>')
    .replace(/---/g, '<hr>')
    .replace(/\[(.+?)\]\((.+?)\)/g, '<a href="$2">$1</a>'))
}

function scrollToBottom() {
  nextTick(() => {
    if (contentRef.value) {
      contentRef.value.scrollTop = contentRef.value.scrollHeight
    }
  })
}

function startWorkflow() {
  if (!userInput.value.trim() || isLoading.value) return

  isLoading.value = true
  progressList.value = []
  resultContent.value = ''
  errorMessage.value = ''

  const url = buildWorkflowPlanUrl(userInput.value)
  const eventSource = new EventSource(url)

  eventSource.addEventListener('status', (e) => {
    progressList.value.push(e.data)
    scrollToBottom()
  })

  eventSource.addEventListener('progress', (e) => {
    progressList.value.push(e.data)
    scrollToBottom()
  })

  eventSource.addEventListener('result', (e) => {
    resultContent.value = e.data
    scrollToBottom()
  })

  eventSource.addEventListener('error', (e) => {
    if (e.data) {
      errorMessage.value = e.data
    }
    eventSource.close()
    isLoading.value = false
  })

  eventSource.onerror = () => {
    eventSource.close()
    isLoading.value = false
  }

  eventSource.addEventListener('complete', () => {
    eventSource.close()
    isLoading.value = false
  })

  // EventSource 没有 'complete' 事件，靠 onerror 或超时关闭
  // 但 Spring SseEmitter complete 会触发连接关闭 -> onerror
}
</script>

<template>
  <div class="workflow-container">
    <header class="workflow-header">
      <h1>🗺️ 行旅 AI · 工作流规划</h1>
      <p class="subtitle">
        基于 LangGraph4j 有向图工作流：需求解析 → 信息增强 → 行程编排 → 预算核算 → 方案整合
      </p>
    </header>

    <div class="input-area">
      <textarea
        v-model="userInput"
        class="workflow-textarea"
        rows="4"
        placeholder="描述您的旅行需求，例如：我想去成都玩3天，2个人，预算5000元，喜欢美食和熊猫"
        :disabled="isLoading"
      ></textarea>
      <button
        type="button"
        :disabled="isLoading"
        @click="startWorkflow"
        class="submit-btn"
      >
        {{ isLoading ? '规划中...' : '🚀 一键规划' }}
      </button>
    </div>

    <div class="result-area" ref="contentRef">
      <!-- 进度面板 -->
      <div v-if="progressList.length > 0" class="progress-panel">
        <h3>📋 工作流进度</h3>
        <div v-for="(item, idx) in progressList" :key="idx" class="progress-item">
          {{ item }}
        </div>
      </div>

      <!-- 错误提示 -->
      <div v-if="errorMessage" class="error-alert">
        {{ errorMessage }}
      </div>

      <!-- 最终结果 -->
      <div v-if="resultContent" class="result-panel">
        <h3>📄 完整旅行方案</h3>
        <div class="markdown-body" v-html="renderMarkdown(resultContent)"></div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.workflow-container {
  max-width: 900px;
  margin: 0 auto;
  padding: 24px;
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.workflow-header {
  text-align: center;
  margin-bottom: 24px;
}

.workflow-header h1 {
  font-size: 1.8rem;
  color: #2c3e50;
  margin-bottom: 8px;
}

.subtitle {
  color: #7f8c8d;
  font-size: 0.9rem;
}

.input-area {
  margin-bottom: 20px;
}

.workflow-textarea {
  width: 100%;
  resize: vertical;
  min-height: 110px;
  padding: 14px 16px;
  border: 1px solid #dcdfe6;
  border-radius: 12px;
  background: #fff;
  color: #303133;
  line-height: 1.6;
  font-size: 0.96rem;
  box-sizing: border-box;
  outline: none;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.workflow-textarea:focus {
  border-color: #409eff;
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.12);
}

.workflow-textarea:disabled {
  background: #f5f7fa;
  cursor: not-allowed;
}

.submit-btn {
  margin-top: 12px;
  width: 100%;
  height: 42px;
  font-size: 1rem;
  border: none;
  border-radius: 12px;
  background: linear-gradient(135deg, #409eff, #2f7df4);
  color: #fff;
  cursor: pointer;
  transition: transform 0.16s ease, opacity 0.16s ease, box-shadow 0.16s ease;
  box-shadow: 0 8px 18px rgba(64, 158, 255, 0.22);
}

.submit-btn:hover:not(:disabled) {
  transform: translateY(-1px);
}

.submit-btn:disabled {
  opacity: 0.72;
  cursor: not-allowed;
  box-shadow: none;
}

.result-area {
  flex: 1;
  overflow-y: auto;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 20px;
  background: #fafafa;
}

.progress-panel {
  margin-bottom: 20px;
  padding: 16px;
  background: #f0f9eb;
  border-radius: 8px;
  border: 1px solid #e1f3d8;
}

.progress-panel h3 {
  margin: 0 0 12px 0;
  color: #67c23a;
}

.progress-item {
  padding: 4px 0;
  color: #333;
  font-size: 0.9rem;
}

.error-alert {
  padding: 12px 14px;
  margin-bottom: 16px;
  border-radius: 10px;
  border: 1px solid #fbc4c4;
  background: #fef0f0;
  color: #c45656;
}

.result-panel {
  padding: 16px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
}

.result-panel h3 {
  margin: 0 0 16px 0;
  color: #409eff;
}

.markdown-body {
  line-height: 1.8;
  color: #333;
}

.markdown-body h1, .markdown-body h2, .markdown-body h3 {
  margin: 16px 0 8px 0;
  color: #2c3e50;
}

.markdown-body hr {
  margin: 16px 0;
  border: none;
  border-top: 1px solid #eee;
}

.markdown-body a {
  color: #409eff;
  text-decoration: none;
}

.markdown-body a:hover {
  text-decoration: underline;
}
</style>
