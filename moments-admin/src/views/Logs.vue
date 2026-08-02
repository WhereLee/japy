<template>
  <div class="page-card">
    <div class="toolbar">
      <el-select v-model="action" placeholder="按操作类型筛选" clearable style="width:200px" @change="load(1)">
        <el-option v-for="a in actionOptions" :key="a" :label="actionLabel(a)" :value="a" />
      </el-select>
      <span style="color:#8a919f;font-size:13px;">由后端 AOP 切面自动记录：操作人 / 参数 / 耗时 / 结果</span>
    </div>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="操作" width="130">
        <template #default="{ row }">
          <el-tag size="small">{{ actionLabel(row.action) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="method" label="请求" min-width="220" show-overflow-tooltip />
      <el-table-column label="耗时" width="90" align="center">
        <template #default="{ row }">
          <span :style="{ color: row.costMs > 500 ? '#dc2626' : '#606266', fontWeight: 600 }">
            {{ row.costMs }} ms
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="detail" label="参数摘要" min-width="240" show-overflow-tooltip />
      <el-table-column label="结果" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.error" type="danger" size="small">失败</el-tag>
          <el-tag v-else type="success" size="small">成功</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="ip" label="IP" width="120" />
      <el-table-column label="时间" width="165">
        <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.error" size="small" type="danger" @click="showError(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        layout="total, prev, pager, next" :total="total" :page-size="size"
        :current-page="page" @current-change="load"
      />
    </div>

    <!-- 失败详情 -->
    <el-dialog v-model="errVisible" title="失败详情" width="560px">
      <pre style="white-space:pre-wrap;background:#fef2f2;padding:14px;border-radius:6px;color:#b91c1c;font-size:13px;line-height:1.7;">{{ errText }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import http from '../api'
import { fmtTime } from '../utils/format'

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = 20
const loading = ref(false)
const action = ref('')

const actionOptions = [
  'ban_user', 'unban_user', 'reset_password', 'force_nickname',
  'hide_moment', 'restore_moment', 'delete_moment', 'pin_moment', 'unpin_moment',
  'hide_comment', 'restore_comment', 'delete_comment',
  'resolve_report', 'reject_report', 'add_word', 'delete_word',
  'announcement', 'upload_novel'
]
const actionLabelMap = {
  ban_user: '封禁用户', unban_user: '解封用户', reset_password: '重置密码', force_nickname: '强制改名',
  hide_moment: '隐藏动态', restore_moment: '恢复动态', delete_moment: '删除动态', pin_moment: '置顶动态', unpin_moment: '取消置顶',
  hide_comment: '隐藏评论', restore_comment: '恢复评论', delete_comment: '删除评论',
  resolve_report: '处理举报', reject_report: '驳回举报', add_word: '添加敏感词', delete_word: '删除敏感词',
  announcement: '发送公告', upload_novel: '上传小说'
}
function actionLabel(a) { return actionLabelMap[a] || a }

const errVisible = ref(false)
const errText = ref('')

function showError(row) {
  errText.value = `操作：${actionLabel(row.action)}\n请求：${row.method}\n参数：${row.detail}\n错误：${row.error}`
  errVisible.value = true
}

async function load(p) {
  page.value = p || 1
  loading.value = true
  try {
    const data = await http.get('/api/admin/logs', {
      params: { page: page.value, size, action: action.value || undefined }
    })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

onMounted(() => load(1))
</script>
