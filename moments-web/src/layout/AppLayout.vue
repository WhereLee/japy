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
          <router-link to="/" class="nav-item" :class="{ active: $route.path === '/' }">首页</router-link>
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
            <button class="logout-btn" @click="logout">退出</button>
          </template>
          <router-link v-else to="/login" class="btn primary btn-sm">登录</router-link>
        </div>
      </div>
    </header>

    <!-- 内容 -->
    <main class="main" :class="{ 'main-full': $route.meta.fullWidth }">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import http from '../api'
import { avatarChar } from '../utils/format'
import { unread, refreshUnread } from '../stores/notify'

const auth = useAuthStore()
const router = useRouter()

let timer = null
function startPolling() {
  refreshUnread()
  timer = setInterval(refreshUnread, 30000)
}
onMounted(() => { startPolling(); window.addEventListener('focus', refreshUnread) })
onUnmounted(() => { clearInterval(timer); window.removeEventListener('focus', refreshUnread) })

function logout() {
  auth.logout()
  router.push('/login')
}
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
.logout-btn {
  border: none; background: none;
  font-size: 12px; color: var(--text-3);
  cursor: pointer; padding: 4px 8px;
}
.logout-btn:hover { color: var(--danger); }
.btn-sm { padding: 5px 14px; font-size: 13px; }
.main {
  max-width: 880px; margin: 0 auto;
  padding: 20px 16px 80px;
}
.main-full {
  max-width: none;
  padding: 16px 24px 40px;
}
@media (max-width: 640px) {
  .me-name { display: none; }
}
</style>
