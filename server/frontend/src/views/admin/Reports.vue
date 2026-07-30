<template>
  <div class="admin-reports">
    <h2>举报管理</h2>
    <div class="toolbar">
      <select v-model="status" @change="search">
        <option value="">全部状态</option>
        <option value="pending">待处理</option>
        <option value="resolved">已处理</option>
        <option value="rejected">已驳回</option>
      </select>
      <button @click="search">筛选</button>
    </div>

    <p v-if="loadError" style="color:#e74c3c">{{ loadError }}</p>
    <table v-else>
      <thead>
        <tr>
          <th>ID</th><th>举报人</th><th>目标</th><th>举报原因</th>
          <th>状态</th><th>处理备注</th><th>时间</th><th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="list.length === 0"><td colspan="8" class="empty-row">暂无数据</td></tr>
        <tr v-for="item in list" :key="item.id">
          <td>{{ item.id }}</td>
          <td>{{ item.reporterNickname || ('用户#' + item.reporterId) }}</td>
          <td>{{ item.targetType === 'annotation' ? '批注' : '评论' }} #{{ item.targetId }}</td>
          <td :title="item.reason">{{ item.reason?.substring(0, 24) }}</td>
          <td><span :class="'status-' + item.status">{{ statusText(item.status) }}</span></td>
          <td :title="item.handleNote">{{ item.handleNote || '-' }}</td>
          <td>{{ item.createdAt }}</td>
          <td>
            <template v-if="item.status === 'pending'">
              <button @click="handle(item, 'resolved')" class="btn-small btn-ok">成立</button>
              <button @click="handle(item, 'rejected')" class="btn-small btn-warn">驳回</button>
            </template>
            <span v-else class="handled">已处理</span>
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
import { getAdminReports, handleReport } from '../../api/index.js'

const list = ref([])
const status = ref('')
const page = ref(1)
const total = ref(0)
const loadError = ref('')

function statusText(s) {
  return { pending: '待处理', resolved: '已处理', rejected: '已驳回' }[s] || s
}

function search() {
  page.value = 1
  loadData()
}

async function loadData() {
  loadError.value = ''
  try {
    const res = await getAdminReports({ page: page.value, size: 10, status: status.value })
    list.value = res.data.records || []
    total.value = res.data.total || 0
  } catch (e) {
    loadError.value = e.message || '加载举报列表失败'
  }
}

async function handle(item, newStatus) {
  const label = newStatus === 'resolved' ? '判定成立（将删除被举报内容）' : '驳回'
  if (!confirm(`确定${label}？举报 #${item.id}`)) return
  let handleNote = ''
  if (newStatus === 'rejected') {
    handleNote = prompt('请输入驳回理由（可选）：') || ''
  }
  try {
    await handleReport(item.id, newStatus, handleNote)
    await loadData()
  } catch (e) {
    alert(e.message || '处理失败')
  }
}

onMounted(loadData)
</script>

<style scoped>
.toolbar { margin-bottom: 16px; display: flex; gap: 8px; }
.toolbar select { padding: 8px; border: 1px solid #ddd; border-radius: 4px; }
.toolbar button { padding: 8px 16px; background: #409eff; color: #fff; border: none; border-radius: 4px; cursor: pointer; }
table { width: 100%; border-collapse: collapse; background: #fff; }
th, td { padding: 10px 12px; text-align: left; border-bottom: 1px solid #eee; }
th { background: #fafafa; }
.empty-row { text-align: center; color: #999; padding: 32px 0; }
.status-pending { color: #e6a23c; font-weight: 600; }
.status-resolved { color: #67c23a; }
.status-rejected { color: #909399; }
.btn-small { padding: 4px 10px; border: none; border-radius: 3px; cursor: pointer; color: #fff; margin-right: 4px; }
.btn-ok { background: #67c23a; }
.btn-warn { background: #e6a23c; }
.handled { color: #ccc; font-size: 0.85rem; }
.pagination { margin-top: 16px; display: flex; gap: 8px; align-items: center; }
.pagination button { padding: 6px 12px; border: 1px solid #ddd; background: #fff; border-radius: 4px; cursor: pointer; }
</style>
