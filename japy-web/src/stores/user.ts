import { defineStore } from 'pinia'
import { getToken, setTokens, clearToken } from '@/utils/auth'

export const useUserStore = defineStore('user', {
  state: () => ({ token: getToken() || '' }),
  actions: {
    setToken(access: string, refresh: string) {
      setTokens(access, refresh)
      this.token = access
    },
    logout() {
      this.token = ''
      clearToken()
    }
  }
})
