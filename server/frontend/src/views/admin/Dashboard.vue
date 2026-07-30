<template>
  <div class="dashboard">
    <h2>数据概览</h2>
    <p v-if="loadError" style="color:#e74c3c">{{ loadError }}</p>
    <div v-else class="stat-cards">
      <div class="card"><span class="label">用户总数</span><span class="value">{{ data.userCount }}</span></div>
      <div class="card"><span class="label">批注总数</span><span class="value">{{ data.annotationCount }}</span></div>
      <div class="card"><span class="label">评论总数</span><span class="value">{{ data.commentCount }}</span></div>
      <div class="card"><span class="label">小说总数</span><span class="value">{{ data.novelCount }}</span></div>
      <div class="card"><span class="label">今日新增用户</span><span class="value">{{ data.todayNewUsers }}</span></div>
      <div class="card"><span class="label">今日新增批注</span><span class="value">{{ data.todayNewAnnotations }}</span></div>
    </div>

    <div class="broadcast-section">
      <h3>群发全体公告</h3>
      <input v-model="broadcast.title" placeholder="公告标题" maxlength="100" />
      <textarea v-model="broadcast.content" placeholder="公告内容（所有用户都会在站内信收到）" rows="3" maxlength="1000"></textarea>
      <button class="btn-broadcast" @click="handleBroadcast" :disabled="sending">{{ sending ? '发送中...' : '发送全体公告' }}</button>
      <p v-if="broadcastMsg" class="broadcast-msg" :class="{ ok: broadcastOk }">{{ broadcastMsg }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getDashboard, broadcastNotification } from '../../api/index.js'

const data = ref({})
const loadError = ref('')

const broadcast = reactive({ title: '', content: '' })
const sending = ref(false)
const broadcastMsg = ref('')
const broadcastOk = ref(false)

async function handleBroadcast() {
  broadcastMsg.value = ''
  if (!broadcast.title.trim() || !broadcast.content.trim()) {
    broadcastOk.value = false
    broadcastMsg.value = '请填写公告标题和内容'
    return
  }
  sending.value = true
  try {
    const res = await broadcastNotification({ title: broadcast.title.trim(), content: broadcast.content.trim() })
    broadcastOk.value = true
    broadcastMsg.value = `已发送给 ${res.data.recipientCount} 位用户`
    broadcast.title = ''
    broadcast.content = ''
  } catch (e) {
    broadcastOk.value = false
    broadcastMsg.value = e.message || '发送失败'
  } finally {
    sending.value = false
  }
}

onMounted(async () => {
  try {
    const res = await getDashboard()
    data.value = res.data
  } catch (e) {
    loadError.value = e.message || '加载仪表盘数据失败'
  }
})
</script>

<style scoped>
.stat-cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin-top: 20px; }
.card { background: #fff; padding: 20px; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); text-align: center; }
.label { display: block; color: #666; font-size: 14px; margin-bottom: 8px; }
.value { display: block; font-size: 28px; font-weight: bold; color: #333; }
.broadcast-section { margin-top: 32px; background: #fff; padding: 20px; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
.broadcast-section h3 { font-size: 16px; margin-bottom: 14px; color: #333; }
.broadcast-section input, .broadcast-section textarea { display: block; width: 100%; padding: 10px; margin-bottom: 10px; border: 1px solid #ddd; border-radius: 4px; box-sizing: border-box; font-family: inherit; font-size: 14px; }
.btn-broadcast { padding: 8px 20px; background: #409eff; color: #fff; border: none; border-radius: 4px; cursor: pointer; font-size: 14px; }
.btn-broadcast:hover:not(:disabled) { background: #66b1ff; }
.btn-broadcast:disabled { opacity: 0.5; cursor: not-allowed; }
.broadcast-msg { margin-top: 10px; font-size: 13px; color: #e74c3c; }
.broadcast-msg.ok { color: #67c23a; }
</style>
