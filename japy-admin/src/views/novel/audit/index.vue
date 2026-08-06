<template>
  <el-card shadow="never" class="page-card">
    <template #header>
      <div class="head">
        <span class="title">内容审核</span>
        <div class="right">
          <el-radio-group v-model="result" size="small" @change="reload">
            <el-radio-button value="PENDING">待处理{{ pending > 0 ? ` (${pending})` : '' }}</el-radio-button>
            <el-radio-button value="PASS">已通过</el-radio-button>
            <el-radio-button value="TAKEDOWN">已下架</el-radio-button>
            <el-radio-button value="">全部</el-radio-button>
          </el-radio-group>
        </div>
      </div>
    </template>

    <el-table :data="rows" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="novelId" label="小说ID" width="80" />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.auditType === 'UPLOAD' ? 'primary' : 'warning'">{{ row.auditType === 'UPLOAD' ? '上传' : '重扫' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="命中词" min-width="220">
        <template #default="{ row }">
          <el-tag v-for="h in parseHits(row.ruleHits)" :key="h.word" size="small" class="hit-tag" :type="hitType(h.category)">
            {{ h.word }} ×{{ h.count }}
          </el-tag>
          <span v-if="!parseHits(row.ruleHits).length" class="no-hit">无命中</span>
        </template>
      </el-table-column>
      <el-table-column label="结果" width="90">
        <template #default="{ row }">
          <el-tag :type="resultType(row.result)" size="small">{{ resultText(row.result) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="auditorId" label="处理人" width="80">
        <template #default="{ row }">{{ row.auditorId ?? '-' }}</template>
      </el-table-column>
      <el-table-column prop="createTime" label="时间" width="165" />
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <template v-if="row.result === 'PENDING'">
            <el-button link type="success" size="small" v-perm="'audit:handle'" @click="onPass(row)">通过</el-button>
            <el-button link type="danger" size="small" v-perm="'audit:handle'" @click="onTakedown(row)">下架</el-button>
          </template>
          <el-button link type="primary" size="small" v-perm="'audit:rescan'" @click="onRescan(row)">重扫</el-button>
        </template>
      </el-table-column>
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
import { listAudits, pendingCount, auditPass, auditTakedown, rescan, parseHits } from '@/api/audit'
import type { AuditRecord } from '@/api/audit'

const rows = ref<AuditRecord[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(15)
const result = ref('PENDING')
const pending = ref(0)
const loading = ref(false)

const resultMap: Record<string, [string, string]> = {
  PENDING: ['待处理', 'warning'],
  PASS: ['已通过', 'success'],
  TAKEDOWN: ['已下架', 'danger'],
  REJECT: ['已驳回', 'info']
}
function resultText(r: string) { return resultMap[r]?.[0] ?? r }
function resultType(r: string) { return resultMap[r]?.[1] ?? 'info' as any }
function hitType(c: string) {
  return ({ 政治: 'danger', 色情: 'danger', 暴力: 'warning', 广告: 'warning' } as any)[c] || 'info'
}

async function load() {
  loading.value = true
  try {
    const p = await listAudits({ page: page.value, size: size.value, result: result.value || undefined })
    rows.value = p.list
    total.value = p.total
  } finally {
    loading.value = false
  }
}

async function reload() {
  page.value = 1
  await load()
  try {
    pending.value = await pendingCount()
  } catch { /* 忽略 */ }
}

async function onPass(row: AuditRecord) {
  await ElMessageBox.confirm('确认该内容合规通过？', '确认通过', { type: 'info' })
  await auditPass(row.id, '人工确认合规')
  ElMessage.success('已通过')
  reload()
}

async function onTakedown(row: AuditRecord) {
  await ElMessageBox.confirm('确认违规并下架？用户端将不可见', '违规下架', { type: 'warning' })
  await auditTakedown(row.id, '违规下架')
  ElMessage.success('已下架')
  reload()
}

async function onRescan(row: AuditRecord) {
  const n = await rescan(row.novelId)
  ElMessage.success(`重扫完成，命中 ${n} 词`)
  reload()
}

onMounted(reload)
</script>

<style scoped>
.page-card { border-radius: 12px; }
.head { display: flex; align-items: center; justify-content: space-between; }
.title { font-weight: 600; }
.right { display: flex; align-items: center; gap: 10px; }
.hit-tag { margin-right: 6px; }
.no-hit { color: #94a3b8; font-size: 12px; }
.pager { margin-top: 14px; display: flex; justify-content: flex-end; }
</style>
