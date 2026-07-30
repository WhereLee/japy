<template>
  <div class="admin-users">
    <h2>用户管理</h2>
    <div class="toolbar">
      <input v-model="keyword" placeholder="搜索用户名/昵称" @keyup.enter="search" />
      <button @click="search">搜索</button>
    </div>
    <p v-if="loadError" style="color:#e74c3c">{{ loadError }}</p>
    <table v-else>
      <thead><tr><th>ID</th><th>用户名</th><th>昵称</th><th>角色</th><th>状态</th><th>注册时间</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-if="users.length === 0"><td colspan="7" class="empty-row">暂无数据</td></tr>
        <tr v-for="user in users" :key="user.id">
          <td>{{ user.id }}</td>
          <td>{{ user.username }}</td>
          <td>{{ user.nickname }}</td>
          <td>{{ user.role }}</td>
          <td><span :class="user.status === 1 ? 'active' : 'disabled'">{{ user.status === 1 ? '正常' : '禁用' }}</span></td>
          <td>{{ user.createdAt }}</td>
          <td>
            <button @click="toggleStatus(user)" class="btn-small">{{ user.status === 1 ? '禁用' : '启用' }}</button>
            <button @click="resetPwd(user)" class="btn-small btn-warn">重置密码</button>
          </td>
        </tr>
      </tbody>
    </table>
    <div class="pagination">
      <button :disabled="page <= 1" @click="page--; loadData()">上一页</button>
      <span>第 {{ page }} 页 / 共 {{ Math.ceil(total / 10) || 1 }} 页</span>
      <button :disabled="page * 10 >= total" @click="page++; loadData()">下一页</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getAdminUsers, updateUserStatus, resetUserPassword } from '../../api/index.js'

const users = ref([])
const keyword = ref('')
const page = ref(1)
const total = ref(0)
const loadError = ref('')

async function search() {
  page.value = 1
  await loadData()
}

async function loadData() {
  loadError.value = ''
  try {
    const res = await getAdminUsers({ page: page.value, size: 10, keyword: keyword.value })
    users.value = res.data.records || []
    total.value = res.data.total || 0
  } catch (e) { loadError.value = e.message || '加载用户列表失败' }
}

async function toggleStatus(user) {
  const newStatus = user.status === 1 ? 0 : 1
  try {
    await updateUserStatus(user.id, newStatus)
    user.status = newStatus
  } catch (e) { alert(e.message) }
}

async function resetPwd(user) {
  if (!confirm(`确定重置 ${user.username} 的密码为默认密码？`)) return
  try {
    await resetUserPassword(user.id)
    alert('密码已重置')
  } catch (e) { alert(e.message) }
}

onMounted(loadData)
</script>

<style scoped>
.toolbar { margin-bottom: 16px; display: flex; gap: 8px; }
.toolbar input { padding: 8px; border: 1px solid #ddd; border-radius: 4px; }
.toolbar button { padding: 8px 16px; background: #409eff; color: #fff; border: none; border-radius: 4px; cursor: pointer; }
table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 8px; overflow: hidden; }
th, td { padding: 10px 12px; text-align: left; border-bottom: 1px solid #eee; }
th { background: #fafafa; font-weight: 600; }
.active { color: #67c23a; } .disabled { color: #e74c3c; }
.empty-row { text-align: center; color: #999; padding: 32px 0; }
.btn-small { padding: 4px 10px; border: none; border-radius: 3px; cursor: pointer; background: #409eff; color: #fff; margin-right: 4px; }
.btn-warn { background: #e6a23c; }
.pagination { margin-top: 16px; display: flex; gap: 8px; align-items: center; }
.pagination button { padding: 6px 12px; border: 1px solid #ddd; background: #fff; border-radius: 4px; cursor: pointer; }
</style>
