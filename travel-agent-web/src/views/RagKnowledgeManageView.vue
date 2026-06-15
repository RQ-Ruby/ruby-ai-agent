<script setup>
import {computed, onMounted, reactive, ref, watch} from 'vue'
import {deleteRagDocument, listRagDocuments, refreshRagVectorDb, saveRagDocument} from '../api/rag.js'
import {MdEditor} from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'

const loading = ref(false)
const saving = ref(false)
const refreshing = ref(false)
const refreshProgress = ref(0)
const refreshStep = ref('')
const editVisible = ref(false)
const configVisible = ref(false)
const isEdit = ref(false)
const deleteId = ref(null)
const activeTab = ref('basic')

const pageState = reactive({current: 1, pageSize: 10, total: 0, records: [], allRecords: []})
const queryForm = reactive({title: '', sourceFile: '', status: ''})
const form = reactive({id: null, title: '', sourceFile: '', content: '', tags: '', status: 'published'})
const feedback = reactive({type: '', text: ''})

/* 全局 RAG 检索参数，独立于文档 */
const ragConfig = reactive({
  chunkSize: 1200,
  chunkOverlap: 120,
  topK: 3,
  similarityThreshold: 0.5,
})

const STATUS_OPTIONS = [
  {label: '已发布', value: 'published'},
  {label: '草稿', value: 'draft'},
  {label: '已归档', value: 'archived'},
]

const STATUS_MAP = Object.fromEntries(STATUS_OPTIONS.map(s => [s.value, s.label]))

const totalPages = computed(() => Math.max(1, Math.ceil(pageState.total / pageState.pageSize)))
const hasRecords = computed(() => pageState.records.length > 0)

const progressPercent = computed(() => Math.round(refreshProgress.value * 100) + '%')
const refreshSteps = ['连接向量数据库', '加载知识文档', '文本分块切分', '生成向量嵌入', '写入向量存储', '索引重建完成']

function setFeedback(type, text) {
  feedback.type = type
  feedback.text = text
}

function clearFeedback() {
  feedback.type = ''
  feedback.text = ''
}

function resetForm() {
  Object.assign(form, {id: null, title: '', sourceFile: '', content: '', tags: '', status: 'published'})
}

function openCreate() {
  resetForm()
  isEdit.value = false
  activeTab.value = 'basic'
  clearFeedback()
  editVisible.value = true
}

function openEdit(item) {
  Object.assign(form, {
    id: item.id,
    title: item.title || '',
    sourceFile: item.sourceFile || '',
    content: item.content || '',
    tags: item.tags || '',
    status: item.status || 'published',
  })
  isEdit.value = true
  activeTab.value = 'basic'
  clearFeedback()
  editVisible.value = true
}

function closeEdit() {
  editVisible.value = false
  resetForm()
  clearFeedback()
}

/* ── 分页：后端若未分页则在客户端补切 ── */
function applyClientPaging(allRecords, current, size) {
  const total = allRecords.length
  const start = (current - 1) * size
  const sliced = allRecords.slice(start, start + size)
  return {current, size, total, records: sliced}
}

async function fetchList(page = 1) {
  loading.value = true
  clearFeedback()
  try {
    const result = await listRagDocuments({
      ...queryForm,
      current: page,
      pageSize: pageState.pageSize,
    })
    const records = Array.isArray(result?.records) ? result.records : []
    const total = Number(result?.total ?? records.length)
    const size = Number(result?.size || pageState.pageSize)
    const current = Number(result?.current || page)

    /* 后端若未分页（records 超过 pageSize），客户端补切 */
    if (records.length > pageState.pageSize) {
      pageState.allRecords = records
      const paged = applyClientPaging(records, page, pageState.pageSize)
      pageState.current = paged.current
      pageState.pageSize = paged.size
      pageState.total = paged.total
      pageState.records = paged.records
      if (!paged.records.length && page > 1) {
        await goToPage(page - 1)
      }
    } else {
      pageState.allRecords = []
      pageState.current = current
      pageState.pageSize = size
      pageState.total = total
      pageState.records = records
      if (!records.length && page > 1) {
        await goToPage(page - 1)
      }
    }
  } catch (error) {
    pageState.records = []
    pageState.total = 0
    setFeedback('error', error?.message || '获取文档列表失败')
  } finally {
    loading.value = false
  }
}

async function goToPage(page) {
  if (pageState.allRecords.length > 0) {
    const paged = applyClientPaging(pageState.allRecords, page, pageState.pageSize)
    pageState.current = paged.current
    pageState.records = paged.records
  } else {
    await fetchList(page)
  }
}

async function handleSearch() {
  pageState.allRecords = []
  await fetchList(1)
}

async function handleResetSearch() {
  queryForm.title = ''
  queryForm.sourceFile = ''
  queryForm.status = ''
  pageState.allRecords = []
  await fetchList(1)
}

async function handleSave() {
  clearFeedback()
  if (!form.title.trim()) {
    setFeedback('error', '请输入文档标题')
    return
  }
  saving.value = true
  try {
    await saveRagDocument({...form})
    editVisible.value = false
    resetForm()
    setFeedback('success', isEdit.value ? '文档已更新' : '文档创建成功')
    pageState.allRecords = []
    await fetchList(pageState.current)
  } catch (error) {
    setFeedback('error', error?.message || '保存文档失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(item) {
  if (!window.confirm(`确认删除文档「${item.title}」吗？此操作不可撤销。`)) return
  clearFeedback()
  deleteId.value = item.id
  try {
    await deleteRagDocument(item.id)
    setFeedback('success', '文档已删除')
    pageState.allRecords = []
    await fetchList(pageState.current)
  } catch (error) {
    setFeedback('error', error?.message || '删除文档失败')
  } finally {
    deleteId.value = null
  }
}

/* ── 刷新向量数据库（模拟进度条 + 后端同步调用） ── */
let progressTimer = null

watch(refreshing, (val) => {
  if (!val && progressTimer) {
    clearInterval(progressTimer)
    progressTimer = null
  }
})

async function handleRefresh() {
  if (refreshing.value) return
  refreshing.value = true
  refreshProgress.value = 0
  refreshStep.value = ''
  clearFeedback()

  const totalSteps = refreshSteps.length
  let stepIdx = 0

  /* 模拟进度条（在实际完成前走到 ~85%） */
  progressTimer = setInterval(() => {
    const target = 0.85
    const remaining = target - refreshProgress.value
    if (remaining > 0.005) {
      refreshProgress.value += remaining * 0.18
    }
    const step = Math.min(Math.floor(refreshProgress.value * totalSteps), totalSteps - 1)
    if (step !== stepIdx) {
      stepIdx = step
      refreshStep.value = refreshSteps[step]
    }
  }, 280)

  try {
    await refreshRagVectorDb()
    /* 完成后瞬间走完 100% */
    clearInterval(progressTimer)
    progressTimer = null
    refreshProgress.value = 1
    refreshStep.value = refreshSteps[totalSteps - 1]
    await new Promise(r => setTimeout(r, 600))
    setFeedback('success', `向量数据库刷新成功，共处理 ${pageState.total} 篇文档`)
    pageState.allRecords = []
    await fetchList(pageState.current)
  } catch (error) {
    clearInterval(progressTimer)
    progressTimer = null
    refreshProgress.value = 0
    refreshStep.value = ''
    setFeedback('error', error?.message || '刷新向量数据库失败')
  } finally {
    refreshing.value = false
    if (progressTimer) {
      clearInterval(progressTimer)
      progressTimer = null
    }
  }
}

async function goPrevPage() {
  if (pageState.current <= 1 || loading.value) return
  await goToPage(pageState.current - 1)
}

async function goNextPage() {
  if (pageState.current >= totalPages.value || loading.value) return
  await goToPage(pageState.current + 1)
}

function formatTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit',
  }).format(date)
}

onMounted(() => fetchList(1))
</script>

<template>
  <div class="admin-page">
    <!-- Hero -->
    <section class="admin-hero">
      <div>
        <span class="admin-badge">RAG 知识库管理</span>
        <h1 class="admin-title">知识文档与向量库</h1>
        <p class="admin-sub">
          支持对 MySQL 中文档的增删改查，全局设定检索增强参数（分块、重叠、TopK、相似度阈值），手动刷新向量数据库即可执行向量化。
        </p>
      </div>
      <div class="admin-hero-meta">
        <div class="meta-card"><strong>{{ pageState.total }}</strong><span>文档总数</span></div>
        <div class="meta-card"><strong>{{ pageState.current }}</strong><span>当前页码</span></div>
      </div>
    </section>

    <!-- Feedback -->
    <p v-if="feedback.text" :class="feedback.type === 'error' ? 'feedback-error' : 'feedback-success'" class="feedback">
      {{ feedback.text }}
    </p>

    <section class="admin-grid">
      <!-- Filter Panel -->
      <article class="panel-card">
        <div class="panel-head">
          <div>
            <h2>检索条件</h2>
            <p>按标题、来源文件和状态筛选知识库文档。</p>
          </div>
        </div>
        <div class="filter-grid">
          <label class="field">
            <span class="field-label">标题</span>
            <input v-model="queryForm.title" class="field-input" placeholder="按标题模糊搜索" type="text"/>
          </label>
          <label class="field">
            <span class="field-label">来源文件</span>
            <input v-model="queryForm.sourceFile" class="field-input" placeholder="按来源文件名搜索" type="text"/>
          </label>
          <label class="field">
            <span class="field-label">状态</span>
            <select v-model="queryForm.status" class="field-input field-select">
              <option value="">全部状态</option>
              <option v-for="s in STATUS_OPTIONS" :key="s.value" :value="s.value">{{ s.label }}</option>
            </select>
          </label>
        </div>
        <div class="actions-row">
          <button :disabled="loading" class="action-btn action-btn-primary" type="button" @click="handleSearch">
            {{ loading ? '查询中...' : '查询' }}
          </button>
          <button :disabled="loading" class="action-btn action-btn-secondary" type="button" @click="handleResetSearch">
            重置
          </button>
        </div>
      </article>

      <!-- Document List Panel + Global Config -->
      <article class="panel-card">
        <div class="panel-head panel-head-split">
          <div>
            <h2>文档列表</h2>
            <p>点击"编辑"可弹出对话框更新文档内容。</p>
          </div>
          <div class="toolbar-row">
            <div class="pagination-meta">第 {{ pageState.current }} / {{ totalPages }} 页</div>
            <button class="action-btn action-btn-secondary" type="button" @click="configVisible = !configVisible">
              <span class="btn-icon">&#x2699;</span>
              检索参数
            </button>
            <button
                :disabled="refreshing"
                class="action-btn action-btn-accent"
                type="button"
                @click="handleRefresh"
            >
              <span :class="{'spin': refreshing}" class="btn-icon">&#x21bb;</span>
              {{ refreshing ? '向量化中...' : '刷新向量数据库' }}
            </button>
            <button class="action-btn action-btn-primary" type="button" @click="openCreate">新增文档</button>
          </div>
        </div>

        <!-- Global RAG Config Panel (collapsible) -->
        <div v-if="configVisible" class="config-panel">
          <div class="config-panel-head">
            <h3>全局检索增强参数</h3>
            <p>以下参数应用于所有文档的向量化与检索过程，调整后需点击"刷新向量数据库"使参数生效。</p>
          </div>
          <div class="config-grid">
            <label class="field">
              <span class="field-label">分块大小 (chunkSize)</span>
              <input v-model.number="ragConfig.chunkSize" class="field-input" max="8000" min="100" step="100"
                     type="number"/>
              <span class="field-hint">每块最大字符数，建议 800–2000</span>
            </label>
            <label class="field">
              <span class="field-label">分块重叠 (chunkOverlap)</span>
              <input v-model.number="ragConfig.chunkOverlap" class="field-input" max="1000" min="0" step="10"
                     type="number"/>
              <span class="field-hint">建议为 chunkSize 的 5%–10%</span>
            </label>
            <label class="field">
              <span class="field-label">检索数量 (topK)</span>
              <input v-model.number="ragConfig.topK" class="field-input" max="20" min="1" step="1" type="number"/>
              <span class="field-hint">返回最相似片段数，建议 3–5</span>
            </label>
            <label class="field">
              <span class="field-label">相似度阈值 (similarityThreshold)</span>
              <input v-model.number="ragConfig.similarityThreshold" class="field-input" max="1" min="0" step="0.01"
                     type="number"/>
              <span class="field-hint">仅返回高于此阈值的片段，建议 0.4–0.7</span>
            </label>
          </div>
          <!-- Visual Preview -->
          <div class="param-preview">
            <div class="param-preview-title">参数预览</div>
            <div class="param-bar-wrap">
              <div class="param-bar">
                <div :style="{width: Math.min(ragConfig.chunkSize / 80, 100) + '%'}"
                     class="param-bar-fill bar-chunk"></div>
              </div>
              <span class="param-bar-label">chunkSize: {{ ragConfig.chunkSize }}</span>
            </div>
            <div class="param-bar-wrap">
              <div class="param-bar">
                <div :style="{width: Math.min(ragConfig.chunkOverlap / 10, 100) + '%'}"
                     class="param-bar-fill bar-overlap"></div>
              </div>
              <span class="param-bar-label">chunkOverlap: {{ ragConfig.chunkOverlap }}</span>
            </div>
            <div class="param-bar-wrap">
              <div class="param-bar">
                <div :style="{width: (ragConfig.topK / 20 * 100) + '%'}" class="param-bar-fill bar-topk"></div>
              </div>
              <span class="param-bar-label">topK: {{ ragConfig.topK }}</span>
            </div>
            <div class="param-bar-wrap">
              <div class="param-bar">
                <div :style="{width: (ragConfig.similarityThreshold * 100) + '%'}"
                     class="param-bar-fill bar-threshold"></div>
              </div>
              <span class="param-bar-label">threshold: {{ ragConfig.similarityThreshold }}</span>
            </div>
          </div>
        </div>

        <!-- Refresh Progress Bar -->
        <div v-if="refreshing" class="refresh-progress">
          <div class="progress-step-text">{{ refreshStep || '准备中...' }}</div>
          <div class="progress-track">
            <div :style="{width: progressPercent}" class="progress-fill">
              <span class="progress-shimmer"></span>
            </div>
          </div>
          <span class="progress-percent">{{ Math.round(refreshProgress * 100) }}%</span>
        </div>

        <div v-if="loading" class="empty-state">正在加载文档列表...</div>
        <div v-else-if="!hasRecords" class="empty-state">暂无符合条件的文档。</div>
        <div v-else class="table-wrap">
          <table class="user-table">
            <thead>
            <tr>
              <th class="col-id">ID</th>
              <th>标题</th>
              <th>来源</th>
              <th>状态</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="item in pageState.records" :key="item.id">
              <td class="col-id">{{ item.id }}</td>
              <td class="doc-title-cell">{{ item.title }}</td>
              <td class="col-source">{{ item.sourceFile || '-' }}</td>
              <td>
                <span :data-status="item.status" class="status-chip">{{ STATUS_MAP[item.status] || item.status }}</span>
              </td>
              <td>{{ formatTime(item.createTime) }}</td>
              <td>
                <div class="table-actions">
                  <button class="table-btn" type="button" @click="openEdit(item)">编辑</button>
                  <button
                      :disabled="deleteId === item.id"
                      class="table-btn table-btn-danger"
                      type="button"
                      @click="handleDelete(item)"
                  >{{ deleteId === item.id ? '删除中...' : '删除' }}
                  </button>
                </div>
              </td>
            </tr>
            </tbody>
          </table>
        </div>

        <div class="pagination-row">
          <button :disabled="pageState.current <= 1 || loading" class="action-btn action-btn-secondary" type="button"
                  @click="goPrevPage">上一页
          </button>
          <span class="pagination-text">共 {{ pageState.total }} 条</span>
          <button :disabled="pageState.current >= totalPages || loading" class="action-btn action-btn-secondary"
                  type="button" @click="goNextPage">下一页
          </button>
        </div>
      </article>
    </section>

    <!-- Edit/Create Dialog (two tabs: basic + content) -->
    <div v-if="editVisible" class="admin-dialog-mask" @click.self="closeEdit">
      <div class="admin-dialog admin-dialog-wide">
        <div class="dialog-head">
          <div>
            <h2>{{ isEdit ? '编辑文档' : '新增文档' }}</h2>
            <p>{{
                isEdit ? `正在编辑文档 #${form.id}` : '新增文档到知识库，保存后可手动刷新向量数据库以执行向量化。'
              }}</p>
          </div>
          <button class="dialog-close" type="button" @click="closeEdit">×</button>
        </div>

        <!-- Tab Navigation (no params tab) -->
        <div class="tab-bar">
          <button :class="['tab-btn', {'tab-active': activeTab === 'basic'}]" type="button"
                  @click="activeTab = 'basic'">基本信息
          </button>
          <button :class="['tab-btn', {'tab-active': activeTab === 'content'}]" type="button"
                  @click="activeTab = 'content'">文档内容
          </button>
        </div>

        <!-- Tab: Basic Info -->
        <div v-show="activeTab === 'basic'" class="form-stack">
          <label class="field">
            <span class="field-label">标题 <span class="required">*</span></span>
            <input v-model="form.title" class="field-input" placeholder="输入文档标题" type="text"/>
          </label>
          <label class="field">
            <span class="field-label">来源文件</span>
            <input v-model="form.sourceFile" class="field-input" placeholder="如：travel_guide_chengdu.md" type="text"/>
          </label>
          <label class="field">
            <span class="field-label">标签</span>
            <input v-model="form.tags" class="field-input" placeholder="多个标签用逗号分隔，如：景点,美食,住宿"
                   type="text"/>
          </label>
          <label class="field">
            <span class="field-label">状态</span>
            <select v-model="form.status" class="field-input field-select">
              <option v-for="s in STATUS_OPTIONS" :key="s.value" :value="s.value">{{ s.label }}</option>
            </select>
          </label>
        </div>

        <!-- Tab: Content (MD Editor) -->
        <div v-show="activeTab === 'content'" class="md-editor-wrap">
          <MdEditor v-model="form.content" :preview="true" :toolbarsExclude="['github']" language="zh-CN"
                    placeholder="在此编写或粘贴文档内容，支持 Markdown 格式..." style="height: 420px;"/>
        </div>

        <div class="actions-row">
          <button :disabled="saving" class="action-btn action-btn-primary" type="button" @click="handleSave">
            {{ saving ? '保存中...' : '保存' }}
          </button>
          <button :disabled="saving" class="action-btn action-btn-secondary" type="button" @click="closeEdit">取消
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ── Page Layout ── */
.admin-page {
  max-width: 1180px;
  margin: 0 auto;
  padding: 24px 24px 56px;
}

/* ── Hero ── */
.admin-hero {
  position: relative;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding: 28px;
  border-radius: 28px;
  border: 1px solid var(--border-strong);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.3), rgba(255, 255, 255, 0.06)),
  var(--surface);
  box-shadow: var(--card-shadow);
  overflow: hidden;
}

.admin-hero::before {
  content: '';
  position: absolute;
  inset: 12px;
  border: 1px solid rgba(var(--gold-rgb), 0.24);
  border-radius: 20px;
  pointer-events: none;
}

.admin-badge {
  display: inline-flex;
  align-items: center;
  height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  background: rgba(248, 245, 238, 0.94);
  color: var(--highlight);
  border: 1px solid rgba(var(--gold-rgb), 0.42);
  font-size: 0.84rem;
  font-weight: 700;
}

.admin-title {
  margin: 16px 0 0;
  font-size: clamp(2rem, 3.8vw, 3rem);
  line-height: 1.12;
  font-family: 'STKaiti', 'KaiTi', 'Songti SC', 'Noto Serif SC', serif;
}

.admin-sub {
  margin: 14px 0 0;
  max-width: 720px;
  color: var(--muted);
  line-height: 1.75;
}

.admin-hero-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(120px, 1fr));
  gap: 14px;
}

.meta-card {
  padding: 16px 18px;
  border-radius: 20px;
  background: rgba(248, 245, 238, 0.94);
  border: 1px solid rgba(var(--gold-rgb), 0.32);
}

.meta-card strong {
  display: block;
  font-size: 1.2rem;
}

.meta-card span {
  display: block;
  margin-top: 6px;
  color: var(--muted);
  font-size: 0.86rem;
}

/* ── Feedback ── */
.feedback {
  margin: 20px 0 0;
  padding: 14px 16px;
  border-radius: 16px;
  font-size: 0.92rem;
}

.feedback-error {
  color: var(--danger);
  background: rgba(var(--highlight-rgb), 0.1);
  border: 1px solid rgba(var(--highlight-rgb), 0.18);
}

.feedback-success {
  color: var(--success);
  background: rgba(var(--accent-rgb), 0.08);
  border: 1px solid rgba(var(--gold-rgb), 0.24);
}

/* ── Grid & Panel ── */
.admin-grid {
  margin-top: 22px;
  display: grid;
  grid-template-columns: 1fr;
  gap: 22px;
}

.panel-card {
  position: relative;
  padding: 24px;
  border-radius: 24px;
  border: 1px solid var(--border-strong);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.3), rgba(255, 255, 255, 0.06)),
  var(--surface-elevated);
  box-shadow: var(--card-shadow);
  overflow: hidden;
}

.panel-card::before {
  content: '';
  position: absolute;
  inset: 12px;
  border: 1px solid rgba(var(--gold-rgb), 0.24);
  border-radius: 18px;
  pointer-events: none;
}

.panel-head {
  margin-bottom: 18px;
}

.panel-head-split {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.panel-head h2 {
  margin: 0;
  font-size: 1.12rem;
  font-family: 'STKaiti', 'KaiTi', 'Songti SC', 'Noto Serif SC', serif;
}

.panel-head p,
.pagination-meta {
  margin: 8px 0 0;
  color: var(--muted);
  font-size: 0.9rem;
}

.toolbar-row {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

/* ── Global Config Panel ── */
.config-panel {
  margin-bottom: 20px;
  padding: 20px;
  border-radius: 18px;
  background: rgba(var(--accent-rgb), 0.03);
  border: 1px solid rgba(var(--gold-rgb), 0.22);
  animation: configSlideIn 0.3s ease;
}

@keyframes configSlideIn {
  from {
    opacity: 0;
    transform: translateY(-8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.config-panel-head {
  margin-bottom: 16px;
}

.config-panel-head h3 {
  margin: 0;
  font-size: 0.98rem;
  font-family: 'STKaiti', 'KaiTi', 'Songti SC', 'Noto Serif SC', serif;
}

.config-panel-head p {
  margin: 6px 0 0;
  color: var(--muted);
  font-size: 0.84rem;
  line-height: 1.6;
}

.config-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

/* ── Refresh Progress ── */
.refresh-progress {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 20px;
  padding: 14px 18px;
  border-radius: 16px;
  background: rgba(var(--highlight-rgb), 0.06);
  border: 1px solid rgba(var(--highlight-rgb), 0.14);
  animation: configSlideIn 0.25s ease;
}

.progress-step-text {
  font-size: 0.84rem;
  font-weight: 700;
  color: var(--highlight);
  min-width: 110px;
  white-space: nowrap;
  animation: pulse-text 1.2s ease-in-out infinite;
}

@keyframes pulse-text {
  0%, 100% {
    opacity: 0.7;
  }
  50% {
    opacity: 1;
  }
}

.progress-track {
  flex: 1;
  height: 10px;
  border-radius: 999px;
  background: rgba(var(--gold-rgb), 0.2);
  overflow: hidden;
  position: relative;
}

.progress-fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--highlight), #C47D5A, var(--highlight));
  background-size: 200% 100%;
  animation: progress-glow 1.6s linear infinite;
  transition: width 0.4s ease;
  position: relative;
  overflow: hidden;
}

@keyframes progress-glow {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

.progress-shimmer {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent 0%, rgba(255, 255, 255, 0.35) 50%, transparent 100%);
  animation: shimmer 1.4s ease-in-out infinite;
}

@keyframes shimmer {
  0% {
    transform: translateX(-100%);
  }
  100% {
    transform: translateX(100%);
  }
}

.progress-percent {
  font-size: 0.88rem;
  font-weight: 800;
  color: var(--highlight);
  min-width: 40px;
  text-align: right;
}

/* ── Filter Grid ── */
.filter-grid,
.form-stack {
  display: grid;
  gap: 14px;
}

.filter-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field-label {
  font-size: 0.88rem;
  font-weight: 600;
}

.required {
  color: var(--danger);
}

.field-hint {
  font-size: 0.82rem;
  color: var(--muted);
  line-height: 1.5;
}

.field-input {
  width: 100%;
  min-height: 46px;
  border-radius: 16px;
  border: 1px solid var(--border-strong);
  background: rgba(255, 255, 255, 0.72);
  color: var(--text);
  padding: 0 14px;
  font-size: 0.94rem;
  outline: none;
  transition: border-color 0.16s ease, box-shadow 0.16s ease;
}

.field-input:focus {
  border-color: var(--highlight);
  box-shadow: 0 0 0 4px rgba(var(--gold-rgb), 0.16);
}

.field-select {
  appearance: none;
}

.field-textarea {
  padding-top: 12px;
  padding-bottom: 12px;
  resize: vertical;
}

.actions-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
}

/* ── Buttons ── */
.action-btn {
  min-width: 96px;
  height: 44px;
  border-radius: 14px;
  border: none;
  font-size: 0.92rem;
  font-weight: 700;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  transition: transform 0.16s ease, opacity 0.16s ease, box-shadow 0.16s ease;
}

.action-btn:hover:not(:disabled) {
  transform: translateY(-1px);
}

.action-btn:disabled {
  opacity: 0.72;
  cursor: not-allowed;
}

.action-btn-primary {
  color: var(--surface);
  background: linear-gradient(135deg, var(--accent), var(--accent-strong));
  border: 1px solid var(--border-strong);
  box-shadow: 0 16px 26px rgba(7, 14, 22, 0.18);
}

.action-btn-secondary {
  color: var(--text);
  background: rgba(248, 245, 238, 0.92);
  border: 1px solid var(--border-strong);
}

.action-btn-accent {
  color: var(--surface);
  background: linear-gradient(135deg, var(--highlight), var(--highlight-strong));
  border: 1px solid rgba(var(--highlight-rgb), 0.42);
  box-shadow: 0 12px 22px rgba(var(--highlight-rgb), 0.18);
}

.btn-icon {
  display: inline-flex;
  font-size: 1.1rem;
  transition: transform 0.6s ease;
}

.btn-icon.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

/* ── Table ── */
.table-wrap {
  overflow-x: auto;
}

.user-table {
  width: 100%;
  border-collapse: collapse;
  min-width: 720px;
}

.user-table th,
.user-table td {
  padding: 14px 12px;
  border-bottom: 1px solid rgba(var(--gold-rgb), 0.22);
  text-align: left;
  vertical-align: top;
  font-size: 0.92rem;
}

.user-table th {
  color: var(--muted);
  font-size: 0.84rem;
  font-weight: 700;
}

.col-id {
  width: 60px;
}

.col-source {
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-title-cell {
  font-weight: 600;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-chip {
  display: inline-flex;
  align-items: center;
  height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  background: rgba(248, 245, 238, 0.92);
  border: 1px solid rgba(var(--gold-rgb), 0.32);
  font-size: 0.82rem;
  font-weight: 700;
}

.status-chip[data-status='published'] {
  color: #2f6e5a;
  background: rgba(95, 122, 98, 0.12);
  border-color: rgba(95, 122, 98, 0.24);
}

.status-chip[data-status='draft'] {
  color: #7a6a3e;
  background: rgba(200, 178, 144, 0.16);
  border-color: rgba(200, 178, 144, 0.32);
}

.status-chip[data-status='archived'] {
  color: var(--muted);
  background: rgba(106, 110, 114, 0.08);
  border-color: rgba(106, 110, 114, 0.2);
}

.table-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.table-btn {
  height: 34px;
  padding: 0 12px;
  border-radius: 12px;
  border: 1px solid var(--border-strong);
  background: rgba(248, 245, 238, 0.92);
  color: var(--text);
  font-size: 0.84rem;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.16s ease;
}

.table-btn:hover:not(:disabled) {
  transform: translateY(-1px);
}

.table-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.table-btn-danger {
  color: var(--danger);
}

.empty-state {
  padding: 34px 16px;
  border-radius: 18px;
  text-align: center;
  color: var(--muted);
  background: rgba(248, 245, 238, 0.9);
  border: 1px solid rgba(var(--gold-rgb), 0.24);
}

/* ── Pagination ── */
.pagination-row {
  margin-top: 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.pagination-text {
  color: var(--muted);
  font-size: 0.9rem;
}

/* ── Dialog ── */
.admin-dialog-mask {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(7, 14, 22, 0.38);
  backdrop-filter: blur(8px);
}

.admin-dialog {
  position: relative;
  width: min(100%, 560px);
  max-height: calc(100vh - 40px);
  overflow-y: auto;
  padding: 24px;
  border-radius: 26px;
  border: 1px solid var(--border-strong);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.3), rgba(255, 255, 255, 0.06)),
  var(--surface-elevated);
  box-shadow: var(--card-shadow-hover);
  overflow-x: hidden;
}

.admin-dialog::before {
  content: '';
  position: absolute;
  inset: 12px;
  border: 1px solid rgba(var(--gold-rgb), 0.24);
  border-radius: 20px;
  pointer-events: none;
}

.admin-dialog-wide {
  width: min(100%, 860px);
}

.dialog-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.dialog-head h2 {
  margin: 0;
  font-size: 1.16rem;
  font-family: 'STKaiti', 'KaiTi', 'Songti SC', 'Noto Serif SC', serif;
}

.dialog-head p {
  margin: 8px 0 0;
  color: var(--muted);
  font-size: 0.9rem;
  line-height: 1.7;
}

.dialog-close {
  width: 38px;
  height: 38px;
  border: 1px solid var(--border-strong);
  border-radius: 12px;
  background: rgba(248, 245, 238, 0.92);
  color: var(--text);
  font-size: 1.2rem;
  cursor: pointer;
  transition: all 0.16s ease;
}

.dialog-close:hover {
  transform: translateY(-1px);
  background: rgba(248, 245, 238, 1);
}

/* ── Tab Bar ── */
.tab-bar {
  display: flex;
  gap: 4px;
  margin-bottom: 18px;
  padding: 4px;
  border-radius: 14px;
  background: rgba(var(--gold-rgb), 0.08);
  border: 1px solid rgba(var(--gold-rgb), 0.18);
}

.tab-btn {
  flex: 1;
  height: 40px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: var(--muted);
  font-size: 0.88rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.tab-btn:hover {
  color: var(--text);
  background: rgba(255, 255, 255, 0.5);
}

.tab-active {
  color: var(--text);
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 2px 8px rgba(7, 14, 22, 0.1);
  font-weight: 700;
}

/* ── MD Editor ── */
.md-editor-wrap {
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid var(--border-strong);
}

.md-editor-wrap :deep(.md-editor) {
  --md-bk-color: rgba(248, 245, 238, 0.96);
  border: none !important;
  box-shadow: none !important;
}

.md-editor-wrap :deep(.md-editor-toolbar-wrapper) {
  border-bottom: 1px solid rgba(var(--gold-rgb), 0.24) !important;
}

/* ── Parameter Section ── */
.param-preview {
  margin-top: 18px;
  padding: 18px;
  border-radius: 16px;
  background: rgba(var(--accent-rgb), 0.03);
  border: 1px solid rgba(var(--gold-rgb), 0.18);
}

.param-preview-title {
  font-size: 0.88rem;
  font-weight: 700;
  margin-bottom: 14px;
  color: var(--text);
  font-family: 'STKaiti', 'KaiTi', 'Songti SC', 'Noto Serif SC', serif;
}

.param-bar-wrap {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.param-bar-wrap:last-child {
  margin-bottom: 0;
}

.param-bar {
  flex: 1;
  height: 8px;
  border-radius: 999px;
  background: rgba(var(--gold-rgb), 0.16);
  overflow: hidden;
}

.param-bar-fill {
  height: 100%;
  border-radius: 999px;
  transition: width 0.3s ease;
  min-width: 4px;
}

.bar-chunk {
  background: linear-gradient(90deg, #2A4758, #35576A);
}

.bar-overlap {
  background: linear-gradient(90deg, #915C41, #A86D52);
}

.bar-topk {
  background: linear-gradient(90deg, #5D7568, #6E8A7A);
}

.bar-threshold {
  background: linear-gradient(90deg, #C8B290, #D4C4A2);
}

.param-bar-label {
  font-size: 0.78rem;
  color: var(--muted);
  min-width: 140px;
  white-space: nowrap;
}

/* ── Responsive ── */
@media (max-width: 860px) {
  .admin-hero,
  .panel-head-split,
  .pagination-row,
  .toolbar-row,
  .dialog-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .filter-grid {
    grid-template-columns: 1fr;
  }

  .admin-hero-meta {
    width: 100%;
    grid-template-columns: 1fr 1fr;
  }

  .config-grid {
    grid-template-columns: 1fr;
  }

  .refresh-progress {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .progress-step-text {
    min-width: auto;
  }
}

@media (max-width: 640px) {
  .admin-page {
    padding: 18px 16px 40px;
  }

  .admin-hero,
  .panel-card,
  .admin-dialog {
    padding: 20px;
    border-radius: 22px;
  }

  .admin-hero-meta {
    grid-template-columns: 1fr;
  }

  .actions-row {
    flex-direction: column;
    align-items: stretch;
  }

  .action-btn {
    width: 100%;
  }

  .param-bar-label {
    min-width: 110px;
    font-size: 0.72rem;
  }
}
</style>
