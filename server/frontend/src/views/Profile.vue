<template>
  <div class="profile-page">
    <header class="profile-header">
      <router-link to="/" class="back-btn">← 返回书架</router-link>
      <h2>个人中心</h2>
    </header>

    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="loadError" class="empty error">{{ loadError }}</div>
    <template v-else>
      <!-- 基本信息 -->
      <section class="profile-card">
        <div class="avatar">{{ profile.nickname?.charAt(0) || 'U' }}</div>
        <div class="profile-info">
          <div class="nickname">{{ profile.nickname }}
            <span class="role-badge" :class="profile.role">{{ profile.role === 'admin' ? '管理员' : '读者' }}</span>
          </div>
          <div class="registered">注册于 {{ profile.registeredAt }}</div>
        </div>
      </section>

      <!-- 社区贡献统计 -->
      <section class="stats-grid">
        <div class="stat-card"><span class="stat-value">{{ profile.annotationCount }}</span><span class="stat-label">批注</span></div>
        <div class="stat-card"><span class="stat-value">{{ profile.totalLikesReceived }}</span><span class="stat-label">获赞</span></div>
        <div class="stat-card"><span class="stat-value">{{ profile.totalCommentsReceived }}</span><span class="stat-label">获评论</span></div>
        <div class="stat-card"><span class="stat-value">{{ profile.favoriteCount }}</span><span class="stat-label">收藏</span></div>
      </section>

      <!-- 最近批注 -->
      <section class="recent-section">
        <h3>最近批注</h3>
        <div v-if="!profile.recentAnnotations || profile.recentAnnotations.length === 0" class="empty">暂无批注</div>
        <div v-else class="recent-list">
          <div v-for="ann in profile.recentAnnotations" :key="ann.id" class="recent-item">
            <div class="recent-original">「{{ ann.selectedText }}」</div>
            <div class="recent-content">{{ ann.content }}</div>
            <div class="recent-meta">
              <span>👍 {{ ann.likeCount || 0 }}</span>
              <span>💬 {{ ann.commentCount || 0 }}</span>
              <span class="recent-time">{{ formatTime(ann.createdAt) }}</span>
            </div>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getMyProfile } from '../api/index.js'

const profile = ref({})
const loading = ref(true)
const loadError = ref('')

function formatTime(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

async function loadProfile() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await getMyProfile()
    profile.value = res.data
  } catch (e) {
    loadError.value = e.message || '加载个人中心失败'
  }
  loading.value = false
}

onMounted(loadProfile)
</script>

<style scoped>
.profile-page { max-width: 720px; margin: 0 auto; padding: 24px 16px; }
.profile-header { display: flex; align-items: center; gap: 16px; margin-bottom: 24px; }
.profile-header h2 { margin: 0; color: #5a4a3a; }
.back-btn { color: #8b7355; text-decoration: none; font-size: 0.9rem; }
.back-btn:hover { text-decoration: underline; }
.loading, .empty { text-align: center; color: #999; padding: 60px 0; }
.empty.error { color: #e74c3c; }

.profile-card { display: flex; align-items: center; gap: 18px; background: #fffdf7; border: 1px solid #e8e0d0; border-radius: 12px; padding: 24px; margin-bottom: 20px; }
.avatar { width: 64px; height: 64px; border-radius: 50%; background: #8b7355; color: #fff; font-size: 1.8rem; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.nickname { font-size: 1.3rem; font-weight: 600; color: #4a3a2a; display: flex; align-items: center; gap: 8px; }
.role-badge { font-size: 0.7rem; padding: 2px 8px; border-radius: 10px; background: #f0ece4; color: #8b7355; }
.role-badge.admin { background: #fff3e0; color: #e65100; }
.registered { font-size: 0.85rem; color: #999; margin-top: 6px; }

.stats-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 24px; }
.stat-card { background: #fffdf7; border: 1px solid #e8e0d0; border-radius: 10px; padding: 18px 8px; text-align: center; display: flex; flex-direction: column; gap: 6px; }
.stat-value { font-size: 1.5rem; font-weight: 700; color: #8b7355; }
.stat-label { font-size: 0.8rem; color: #999; }

.recent-section h3 { color: #5a4a3a; margin-bottom: 14px; font-size: 1.05rem; }
.recent-list { display: flex; flex-direction: column; gap: 12px; }
.recent-item { background: #fffdf7; border: 1px solid #e8e0d0; border-radius: 8px; padding: 14px 16px; }
.recent-original { font-size: 0.85rem; color: #8b7355; margin-bottom: 6px; }
.recent-content { font-size: 0.9rem; color: #333; line-height: 1.6; margin-bottom: 8px; }
.recent-meta { display: flex; gap: 14px; font-size: 0.78rem; color: #999; }
.recent-time { margin-left: auto; }
@media (max-width: 480px) { .stats-grid { grid-template-columns: repeat(2, 1fr); } }
</style>
