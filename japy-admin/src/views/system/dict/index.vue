<template>
  <el-card shadow="never" class="page-card">
    <template #header><span class="title">字典管理</span></template>
    <el-table :data="types" v-loading="loading" stripe>
      <el-table-column prop="dictName" label="字典名称" width="180" />
      <el-table-column prop="dictType" label="字典类型" min-width="200" />
      <el-table-column prop="remark" label="备注" min-width="200" />
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openData(row)">数据项</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-drawer v-model="drawer" :title="`数据项 - ${current?.dictName}`" size="480px">
      <el-table :data="dataRows" v-loading="dataLoading" stripe>
        <el-table-column prop="dictLabel" label="标签" width="120" />
        <el-table-column prop="dictValue" label="键值" width="100" />
        <el-table-column prop="sort" label="排序" width="70" />
        <el-table-column prop="remark" label="备注" />
      </el-table>
    </el-drawer>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { listDictTypes, listDictData } from '@/api/system'

const types = ref<any[]>([])
const loading = ref(false)
const drawer = ref(false)
const current = ref<any>(null)
const dataRows = ref<any[]>([])
const dataLoading = ref(false)

async function openData(row: any) {
  current.value = row
  drawer.value = true
  dataLoading.value = true
  try {
    dataRows.value = await listDictData(row.dictType)
  } finally {
    dataLoading.value = false
  }
}

onMounted(async () => {
  loading.value = true
  try {
    types.value = await listDictTypes()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.page-card { border-radius: 12px; }
</style>
