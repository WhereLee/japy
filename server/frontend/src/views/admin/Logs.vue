<template>
  <div class="admin-logs">
    <h2>操作日志</h2>
    <div class="toolbar">
      <select v-model="module">
        <option value="">全部模块</option>
        <option value="auth">认证</option>
        <option value="user">用户</option>
        <option value="annotation">批注</option>
        <option value="comment">评论</option>
        <option value="novel">小说</option>
      </select>
      <input type="date" v-model="startDate" />
      <input type="date" v-model="endDate" />
      <button @click="search">查询</button>
    </div>
    <table>
      <thead>
        <tr>
          <th>ID</th>
          <th>模块</th>
          <th>操作</th>
          <th>方法</th>
          <th>请求URL</th>
          <th>操作人</th>
          <th>IP</th>
          <th>耗时(ms)</th>
          <th>状态</th>
          <th>时间</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="log in list" :key="log.id">
          <td>{{ log.id }}</td>
          <td>{{ log.module }}</td>
          <td>{{ log.operation }}</td>
          <td>{{ log.method }}</td>
          <td>{{ log.requestMethod }} {{ log.requestUrl }}</td>
          <td>{{ log.operatorName }}</td>
          <td>{{ log.ip }}</td>
          <td>{{ log.executeTime }}</td>
          <td><span :class="log.status === '1' ? 'success' : 'error'">{{ log.status === '1' ? '成功' : '失败' }}</span></td>
          <td>{{ log.createdAt }}</td>
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
import { getAdminLogs } from '../../api/index.js'

const list = ref([])
const module = ref('')
const startDate = ref('')
const endDate = ref('')
const page = ref(1)
const total = ref(0)

async function search() {
  page.value = 1
  await loadData()
}

async function loadData() {
  try {
    const res = await getAdminLogs({
      page: page.value,
      size: 10,
      module: module.value,
      startDate: startDate.value,
      endDate: endDate.value
    })
    list.value = res.data.records || []
    total.value = res.data.total || 0
  } catch (e) {
    console.error(e)
  }
}

onMounted(loadData)
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
  display: flex;
  gap: 8px;
}
.toolbar select,
.toolbar input {
  padding: 8px;
  border: 1px solid #ddd;
  border-radius: 4px;
}
.toolbar button {
  padding: 8px 16px;
  background: #409eff;
  color: #fff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}
table {
  width: 100%;
  border-collapse: collapse;
  background: #fff;
  font-size: 13px;
}
th, td {
  padding: 10px 12px;
  text-align: left;
  border-bottom: 1px solid #eee;
}
th {
  background: #fafafa;
  font-weight: 600;
}
.success {
  color: #67c23a;
}
.error {
  color: #e74c3c;
}
.pagination {
  margin-top: 16px;
  display: flex;
  gap: 8px;
  align-items: center;
}
.pagination button {
  padding: 6px 12px;
  border: 1px solid #ddd;
  background: #fff;
  border-radius: 4px;
  cursor: pointer;
}
</style>
