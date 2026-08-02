<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <span class="brand-icon">拼</span>
        <h2>拼象管理后台</h2>
        <p>小说动态社区 · 运营管理</p>
      </div>
      <el-form :model="form" @submit.prevent="onLogin">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" size="large" :prefix-icon="User" />
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="form.password" type="password" placeholder="密码" size="large"
            show-password :prefix-icon="Lock" @keyup.enter="onLogin"
          />
        </el-form-item>
        <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="onLogin">
          登 录
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import http from '../api'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const form = reactive({ username: 'admin', password: '' })

async function onLogin() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const data = await http.post('/auth/login', form)
    auth.setLogin(data.token, data.nickname, data.userId)
    ElMessage.success('登录成功')
    router.push('/')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100%;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #1e3c72 0%, #2a5298 50%, #3b6ef6 100%);
}
.login-card {
  width: 380px; background: #fff; border-radius: 12px;
  padding: 40px 36px 32px;
  box-shadow: 0 16px 48px rgba(0,0,0,0.25);
}
.brand { text-align: center; margin-bottom: 28px; }
.brand-icon {
  width: 52px; height: 52px; border-radius: 12px;
  background: linear-gradient(135deg, #3b6ef6, #7c5cf0);
  color: #fff; font-size: 26px; font-weight: 700;
  display: inline-flex; align-items: center; justify-content: center;
  margin-bottom: 12px;
}
.brand h2 { font-size: 20px; color: #1f2329; }
.brand p { font-size: 13px; color: #8a919f; margin-top: 6px; }
.login-btn { width: 100%; font-size: 15px; }
</style>
