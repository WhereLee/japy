import axios from 'axios'

const api = axios.create({
  baseURL: '/'
})

// Token 刷新状态管理
let isRefreshing = false
let failedQueue = []

const processQueue = (error, token = null) => {
  failedQueue.forEach(prom => {
    if (error) {
      prom.reject(error)
    } else {
      prom.resolve(token)
    }
  })
  failedQueue = []
}

// 请求拦截器：自动携带 JWT Token
api.interceptors.request.use(config => {
  const token = localStorage.getItem('accessToken')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  // 幂等性 Token（写操作自动生成 UUID）
  if (['post', 'put', 'delete'].includes(config.method)) {
    config.headers['X-Idempotent-Token'] = crypto.randomUUID()
  }
  return config
})

// 响应拦截器：处理 401 + 自动刷新 Token
api.interceptors.response.use(
  response => {
    // 统一 R<T> 格式：response.data 就是 R<T>
    const res = response.data
    if (res.code !== 200) {
      // 业务错误
      if (res.code === 401) {
        return handleUnauthorized(response.config)
      }
      return Promise.reject(new Error(res.msg || '请求失败'))
    }
    return res
  },
  error => {
    if (error.response?.status === 401) {
      return handleUnauthorized(error.config)
    }
    // 提取后端业务错误信息（R<T> 格式的 msg 字段）
    const msg = error.response?.data?.msg || error.message || '请求失败'
    return Promise.reject(new Error(msg))
  }
)

// 处理 401：尝试刷新 Token，失败则跳转登录
async function handleUnauthorized(originalRequest) {
  // 如果已经在刷新中，将请求加入队列等待
  if (isRefreshing) {
    return new Promise((resolve, reject) => {
      failedQueue.push({ resolve, reject })
    }).then(token => {
      originalRequest.headers.Authorization = `Bearer ${token}`
      return api(originalRequest)
    })
  }

  // 如果是刷新 Token 的请求本身失败，直接跳转登录
  if (originalRequest._isRetry) {
    clearAuthAndRedirect()
    return Promise.reject(new Error('Token 刷新失败'))
  }

  isRefreshing = true
  originalRequest._isRetry = true

  try {
    const refreshToken = localStorage.getItem('refreshToken')
    if (!refreshToken) {
      throw new Error('No refresh token')
    }

    // 调用刷新接口
    const response = await axios.post('/auth/refresh', { refreshToken })
    const res = response.data

    if (res.code === 200) {
      const { accessToken, refreshToken: newRefreshToken } = res.data
      localStorage.setItem('accessToken', accessToken)
      localStorage.setItem('refreshToken', newRefreshToken)

      // 重试原始请求
      originalRequest.headers.Authorization = `Bearer ${accessToken}`
      processQueue(null, accessToken)
      return api(originalRequest)
    } else {
      throw new Error(res.msg)
    }
  } catch (error) {
    processQueue(error, null)
    clearAuthAndRedirect()
    return Promise.reject(error)
  } finally {
    isRefreshing = false
  }
}

function clearAuthAndRedirect() {
  localStorage.removeItem('accessToken')
  localStorage.removeItem('refreshToken')
  localStorage.removeItem('userInfo')
  window.location.href = '/login'
}

// ==================== 认证 API ====================

export function register(data) {
  return api.post('/auth/register', data)
}

export function login(data) {
  return api.post('/auth/login', data)
}

export function logout() {
  const refreshToken = localStorage.getItem('refreshToken')
  return api.post('/auth/logout', null, {
    headers: { 'X-Refresh-Token': refreshToken }
  })
}

export function refreshToken(refreshTokenValue) {
  return api.post('/auth/refresh', { refreshToken: refreshTokenValue })
}

// ==================== 小说 API ====================

export function getNovels() {
  return api.get('/api/novels')
}

export function getNovelDetail(id) {
  return api.get(`/api/novels/${id}`)
}

export function getChapter(id) {
  return api.get(`/api/chapters/${id}`)
}

// ==================== 批注 API ====================

export function createAnnotation(data) {
  return api.post('/api/annotations', data)
}

export function getAnnotations(chapterId) {
  return api.get('/api/annotations', { params: { chapterId } })
}

export function deleteAnnotation(id) {
  return api.delete(`/api/annotations/${id}`)
}

export function toggleLike(annotationId) {
  return api.post(`/api/annotations/${annotationId}/like`)
}

export function getLikeStatus(annotationId) {
  return api.get(`/api/annotations/${annotationId}/like-status`)
}

// ==================== 举报 API ====================

export function createReport(data) {
  return api.post('/api/reports', data)
}

export function getMyAnnotations(params) {
  return api.get('/api/annotations/mine', { params })
}

// ==================== 评论 API ====================

export function createComment(data) {
  return api.post('/api/comments', data)
}

export function getComments(annotationId) {
  return api.get('/api/comments', { params: { annotationId } })
}

export function deleteComment(id) {
  return api.delete(`/api/comments/${id}`)
}

// ==================== 用户 API ====================

export function getCurrentUser() {
  return api.get('/api/users/me')
}

export function getMyProfile() {
  return api.get('/api/users/me/profile')
}

export function updateNickname(nickname) {
  return api.put('/api/users/me/nickname', null, { params: { nickname } })
}

// ==================== 管理端 API ====================

export function getAdminDashboard() {
  return api.get('/admin/dashboard')
}

// 兼容旧名称
export const getDashboard = getAdminDashboard

export function getAdminUsers(params) {
  return api.get('/admin/users', { params })
}

export function updateUserStatus(id, status) {
  return api.put(`/admin/users/${id}/status`, null, { params: { status } })
}

export function resetUserPassword(id) {
  return api.put(`/admin/users/${id}/reset-password`)
}

export function getAdminNovels(params) {
  return api.get('/admin/novels', { params })
}

export function importNovels() {
  return api.post('/admin/novels/import')
}

export function deleteNovel(id) {
  return api.delete(`/admin/novels/${id}`)
}

// 兼容旧名称
export const deleteAdminNovel = deleteNovel

export function getAdminAnnotations(params) {
  return api.get('/admin/annotations', { params })
}

export function deleteAdminAnnotation(id) {
  return api.delete(`/admin/annotations/${id}`)
}

export function getAdminLogs(params) {
  return api.get('/admin/logs', { params })
}

// ==================== 通知 API ====================

export function getNotifications(params) {
  return api.get('/api/notifications', { params })
}

export function getUnreadCount() {
  return api.get('/api/notifications/unread-count')
}

export function markNotificationRead(id) {
  return api.put(`/api/notifications/${id}/read`)
}

export function markAllNotificationsRead() {
  return api.put('/api/notifications/read-all')
}

// ==================== 管理端通知 API ====================

export function broadcastNotification(data) {
  return api.post('/admin/notifications/broadcast', data)
}

// ==================== 管理端举报 API ====================

export function getAdminReports(params) {
  return api.get('/admin/reports', { params })
}

export function handleReport(id, status, handleNote) {
  return api.put(`/admin/reports/${id}/handle`, null, { params: { status, handleNote } })
}

// ==================== 用户密码 API ====================

export function changePassword(data) {
  return api.put('/api/users/me/password', data)
}

export default api
