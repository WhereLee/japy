<template>
  <el-card shadow="never" class="page-card">
    <template #header>
      <div class="head">
        <span class="title">操作日志</span>
        <el-button type="danger" plain size="small" v-perm="'system:operlog:clean'" @click="onClean">清空日志</el-button>
      </div>
    </template>
    <el-table :data="rows" v-loading="loading" stripe>
      <el-table-column prop="title" label="模块" width="110" />
      <el-table-column prop="operName" label="操作人" width="110" />
      <el-table-column prop="requestMethod" label="方式" width="70" />
      <el-table-column prop="method" label="方法" min-width="200" show-overflow-tooltip />
      <el-table-column prop="operIp" label="IP" width="120" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small">{{ row.status === 0 ? '成功' : '失败' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="costTime" label="耗时(ms)" width="90" />
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { listOperLogs, cleanOperLogs } from '@/api/system'
import type { OperLog } from '@/api/system'

const rows = ref<OperLog[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(15)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const p = await listOperLogs({ page: page.value, size: size.value })
    rows.value = p.list
    total.value = p.total
  } finally {
    loading.value = false
  }
}

async function onClean() {
  await ElMessageBox.confirm('清空全部操作日志？不可恢复', '警告', { type: 'warning' })
  await cleanOperLogs()
  ElMessage.success('已清空')
  load()
}

onMounted(load)
</script>

<style scoped>
.page-card { border-radius: 12px; }
.head { display: flex; align-items: center; justify-content: space-between; }
.title { font-weight: 600; }
.pager { margin-top: 14px; display: flex; justify-content: flex-end; }
</style>
