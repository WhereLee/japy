import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { usePermissionStore } from '@/stores/permission'

// 常量路由（无需权限）
export const constantRoutes = [
  { path: '/login', component: () => import('@/views/login/index.vue'), meta: { title: '登录' } },
  { path: '/', redirect: '/dashboard', meta: { hidden: true } },
  { path: '/404', component: () => import('@/views/error/404.vue'), meta: { hidden: true } }
]

const router = createRouter({
  history: createWebHistory(),
  routes: constantRoutes
})

const WHITE_LIST = ['/login']

router.beforeEach(async (to) => {
  const userStore = useUserStore()
  if (WHITE_LIST.includes(to.path)) {
    return true
  }
  if (!userStore.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  // 已登录但没拉权限 → 拉信息 + 动态路由
  const permStore = usePermissionStore()
  if (!permStore.loaded) {
    try {
      await userStore.fetchInfo()
      const routes = await permStore.generateRoutes()
      routes.forEach((r: any) => router.addRoute(r))
      // 404 兜底最后注册（避免抢在动态路由前匹配）
      router.addRoute({ path: '/:pathMatch(.*)*', redirect: '/404', meta: { hidden: true } })
      // 动态路由注册后再进目标页
      return { ...to, replace: true }
    } catch (e) {
      userStore.reset()
      permStore.reset()
      return { path: '/login' }
    }
  }
  return true
})

router.afterEach((to) => {
  document.title = (to.meta?.title ? to.meta.title + ' - ' : '') + 'Japy 管理端'
})

export default router
