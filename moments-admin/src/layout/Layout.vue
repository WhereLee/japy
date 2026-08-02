<template>
  <el-container class="layout">
    <!-- 侧边栏 -->
    <el-aside :width="collapsed ? '64px' : '220px'" class="aside">
      <div class="logo" :style="{ justifyContent: collapsed ? 'center' : 'space-between' }">
        <div style="display:flex;align-items:center;gap:8px;overflow:hidden;">
          <span class="logo-icon">拼</span>
          <span v-show="!collapsed" class="logo-text">拼象管理后台</span>
        </div>
        <el-icon v-show="!collapsed" class="collapse-btn" @click="collapsed = true"><Fold /></el-icon>
      </div>
      <el-menu
        :default-active="$route.path"
        router
        :collapse="collapsed"
        :collapse-transition="false"
        background-color="#001529"
        text-color="rgba(255,255,255,0.68)"
        active-text-color="#ffffff"
        class="menu"
      >
        <el-menu-item index="/dashboard"><el-icon><Odometer /></el-icon><span>仪表盘</span></el-menu-item>
        <el-menu-item index="/users"><el-icon><User /></el-icon><span>用户管理</span></el-menu-item>
        <el-menu-item index="/moments"><el-icon><ChatDotRound /></el-icon><span>动态管理</span></el-menu-item>
        <el-menu-item index="/comments"><el-icon><Comment /></el-icon><span>评论管理</span></el-menu-item>
        <el-menu-item index="/reports"><el-icon><Warning /></el-icon><span>举报处理</span></el-menu-item>
        <el-menu-item index="/words"><el-icon><Lock /></el-icon><span>敏感词管理</span></el-menu-item>
        <el-menu-item index="/announce"><el-icon><Bell /></el-icon><span>公告广播</span></el-menu-item>
        <el-menu-item index="/novels"><el-icon><Reading /></el-icon><span>小说管理</span></el-menu-item>
        <el-menu-item index="/logs"><el-icon><Document /></el-icon><span>操作日志</span></el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <!-- 顶栏 -->
      <el-header class="header">
        <div style="display:flex;align-items:center;gap:14px;">
          <el-icon v-if="collapsed" class="expand-btn" :size="20" @click="collapsed = false"><Expand /></el-icon>
          <div class="page-title">{{ $route.meta.title }}</div>
        </div>
        <div class="header-right">
          <el-dropdown @command="onCommand">
            <span class="admin-info">
              <el-avatar :size="30" class="avatar">{{ (auth.nickname || '管').charAt(0) }}</el-avatar>
              <span class="name">{{ auth.nickname || '管理员' }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 内容区 -->
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import {
  Odometer, User, ChatDotRound, Comment, Warning, Lock, Bell, Reading, Document, ArrowDown, Fold, Expand
} from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const collapsed = ref(false)

async function onCommand(cmd) {
  if (cmd === 'logout') {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
    auth.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.layout { height: 100%; }
.aside {
  background: #001529;
  display: flex; flex-direction: column;
  overflow: hidden;
}
.logo {
  height: 56px; display: flex; align-items: center; gap: 8px;
  color: #fff; font-size: 16px; font-weight: 600;
  padding: 0 16px;
  border-bottom: 1px solid rgba(255,255,255,0.08);
  flex-shrink: 0;
  transition: all .2s;
}
.logo-text { white-space: nowrap; }
.collapse-btn { cursor: pointer; color: rgba(255,255,255,0.6); font-size: 16px; }
.collapse-btn:hover { color: #fff; }
.expand-btn { cursor: pointer; color: #606266; }
.expand-btn:hover { color: #3b6ef6; }
.logo-icon {
  width: 30px; height: 30px; border-radius: 6px;
  background: linear-gradient(135deg, #3b6ef6, #7c5cf0);
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 15px;
}
.menu { border-right: none; flex: 1; overflow-y: auto; overflow-x: hidden; }
.menu:not(.el-menu--collapse) { width: 220px; }
.menu .el-menu-item.is-active { background: #3b6ef6 !important; }
.menu .el-menu-item:hover { background: rgba(255,255,255,0.08); }
.header {
  background: #fff; height: 56px;
  display: flex; align-items: center; justify-content: space-between;
  box-shadow: 0 1px 4px rgba(0,21,41,0.08);
  position: relative; z-index: 5;
}
.page-title { font-size: 16px; font-weight: 600; color: #1f2329; }
.admin-info { display: flex; align-items: center; gap: 8px; cursor: pointer; outline: none; }
.avatar { background: #3b6ef6; }
.name { font-size: 14px; color: #333; }
.main { padding: 16px; overflow-y: auto; background: #f0f2f5; }
</style>
