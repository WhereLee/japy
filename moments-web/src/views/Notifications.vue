<template>
  <div class="notif-wrap">
    <!-- 第一栏：分类导航 -->
    <aside class="nav-col">
      <div class="nav-title">消息中心</div>
      <div class="nav-list">
        <button
          v-for="c in categories" :key="c.key"
          class="nav-item" :class="{ active: category === c.key }"
          @click="switchCategory(c.key)"
        >
          <span class="nav-dot" :class="{ on: category === c.key }"></span>
          <span class="nav-label">{{ c.label }}</span>
          <span v-if="c.key === 'all' && unread > 0" class="nav-count">{{ unread > 99 ? '99+' : unread }}</span>
        </button>
      </div>
      <div class="nav-foot">
        <button class="nav-item" @click="readAll">✓ 全部已读</button>
      </div>
    </aside>

    <!-- 第二栏：通知列表 -->
    <section class="list-col">
      <div class="list-head">
        <h3>{{ currentCat.label }}</h3>
        <span v-if="total > 0" class="list-total">{{ total }} 条</span>
      </div>
      <div class="list-sub">最近消息</div>
      <div class="list-body">
        <div v-if="!list.length && !loading" class="empty" style="padding:40px 0;">
          <div class="icon">🔕</div><p>暂无通知</p>
        </div>
        <div
          v-for="n in list" :key="n.id"
          class="list-item" :class="{ active: current?.id === n.id, unread: n.isRead === 0 }"
          @click="select(n)"
        >
          <span class="item-avatar" :class="'av-' + (iconOf(n.type) === '📢' ? 'ann' : iconOf(n.type) === '👍' ? 'like' : iconOf(n.type) === '⚖️' ? 'rep' : 'gen')">{{ iconOf(n.type) }}</span>
          <div class="item-body">
            <div class="item-name">{{ typeLabel(n.type) }}</div>
            <div class="item-text">{{ n.content }}</div>
            <div class="item-time">{{ timeAgo(n.createdAt) }}</div>
          </div>
          <span v-if="n.isRead === 0" class="item-dot" title="未读"></span>
        </div>
      </div>
      <div class="list-more">
        <button v-if="total > list.length" class="link-btn" @click="loadMore">加载更多（{{ total - list.length }}）</button>
      </div>
    </section>

    <!-- 第三栏：内容面板 -->
    <section class="detail-col">
      <template v-if="current">
        <div class="detail-head">
          <span class="detail-icon">{{ iconOf(current.type) }}</span>
          <span class="detail-type">{{ typeLabel(current.type) }}</span>
          <span v-if="current.isRead === 0" class="detail-unread">未读</span>
        </div>
        <div class="detail-content">{{ current.content }}</div>
        <div class="detail-meta">
          <div>时间：{{ fullTime(current.createdAt) }}</div>
          <div v-if="current.refType">关联：{{ refLabel(current.refType) }} #{{ current.refId }}</div>
        </div>
      </template>
      <div v-else class="detail-empty">
        <div class="de-icon">💌</div>
        <p class="de-text">点击左侧通知查看详情</p>
        <p class="de-sub">收到的赞、评论与系统通知都会出现在这里</p>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import http from '../api'
import { timeAgo, fullTime, toast } from '../utils/format'
import { unread, refreshUnread } from '../stores/notify'

const categories = [
  { key: 'all', label: '我的消息', types: null },
  { key: 'like', label: '收到的赞', types: ['like'] },
  { key: 'reply', label: '回复我的', types: ['comment', 'reply'] },
  { key: 'system', label: '系统通知', types: ['announcement', 'report_result', 'hidden', 'banned', 'novel_apply'] }
]

const category = ref('all')
const currentCat = computed(() => categories.find(c => c.key === category.value))

const list = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const current = ref(null)

function iconOf(type) {
  return { like: '👍', comment: '💬', reply: '↩️', report_result: '⚖️', announcement: '📢', hidden: '🙈', banned: '🚫', novel_apply: '📄' }[type] || '📌'
}
function typeLabel(type) {
  return {
    like: '点赞通知', comment: '评论通知', reply: '回复通知', report_result: '举报结果',
    announcement: '平台公告', hidden: '内容被隐藏', banned: '账号状态', novel_apply: '小说申请'
  }[type] || type
}
function refLabel(t) {
  return { moment: '动态', comment: '评论', novel_apply: '申请' }[t] || t
}

async function load(reset = true) {
  loading.value = true
  try {
    const cat = currentCat.value
    const params = { page: reset ? 1 : page.value, size: 20 }
    if (cat.types) params.type = cat.types.join(',')
    const data = await http.get('/api/notifications', { params })
    if (reset) { list.value = data.list; page.value = 1 } else list.value = list.value.concat(data.list)
    total.value = data.total
    // 分类切换后重置选中
    if (reset) current.value = list.value[0] || null
  } finally {
    loading.value = false
  }
}
function loadMore() { page.value += 1; load(false) }

function switchCategory(key) {
  if (category.value === key) return
  category.value = key
  load(true)
}

async function select(n) {
  current.value = n
  if (n.isRead === 0) {
    try {
      await http.put(`/api/notifications/${n.id}/read`)
      n.isRead = 1
      refreshUnread()   // 顶栏红点实时更新
    } catch { /* 忽略 */ }
  }
}

async function readAll() {
  try {
    await http.put('/api/notifications/read-all')
    list.value.forEach(n => (n.isRead = 1))
    if (current.value) current.value.isRead = 1
    refreshUnread()
    toast('已全部标为已读')
  } catch { /* 忽略 */ }
}

onMounted(() => { load(true); refreshUnread() })
</script>

<style scoped>
/* 三栏布局（B 站消息中心结构）：160px 导航 + 320px 列表 + 1fr 面板 */
.notif-wrap {
  display: grid;
  grid-template-columns: 160px 320px 1fr;
  gap: 1px;
  background: var(--line);
  border: 1px solid var(--line);
  border-radius: 12px;
  overflow: hidden;
  min-height: calc(100vh - 130px);
}

/* ===== 第一栏：分类导航 ===== */
.nav-col {
  background: var(--card);
  display: flex; flex-direction: column;
  padding: 18px 0;
}
.nav-title {
  font-size: 16px; font-weight: 700; color: var(--text);
  padding: 0 16px 14px;
  font-family: var(--serif);
}
.nav-list { flex: 1; }
.nav-item {
  display: flex; align-items: center; gap: 10px;
  width: 100%;
  border: none; background: none;
  padding: 11px 16px;
  font-size: 13.5px;
  color: var(--text-2);
  cursor: pointer;
  text-align: left;
  transition: background .15s;
}
.nav-item:hover { background: var(--bg-soft); color: var(--text); }
.nav-item.active { background: var(--accent-soft); color: var(--accent); font-weight: 600; }
.nav-dot {
  width: 8px; height: 8px; border-radius: 50%;
  border: 1.5px solid var(--text-3);
  flex-shrink: 0;
}
.nav-dot.on { background: var(--accent); border-color: var(--accent); }
.nav-label { flex: 1; }
.nav-count {
  font-size: 10px; color: #fff; background: var(--danger);
  border-radius: 999px; padding: 1px 6px;
}
.nav-foot { border-top: 1px solid var(--line-soft); padding-top: 8px; }

/* ===== 第二栏：通知列表 ===== */
.list-col {
  background: var(--card);
  display: flex; flex-direction: column;
  min-width: 0;
}
.list-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 18px 16px 6px;
}
.list-head h3 { font-size: 15px; }
.list-total { font-size: 12px; color: var(--text-3); }
.list-sub {
  font-size: 12px; color: var(--text-3);
  padding: 4px 16px 10px;
  border-bottom: 1px solid var(--line-soft);
}
.list-body {
  flex: 1;
  overflow-y: auto;
  max-height: calc(100vh - 260px);
}
.list-item {
  display: flex; align-items: flex-start; gap: 10px;
  padding: 12px 14px;
  cursor: pointer;
  border-left: 3px solid transparent;
  transition: background .15s;
}
.list-item:hover { background: var(--bg-soft); }
.list-item.active { background: var(--accent-soft); border-left-color: var(--accent); }
.list-item.unread .item-name { font-weight: 700; }
.item-avatar {
  width: 38px; height: 38px; border-radius: 50%;
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 17px;
  flex-shrink: 0;
  background: var(--bg-soft);
}
.av-ann { background: #faf3e3; }
.av-like { background: #fdf0ee; }
.av-rep { background: #eef3fe; }
.item-body { flex: 1; min-width: 0; }
.item-name { font-size: 13px; color: var(--text); }
.item-text {
  font-size: 12px; color: var(--text-2);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  margin-top: 1px;
}
.item-time { font-size: 11px; color: var(--text-3); margin-top: 3px; }
.item-dot {
  width: 9px; height: 9px; border-radius: 50%;
  background: var(--accent);
  margin-top: 8px; flex-shrink: 0;
}
.list-more { text-align: center; padding: 10px 0 14px; }
.link-btn { border: none; background: none; color: var(--accent); font-size: 12px; cursor: pointer; }

/* ===== 第三栏：内容面板 ===== */
.detail-col {
  background: var(--bg-soft);
  padding: 28px 32px;
  display: flex; flex-direction: column;
  min-width: 0;
}
.detail-head { display: flex; align-items: center; gap: 10px; margin-bottom: 18px; }
.detail-icon { font-size: 28px; }
.detail-type { font-size: 14px; color: var(--text-2); }
.detail-unread {
  font-size: 11px; color: var(--accent);
  border: 1px solid var(--accent); border-radius: 4px;
  padding: 0 6px;
}
.detail-content {
  font-size: 15.5px; line-height: 2;
  color: var(--text);
  white-space: pre-wrap; word-break: break-word;
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 22px 26px;
  flex: 1;
}
.detail-meta { margin-top: 18px; font-size: 13px; color: var(--text-3); display: flex; flex-direction: column; gap: 6px; }
.detail-empty {
  flex: 1;
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  gap: 10px;
}
.de-icon { font-size: 52px; opacity: .5; }
.de-text { font-size: 15px; color: var(--text-2); }
.de-sub { font-size: 12px; color: var(--text-3); }

/* 窄屏：退化为上下结构 */
@media (max-width: 900px) {
  .notif-wrap { grid-template-columns: 1fr; }
  .nav-col { flex-direction: row; overflow-x: auto; padding: 8px; }
  .nav-title, .nav-foot { display: none; }
  .nav-list { display: flex; }
  .nav-item { width: auto; white-space: nowrap; padding: 8px 14px; }
}
</style>
