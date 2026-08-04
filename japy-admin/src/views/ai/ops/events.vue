<template>
  <el-card shadow="never" class="page-card">
    <template #header>
      <div class="head">
        <span class="title">信号列表</span>
        <div class="filters">
          <el-select v-model="status" placeholder="状态" clearable style="width: 120px" @change="load">
            <el-option label="待处理" :value="0" />
            <el-option label="已确认" :value="2" />
            <el-option label="已忽略" :value="3" />
          </el-select>
        </div>
      </div>
    </template>

    <el-table :data="rows" v-loading="loading" stripe>
      <el-table-column prop="monitorName" label="检测项" width="140" />
      <el-table-column prop="summary" label="事实描述" min-width="240" show-overflow-tooltip />
      <el-table-column label="置信度" width="90">
        <template #default="{ row }">
          <el-tag :type="confidenceType(row.confidence)" size="small">{{ row.confidence }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="时间" width="165" />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
          <el-button v-if="row.status === 0" link type="success" size="small" v-perm="'ai:event:confirm'" @click="onConfirm(row)">确认</el-button>
          <el-button v-if="row.status === 0" link type="danger" size="small" v-perm="'ai:event:confirm'" @click="onIgnore(row)">忽略</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="(p: number) => { page = p; load() }"
      />
    </div>

    <!-- 详情抽屉 -->
    <el-drawer v-model="drawer" :title="current?.monitorName || '信号详情'" size="520px">
      <template v-if="current">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="检测项">{{ current.monitorName }}</el-descriptions-item>
          <el-descriptions-item label="置信度">{{ current.confidence }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusText(current.status) }}</el-descriptions-item>
          <el-descriptions-item label="时间">{{ current.createTime }}</el-descriptions-item>
        </el-descriptions>
        <div class="sec"><div class="sec-title">事实描述</div><div class="sec-body">{{ current.summary }}</div></div>
        <div class="sec" v-if="current.insight"><div class="sec-title">AI 解读</div><div class="sec-body ai">{{ current.insight }}</div></div>
        <div class="sec" v-if="current.rootCause"><div class="sec-title">根因分析</div><div class="sec-body">{{ current.rootCause }}</div></div>
        <div class="sec" v-if="current.suggestion"><div class="sec-title">处置建议</div><div class="sec-body suggest">{{ current.suggestion }}</div></div>
      </template>
    </el-drawer>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listEvents, confirmEvent, ignoreEvent } from '@/api/ai'
import type { MonitorEvent } from '@/api/ai'

const rows = ref<MonitorEvent[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(15)
const status = ref<number | undefined>(undefined)
const loading = ref(false)
const drawer = ref(false)
const current = ref<MonitorEvent | null>(null)

const statusMap: Record<number, [string, string]> = {
  0: ['待处理', 'warning'],
  2: ['已确认', 'success'],
  3: ['已忽略', 'info']
}
function statusText(s: number) { return statusMap[s]?.[0] ?? '未知' }
function statusType(s: number) { return statusMap[s]?.[1] ?? 'info' as any }
function confidenceType(c: number) { return c >= 0.8 ? 'danger' : c >= 0.5 ? 'warning' : 'info' as any }

async function load() {
  loading.value = true
  try {
    const p = await listEvents({ page: page.value, size: size.value, status: status.value })
    rows.value = p.records
    total.value = p.total
  } finally {
    loading.value = false
  }
}

function openDetail(row: MonitorEvent) {
  current.value = row
  drawer.value = true
}

async function onConfirm(row: MonitorEvent) {
  await ElMessageBox.confirm('确认该问题已处理/已知晓？', '确认', { type: 'info' })
  await confirmEvent(row.id)
  ElMessage.success('已确认')
  load()
}

async function onIgnore(row: MonitorEvent) {
  await ElMessageBox.confirm('标记为误报忽略？', '忽略', { type: 'warning' })
  await ignoreEvent(row.id)
  ElMessage.success('已忽略')
  load()
}

onMounted(load)
</script>

<style scoped>
.page-card { border-radius: 12px; }
.head { display: flex; align-items: center; justify-content: space-between; }
.title { font-weight: 600; }
.pager { margin-top: 14px; display: flex; justify-content: flex-end; }
.sec { margin-top: 18px; }
.sec-title { font-size: 13px; font-weight: 600; color: #4b5563; margin-bottom: 6px; }
.sec-body { font-size: 13px; color: #374151; line-height: 1.7; background: #f8fafc; padding: 10px 12px; border-radius: 8px; white-space: pre-wrap; }
.sec-body.ai { border-left: 3px solid #6366f1; }
.sec-body.suggest { border-left: 3px solid #10b981; }
</style>
