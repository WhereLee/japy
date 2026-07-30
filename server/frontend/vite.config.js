import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8081',
      '/auth': 'http://localhost:8081',
      '/actuator': 'http://localhost:8081',
      '/admin': {
        target: 'http://localhost:8081',
        // 只代理 API 请求（XHR/fetch），不代理页面导航（浏览器直接访问）
        bypass: (req) => {
          if (req.headers.accept && req.headers.accept.includes('text/html')) {
            return '/index.html'
          }
        }
      }
    }
  }
})
