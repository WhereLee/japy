<template>
  <div class="novel-list-page">
    <header class="site-header">
      <h1>ReCloud</h1>
      <p class="subtitle">小说批注社区</p>
      <div class="user-bar">
        <template v-if="currentUser">
          <router-link to="/notifications" class="notification-bell" title="我的通知">
            🔔
            <span v-if="unreadCount > 0" class="bell-badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
          </router-link>
          <span class="user-info">当前用户：<strong>{{ currentUser.username }}</strong></span>
          <router-link to="/profile" class="btn-small">个人中心</router-link>
          <button class="btn-small" @click="handleLogout">退出</button>
        </template>
        <template v-else>
          <router-link to="/login" class="btn-small">登录 / 注册</router-link>
        </template>
      </div>
    </header>

    <div class="novel-grid">
      <div
        v-for="novel in novels"
        :key="novel.id"
        class="novel-card"
        @click="goToReader(novel.id)"
      >
        <div class="novel-title">{{ novel.title }}</div>
        <div class="novel-meta">
          <span v-if="novel.author">{{ novel.author }}</span>
          <span v-else class="unknown-author">未知作者</span>
        </div>
        <div class="novel-desc" v-if="novel.description">{{ novel.description }}</div>
      </div>

      <div v-if="loadError" class="empty-state">
        <p style="color:#e74c3c">{{ loadError }}</p>
        <button class="btn-small" @click="loadNovels">重试</button>
      </div>
      <div v-else-if="novels.length === 0 && !loading" class="empty-state">
        <p>还没有小说</p>
        <p class="hint">将 .txt 文件放入 novels/ 目录，然后访问 POST /api/novels/import</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getNovels, getCurrentUser, logout, getUnreadCount } from '../api'

const router = useRouter()
const novels = ref([])
const loading = ref(true)
const currentUser = ref(null)
const unreadCount = ref(0)

onMounted(async () => {
  // 从 Token 恢复登录状态
  const token = localStorage.getItem('accessToken')
  if (token) {
    try {
      const res = await getCurrentUser()
      currentUser.value = res.data
      // 加载未读通知数
      try {
        const nRes = await getUnreadCount()
        unreadCount.value = nRes.data.count || 0
      } catch (e) { /* 忽略 */ }
    } catch {
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('userInfo')
    }
  }
  await loadNovels()
})

const loadError = ref('')

async function loadNovels() {
  loadError.value = ''
  try {
    const res = await getNovels()
    novels.value = res.data
  } catch (e) {
    loadError.value = e.message || '加载小说列表失败'
  } finally {
    loading.value = false
  }
}

async function handleLogout() {
  try { await logout() } catch {}
  currentUser.value = null
  localStorage.removeItem('accessToken')
  localStorage.removeItem('refreshToken')
  localStorage.removeItem('userInfo')
}

function goToReader(id) {
  router.push(`/read/${id}`)
}
</script>

<style scoped>
.novel-list-page {
  max-width: 900px;
  margin: 0 auto;
  padding: 60px 20px;
}

.site-header {
  text-align: center;
  margin-bottom: 50px;
}

.site-header h1 {
  font-size: 2.4rem;
  font-weight: 700;
  letter-spacing: 4px;
  color: #1a1a1a;
}

.subtitle {
  margin-top: 8px;
  font-size: 0.95rem;
  color: #888;
  letter-spacing: 2px;
}

.user-bar {
  margin-top: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.user-info {
  font-size: 0.9rem;
  color: #666;
}

.btn-small {
  padding: 4px 14px;
  font-size: 0.85rem;
  border: 1px solid #ccc;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
  transition: background 0.2s;
}

.btn-small:hover {
  background: #f0ece4;
}

.user-panel {
  max-width: 500px;
  margin: 0 auto 30px;
  background: #fffdf7;
  border: 1px solid #e8e0d0;
  border-radius: 8px;
  padding: 24px;
}

.panel-section {
  margin-bottom: 20px;
}

.panel-section:last-child {
  margin-bottom: 0;
}

.panel-section h3 {
  font-size: 0.95rem;
  margin-bottom: 10px;
  color: #555;
}

.user-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.user-btn {
  padding: 6px 16px;
  border: 1px solid #ddd;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
  font-size: 0.9rem;
  transition: all 0.2s;
}

.user-btn:hover {
  background: #f0ece4;
}

.user-btn.active {
  background: #1a1a1a;
  color: #fff;
  border-color: #1a1a1a;
}

.register-form {
  display: flex;
  gap: 8px;
}

.register-form input {
  padding: 6px 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 0.9rem;
  flex: 1;
}

.novel-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 24px;
}

.novel-card {
  background: #fffdf7;
  border: 1px solid #e8e0d0;
  border-radius: 8px;
  padding: 28px 24px;
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.2s;
}

.novel-card:hover {
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.08);
  transform: translateY(-2px);
}

.novel-title {
  font-size: 1.25rem;
  font-weight: 600;
  margin-bottom: 8px;
  color: #1a1a1a;
}

.novel-meta {
  font-size: 0.85rem;
  color: #999;
  margin-bottom: 12px;
}

.novel-desc {
  font-size: 0.9rem;
  color: #666;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.empty-state {
  grid-column: 1 / -1;
  text-align: center;
  padding: 80px 0;
  color: #999;
}

.empty-state .hint {
  margin-top: 12px;
  font-size: 0.85rem;
  color: #bbb;
}

.notification-bell {
  position: relative;
  text-decoration: none;
  font-size: 1.1rem;
  margin-right: 10px;
}

.bell-badge {
  position: absolute;
  top: -6px;
  right: -10px;
  background: #e65100;
  color: white;
  font-size: 0.6rem;
  min-width: 16px;
  height: 16px;
  line-height: 16px;
  border-radius: 8px;
  text-align: center;
  padding: 0 3px;
}
</style>
