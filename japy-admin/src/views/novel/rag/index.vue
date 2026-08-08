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
      <el-table-column prop="novel_id" label="小说ID" width="80" />
      <el-table-column prop="title" label="书名" min-width="140" />
      <el-table-column prop="total" label="检索块数" width="90" />
      <el-table-column label="同步进度" min-width="230">
        <template #default="{ row }">
          <!-- 同步中：阶段文案 + 进度条 -->
          <div v-if="row.task && row.task.status === 'running'" class="task-cell">
            <el-progress :percentage="row.task.percent" :stroke-width="8" :status="row.task.percent >= 100 ? 'success' : ''" />
            <div class="task-phase">{{ row.task.phaseText }}</div>
          </div>
          <!-- 完成：分阶段耗时 -->
          <div v-else-if="row.task && row.task.status === 'done'" class="task-cell">
            <el-tag size="small" type="success">同步完成</el-tag>
            <span class="task-phases">{{ row.task.phasesText }}</span>
          </div>
          <!-- 失败 -->
          <div v-else-if="row.task && row.task.status === 'failed'" class="task-cell">
            <el-tag size="small" type="danger">同步失败</el-tag>
            <span class="task-err">{{ row.task.error }}</span>
          </div>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.total > 0 && row.total === row.vectorized" type="success" size="small">已就绪</el-tag>
          <el-tag v-else-if="row.total > 0" type="warning" size="small">部分完成</el-tag>
          <el-tag v-else type="info" size="small">未索引</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" :disabled="row.task?.status === 'running'" v-perm="'rag:sync'" @click="onSync(row)">
            {{ row.task?.status === 'running' ? '同步中…' : '重建索引' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && !rows.length" description="暂无小说，先上传小说" />
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { ragStatus, ragSync, ragSyncStatus, ragHealth } from '@/api/rag'
import { adminListNovels } from '@/api/novel'

const rows = ref<any[]>([])
const loading = ref(false)
const healthy = ref(false)
const syncingAll = ref(false)
let pollTimer: any = null

const PHASE_TEXT: Record<string, string> = {
  fetch: '拉取自然段…',
  chunk: '切块中…',
  embed: '向量化中（耗时大头，耐心等待）…',
  save: '写入向量库…',
}

async function load() {
  loading.value = true
  try {
    const novels = await adminListNovels({ page: 1, size: 100 })
    const statusData: any = await ragStatus()
    const statusMap = new Map((statusData.novels || []).map((s: any) => [s.novel_id, s]))
    rows.value = novels.list.map((n: any) => ({
      novel_id: n.id,
      title: n.title,
      ...(statusMap.get(n.id) || { total: 0, vectorized: 0 })
    }))
    healthy.value = await ragHealth()
    pollTasks() // 拉取进行中的任务进度
  } catch (e: any) {
    ElMessage.warning(e?.message || 'RAG 服务不可用')
  } finally {
    loading.value = false
  }
}

/** 轮询所有任务的进度（简单方案：逐个查，只查运行中的） */
async function pollTasks() {
  const running = rows.value.filter(r => r.task?.status === 'running')
  const ids = running.length ? running.map(r => r.novel_id) : rows.value.map(r => r.novel_id)
  const tasks = await Promise.all(ids.map(id => ragSyncStatus(id).catch(() => null)))
  let anyRunning = false
  tasks.forEach((t, i) => {
    if (!t) return
    const row = rows.value.find(r => r.novel_id === ids[i])
    if (!row) return
    row.task = {
      status: t.status,
      percent: t.total ? Math.round((t.processed / t.total) * 100) : (t.status === 'running' ? 5 : 100),
      phaseText: PHASE_TEXT[t.phase] || t.phase || '',
      phasesText: fmtPhases(t.phases, t.elapsed),
      error: t.error || '',
    }
    if (t.status === 'running') anyRunning = true
  })
  // 运行中的继续轮询（2s）
  if (pollTimer) clearTimeout(pollTimer)
  if (anyRunning) pollTimer = setTimeout(pollTasks, 2000)
}

function fmtPhases(phases: any, elapsed: number) {
  if (!phases) return elapsed ? `总耗时 ${elapsed}s` : ''
  const parts: string[] = []
  if (phases.fetch_ms != null) parts.push(`拉取 ${(phases.fetch_ms / 1000).toFixed(1)}s`)
  if (phases.chunk_ms != null) parts.push(`切块 ${(phases.chunk_ms / 1000).toFixed(1)}s`)
  if (phases.embed_ms != null) parts.push(`向量化 ${(phases.embed_ms / 1000).toFixed(1)}s`)
  if (phases.save_ms != null) parts.push(`入库 ${(phases.save_ms / 1000).toFixed(1)}s`)
  if (elapsed) parts.push(`总 ${elapsed}s`)
  return parts.join(' · ')
}

async function onSync(row: any) {
  try {
    await ragSync(row.novel_id)
    row.task = { status: 'queued', percent: 1, phaseText: '排队中…' }
    pollTasks()
  } catch (e: any) {
    ElMessage.error(e?.message || '同步触发失败')
  }
}

async function onSyncAll() {
  syncingAll.value = true
  try {
    await ragSync()
    ElMessage.success('已触发全量同步，进度见下方列表')
    pollTasks()
  } catch (e: any) {
    ElMessage.error(e?.message || '同步触发失败')
  } finally {
    syncingAll.value = false
  }
}

onMounted(load)
onUnmounted(() => pollTimer && clearTimeout(pollTimer))
</script>

<style scoped>
.page-card { border-radius: 12px; }
.head { display: flex; align-items: center; justify-content: space-between; }
.title { font-weight: 600; }
.right { display: flex; align-items: center; gap: 10px; }
.task-cell { display: flex; flex-direction: column; gap: 3px; }
.task-phase { font-size: 12px; color: var(--el-color-primary); }
.task-phases { font-size: 12px; color: var(--el-text-color-secondary); }
.task-err { font-size: 12px; color: var(--el-color-danger); max-width: 180px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.muted { color: var(--el-text-color-placeholder); font-size: 12px; }
</style>
