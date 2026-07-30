import { ref } from 'vue'
import { getCurrentUser } from '../api'

const currentUser = ref(null)
const userMap = ref({}) // id -> nickname
let initialized = false

/**
 * 公共用户状态管理 composable
 * 
 * 全局单例：所有组件共享同一个 currentUser 和 userMap
 * 避免每个页面重复请求 /api/users/me
 */
export function useCurrentUser() {

  async function initUser() {
    const token = localStorage.getItem('accessToken')
    if (!token) {
      currentUser.value = null
      return
    }
    try {
      const res = await getCurrentUser()
      currentUser.value = res.data
      userMap.value[res.data.id] = res.data.nickname || res.data.username
    } catch {
      currentUser.value = null
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('userInfo')
    }
  }

  async function ensureUser() {
    if (!initialized) {
      initialized = true
      await initUser()
    }
    return currentUser.value
  }

  function getUserNickname(userId) {
    return userMap.value[userId] || `用户${userId}`
  }

  function clearUser() {
    currentUser.value = null
    initialized = false
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('userInfo')
  }

  return {
    currentUser,
    userMap,
    initUser,
    ensureUser,
    getUserNickname,
    clearUser
  }
}
