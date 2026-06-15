<script setup>
import {computed, onMounted, reactive, ref} from 'vue'
import {deleteRagDocument, listRagDocuments, refreshRagVectorDb, saveRagDocument} from '../api/rag.js'

const loading = ref(false)
const saving = ref(false)
const refreshing = ref(false)
const editVisible = ref(false)
const pageState = reactive({ current: 1, pageSize: 10, total: 0, records: [] })
const queryForm = reactive({ title: '', sourceFile: '', status: '' })
const form = reactive({ id: null, title: '', sourceFile: '', content: '', tags: '', status: 'published', chunkSize: 1200, chunkOverlap: 120, topK: 3, similarityThreshold: 0.5 })

const totalPages = computed(() => Math.max(1, Math.ceil(pageState.total / pageState.pageSize)))

function openCreate() {
  Object.assign(form, { id: null, title: '', sourceFile: '', content: '', tags: '', status: 'published', chunkSize: 1200, chunkOverlap: 120, topK: 3, similarityThreshold: 0.5 })
  editVisible.value = true
}

function openEdit(item) {
  Object.assign(form, item)
  editVisible.value = true
}

async function fetchList(page = 1) {
  loading.value = true
  try {
    const result = await listRagDocuments({ ...queryForm, current: page, pageSize: pageState.pageSize })
    pageState.current = Number(result?.current || page)
    pageState.pageSize = Number(result?.size || pageState.pageSize)
    pageState.total = Number(result?.total || 0)
    pageState.records = Array.isArray(result?.records) ? result.records : []
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  saving.value = true
  try {
    await saveRagDocument({ ...form })
    editVisible.value = false
    await fetchList(pageState.current)
  } finally {
    saving.value = false
  }
}

async function handleDelete(item) {
  if (!window.confirm(`确认删除文档「${item.title}」吗？`)) return
  await deleteRagDocument(item.id)
  await fetchList(pageState.current)
}

async function handleRefresh() {
  refreshing.value = true
  try {
    await refreshRagVectorDb()
    await fetchList(pageState.current)
  } finally {
    refreshing.value = false
  }
}

onMounted(() => fetchList(1))
</script>

<template>
  <div class="admin-page">
    <section class="admin-hero">
      <div>
        <span class="admin-badge">RAG 知识库管理</span>
        <h1 class="admin-title">知识文档与向量库</h1>
        <p class="admin-sub">支持文档 CRUD、参数设定、手动刷新向量数据库，启动自动向量化与手动刷新复用同一套逻辑。</p>
      </div>
      <div class="admin-hero-meta">
        <div class="meta-card"><strong>{{ pageState.total }}</strong><span>文档总数</span></div>
      </div>
    </section>

    <section class="panel-card" style="margin-top:22px;">
      <div class="panel-head panel-head-split">
        <div><h2>检索条件</h2><p>按标题、来源文件和状态筛选知识库。</p></div>
        <div class="toolbar-row">
          <button class="action-btn action-btn-secondary" @click="handleRefresh">{{ refreshing ? '刷新中...' : '刷新向量数据库' }}</button>
          <button class="action-btn action-btn-primary" @click="openCreate">新增文档</button>
        </div>
      </div>
      <div class="filter-grid">
        <label class="field"><span class="field-label">标题</span><input v-model="queryForm.title" class="field-input" /></label>
        <label class="field"><span class="field-label">来源文件</span><input v-model="queryForm.sourceFile" class="field-input" /></label>
        <label class="field"><span class="field-label">状态</span><input v-model="queryForm.status" class="field-input" /></label>
      </div>
      <div class="actions-row"><button class="action-btn action-btn-primary" @click="fetchList(1)">查询</button></div>
    </section>

    <section class="panel-card" style="margin-top:22px;">
      <div class="panel-head"><h2>文档列表</h2></div>
      <div v-if="loading" class="empty-state">加载中...</div>
      <div v-else class="table-wrap">
        <table class="user-table">
          <thead><tr><th>ID</th><th>标题</th><th>来源</th><th>状态</th><th>参数</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="item in pageState.records" :key="item.id">
              <td>{{ item.id }}</td><td>{{ item.title }}</td><td>{{ item.sourceFile }}</td><td>{{ item.status }}</td>
              <td>chunk={{ item.chunkSize }}, overlap={{ item.chunkOverlap }}, topK={{ item.topK }}</td>
              <td><button class="table-btn" @click="openEdit(item)">编辑</button><button class="table-btn table-btn-danger" @click="handleDelete(item)">删除</button></td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <div v-if="editVisible" class="admin-dialog-mask" @click.self="editVisible=false">
      <div class="admin-dialog">
        <div class="dialog-head"><div><h2>文档编辑</h2></div><button class="dialog-close" @click="editVisible=false">×</button></div>
        <div class="form-stack">
          <label class="field"><span class="field-label">标题</span><input v-model="form.title" class="field-input" /></label>
          <label class="field"><span class="field-label">来源文件</span><input v-model="form.sourceFile" class="field-input" /></label>
          <label class="field"><span class="field-label">内容</span><textarea v-model="form.content" class="field-input field-textarea" rows="6"></textarea></label>
          <label class="field"><span class="field-label">标签</span><input v-model="form.tags" class="field-input" /></label>
          <label class="field"><span class="field-label">状态</span><input v-model="form.status" class="field-input" /></label>
          <label class="field"><span class="field-label">chunkSize</span><input v-model.number="form.chunkSize" class="field-input" type="number" /></label>
          <label class="field"><span class="field-label">chunkOverlap</span><input v-model.number="form.chunkOverlap" class="field-input" type="number" /></label>
          <label class="field"><span class="field-label">topK</span><input v-model.number="form.topK" class="field-input" type="number" /></label>
          <label class="field"><span class="field-label">similarityThreshold</span><input v-model.number="form.similarityThreshold" class="field-input" type="number" step="0.01" /></label>
        </div>
        <div class="actions-row"><button class="action-btn action-btn-primary" @click="handleSave">{{ saving ? '保存中...' : '保存' }}</button></div>
      </div>
    </div>
  </div>
</template>
