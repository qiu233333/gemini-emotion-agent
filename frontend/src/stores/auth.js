import { defineStore } from 'pinia'
import { api } from '@/api'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('emotion_token') || '',
    user: JSON.parse(localStorage.getItem('emotion_user') || 'null'),
    systemInfo: null
  }),
  getters: {
    isAuthenticated: (state) => !!state.token,
    isAdmin: (state) => state.user?.role === 'ADMIN'
  },
  actions: {
    setSession(token, user) {
      this.token = token
      this.user = user
      localStorage.setItem('emotion_token', token)
      localStorage.setItem('emotion_user', JSON.stringify(user))
    },
    async refreshProfile() {
      if (!this.token) return
      const res = await api.getProfile()
      this.user = res.data
      localStorage.setItem('emotion_user', JSON.stringify(res.data))
    },
    async fetchSystemInfo() {
      try {
        const res = await api.systemInfo()
        this.systemInfo = res.data
      } catch (e) {
        // ignore
      }
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('emotion_token')
      localStorage.removeItem('emotion_user')
    }
  }
})
