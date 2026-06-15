<script setup>
import {computed, onMounted, reactive, ref} from 'vue'
import {addUser, deleteUser, listUsersByPage, updateUser, USER_ROLE_OPTIONS,} from '../api/user.js'

const loading = ref(false)
const createLoading = ref(false)
const updateLoading = ref(false)
const deleteId = ref(null)
const createDialogVisible = ref(false)
const editDialogVisible = ref(false)
const pageState = reactive({
  current: 1,
  pageSize: 8,
  total: 0,
  records: [],
})
const feedback = reactive({
  type: '',
  text: '',
})

const queryForm = reactive({
  userAccount: '',
  userName: '',
  userRole: '',
})

const createForm = reactive({
  userAccount: '',
  userName: '',
  userAvatar: '',
  userProfile: '',
  userRole: 'user',
})

const editForm = reactive({
  id: null,
  userName: '',
  userAvatar: '',
  userProfile: '',
  userRole: 'user',
})

const hasRecords = computed(() => pageState.records.length > 0)
const totalPages = computed(() => Math.max(1, Math.ceil(pageState.total / pageState.pageSize)))
const roleOptionsWithAll = computed(() => [{label: '全部角色', value: ''}, ...USER_ROLE_OPTIONS])

function setFeedback(type, text) {
  feedback.type = type
  feedback.text = text
}

function clearFeedback() {
  feedback.type = ''
  feedback.text = ''
}

function normalizeQueryPayload(page = pageState.current) {
  return {
    current: page,
    pageSize: pageState.pageSize,
    userAccount: queryForm.userAccount.trim() || undefined,
    userName: queryForm.userName.trim() || undefined,
    userRole: queryForm.userRole || undefined,
    sortField: 'createTime',
    sortOrder: 'descend',
  }
}

async function fetchUsers(page = pageState.current) {
  loading.value = true
  clearFeedback()
  try {
    const result = await listUsersByPage(normalizeQueryPayload(page))
    pageState.current = Number(result?.current || page)
    pageState.pageSize = Number(result?.size || pageState.pageSize)
    pageState.total = Number(result?.total || 0)
    pageState.records = Array.isArray(result?.records) ? result.records : []
    if (!pageState.records.length && pageState.current > 1) {
      await fetchUsers(pageState.current - 1)
    }
  } catch (error) {
    pageState.records = []
    pageState.total = 0
    setFeedback('error', error?.message || '获取用户列表失败')
  } finally {
    loading.value = false
  }
}

function resetCreateForm() {
  createForm.userAccount = ''
  createForm.userName = ''
  createForm.userAvatar = ''
  createForm.userProfile = ''
  createForm.userRole = 'user'
}

function openCreateDialog() {
  resetCreateForm()
  clearFeedback()
  createDialogVisible.value = true
}

function closeCreateDialog() {
  createDialogVisible.value = false
  resetCreateForm()
}

function startEdit(user) {
  editForm.id = user.id
  editForm.userName = user.userName || ''
  editForm.userAvatar = user.userAvatar || ''
  editForm.userProfile = user.userProfile || ''
  editForm.userRole = user.userRole || 'user'
  clearFeedback()
  editDialogVisible.value = true
}

function resetEditForm() {
  editForm.id = null
  editForm.userName = ''
  editForm.userAvatar = ''
  editForm.userProfile = ''
  editForm.userRole = 'user'
}

function closeEditDialog() {
  editDialogVisible.value = false
  resetEditForm()
}

async function handleSearch() {
  await fetchUsers(1)
}

async function handleResetSearch() {
  queryForm.userAccount = ''
  queryForm.userName = ''
  queryForm.userRole = ''
  await fetchUsers(1)
}

async function handleCreate() {
  clearFeedback()
  if (!createForm.userAccount.trim()) {
    setFeedback('error', '请输入新用户账号')
    return
  }
  if (createForm.userAccount.trim().length < 4) {
    setFeedback('error', '账号长度不能少于 4 位')
    return
  }
  createLoading.value = true
  try {
    await addUser({
      userAccount: createForm.userAccount.trim(),
      userName: createForm.userName.trim() || undefined,
      userAvatar: createForm.userAvatar.trim() || undefined,
      userProfile: createForm.userProfile.trim() || undefined,
      userRole: createForm.userRole,
    })
    createDialogVisible.value = false
    resetCreateForm()
    setFeedback('success', '用户创建成功，默认密码为 12345678')
    await fetchUsers(1)
  } catch (error) {
    setFeedback('error', error?.message || '创建用户失败')
  } finally {
    createLoading.value = false
  }
}

async function handleUpdate() {
  clearFeedback()
  if (!editForm.id) {
    setFeedback('error', '请先选择要编辑的用户')
    return
  }
  updateLoading.value = true
  try {
    await updateUser({
      id: editForm.id,
      userName: editForm.userName.trim() || undefined,
      userAvatar: editForm.userAvatar.trim() || undefined,
      userProfile: editForm.userProfile.trim() || undefined,
      userRole: editForm.userRole,
    })
    editDialogVisible.value = false
    setFeedback('success', '用户信息已更新')
    await fetchUsers(pageState.current)
    resetEditForm()
  } catch (error) {
    setFeedback('error', error?.message || '更新用户失败')
  } finally {
    updateLoading.value = false
  }
}

async function handleDelete(user) {
  const confirmed = window.confirm(`确认删除用户「${user.userAccount}」吗？`)
  if (!confirmed) {
    return
  }
  clearFeedback()
  deleteId.value = user.id
  try {
    await deleteUser(user.id)
    if (editForm.id === user.id) {
      editDialogVisible.value = false
      resetEditForm()
    }
    setFeedback('success', '用户已删除')
    await fetchUsers(pageState.current)
  } catch (error) {
    setFeedback('error', error?.message || '删除用户失败')
  } finally {
    deleteId.value = null
  }
}

async function goPrevPage() {
  if (pageState.current <= 1 || loading.value) {
    return
  }
  await fetchUsers(pageState.current - 1)
}

async function goNextPage() {
  if (pageState.current >= totalPages.value || loading.value) {
    return
  }
  await fetchUsers(pageState.current + 1)
}

function formatRole(role) {
  return USER_ROLE_OPTIONS.find((item) => item.value === role)?.label || role || '-'
}

function formatTime(value) {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

onMounted(async () => {
  await fetchUsers(1)
})
</script>

<template>
  <div class="admin-page">
    <section class="admin-hero">
      <div>
        <span class="admin-badge">管理员工作台</span>
        <h1 class="admin-title">用户管理</h1>
        <p class="admin-sub">
          支持管理员筛选、分页查看、新增、编辑和删除用户。创建用户后默认密码为 12345678。
        </p>
      </div>
      <div class="admin-hero-meta">
        <div class="meta-card">
          <strong>{{ pageState.total }}</strong>
          <span>当前总用户数</span>
        </div>
        <div class="meta-card">
          <strong>{{ pageState.current }}</strong>
          <span>当前页码</span>
        </div>
      </div>
    </section>

    <p v-if="feedback.text" :class="feedback.type === 'error' ? 'feedback-error' : 'feedback-success'" class="feedback">
      {{ feedback.text }}
    </p>

    <section class="admin-grid">
      <article class="panel-card">
        <div class="panel-head">
          <div>
            <h2>筛选条件</h2>
            <p>按账号、昵称、角色快速查询管理员视角下的用户列表。</p>
          </div>
        </div>

        <div class="filter-grid">
          <label class="field">
            <span class="field-label">账号</span>
            <input v-model="queryForm.userAccount" class="field-input" placeholder="按账号模糊搜索" type="text"/>
          </label>
          <label class="field">
            <span class="field-label">昵称</span>
            <input v-model="queryForm.userName" class="field-input" placeholder="按昵称模糊搜索" type="text"/>
          </label>
          <label class="field">
            <span class="field-label">角色</span>
            <select v-model="queryForm.userRole" class="field-input field-select">
              <option v-for="item in roleOptionsWithAll" :key="item.value || 'all'" :value="item.value">
                {{ item.label }}
              </option>
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

      <article class="panel-card">
        <div class="panel-head panel-head-split">
          <div>
            <h2>用户列表</h2>
            <p>点击“编辑”可弹出对话框更新用户资料与角色。</p>
          </div>
          <div class="toolbar-row">
            <div class="pagination-meta">
              第 {{ pageState.current }} / {{ totalPages }} 页
            </div>
            <button class="action-btn action-btn-primary" type="button" @click="openCreateDialog">
              新增用户
            </button>
          </div>
        </div>

        <div v-if="loading" class="empty-state">正在加载用户列表...</div>

        <div v-else-if="!hasRecords" class="empty-state">暂无符合条件的用户。</div>

        <div v-else class="table-wrap">
          <table class="user-table">
            <thead>
            <tr>
              <th>ID</th>
              <th>账号</th>
              <th>昵称</th>
              <th>角色</th>
              <th>简介</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="user in pageState.records" :key="user.id">
              <td>{{ user.id }}</td>
              <td>{{ user.userAccount }}</td>
              <td>{{ user.userName || '-' }}</td>
              <td>
                <span :data-role="user.userRole" class="role-chip">{{ formatRole(user.userRole) }}</span>
              </td>
              <td class="user-profile-cell">{{ user.userProfile || '-' }}</td>
              <td>{{ formatTime(user.createTime) }}</td>
              <td>
                <div class="table-actions">
                  <button class="table-btn" type="button" @click="startEdit(user)">编辑</button>
                  <button
                      :disabled="deleteId === user.id"
                      class="table-btn table-btn-danger"
                      type="button"
                      @click="handleDelete(user)"
                  >
                    {{ deleteId === user.id ? '删除中...' : '删除' }}
                  </button>
                </div>
              </td>
            </tr>
            </tbody>
          </table>
        </div>

        <div class="pagination-row">
          <button :disabled="pageState.current <= 1 || loading" class="action-btn action-btn-secondary" type="button"
                  @click="goPrevPage">
            上一页
          </button>
          <span class="pagination-text">共 {{ pageState.total }} 条</span>
          <button :disabled="pageState.current >= totalPages || loading" class="action-btn action-btn-secondary"
                  type="button"
                  @click="goNextPage">
            下一页
          </button>
        </div>
      </article>
    </section>

    <div v-if="createDialogVisible" class="admin-dialog-mask" @click.self="closeCreateDialog">
      <div class="admin-dialog">
        <div class="dialog-head">
          <div>
            <h2>新增用户</h2>
            <p>新增后用户可使用默认密码登录，建议后续提示其自行修改。</p>
          </div>
          <button class="dialog-close" type="button" @click="closeCreateDialog">×</button>
        </div>

        <div class="form-stack">
          <label class="field">
            <span class="field-label">账号</span>
            <input v-model="createForm.userAccount" class="field-input" placeholder="至少 4 位" type="text"/>
          </label>
          <label class="field">
            <span class="field-label">昵称</span>
            <input v-model="createForm.userName" class="field-input" placeholder="可选" type="text"/>
          </label>
          <label class="field">
            <span class="field-label">头像地址</span>
            <input v-model="createForm.userAvatar" class="field-input" placeholder="可选" type="text"/>
          </label>
          <label class="field">
            <span class="field-label">简介</span>
            <textarea v-model="createForm.userProfile" class="field-input field-textarea" placeholder="可选"
                      rows="4"></textarea>
          </label>
          <label class="field">
            <span class="field-label">角色</span>
            <select v-model="createForm.userRole" class="field-input field-select">
              <option v-for="item in USER_ROLE_OPTIONS" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
          </label>
        </div>

        <div class="actions-row">
          <button :disabled="createLoading" class="action-btn action-btn-primary" type="button" @click="handleCreate">
            {{ createLoading ? '创建中...' : '创建用户' }}
          </button>
          <button :disabled="createLoading" class="action-btn action-btn-secondary" type="button"
                  @click="closeCreateDialog">
            取消
          </button>
        </div>
      </div>
    </div>

    <div v-if="editDialogVisible" class="admin-dialog-mask" @click.self="closeEditDialog">
      <div class="admin-dialog">
        <div class="dialog-head">
          <div>
            <h2>编辑用户</h2>
            <p>{{ editForm.id ? `正在编辑用户 #${editForm.id}` : '请先从左侧列表选择一个用户' }}</p>
          </div>
          <button class="dialog-close" type="button" @click="closeEditDialog">×</button>
        </div>

        <div class="form-stack">
          <label class="field">
            <span class="field-label">昵称</span>
            <input v-model="editForm.userName" :disabled="!editForm.id" class="field-input" placeholder="更新昵称"
                   type="text"/>
          </label>
          <label class="field">
            <span class="field-label">头像地址</span>
            <input v-model="editForm.userAvatar" :disabled="!editForm.id" class="field-input" placeholder="更新头像"
                   type="text"/>
          </label>
          <label class="field">
            <span class="field-label">简介</span>
            <textarea v-model="editForm.userProfile" :disabled="!editForm.id" class="field-input field-textarea"
                      placeholder="更新简介"
                      rows="4"></textarea>
          </label>
          <label class="field">
            <span class="field-label">角色</span>
            <select v-model="editForm.userRole" :disabled="!editForm.id" class="field-input field-select">
              <option v-for="item in USER_ROLE_OPTIONS" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
          </label>
        </div>

        <div class="actions-row">
          <button :disabled="!editForm.id || updateLoading" class="action-btn action-btn-primary" type="button"
                  @click="handleUpdate">
            {{ updateLoading ? '保存中...' : '保存修改' }}
          </button>
          <button :disabled="updateLoading" class="action-btn action-btn-secondary" type="button"
                  @click="closeEditDialog">
            取消
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin-page {
  max-width: 1180px;
  margin: 0 auto;
  padding: 24px 24px 56px;
}

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

.actions-row-full .action-btn {
  width: 100%;
}

.action-btn {
  min-width: 96px;
  height: 44px;
  border-radius: 14px;
  border: none;
  font-size: 0.92rem;
  font-weight: 700;
  cursor: pointer;
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

.table-wrap {
  overflow-x: auto;
}

.user-table {
  width: 100%;
  border-collapse: collapse;
  min-width: 820px;
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

.user-profile-cell {
  min-width: 180px;
  color: var(--muted);
  line-height: 1.6;
}

.role-chip {
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

.role-chip[data-role='admin'] {
  color: #2f6e5a;
  background: rgba(95, 122, 98, 0.12);
}

.role-chip[data-role='ban'] {
  color: var(--danger);
  background: rgba(192, 86, 61, 0.1);
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
}

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
}
</style>
