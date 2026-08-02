import axios from 'axios'
import router from '../router'
import { useAuthStore } from '../stores/auth'

const http = axios.create({ timeout: 30000 })

http.interceptors.request.use(config => {
  const auth = useAuthStore()
  if (auth.token) config.headers.Authorization = `Bearer ${auth.token}`
  return config
})

http.interceptors.response.use(
  res => {
    const json = res.data
    if (json && json.code === 200) return json.data
    return Promise.reject(new Error(json?.msg || '请求失败'))
  },
  err => {
    if (err.response?.status === 401 || err.response?.status === 403) {
      const auth = useAuthStore()
      auth.logout()
      router.push('/login')
    }
    return Promise.reject(err)
  }
)

export default http
