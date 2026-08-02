import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import Layout from '../layout/Layout.vue'

const routes = [
  { path: '/login', component: () => import('../views/Login.vue') },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '仪表盘' } },
      { path: 'users', name: 'Users', component: () => import('../views/Users.vue'), meta: { title: '用户管理' } },
      { path: 'moments', name: 'Moments', component: () => import('../views/Moments.vue'), meta: { title: '动态管理' } },
      { path: 'comments', name: 'Comments', component: () => import('../views/Comments.vue'), meta: { title: '评论管理' } },
      { path: 'reports', name: 'Reports', component: () => import('../views/Reports.vue'), meta: { title: '举报处理' } },
      { path: 'words', name: 'Words', component: () => import('../views/Words.vue'), meta: { title: '敏感词管理' } },
      { path: 'announce', name: 'Announce', component: () => import('../views/Announce.vue'), meta: { title: '公告广播' } },
      { path: 'novels', name: 'Novels', component: () => import('../views/Novels.vue'), meta: { title: '小说管理' } },
      { path: 'logs', name: 'Logs', component: () => import('../views/Logs.vue'), meta: { title: '操作日志' } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(to => {
  const auth = useAuthStore()
  if (to.path !== '/login' && !auth.token) return '/login'
  if (to.path === '/login' && auth.token) return '/'
  return true
})

export default router
