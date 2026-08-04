<template>
  <el-card shadow="never" class="page-card">
    <template #header><span class="title">权限管理</span></template>
    <el-table :data="treeData" v-loading="loading" row-key="id" :tree-props="{ children: 'children' }">
      <el-table-column prop="permName" label="名称" min-width="160" />
      <el-table-column prop="permKey" label="权限标识" min-width="180">
        <template #default="{ row }">{{ row.permKey || '-' }}</template>
      </el-table-column>
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="typeOf(row.permType)">{{ typeText(row.permType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="path" label="路由" width="130">
        <template #default="{ row }">{{ row.path || '-' }}</template>
      </el-table-column>
      <el-table-column prop="sort" label="排序" width="70" />
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { permTree } from '@/api/system'

const treeData = ref<any[]>([])
const loading = ref(false)

function typeText(t: number) {
  return { 1: '目录', 2: '菜单', 3: '按钮' }[t] || '未知'
}
function typeOf(t: number) {
  return ({ 1: 'warning', 2: 'primary', 3: 'info' } as any)[t] || 'info'
}

onMounted(async () => {
  loading.value = true
  try {
    const list = await permTree()
    const map = new Map(list.map((n: any) => [n.id, { ...n, children: [] }]))
    const roots: any[] = []
    map.forEach((n: any) => {
      if (n.parentId === 0) roots.push(n)
      else map.get(n.parentId)?.children.push(n)
    })
    treeData.value = roots
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.page-card { border-radius: 12px; }
</style>
