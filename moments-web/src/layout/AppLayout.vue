<template>
  <div class="layout">
    <!-- 顶栏 -->
    <header class="topbar">
      <div class="topbar-inner">
        <router-link to="/" class="brand">
          <span class="brand-mark">拼</span>
          <span class="brand-name">拼象</span>
        </router-link>

        <nav class="nav">
          <router-link to="/" class="nav-item" :class="{ active: $route.path === '/' }">动态</router-link>
          <router-link to="/novels" class="nav-item" :class="{ active: $route.path.startsWith('/novels') }">小说</router-link>
        </nav>

        <div class="topbar-right">
          <template v-if="auth.isLogin">
            <router-link to="/notifications" class="bell-wrap">
              <span class="bell">🔔</span>
              <span v-if="unread > 0" class="badge">{{ unread > 99 ? '99+' : unread }}</span>
            </router-link>
            <router-link to="/me" class="me">
              <span class="avatar avatar-sm">{{ avatarChar(auth.nickname) }}</span>
              <span class="me-name">{{ auth.nickname }}</span>
            </router-link>
          </template>
          <router-link v-else to="/login" class="btn primary btn-sm">登录</router-link>
        </div>
      </div>
    </header>

    <!-- 内容 -->
    <main class="main">
      <router-view />
    </main>

    <!-- 移动端浮动发动态按钮 -->
    <button v-if="auth.isLogin && $route.path === '/'" class="fab" @click="openPublish()">✎</button>

    <!-- 发动态弹层 -->
    <PublishModal />
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useAuthStore } from '../stores/auth'
import http from '../api'
import { avatarChar } from '../utils/format'
import { openPublish } from '../stores/publish'
import PublishModal from '../components/PublishModal.vue'

const auth = useAuthStore()
const unread = ref(0)

let timer = null
async function loadUnread() {
  if (!auth.isLogin) return
  try {
    const data = await http.get('/api/notifications/unread-count')
    unread.value = data.count
  } catch { /* 静默 */ }
}
function startPolling() {
  loadUnread()
  timer = setInterval(loadUnread, 30000)
}
onMounted(() => { startPolling(); window.addEventListener('focus', loadUnread) })
onUnmounted(() => { clearInterval(timer); window.removeEventListener('focus', loadUnread) })
</script>

<style scoped>
.topbar {
  position: sticky; top: 0; z-index: 100;
  background: rgba(250,248,244,.92);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--line);
}
.topbar-inner {
  max-width: 880px; margin: 0 auto;
  height: 56px;
  display: flex; align-items: center; gap: 28px;
  padding: 0 16px;
}
.brand { display: flex; align-items: center; gap: 8px; }
.brand-mark {
  width: 30px; height: 30px; border-radius: 8px;
  background: var(--ink);
  color: var(--accent);
  font-family: var(--serif);
  font-size: 15px; font-weight: 700;
  display: inline-flex; align-items: center; justify-content: center;
}
.brand-name { font-family: var(--serif); font-size: 19px; font-weight: 700; color: var(--ink); letter-spacing: 2px; }
.nav { display: flex; gap: 6px; flex: 1; }
.nav-item {
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 14px;
  color: var(--text-2);
  transition: all .15s;
}
.nav-item:hover { color: var(--text); background: var(--line-soft); }
.nav-item.active { color: var(--accent); font-weight: 600; }
.topbar-right { display: flex; align-items: center; gap: 16px; }
.bell-wrap { position: relative; font-size: 18px; line-height: 1; cursor: pointer; }
.badge {
  position: absolute; top: -7px; right: -10px;
  background: var(--danger); color: #fff;
  font-size: 10px; padding: 1px 5px;
  border-radius: 999px;
  line-height: 1.5;
}
.me { display: flex; align-items: center; gap: 8px; }
.avatar-sm { width: 30px; height: 30px; font-size: 13px; }
.me-name { font-size: 13px; color: var(--text); max-width: 90px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.btn-sm { padding: 5px 14px; font-size: 13px; }
.main {
  max-width: 880px; margin: 0 auto;
  padding: 20px 16px 80px;
}
.fab {
  position: fixed; right: 24px; bottom: 40px;
  width: 52px; height: 52px;
  border-radius: 50%;
  border: none;
  background: var(--ink); color: var(--accent);
  font-size: 22px;
  box-shadow: 0 6px 20px rgba(30,26,20,.35);
  cursor: pointer;
  z-index: 90;
  transition: transform .15s;
}
.fab:hover { transform: scale(1.06); }
@media (max-width: 640px) {
  .me-name { display: none; }
}
</style>
