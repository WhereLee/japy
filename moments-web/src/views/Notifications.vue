<template>
  <div class="card">
    <div class="n-head">
      <h3>通知</h3>
      <button v-if="list.length" class="link-btn" @click="readAll">全部已读</button>
    </div>

    <div v-if="list.length === 0 && !loading" class="empty">
      <div class="icon">🔕</div><p>暂无通知</p>
    </div>

    <div v-for="n in list" :key="n.id" class="n-item" :class="{ unread: n.isRead === 0 }" @click="open(n)">
      <span class="n-icon">{{ iconOf(n.type) }}</span>
      <div class="n-body">
        <div class="n-text">{{ n.content }}</div>
        <div class="n-time">{{ timeAgo(n.createdAt) }}</div>
      </div>
      <span v-if="n.isRead === 0" class="n-dot"></span>
    </div>

    <div class="pager">
      <button v-if="total > list.length" class="link-btn" @click="loadMore">加载更多</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import http from '../api'
import { timeAgo, toast } from '../utils/format'

const router = useRouter()

const list = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)

function iconOf(type) {
  return { like: '👍', comment: '💬', reply: '↩️', report_result: '⚖️', announce: '📢', hidden: '🙈', banned: '🚫' }[type] || '📌'
}

async function load(reset = true) {
  loading.value = true
  try {
    const data = await http.get('/api/notifications', { params: { page: reset ? 1 : page.value, size: 20 } })
    if (reset) { list.value = data.list; page.value = 1 }
    else list.value = list.value.concat(data.list)
    total.value = data.total
  } finally {
    loading.value = false
  }
}
function loadMore() { page.value += 1; load(false) }

async function open(n) {
  if (n.isRead === 0) {
    try {
      await http.put(`/api/notifications/${n.id}/read`)
      n.isRead = 1
    } catch { /* 忽略 */ }
  }
  // 通知类型含动态引用（点赞/评论/回复/被隐藏）：跳回时间线查看
  if (['like', 'comment', 'reply', 'hidden'].includes(n.type) && n.refId) {
    router.push('/')
  }
}

async function readAll() {
  try {
    await http.put('/api/notifications/read-all')
    list.value.forEach(n => (n.isRead = 1))
    toast('已全部标为已读')
  } catch { /* 忽略 */ }
}

onMounted(() => load(true))
</script>

<style scoped>
.n-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 20px 8px;
}
.n-head h3 { font-size: 15px; }
.link-btn {
  border: none; background: none;
  color: var(--accent); font-size: 12px; cursor: pointer;
}
.n-item {
  display: flex; align-items: flex-start; gap: 12px;
  padding: 12px 20px;
  cursor: pointer;
  border-bottom: 1px solid var(--line-soft);
  transition: background .15s;
}
.n-item:hover { background: var(--bg-soft); }
.n-item.unread { background: var(--accent-soft); }
.n-item.unread:hover { background: #f6ecd8; }
.n-icon { font-size: 18px; margin-top: 2px; }
.n-body { flex: 1; min-width: 0; }
.n-text { font-size: 14px; word-break: break-word; }
.n-time { font-size: 12px; color: var(--text-3); margin-top: 3px; }
.n-dot {
  width: 8px; height: 8px; border-radius: 50%;
  background: var(--accent);
  margin-top: 6px; flex-shrink: 0;
}
.pager { text-align: center; padding: 12px 0 16px; }
</style>
