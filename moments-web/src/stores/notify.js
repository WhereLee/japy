import { ref } from 'vue'
import http from '../api'

/** 全局未读数：顶栏 badge 与通知页共享，已读操作后实时更新（无需刷新） */
export const unread = ref(0)

/** 从后端拉取未读数并更新全局状态 */
export async function refreshUnread() {
  try {
    const data = await http.get('/api/notifications/unread-count')
    unread.value = data.count
  } catch { /* 未登录等静默 */ }
}
