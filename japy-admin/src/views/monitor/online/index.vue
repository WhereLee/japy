<template>
  <el-card shadow="never" class="page-card">
    <template #header><span class="title">在线用户</span></template>
    <el-table :data="rows" v-loading="loading" stripe>
      <el-table-column prop="nickname" label="昵称" width="140" />
      <el-table-column prop="username" label="用户名" width="140" />
      <el-table-column prop="loginTime" label="登录时间" min-width="180" />
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button link type="danger" size="small" v-perm="'system:online:forceLogout'" @click="onForce(row)">强制下线</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listOnline, forceLogout } from '@/api/system'
import type { OnlineUser } from '@/api/system'

const rows = ref<OnlineUser[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    rows.value = await listOnline()
  } finally {
    loading.value = false
  }
}

async function onForce(row: OnlineUser) {
  await ElMessageBox.confirm(`强制下线 ${row.nickname}？`, '警告', { type: 'warning' })
  await forceLogout(row.userId)
  ElMessage.success('已下线')
  load()
}

onMounted(load)
</script>

<style scoped>
.page-card { border-radius: 12px; }
.title { font-weight: 600; }
</style>
