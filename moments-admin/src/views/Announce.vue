<template>
  <div class="page-card">
    <div class="toolbar">
      <span style="color:#8a919f; font-size:13px;">公告推送给所有状态正常的用户（站内通知）</span>
      <el-button type="primary" :icon="Bell" @click="openDialog">发布公告</el-button>
    </div>

    <el-table :data="history" v-loading="loading" stripe>
      <el-table-column label="公告内容" min-width="260" show-overflow-tooltip>
        <template #default="{ row }">
          <span style="color:#1f2329;">{{ row.contentText }}</span>
        </template>
      </el-table-column>
      <el-table-column label="结果" width="90" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.error" type="danger" size="small">失败</el-tag>
          <el-tag v-else type="success" size="small">成功</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="耗时" width="100" align="center">
        <template #default="{ row }">
          <span :style="{ color: row.costMs > 500 ? '#dc2626' : '#606266', fontWeight: 600 }">
            {{ row.costMs }} ms
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="发送时间" width="165">
        <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
      </el-table-column>
    </el-table>
    <div v-if="history.length === 0 && !loading" class="empty">暂无广播记录</div>
    <div class="pager">
      <el-pagination
        layout="total, prev, pager, next" :total="total" :page-size="size"
        :current-page="page" @current-change="load"
      />
    </div>

    <!-- 发布公告弹窗 -->
    <el-dialog v-model="dialogVisible" title="发布公告" width="480px" :close-on-click-modal="false">
      <el-alert
        type="info" :closable="false" style="margin-bottom:14px;"
        title="公告将推送给所有状态正常的用户"
      />
      <el-input
        v-model="content" type="textarea" :rows="6" maxlength="300" show-word-limit
        placeholder="输入公告内容（最多 300 字）"
      />
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="sending" @click="send">发送</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Bell } from '@element-plus/icons-vue'
import http from '../api'
import { fmtTime } from '../utils/format'

const content = ref('')
const sending = ref(false)
const dialogVisible = ref(false)

const history = ref([])
const total = ref(0)
const page = ref(1)
const size = 10
const loading = ref(false)

/** 从 AOP 日志的 detail 参数中提取公告内容（detail 形如 {"content":"xxx"}） */
function extractContent(detail) {
  if (!detail) return ''
  try {
    const obj = JSON.parse(detail)
    return obj.content || detail
  } catch {
    return detail
  }
}

async function load(p) {
  page.value = p || 1
  loading.value = true
  try {
    const data = await http.get('/api/admin/logs', {
      params: { page: page.value, size, action: 'announcement' }
    })
    history.value = data.list.map(r => ({
      ...r,
      contentText: extractContent(r.detail)
    }))
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function openDialog() {
  content.value = ''
  dialogVisible.value = true
}

async function send() {
  if (!content.value.trim()) { ElMessage.warning('请输入公告内容'); return }
  sending.value = true
  try {
    await http.post('/api/admin/announcements', { content: content.value.trim() })
    ElMessage.success('公告已发送')
    dialogVisible.value = false
    load(1)
  } finally {
    sending.value = false
  }
}

onMounted(() => load(1))
</script>

<style scoped>
.empty {
  text-align: center; color: #c0c4cc; padding: 28px 0;
  border: 1px dashed #e6e8ec; border-radius: 6px; font-size: 13px;
}
</style>
