<template>
  <el-card shadow="never" class="page-card">
    <template #header><span class="title">参数管理</span></template>
    <el-table :data="rows" v-loading="loading" stripe>
      <el-table-column prop="configName" label="参数名称" width="180" />
      <el-table-column prop="configKey" label="参数键" min-width="200" />
      <el-table-column prop="configValue" label="参数值" min-width="220" show-overflow-tooltip />
      <el-table-column prop="remark" label="备注" min-width="160" />
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { listConfigs } from '@/api/system'

const rows = ref<any[]>([])
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    const p = await listConfigs({ page: 1, size: 50 })
    rows.value = p.list
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.page-card { border-radius: 12px; }
</style>
