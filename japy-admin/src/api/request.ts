import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, clearToken, getRefreshToken } from '@/utils/auth'

const service = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// 请求拦截：带 access token
service.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

let refreshing = false
let waitQueue: Array<(t: string | null) => void> = []

// 刷新 access token
async function doRefresh(): Promise<string | null> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) return null
  try {
    const { data } = await axios.post('/api/auth/refresh', { refreshToken })
    if (data.code === 200) {
      localStorage.setItem('access_token', data.data.accessToken)
      localStorage.setItem('refresh_token', data.data.refreshToken)
      return data.data.accessToken
    }
    return null
  } catch {
    return null
  }
}

// 响应拦截：统一解包 + 401 自动刷新 + 错误提示
service.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code === 200) return res.data
    // 业务错误（400/403...）
    if (res.code === 401) {
      ElMessage.error('登录已过期，请重新登录')
      clearToken()
      window.location.href = '/login'
      return Promise.reject(new Error(res.msg || '未登录'))
    }
    ElMessage.error(res.msg || '请求失败')
    return Promise.reject(new Error(res.msg || '请求失败'))
  },
  async (error) => {
    const status = error.response?.status
    const original = error.config
    // token 过期 → 单飞刷新
    if (status === 401 && !original._retry && !original.url.includes('/auth/')) {
      if (!refreshing) {
        refreshing = true
        const newToken = await doRefresh()
        refreshing = false
        waitQueue.forEach((cb) => cb(newToken))
        waitQueue = []
        if (newToken) {
          original._retry = true
          original.headers.Authorization = `Bearer ${newToken}`
          return service(original)
        }
      } else {
        // 等待刷新完成
        return new Promise((resolve) => {
          waitQueue.push((t) => {
            if (t) {
              original._retry = true
              original.headers.Authorization = `Bearer ${t}`
              resolve(service(original))
            } else {
              resolve(Promise.reject(new Error('刷新失败')))
            }
          })
        })
      }
      clearToken()
      ElMessage.error('登录已过期，请重新登录')
      window.location.href = '/login'
    } else {
      ElMessage.error(error.response?.data?.msg || '网络错误')
    }
    return Promise.reject(error)
  }
)

export default service
