<template>
  <div class="page-card">
    <div class="toolbar">
      <el-input
        v-model="newWord" placeholder="输入敏感词，回车或点击添加" clearable style="width:300px"
        @keyup.enter="addWord"
      >
        <template #append>
          <el-button type="primary" :icon="Plus" @click="addWord">添加</el-button>
        </template>
      </el-input>
      <span style="color:#8a919f;font-size:13px;">添加/删除即时生效（内存缓存 60s 自动刷新）</span>
    </div>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="word" label="敏感词" min-width="200" />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button type="danger" size="small" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import http from '../api'

const list = ref([])
const newWord = ref('')
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    list.value = await http.get('/api/admin/sensitive-words')
  } finally {
    loading.value = false
  }
}

async function addWord() {
  const word = newWord.value.trim()
  if (!word) { ElMessage.warning('请输入敏感词'); return }
  await http.post('/api/admin/sensitive-words', { word })
  ElMessage.success('已添加')
  newWord.value = ''
  load()
}

async function remove(row) {
  await ElMessageBox.confirm(`删除敏感词「${row.word}」？`, '删除', { type: 'warning' })
  await http.delete(`/api/admin/sensitive-words/${row.id}`)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>
