<template>
  <div class="login-page">
    <div class="login-card">
      <h2>{{ isRegister ? '注册' : '登录' }}</h2>
      <form @submit.prevent="handleSubmit">
        <input v-model="form.username" placeholder="用户名" required />
        <input v-if="isRegister" v-model="form.nickname" placeholder="昵称" required />
        <input v-model="form.password" type="password" placeholder="密码" required />
        <p v-if="error" class="error">{{ error }}</p>
        <button type="submit">{{ isRegister ? '注册' : '登录' }}</button>
      </form>
      <p class="toggle" @click="isRegister = !isRegister">
        {{ isRegister ? '已有账号？去登录' : '没有账号？去注册' }}
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { login, register } from '../api/index.js'

const router = useRouter()
const isRegister = ref(false)
const error = ref('')
const form = reactive({ username: '', nickname: '', password: '' })

async function handleSubmit() {
  error.value = ''
  try {
    const res = isRegister.value
      ? await register({ username: form.username, nickname: form.nickname, password: form.password })
      : await login({ username: form.username, password: form.password })

    // 存储 Token 和用户信息
    localStorage.setItem('accessToken', res.data.accessToken)
    localStorage.setItem('refreshToken', res.data.refreshToken)
    localStorage.setItem('userInfo', JSON.stringify(res.data))

    // 根据角色跳转
    if (res.data.role === 'admin') {
      router.push('/admin/dashboard')
    } else {
      router.push('/')
    }
  } catch (e) {
    error.value = e.message || '操作失败'
  }
}
</script>

<style scoped>
.login-page { display: flex; justify-content: center; align-items: center; min-height: 100vh; background: #f0f2f5; }
.login-card { background: #fff; padding: 40px; border-radius: 8px; box-shadow: 0 2px 12px rgba(0,0,0,0.1); width: 360px; }
h2 { text-align: center; margin-bottom: 24px; }
input { display: block; width: 100%; padding: 10px; margin-bottom: 12px; border: 1px solid #ddd; border-radius: 4px; box-sizing: border-box; }
button { width: 100%; padding: 10px; background: #409eff; color: #fff; border: none; border-radius: 4px; cursor: pointer; font-size: 16px; }
button:hover { background: #66b1ff; }
.error { color: #e74c3c; font-size: 14px; margin-bottom: 8px; }
.toggle { text-align: center; color: #409eff; cursor: pointer; margin-top: 12px; font-size: 14px; }
</style>
