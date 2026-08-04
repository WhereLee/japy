<template>
  <div>
    <el-row :gutter="16">
      <el-col :span="8">
        <el-card shadow="never" class="page-card">
          <template #header><span class="title">反馈统计</span></template>
          <div class="fb-stats">
            <div class="fb-item">
              <div class="fb-num">{{ stats?.total ?? '-' }}</div>
              <div class="fb-lbl">总反馈</div>
            </div>
            <div class="fb-item good">
              <div class="fb-num">{{ stats?.positive ?? '-' }}</div>
              <div class="fb-lbl">有效（好评）</div>
            </div>
            <div class="fb-item bad">
              <div class="fb-num">{{ stats?.negative ?? '-' }}</div>
              <div class="fb-lbl">无效（差评）</div>
            </div>
          </div>
          <el-button type="primary" style="width: 100%; margin-top: 14px" :loading="analyzing" v-perm="'ai:insight:analyze'" @click="onAnalyze">
            <el-icon style="margin-right: 4px"><MagicStick /></el-icon>触发反馈分析
          </el-button>
        </el-card>
      </el-col>
      <el-col :span="16">
        <el-card shadow="never" class="page-card">
          <template #header><span class="title">分析洞察</span></template>
          <el-empty v-if="!insights.length" description="暂无洞察，点击左侧按钮分析" />
          <div class="ins-list" v-else>
            <div v-for="ins in insights" :key="ins.id" class="ins-card">
              <div class="ins-head">
                <span class="ins-title">{{ ins.title || '反馈聚类' }}</span>
                <span class="ins-time">{{ ins.createTime }}</span>
              </div>
              <div class="ins-body">{{ ins.clusterResult || ins.summary }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { MagicStick } from '@element-plus/icons-vue'
import { getFeedbackStats, analyzeInsight, listInsights } from '@/api/ai'

const stats = ref<any>(null)
const insights = ref<any[]>([])
const analyzing = ref(false)

async function onAnalyze() {
  analyzing.value = true
  try {
    const res: any = await analyzeInsight()
    ElMessage.success('分析完成')
    if (res?.clusterResult) ElMessage.success(res.clusterResult)
    await loadInsights()
  } finally {
    analyzing.value = false
  }
}

async function loadInsights() {
  try {
    const p = await listInsights({ page: 1, size: 10 })
    insights.value = p.records
  } catch { /* 忽略 */ }
}

onMounted(async () => {
  try { stats.value = await getFeedbackStats() } catch { /* 忽略 */ }
  loadInsights()
})
</script>

<style scoped>
.page-card { border-radius: 12px; }
.title { font-weight: 600; }
.fb-stats { display: flex; gap: 12px; }
.fb-item { flex: 1; text-align: center; background: #f8fafc; border-radius: 10px; padding: 12px 0; }
.fb-item.good .fb-num { color: #10b981; }
.fb-item.bad .fb-num { color: #ef4444; }
.fb-num { font-size: 26px; font-weight: 700; color: #6366f1; }
.fb-lbl { font-size: 12px; color: #6b7280; margin-top: 4px; }
.ins-list { display: flex; flex-direction: column; gap: 10px; }
.ins-card { border: 1px solid #eef0f4; border-radius: 10px; padding: 12px 14px; }
.ins-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.ins-title { font-weight: 600; font-size: 13px; }
.ins-time { font-size: 12px; color: #9ca3af; }
.ins-body { font-size: 13px; color: #4b5563; line-height: 1.7; white-space: pre-wrap; }
</style>
