<template>
  <div class="admin-annotations">
    <h2>批注管理</h2>
    <div class="toolbar">
      <input v-model="keyword" placeholder="搜索内容" @keyup.enter="search" />
      <select v-model="type"><option value="">全部类型</option><option value="0">普通</option><option value="1">数据校验</option></select>
      <button @click="search">搜索</button>
    </div>
    <p v-if="loadError" style="color:#e74c3c">{{ loadError }}</p>
    <table v-else>
      <thead><tr><th>ID</th><th>用户</th><th>小说</th><th>章节</th><th>内容</th><th>类型</th><th>点赞</th><th>评论</th><th>时间</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-if="list.length === 0"><td colspan="10" class="empty-row">暂无数据</td></tr>
        <tr v-for="item in list" :key="item.id">
          <td>{{ item.id }}</td>
          <td>{{ item.userNickname || item.userId }}</td>
          <td>{{ item.novelTitle || '-' }}</td>
          <td>{{ item.chapterTitle || '-' }}</td>
          <td :title="item.content">{{ item.content?.substring(0, 30) }}</td>
          <td>{{ item.type === 1 ? '数据校验' : '普通' }}</td>
          <td>{{ item.likeCount }}</td>
          <td>{{ item.commentCount }}</td>
          <td>{{ item.createdAt }}</td>
          <td><button @click="handleDelete(item)" class="btn-small btn-danger">删除</button></td>
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
import { getAdminAnnotations, deleteAdminAnnotation } from '../../api/index.js'

const list = ref([])
const keyword = ref('')
const type = ref('')
const page = ref(1)
const total = ref(0)
const loadError = ref('')

async function search() {
  page.value = 1
  await loadData()
}

async function loadData() {
  try {
    const res = await getAdminAnnotations({ page: page.value, size: 10, keyword: keyword.value, type: type.value })
    list.value = res.data.records || []
    total.value = res.data.total || 0
  } catch (e) { loadError.value = e.message || '加载批注列表失败' }
}

async function handleDelete(item) {
  if (!confirm('确定删除此批注？')) return
  try {
    await deleteAdminAnnotation(item.id)
    loadData()
  } catch (e) { alert(e.message) }
}

onMounted(loadData)
</script>

<style scoped>
.toolbar { margin-bottom: 16px; display: flex; gap: 8px; }
.toolbar input, .toolbar select { padding: 8px; border: 1px solid #ddd; border-radius: 4px; }
.toolbar button { padding: 8px 16px; background: #409eff; color: #fff; border: none; border-radius: 4px; cursor: pointer; }
table { width: 100%; border-collapse: collapse; background: #fff; }
th, td { padding: 10px 12px; text-align: left; border-bottom: 1px solid #eee; }
th { background: #fafafa; }
.empty-row { text-align: center; color: #999; padding: 32px 0; }
.btn-small { padding: 4px 10px; border: none; border-radius: 3px; cursor: pointer; color: #fff; }
.btn-danger { background: #e74c3c; }
.pagination { margin-top: 16px; display: flex; gap: 8px; align-items: center; }
.pagination button { padding: 6px 12px; border: 1px solid #ddd; background: #fff; border-radius: 4px; cursor: pointer; }
</style>
