<template>
  <div>
    <el-row :gutter="16" v-loading="loading">
      <el-col :span="6" v-for="card in cards" :key="card.label">
        <div class="stat-card">
          <div class="stat-icon" :style="{ background: card.bg, color: card.color }">
            <el-icon :size="26"><component :is="card.icon" /></el-icon>
          </div>
          <div class="stat-body">
            <div class="stat-value">{{ fmtNum(card.value) }}</div>
            <div class="stat-label">{{ card.label }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <div class="page-card" style="margin-top:16px;">
      <h3 style="margin-bottom:12px;">快捷说明</h3>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="用户管理">封禁 / 解封 / 重置密码（123456）/ 强制改名</el-descriptions-item>
        <el-descriptions-item label="动态管理">隐藏 / 恢复 / 删除 / 置顶 / 取消置顶</el-descriptions-item>
        <el-descriptions-item label="举报处理">处理（自动隐藏内容并通知举报者）/ 驳回</el-descriptions-item>
        <el-descriptions-item label="小说管理">上传 txt 自动入库（章节检测 → 段落切分 → 统计）</el-descriptions-item>
        <el-descriptions-item label="操作日志">AOP 自动记录每个管理操作的参数 / 耗时 / 结果</el-descriptions-item>
      </el-descriptions>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { User, ChatDotRound, Comment, Warning } from '@element-plus/icons-vue'
import http from '../api'
import { fmtNum } from '../utils/format'

const loading = ref(true)
const cards = ref([
  { label: '用户总数', value: 0, icon: User, bg: '#eef3fe', color: '#3b6ef6' },
  { label: '正常动态', value: 0, icon: ChatDotRound, bg: '#ecfdf3', color: '#16a34a' },
  { label: '正常评论', value: 0, icon: Comment, bg: '#fffbeb', color: '#d97706' },
  { label: '待处理举报', value: 0, icon: Warning, bg: '#fef2f2', color: '#dc2626' }
])

onMounted(async () => {
  try {
    const data = await http.get('/api/admin/dashboard')
    cards.value[0].value = data.userCount
    cards.value[1].value = data.momentCount
    cards.value[2].value = data.commentCount
    cards.value[3].value = data.pendingReports
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.stat-card {
  background: #fff; border-radius: 8px; padding: 20px;
  display: flex; align-items: center; gap: 16px;
  box-shadow: 0 1px 2px rgba(0,21,41,0.06);
}
.stat-icon {
  width: 52px; height: 52px; border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.stat-value { font-size: 26px; font-weight: 700; color: #1f2329; font-variant-numeric: tabular-nums; }
.stat-label { font-size: 13px; color: #8a919f; margin-top: 2px; }
</style>
