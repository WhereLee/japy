<template>
  <el-card shadow="never" class="page-card">
    <template #header>
      <div class="head">
        <span class="title">建议卡</span>
        <el-select v-model="status" placeholder="状态" clearable style="width: 120px" @change="load">
          <el-option label="待处理" :value="0" />
          <el-option label="已批准" :value="1" />
          <el-option label="已驳回" :value="2" />
          <el-option label="已执行" :value="3" />
        </el-select>
      </div>
    </template>

    <el-empty v-if="!loading && !rows.length" description="暂无建议卡" />
    <div v-loading="loading" class="sug-list">
      <div v-for="s in rows" :key="s.id" class="sug-card" :class="'st-' + s.status">
        <div class="sug-head">
          <span class="sug-title">{{ s.title }}</span>
          <el-tag :type="statusType(s.status)" size="small">{{ statusText(s.status) }}</el-tag>
        </div>
        <div class="sug-content">{{ s.content }}</div>
        <div class="sug-foot">
          <span class="sug-time">{{ s.createTime }}</span>
          <div class="sug-actions" v-if="s.status === 0" v-perm="'ai:suggestion:handle'">
            <el-button type="primary" size="small" @click="onApprove(s)">批准</el-button>
            <el-button size="small" @click="onReject(s)">驳回</el-button>
            <el-button type="success" size="small" @click="onExecute(s)">执行</el-button>
          </div>
        </div>
      </div>
    </div>

    <div class="pager" v-if="total > size">
      <el-pagination layout="total, prev, pager, next" :total="total" :page-size="size"
        :current-page="page" @current-change="(p: number) => { page = p; load() }" />
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listSuggestions, approveSuggestion, rejectSuggestion, executeSuggestion } from '@/api/ai'
import type { AiSuggestion } from '@/api/ai'

const rows = ref<AiSuggestion[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const status = ref<number | undefined>(undefined)
const loading = ref(false)

const statusMap: Record<number, [string, string]> = {
  0: ['待处理', 'warning'],
  1: ['已批准', 'success'],
  2: ['已驳回', 'info'],
  3: ['已执行', 'primary']
}
function statusText(s: number) { return statusMap[s]?.[0] ?? '未知' }
function statusType(s: number) { return statusMap[s]?.[1] ?? 'info' as any }

async function load() {
  loading.value = true
  try {
    const p = await listSuggestions({ page: page.value, size: size.value, status: status.value })
    rows.value = p.records
    total.value = p.total
  } finally {
    loading.value = false
  }
}

async function onApprove(s: AiSuggestion) {
  await ElMessageBox.confirm('批准该建议？', '批准', { type: 'info' })
  await approveSuggestion(s.id)
  ElMessage.success('已批准')
  load()
}
async function onReject(s: AiSuggestion) {
  await ElMessageBox.confirm('驳回该建议？', '驳回', { type: 'warning' })
  await rejectSuggestion(s.id)
  ElMessage.success('已驳回')
  load()
}
async function onExecute(s: AiSuggestion) {
  await ElMessageBox.confirm('执行该建议？', '执行', { type: 'warning' })
  await executeSuggestion(s.id)
  ElMessage.success('已执行')
  load()
}

onMounted(load)
</script>

<style scoped>
.page-card { border-radius: 12px; }
.head { display: flex; align-items: center; justify-content: space-between; }
.title { font-weight: 600; }
.sug-list { display: flex; flex-direction: column; gap: 12px; min-height: 120px; }
.sug-card { border: 1px solid #eef0f4; border-radius: 10px; padding: 14px 16px; background: #fff; }
.sug-card.st-1 { border-left: 3px solid #10b981; }
.sug-card.st-2 { border-left: 3px solid #9ca3af; }
.sug-card.st-3 { border-left: 3px solid #6366f1; }
.sug-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.sug-title { font-weight: 600; font-size: 14px; }
.sug-content { font-size: 13px; color: #4b5563; line-height: 1.7; white-space: pre-wrap; }
.sug-foot { display: flex; align-items: center; justify-content: space-between; margin-top: 10px; }
.sug-time { font-size: 12px; color: #9ca3af; }
.pager { margin-top: 14px; display: flex; justify-content: flex-end; }
</style>
