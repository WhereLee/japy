import { defineStore } from 'pinia'

const TOKEN_KEY = 'japy_user_token'
const INFO_KEY = 'japy_user_info'

export const useAuthStore = defineStore('auth', {
  state: () => {
    let info = null
    try { info = JSON.parse(localStorage.getItem(INFO_KEY) || 'null') } catch { info = null }
    return {
      token: localStorage.getItem(TOKEN_KEY) || '',
      userId: info?.userId || 0,
      nickname: info?.nickname || '',
      role: info?.role || 'user'
    }
  },
  getters: {
    isLogin: s => !!s.token
  },
  actions: {
    setLogin(token, nickname, userId, role) {
      this.token = token
      this.nickname = nickname
      this.userId = Number(userId || 0)
      this.role = role || 'user'
      localStorage.setItem(TOKEN_KEY, token)
      localStorage.setItem(INFO_KEY, JSON.stringify({ userId: this.userId, nickname: this.nickname, role: this.role }))
    },
    setNickname(nickname) {
      this.nickname = nickname
      localStorage.setItem(INFO_KEY, JSON.stringify({ userId: this.userId, nickname, role: this.role }))
    },
    logout() {
      this.token = ''
      this.userId = 0
      this.nickname = ''
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(INFO_KEY)
    }
  }
})
