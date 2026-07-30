<template>
  <div class="admin-novels">
    <h2>小说管理</h2>
    <div class="toolbar">
      <button @click="handleImport" class="btn-import">导入小说</button>
    </div>
    <p v-if="loadError" style="color:#e74c3c">{{ loadError }}</p>
    <table v-else>
      <thead><tr><th>ID</th><th>标题</th><th>文件名</th><th>创建时间</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-if="list.length === 0"><td colspan="5" class="empty-row">暂无数据</td></tr>
        <tr v-for="novel in list" :key="novel.id">
          <td>{{ novel.id }}</td><td>{{ novel.title }}</td><td>{{ novel.fileName }}</td><td>{{ novel.createdAt }}</td>
          <td><button @click="handleDelete(novel)" class="btn-small btn-danger">删除</button></td>
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
import { getAdminNovels, importNovels, deleteAdminNovel } from '../../api/index.js'

const list = ref([])
const page = ref(1)
const total = ref(0)
const loadError = ref('')

async function loadData() {
  try {
    const res = await getAdminNovels({ page: page.value, size: 10 })
    list.value = res.data.records || []
    total.value = res.data.total || 0
  } catch (e) { loadError.value = e.message || '加载小说列表失败' }
}

async function handleImport() {
  try {
    await importNovels()
    alert('导入完成')
    loadData()
  } catch (e) { alert(e.message) }
}

async function handleDelete(novel) {
  if (!confirm(`确定删除《${novel.title}》？`)) return
  try {
    await deleteAdminNovel(novel.id)
    loadData()
  } catch (e) { alert(e.message) }
}

onMounted(loadData)
</script>

<style scoped>
.toolbar { margin-bottom: 16px; }
.btn-import { padding: 8px 16px; background: #67c23a; color: #fff; border: none; border-radius: 4px; cursor: pointer; }
table { width: 100%; border-collapse: collapse; background: #fff; }
th, td { padding: 10px 12px; text-align: left; border-bottom: 1px solid #eee; }
th { background: #fafafa; }
.empty-row { text-align: center; color: #999; padding: 32px 0; }
.btn-small { padding: 4px 10px; border: none; border-radius: 3px; cursor: pointer; color: #fff; }
.btn-danger { background: #e74c3c; }
.pagination { margin-top: 16px; display: flex; gap: 8px; align-items: center; }
.pagination button { padding: 6px 12px; border: 1px solid #ddd; background: #fff; border-radius: 4px; cursor: pointer; }
</style>
