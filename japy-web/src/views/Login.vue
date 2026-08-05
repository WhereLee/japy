<template>
  <div class="login-page">
    <div class="login-card">
      <h1>Japy 阅读</h1>
      <p class="sub">登录后开始阅读</p>
      <el-form :model="form" size="large" @keyup.enter="onLogin">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" show-password />
        </el-form-item>
        <el-button type="primary" class="btn" :loading="loading" @click="onLogin">登录</el-button>
      </el-form>
      <p class="hint">演示账号：demo / 123456</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

async function onLogin() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const data = await login(form)
    userStore.setToken(data.accessToken, data.refreshToken)
    ElMessage.success('登录成功')
    router.push((route.query.redirect as string) || '/')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1e293b, #0f172a);
}
.login-card {
  width: 360px;
  background: #fff;
  border-radius: 14px;
  padding: 36px 32px 24px;
  text-align: center;
}
h1 { margin: 0 0 6px; font-size: 22px; color: #0f172a; }
.sub { margin: 0 0 24px; font-size: 13px; color: #94a3b8; }
.btn { width: 100%; }
.hint { margin-top: 16px; font-size: 12px; color: #94a3b8; }
</style>
