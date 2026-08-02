<template>
  <div class="page-card">
    <div class="toolbar">
      <el-radio-group v-model="status" @change="load(1)">
        <el-radio-button :value="0">待处理</el-radio-button>
        <el-radio-button :value="1">已处理</el-radio-button>
        <el-radio-button :value="2">已驳回</el-radio-button>
      </el-radio-group>
    </div>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="举报对象" width="150">
        <template #default="{ row }">
          <el-tag size="small" :type="row.targetType === 'moment' ? 'primary' : 'info'">
            {{ row.targetType === 'moment' ? '动态' : '评论' }} #{{ row.targetId }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="reason" label="举报理由" min-width="200" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="['warning', 'success', 'info'][row.status]">
            {{ ['待处理', '已处理', '已驳回'][row.status] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="result" label="处理结果" min-width="140" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="举报时间" width="165">
        <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 0">
            <el-button type="primary" size="small" @click="openResolve(row)">处理</el-button>
            <el-button type="info" size="small" @click="reject(row)">驳回</el-button>
          </template>
          <span v-else style="color:#c0c4cc;">已办结</span>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        layout="total, prev, pager, next" :total="total" :page-size="size"
        :current-page="page" @current-change="load"
      />
    </div>

    <el-dialog v-model="resolveVisible" title="处理举报" width="440px">
      <el-alert
        type="warning" :closable="false" style="margin-bottom:14px;"
        title="处理后将自动隐藏被举报内容，并通知举报者"
      />
      <el-form label-width="70px">
        <el-form-item label="处理结果">
          <el-input v-model="resolveResult" placeholder="如：内容已隐藏，违规处理" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resolveVisible = false">取消</el-button>
        <el-button type="primary" @click="resolve">确认处理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api'
import { fmtTime } from '../utils/format'

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = 20
const status = ref(0)
const loading = ref(false)

const resolveVisible = ref(false)
const resolveRow = ref(null)
const resolveResult = ref('')

async function load(p) {
  page.value = p || 1
  loading.value = true
  try {
    const data = await http.get('/api/admin/reports', { params: { page: page.value, size, status: status.value } })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function openResolve(row) {
  resolveRow.value = row
  resolveResult.value = ''
  resolveVisible.value = true
}
async function resolve() {
  await http.put(`/api/admin/reports/${resolveRow.value.id}/resolve`, { result: resolveResult.value || '内容已隐藏' })
  ElMessage.success('已处理')
  resolveVisible.value = false
  load(page.value)
}
async function reject(row) {
  await http.put(`/api/admin/reports/${row.id}/reject`)
  ElMessage.success('已驳回')
  load(page.value)
}

onMounted(() => load(1))
</script>
