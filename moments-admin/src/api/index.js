import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { useAuthStore } from '../stores/auth'

const http = axios.create({ timeout: 60000 })

http.interceptors.request.use(config => {
  const auth = useAuthStore()
  if (auth.token) config.headers.Authorization = `Bearer ${auth.token}`
  return config
})

http.interceptors.response.use(
  res => {
    const json = res.data
    if (json && json.code === 200) return json.data
    ElMessage.error(json?.msg || '请求失败')
    return Promise.reject(new Error(json?.msg || '请求失败'))
  },
  err => {
    if (err.response?.status === 401 || err.response?.status === 403) {
      const auth = useAuthStore()
      auth.logout()
      ElMessage.warning('登录已失效，请重新登录')
      router.push('/login')
    } else if (err.response?.status === 400) {
      ElMessage.error(err.response.data?.msg || '参数错误')
    } else {
      ElMessage.error(err.message || '网络错误')
    }
    return Promise.reject(err)
  }
)

export default http
