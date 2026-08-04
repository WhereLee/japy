import { defineStore } from 'pinia'
import { login as apiLogin, logout as apiLogout, getInfo } from '@/api/auth'
import type { LoginResult, UserInfo } from '@/api/auth'
import { setTokens, clearToken, getToken } from '@/utils/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken() || '',
    user: null as UserInfo | null,
    roles: [] as string[],
    permissions: [] as string[]
  }),
  getters: {
    isAdmin: (s) => s.roles.includes('admin'),
    nickname: (s) => s.user?.nickname || ''
  },
  actions: {
    async login(form: { username: string; password: string }) {
      const data: LoginResult = await apiLogin(form)
      setTokens(data.accessToken, data.refreshToken)
      this.token = data.accessToken
      this.roles = data.roles
    },
    async fetchInfo() {
      const info = await getInfo()
      this.user = info.user
      this.roles = info.roles
      this.permissions = info.permissions
      return info
    },
    hasPerm(perm: string): boolean {
      if (!perm) return true
      return this.permissions.includes('*:*:*') || this.permissions.includes(perm)
    },
    async logout() {
      try {
        await apiLogout()
      } finally {
        this.reset()
      }
    },
    reset() {
      this.token = ''
      this.user = null
      this.roles = []
      this.permissions = []
      clearToken()
    }
  }
})
