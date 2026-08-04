<template>
  <el-card shadow="never" class="page-card">
    <template #header><span class="title">登录日志</span></template>
    <el-table :data="rows" v-loading="loading" stripe>
      <el-table-column prop="username" label="用户名" width="130" />
      <el-table-column prop="ipaddr" label="IP 地址" width="140" />
      <el-table-column label="结果" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small">{{ row.status === 0 ? '成功' : '失败' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="msg" label="说明" min-width="200" show-overflow-tooltip />
      <el-table-column prop="createTime" label="时间" width="165" />
    </el-table>
    <div class="pager">
      <el-pagination layout="total, prev, pager, next" :total="total" :page-size="size"
        :current-page="page" @current-change="(p: number) => { page = p; load() }" />
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { listLoginLogs } from '@/api/system'
import type { LoginLog } from '@/api/system'

const rows = ref<LoginLog[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(15)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const p = await listLoginLogs({ page: page.value, size: size.value })
    rows.value = p.list
    total.value = p.total
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page-card { border-radius: 12px; }
.title { font-weight: 600; }
.pager { margin-top: 14px; display: flex; justify-content: flex-end; }
</style>
