import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('@/views/Login.vue'), meta: { title: '登录' } },
    { path: '/', component: () => import('@/views/Home.vue'), meta: { title: '书库' } },
    { path: '/novel/:id', component: () => import('@/views/NovelDetail.vue'), meta: { title: '小说详情' } },
    { path: '/reader/:chapterId', component: () => import('@/views/Reader.vue'), meta: { title: '阅读' } }
  ]
})

const WHITE = ['/login']
router.beforeEach((to) => {
  const userStore = useUserStore()
  if (!WHITE.includes(to.path) && !userStore.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

router.afterEach((to) => {
  document.title = (to.meta?.title ? to.meta.title + ' - ' : '') + 'Japy 阅读'
})

export default router
