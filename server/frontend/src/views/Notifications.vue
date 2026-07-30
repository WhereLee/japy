<template>
  <div class="notifications-page">
    <header class="notifications-header">
      <router-link to="/" class="back-btn">← 返回书架</router-link>
      <h2>我的通知</h2>
      <button v-if="unreadCount > 0" class="mark-all-read" @click="handleMarkAllRead">全部已读</button>
    </header>

    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="loadError" class="empty error">{{ loadError }}</div>
    <div v-else-if="notifications.length === 0" class="empty">暂无通知</div>

    <!-- 主从布局：左列表 + 右详情（邮件客户端式） -->
    <div v-else class="mail-layout">
      <!-- 左侧列表 -->
      <div class="mail-list" :class="{ 'mobile-hidden': mobileDetailOpen }">
        <div
          v-for="n in notifications"
          :key="n.id"
          class="mail-item"
          :class="{ active: selectedId === n.id, unread: n.isRead === 0 }"
          @click="selectNotification(n)"
        >
          <div class="mail-item-icon">{{ getTypeIcon(n.type) }}</div>
          <div class="mail-item-body">
            <div class="mail-item-title">{{ n.title }}</div>
            <div class="mail-item-preview">{{ n.content }}</div>
          </div>
          <div class="mail-item-side">
            <span v-if="n.isRead === 0" class="unread-dot"></span>
            <span class="mail-item-time">{{ shortTime(n.createdAt) }}</span>
          </div>
        </div>
      </div>

      <!-- 右侧详情 -->
      <div class="mail-detail" :class="{ 'mobile-show': mobileDetailOpen }">
        <button class="detail-back" @click="closeDetail">← 返回列表</button>
        <template v-if="selected">
          <div class="detail-header">
            <div class="detail-icon">{{ getTypeIcon(selected.type) }}</div>
            <div class="detail-heading">
              <h3 class="detail-title">{{ selected.title }}</h3>
              <div class="detail-time">{{ formatTime(selected.createdAt) }}</div>
            </div>
          </div>
          <div class="detail-content">{{ selected.content }}</div>
        </template>
        <div v-else class="detail-empty">选择左侧通知查看详情</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getNotifications, markNotificationRead, markAllNotificationsRead } from '../api'

const notifications = ref([])
const loading = ref(true)
const loadError = ref('')
const unreadCount = ref(0)
const selectedId = ref(null)
const mobileDetailOpen = ref(false)

const selected = computed(() => notifications.value.find(n => n.id === selectedId.value) || null)

function getTypeIcon(type) {
  const icons = { ban: '🚫', unban: '✅', password_reset: '🔑', report_resolved: '📋', report_rejected: '❌', announcement: '📢', like: '👍', comment: '💬', reply: '↩️' }
  return icons[type] || '📩'
}

function formatTime(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`
}

// 列表用紧凑时间：MM-DD HH:mm
function shortTime(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`
}

async function loadData() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await getNotifications({ page: 1, size: 50 })
    notifications.value = res.data.records || []
    unreadCount.value = notifications.value.filter(n => n.isRead === 0).length
    // 默认选中第一条，避免右侧详情空白
    if (notifications.value.length > 0) {
      selectedId.value = notifications.value[0].id
    }
  } catch (e) {
    loadError.value = e.message || '加载通知失败'
  }
  loading.value = false
}

// 选中通知：右侧显示详情 + 标记已读（邮件式交互）
async function selectNotification(n) {
  selectedId.value = n.id
  mobileDetailOpen.value = true
  if (n.isRead === 0) {
    try {
      await markNotificationRead(n.id)
      n.isRead = 1
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    } catch (e) { console.error(e) }
  }
}

function closeDetail() {
  mobileDetailOpen.value = false
}

async function handleMarkAllRead() {
  try {
    await markAllNotificationsRead()
    notifications.value.forEach(n => n.isRead = 1)
    unreadCount.value = 0
  } catch (e) { console.error(e) }
}

onMounted(loadData)
</script>

<style scoped>
.notifications-page { max-width: 960px; margin: 0 auto; padding: 24px 16px; }
.notifications-header { display: flex; align-items: center; gap: 16px; margin-bottom: 20px; padding-bottom: 12px; border-bottom: 1px solid #e8e0d0; }
.back-btn { color: #888; text-decoration: none; font-size: 0.9rem; }
.back-btn:hover { color: #333; }
h2 { font-size: 1.2rem; color: #333; flex: 1; }
.mark-all-read { padding: 6px 14px; background: #f5f0e8; border: 1px solid #e8e0d0; border-radius: 4px; font-size: 0.85rem; color: #8b7355; cursor: pointer; }
.mark-all-read:hover { background: #ebe5d8; }
.loading, .empty { text-align: center; color: #999; padding: 60px 0; }
.empty.error { color: #e74c3c; }

/* 主从布局 */
.mail-layout { display: flex; gap: 16px; align-items: flex-start; }
.mail-list { width: 340px; flex-shrink: 0; display: flex; flex-direction: column; gap: 8px; }
.mail-item { display: flex; align-items: flex-start; gap: 10px; padding: 12px; background: #fffdf7; border: 1px solid #e8e0d0; border-radius: 8px; cursor: pointer; transition: all 0.15s; }
.mail-item:hover { box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
.mail-item.unread { background: #fff8f0; border-color: #ffcc80; }
.mail-item.active { background: #fff; border-color: #8b7355; box-shadow: 0 2px 10px rgba(139,115,85,0.15); }
.mail-item-icon { font-size: 1.1rem; flex-shrink: 0; width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; background: #f5f0e8; border-radius: 50%; }
.mail-item-body { flex: 1; min-width: 0; }
.mail-item-title { font-size: 0.9rem; font-weight: 600; color: #333; margin-bottom: 3px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.mail-item-preview { font-size: 0.8rem; color: #999; line-height: 1.4; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.mail-item-side { display: flex; flex-direction: column; align-items: flex-end; gap: 5px; flex-shrink: 0; }
.mail-item-time { font-size: 0.7rem; color: #bbb; }
.unread-dot { width: 8px; height: 8px; background: #e65100; border-radius: 50%; }

/* 右侧详情 */
.mail-detail { flex: 1; min-width: 0; background: #fff; border: 1px solid #e8e0d0; border-radius: 8px; padding: 24px; position: sticky; top: 24px; min-height: 300px; }
.detail-back { display: none; }
.detail-header { display: flex; align-items: flex-start; gap: 14px; padding-bottom: 16px; border-bottom: 1px solid #f0ece4; margin-bottom: 16px; }
.detail-icon { font-size: 1.6rem; flex-shrink: 0; width: 48px; height: 48px; display: flex; align-items: center; justify-content: center; background: #f5f0e8; border-radius: 50%; }
.detail-heading { flex: 1; min-width: 0; }
.detail-title { font-size: 1.15rem; font-weight: 700; color: #1a1a1a; margin: 0 0 6px; }
.detail-time { font-size: 0.8rem; color: #aaa; }
.detail-content { font-size: 0.95rem; color: #444; line-height: 1.8; white-space: pre-wrap; word-break: break-word; }
.detail-empty { display: flex; align-items: center; justify-content: center; height: 240px; color: #ccc; font-size: 0.95rem; }

/* 响应式：窄屏改为列表 + 全屏详情覆盖 */
@media (max-width: 768px) {
  .mail-layout { display: block; }
  .mail-list { width: 100%; }
  .mail-list.mobile-hidden { display: none; }
  .mail-detail { display: none; position: fixed; inset: 0; z-index: 200; border-radius: 0; overflow-y: auto; }
  .mail-detail.mobile-show { display: block; }
  .detail-back { display: inline-block; margin-bottom: 16px; padding: 6px 14px; background: #f5f0e8; border: 1px solid #e8e0d0; border-radius: 4px; font-size: 0.85rem; color: #8b7355; cursor: pointer; }
}
</style>
