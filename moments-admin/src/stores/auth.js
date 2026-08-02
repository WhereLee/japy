import { defineStore } from 'pinia'

const KEY = 'japy_admin_token'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(KEY) || '',
    nickname: localStorage.getItem('japy_admin_nickname') || '',
    userId: Number(localStorage.getItem('japy_admin_uid') || 0)
  }),
  actions: {
    setLogin(token, nickname, userId) {
      this.token = token
      this.nickname = nickname
      this.userId = Number(userId || 0)
      localStorage.setItem(KEY, token)
      localStorage.setItem('japy_admin_nickname', nickname || '')
      localStorage.setItem('japy_admin_uid', String(this.userId))
    },
    logout() {
      this.token = ''
      this.nickname = ''
      this.userId = 0
      localStorage.removeItem(KEY)
      localStorage.removeItem('japy_admin_nickname')
      localStorage.removeItem('japy_admin_uid')
    }
  }
})
