import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  { path: '/login', component: () => import('../views/Login.vue') },
  {
    path: '/',
    component: () => import('../layout/AppLayout.vue'),
    children: [
      { path: '', name: 'Feed', component: () => import('../views/Feed.vue'), meta: { title: '动态' } },
      { path: 'novels', name: 'Novels', component: () => import('../views/Novels.vue'), meta: { title: '小说' } },
      { path: 'notifications', name: 'Notifications', component: () => import('../views/Notifications.vue'), meta: { title: '通知', auth: true, fullWidth: true } },
      { path: 'user/:id', name: 'UserProfile', component: () => import('../views/UserProfile.vue'), meta: { title: '个人主页' } },
      { path: 'me', name: 'Me', component: () => import('../views/Me.vue'), meta: { title: '我的', auth: true } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach(to => {
  const auth = useAuthStore()
  if (to.meta.auth && !auth.token) return { path: '/login', query: { redirect: to.fullPath } }
  if (to.path === '/login' && auth.token) return '/'
  return true
})

export default router
