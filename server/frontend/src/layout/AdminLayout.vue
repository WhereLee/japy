<template>
  <div class="admin-layout">
    <aside class="sidebar">
      <div class="logo">ReCloud 管理</div>
      <nav>
        <router-link to="/admin/dashboard" class="nav-item">数据概览</router-link>
        <router-link to="/admin/users" class="nav-item">用户管理</router-link>
        <router-link to="/admin/annotations" class="nav-item">批注管理</router-link>
        <router-link to="/admin/reports" class="nav-item">举报管理</router-link>
        <router-link to="/admin/novels" class="nav-item">小说管理</router-link>
        <router-link to="/admin/logs" class="nav-item">操作日志</router-link>
      </nav>
    </aside>
    <main class="main-content">
      <header class="topbar">
        <span>管理员：{{ userInfo?.nickname || userInfo?.username }}</span>
        <button @click="handleLogout" class="logout-btn">退出</button>
      </header>
      <div class="content">
        <router-view />
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const userInfo = computed(() => {
  try {
    return JSON.parse(localStorage.getItem('userInfo') || '{}')
  } catch { return {} }
})

function handleLogout() {
  localStorage.removeItem('accessToken')
  localStorage.removeItem('refreshToken')
  localStorage.removeItem('userInfo')
  router.push('/login')
}
</script>

<style scoped>
.admin-layout { display: flex; min-height: 100vh; }
.sidebar { width: 200px; background: #1a1a2e; color: #fff; padding: 20px 0; }
.logo { text-align: center; font-size: 18px; font-weight: bold; padding: 10px 0 20px; border-bottom: 1px solid #333; }
.nav-item { display: block; padding: 12px 20px; color: #ccc; text-decoration: none; transition: all 0.2s; }
.nav-item:hover, .nav-item.router-link-active { background: #16213e; color: #fff; }
.main-content { flex: 1; background: #f5f5f5; }
.topbar { background: #fff; padding: 12px 20px; display: flex; justify-content: space-between; align-items: center; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
.logout-btn { padding: 6px 16px; background: #e74c3c; color: #fff; border: none; border-radius: 4px; cursor: pointer; }
.content { padding: 20px; }
</style>
