import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 3002,
    proxy: {
      '/api': 'http://localhost:8083',
      '/auth': 'http://localhost:8083'
    }
  }
})
