<template>
  <el-container class="layout">
    <!-- 侧边栏 -->
    <el-aside :width="collapsed ? '64px' : '220px'" class="aside">
      <div class="logo">
        <span class="logo-mark">J</span>
        <span v-if="!collapsed" class="logo-text">Japy 管理端</span>
      </div>
      <el-scrollbar>
        <el-menu
          :default-active="activeMenu"
          :collapse="collapsed"
          :collapse-transition="false"
          router
          background-color="#1f2937"
          text-color="#cbd5e1"
          active-text-color="#ffffff"
        >
          <template v-for="item in menuTree" :key="item.name">
            <el-sub-menu v-if="item.children && item.children.length" :index="item.path">
              <template #title>
                <el-icon><component :is="iconOf(item.meta.icon)" /></el-icon>
                <span>{{ item.meta.title }}</span>
              </template>
              <el-menu-item v-for="child in item.children" :key="child.name" :index="joinPath(item.path, child.path)">
                <el-icon><component :is="iconOf(child.meta.icon)" /></el-icon>
                <span>{{ child.meta.title }}</span>
              </el-menu-item>
            </el-sub-menu>
            <el-menu-item v-else :index="item.path">
              <el-icon><component :is="iconOf(item.meta.icon)" /></el-icon>
              <template #title>{{ item.meta.title }}</template>
            </el-menu-item>
          </template>
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <el-container>
      <!-- 顶栏 -->
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="collapsed = !collapsed">
            <Expand v-if="collapsed" />
            <Fold v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-tag size="small" effect="plain" class="role-tag">
            {{ userStore.roles.includes('admin') ? '超级管理员' : userStore.roles.join(',') }}
          </el-tag>
          <el-dropdown @command="onCommand">
            <span class="user-info">
              <el-avatar :size="28" :src="userStore.user?.avatar || undefined">{{ userStore.nickname.charAt(0) }}</el-avatar>
              <span class="nickname">{{ userStore.nickname }}</span>
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

      <!-- 主内容 -->
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { usePermissionStore } from '@/stores/permission'
import * as Icons from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const permStore = usePermissionStore()

const collapsed = ref(false)
const menuTree = computed(() => permStore.menuTree)
const activeMenu = computed(() => route.path)
const currentTitle = computed(() => (route.meta?.title as string) || '')

function joinPath(parent: string, child?: string) {
  if (!child) return parent
  if (child.startsWith('/')) return child
  return `${parent.replace(/\/$/, '')}/${child}`
}

function iconOf(name?: string) {
  if (!name) return 'Menu'
  return (Icons as any)[name] || 'Menu'
}

async function onCommand(cmd: string) {
  if (cmd === 'logout') {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
    await userStore.logout()
    permStore.reset()
    router.push('/login')
  }
}
</script>

<style scoped>
.layout {
  height: 100vh;
}
.aside {
  background: #1f2937;
  transition: width 0.2s;
  overflow: hidden;
}
.logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}
.logo-mark {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 15px;
}
.logo-text {
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap;
}
.aside :deep(.el-menu) {
  border-right: none;
}
.header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  z-index: 1;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
}
.collapse-btn {
  font-size: 18px;
  cursor: pointer;
  color: #4b5563;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.role-tag {
  border-radius: 10px;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}
.nickname {
  font-size: 14px;
  color: #374151;
}
.main {
  background: #f5f7fa;
  padding: 16px;
  overflow-y: auto;
}
</style>
