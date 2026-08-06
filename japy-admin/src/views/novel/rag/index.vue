<template>
  <el-card shadow="never" class="page-card">
    <template #header>
      <div class="head">
        <span class="title">RAG 索引管理</span>
        <div class="right">
          <el-tag :type="healthy ? 'success' : 'danger'" size="small">
            {{ healthy ? 'AI 服务在线' : 'AI 服务离线' }}
          </el-tag>
          <el-button type="primary" size="small" :loading="syncingAll" v-perm="'rag:sync'" @click="onSyncAll">同步全部</el-button>
        </div>
      </div>
    </template>

    <el-table :data="rows" v-loading="loading" stripe>
      <el-table-column prop="novel_id" label="小说ID" width="90" />
      <el-table-column prop="title" label="书名" min-width="160" />
      <el-table-column prop="total" label="检索块数" width="110" />
      <el-table-column prop="vectorized" label="已向量化" width="110" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.total > 0 && row.total === row.vectorized" type="success" size="small">已就绪</el-tag>
          <el-tag v-else-if="row.total > 0" type="warning" size="small">部分完成</el-tag>
          <el-tag v-else type="info" size="small">未索引</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" :loading="syncingId === row.novel_id" v-perm="'rag:sync'" @click="onSync(row)">
            重建索引
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && !rows.length" description="暂无小说，先上传小说" />
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { ragStatus, ragSync, ragHealth } from '@/api/rag'
import { adminListNovels } from '@/api/novel'

const rows = ref<any[]>([])
const loading = ref(false)
const healthy = ref(false)
const syncingAll = ref(false)
const syncingId = ref<number | null>(null)

async function load() {
  loading.value = true
  try {
    // 小说列表
    const novels = await adminListNovels({ page: 1, size: 100 })
    // 索引状态
    const statusData: any = await ragStatus()
    const statusMap = new Map((statusData.novels || []).map((s: any) => [s.novel_id, s]))
    rows.value = novels.list.map((n: any) => ({
      novel_id: n.id,
      title: n.title,
      ...(statusMap.get(n.id) || { total: 0, vectorized: 0 })
    }))
    healthy.value = await ragHealth()
  } catch (e: any) {
    ElMessage.warning(e?.message || 'RAG 服务不可用')
  } finally {
    loading.value = false
  }
}

async function onSync(row: any) {
  syncingId.value = row.novel_id
  try {
    const r = await ragSync(row.novel_id)
    ElMessage.success(`《${row.title}》同步完成：${r.chunks} 块`)
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '同步失败')
  } finally {
    syncingId.value = null
  }
}

async function onSyncAll() {
  syncingAll.value = true
  try {
    const r = await ragSync()
    ElMessage.success(`同步完成：${r.synced?.length || 0} 本书`)
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '同步失败')
  } finally {
    syncingAll.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page-card { border-radius: 12px; }
.head { display: flex; align-items: center; justify-content: space-between; }
.title { font-weight: 600; }
.right { display: flex; align-items: center; gap: 10px; }
</style>
