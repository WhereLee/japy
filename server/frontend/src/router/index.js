import { createRouter, createWebHistory } from 'vue-router'
import api from '../api/index.js'

const routes = [
  { path: '/', component: () => import('../views/NovelList.vue') },
  { path: '/read/:novelId', component: () => import('../views/Reader.vue') },
  { path: '/notifications', component: () => import('../views/Notifications.vue') },
  { path: '/profile', component: () => import('../views/Profile.vue') },
  { path: '/login', component: () => import('../views/Login.vue') },
  {
    path: '/admin',
    component: () => import('../layout/AdminLayout.vue'),
    meta: { requiresAdmin: true },
    children: [
      { path: '', redirect: '/admin/dashboard' },
      { path: 'dashboard', component: () => import('../views/admin/Dashboard.vue') },
      { path: 'users', component: () => import('../views/admin/Users.vue') },
      { path: 'annotations', component: () => import('../views/admin/Annotations.vue') },
      { path: 'reports', component: () => import('../views/admin/Reports.vue') },
      { path: 'novels', component: () => import('../views/admin/Novels.vue') },
      { path: 'logs', component: () => import('../views/admin/Logs.vue') }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：调后端验证用户身份，而非依赖 localStorage
router.beforeEach(async (to, from, next) => {
  if (to.meta.requiresAdmin) {
    const token = localStorage.getItem('accessToken')
    if (!token) {
      return next('/login')
    }
    try {
      const res = await api.get('/api/users/me')
      if (res.data?.role !== 'admin') {
        return next('/login')
      }
      next()
    } catch {
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('userInfo')
      return next('/login')
    }
  } else {
    next()
  }
})

export default router
