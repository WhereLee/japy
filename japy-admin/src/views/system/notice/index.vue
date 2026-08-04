<template>
  <el-card shadow="never" class="page-card">
    <template #header><span class="title">公告管理</span></template>
    <el-table :data="rows" v-loading="loading" stripe>
      <el-table-column label="类型" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="row.noticeType === 1 ? 'info' : 'warning'">{{ row.noticeType === 1 ? '通知' : '公告' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="noticeTitle" label="标题" min-width="200" show-overflow-tooltip />
      <el-table-column prop="noticeContent" label="内容" min-width="260" show-overflow-tooltip />
      <el-table-column prop="createTime" label="时间" width="170" />
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button link type="danger" size="small" v-perm="'system:notice:delete'" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listNotices, deleteNotice } from '@/api/system'

const rows = ref<any[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const p = await listNotices({ page: 1, size: 50 })
    rows.value = p.list
  } finally {
    loading.value = false
  }
}

async function onDelete(row: any) {
  await ElMessageBox.confirm(`删除公告「${row.noticeTitle}」？`, '警告', { type: 'warning' })
  await deleteNotice(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.page-card { border-radius: 12px; }
</style>
