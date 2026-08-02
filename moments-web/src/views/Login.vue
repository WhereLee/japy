<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <span class="brand-mark">拼</span>
        <h1 class="brand-name">拼象</h1>
        <p class="brand-slogan">一群把书读了很多遍的人</p>
      </div>

      <div class="tabs">
        <button :class="{ active: mode === 'login' }" @click="mode = 'login'">登录</button>
        <button :class="{ active: mode === 'register' }" @click="mode = 'register'">注册</button>
      </div>

      <form @submit.prevent="submit">
        <input v-model="form.username" class="input" placeholder="用户名" maxlength="50" autocomplete="username" />
        <input v-if="mode === 'register'" v-model="form.nickname" class="input" placeholder="昵称（展示给别人看）" maxlength="50" />
        <input v-model="form.password" type="password" class="input" placeholder="密码（至少 6 位）" autocomplete="current-password" />
        <button class="btn primary submit" :disabled="loading" type="submit">
          {{ loading ? '请稍候…' : mode === 'login' ? '登 录' : '注 册' }}
        </button>
      </form>

      <p v-if="err" class="err">{{ err }}</p>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import http from '../api'
import { useAuthStore } from '../stores/auth'
import { toast } from '../utils/format'

const mode = ref('login')
const loading = ref(false)
const err = ref('')
const form = reactive({ username: '', nickname: '', password: '' })
const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

async function submit() {
  err.value = ''
  if (!form.username.trim() || !form.password) { err.value = '请输入用户名和密码'; return }
  if (mode.value === 'register' && !form.nickname.trim()) { err.value = '请输入昵称'; return }
  loading.value = true
  try {
    const data = mode.value === 'login'
      ? await http.post('/auth/login', { username: form.username.trim(), password: form.password })
      : await http.post('/auth/register', {
          username: form.username.trim(), password: form.password, nickname: form.nickname.trim()
        })
    auth.setLogin(data.token, data.nickname, data.userId, data.role)
    toast(mode.value === 'login' ? '欢迎回来' : '注册成功，欢迎加入')
    router.push(route.query.redirect || '/')
  } catch (e) {
    err.value = e.response?.data?.msg || e.message || '操作失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100%;
  display: flex; align-items: center; justify-content: center;
  background:
    radial-gradient(ellipse at 20% 10%, rgba(184,134,11,.08), transparent 50%),
    radial-gradient(ellipse at 80% 90%, rgba(184,134,11,.06), transparent 50%),
    var(--bg);
  padding: 20px;
}
.login-card {
  width: 380px; max-width: 100%;
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: 16px;
  box-shadow: 0 12px 40px rgba(60,50,30,.08);
  padding: 40px 36px 32px;
}
.brand { text-align: center; margin-bottom: 26px; }
.brand-mark {
  width: 54px; height: 54px; border-radius: 14px;
  background: var(--ink);
  color: var(--accent);
  font-family: var(--serif);
  font-size: 26px; font-weight: 700;
  display: inline-flex; align-items: center; justify-content: center;
  margin-bottom: 14px;
}
.brand-name { font-family: var(--serif); font-size: 24px; color: var(--ink); letter-spacing: 6px; }
.brand-slogan { font-size: 13px; color: var(--text-2); margin-top: 8px; letter-spacing: 1px; }
.tabs {
  display: flex;
  border-bottom: 1px solid var(--line);
  margin-bottom: 22px;
}
.tabs button {
  flex: 1;
  border: none; background: none;
  padding: 10px 0;
  font-size: 14px;
  color: var(--text-2);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  transition: all .15s;
}
.tabs button.active { color: var(--accent); border-bottom-color: var(--accent); font-weight: 600; }
form .input { margin-bottom: 14px; }
.submit { width: 100%; margin-top: 6px; padding: 10px 0; font-size: 15px; letter-spacing: 4px; }
.err { color: var(--danger); font-size: 13px; margin-top: 12px; text-align: center; }
</style>
