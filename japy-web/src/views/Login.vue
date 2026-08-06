<template>
  <div class="login-page">
    <div class="login-card">
      <div class="seal">阅</div>
      <h1 class="serif">Japy · 阅</h1>
      <p class="sub">登录后开始阅读</p>
      <el-form :model="form" size="large" @keyup.enter="onLogin">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" show-password />
        </el-form-item>
        <el-button type="primary" class="btn" :loading="loading" @click="onLogin">登 录</el-button>
      </el-form>
      <p class="hint">演示账号：admin / admin123</p>
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
  background:
    radial-gradient(ellipse at 30% 20%, rgba(179, 71, 45, .06), transparent 60%),
    radial-gradient(ellipse at 70% 80%, rgba(61, 90, 76, .08), transparent 60%),
    var(--paper);
}
.login-card {
  width: 360px;
  background: #fffdf7;
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 40px 36px 28px;
  text-align: center;
  box-shadow: 0 12px 40px rgba(45, 42, 38, .08);
}
.seal {
  width: 52px; height: 52px;
  margin: 0 auto 14px;
  background: var(--cinnabar);
  color: #f5efe2;
  border-radius: 8px;
  font-size: 26px;
  font-family: var(--serif);
  display: flex; align-items: center; justify-content: center;
  box-shadow: inset 0 0 0 2px rgba(245,239,226,.35);
}
h1 { margin: 0 0 6px; font-size: 22px; font-weight: 700; letter-spacing: 3px; }
.sub { margin: 0 0 26px; font-size: 13px; color: var(--ink-faint); }
.btn {
  width: 100%;
  background: var(--ink); border-color: var(--ink);
}
.btn:hover { background: var(--cinnabar); border-color: var(--cinnabar); }
.hint { margin-top: 18px; font-size: 12px; color: var(--ink-faint); }
</style>
