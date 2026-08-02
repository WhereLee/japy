<template>
  <div>
    <!-- 我的资料卡 -->
    <div class="card me-card">
      <div class="m-row">
        <span class="avatar avatar-lg">{{ avatarChar(auth.nickname) }}</span>
        <div class="m-info">
          <h2 class="m-nick">{{ auth.nickname }}</h2>
          <p class="m-bio">{{ profile.bio || '这个人很神秘，什么也没写' }}</p>
          <p class="m-meta">加入于 {{ fullTime(profile.createdAt) }}</p>
        </div>
      </div>

      <div class="tabs">
        <button :class="{ active: tab === 'info' }" @click="tab = 'info'">编辑资料</button>
        <button :class="{ active: tab === 'pwd' }" @click="tab = 'pwd'">修改密码</button>
      </div>

      <!-- 编辑资料 -->
      <form v-if="tab === 'info'" class="form" @submit.prevent="saveInfo">
        <label class="f-label">昵称</label>
        <input v-model="form.nickname" class="input" maxlength="50" />
        <label class="f-label">简介</label>
        <textarea v-model="form.bio" class="input" maxlength="200" placeholder="介绍一下自己（最多 200 字）"></textarea>
        <button class="btn primary submit" :disabled="saving">保存</button>
      </form>

      <!-- 修改密码 -->
      <form v-else class="form" @submit.prevent="savePwd">
        <label class="f-label">旧密码</label>
        <input v-model="pwd.oldPassword" type="password" class="input" autocomplete="current-password" />
        <label class="f-label">新密码（至少 6 位）</label>
        <input v-model="pwd.newPassword" type="password" class="input" autocomplete="new-password" />
        <button class="btn primary submit" :disabled="saving">确认修改</button>
      </form>
    </div>

    <!-- 我的动态 -->
    <h3 class="sec-title">我的动态</h3>
    <MomentCard
      v-for="m in myMoments" :key="m.id" :m="m"
      @deleted="id => (myMoments = myMoments.filter(x => x.id !== id))"
    />
    <div v-if="!myMoments.length && !loading" class="empty"><div class="icon">📭</div><p>还没有发布过动态</p></div>
    <div class="pager">
      <button v-if="myTotal > myMoments.length" class="link-btn" @click="moreMoments">加载更多</button>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import http from '../api'
import { useAuthStore } from '../stores/auth'
import { avatarChar, fullTime, toast } from '../utils/format'
import MomentCard from '../components/MomentCard.vue'

const auth = useAuthStore()
const tab = ref('info')
const profile = reactive({ bio: '', createdAt: '' })
const form = reactive({ nickname: '', bio: '' })
const pwd = reactive({ oldPassword: '', newPassword: '' })
const saving = ref(false)

const myMoments = ref([])
const myTotal = ref(0)
const myPage = ref(1)
const loading = ref(false)

async function load() {
  const data = await http.get(`/api/users/${auth.userId}?page=1&size=10`)
  profile.bio = data.bio || ''
  profile.createdAt = data.createdAt
  form.nickname = data.nickname
  form.bio = data.bio || ''
  myMoments.value = data.moments.list || []
  myTotal.value = data.moments.total || 0
}
async function moreMoments() {
  myPage.value += 1
  const data = await http.get(`/api/users/${auth.userId}?page=${myPage.value}&size=10`)
  myMoments.value = myMoments.value.concat(data.moments.list || [])
  myTotal.value = data.moments.total || 0
}

async function saveInfo() {
  if (!form.nickname.trim()) { toast('昵称不能为空', 'error'); return }
  saving.value = true
  try {
    const data = await http.put('/api/users/me', { nickname: form.nickname.trim(), bio: form.bio.trim() })
    auth.setNickname(data.nickname)
    profile.bio = form.bio.trim()
    toast('资料已保存')
  } catch (e) {
    toast(e.response?.data?.msg || '保存失败', 'error')
  } finally {
    saving.value = false
  }
}

async function savePwd() {
  if (pwd.newPassword.length < 6) { toast('新密码至少 6 位', 'error'); return }
  saving.value = true
  try {
    await http.put('/api/users/me/password', { oldPassword: pwd.oldPassword, newPassword: pwd.newPassword })
    pwd.oldPassword = ''
    pwd.newPassword = ''
    toast('密码已修改')
  } catch (e) {
    toast(e.response?.data?.msg || '修改失败', 'error')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.me-card { padding: 22px; margin-bottom: 20px; }
.m-row { display: flex; gap: 18px; align-items: center; margin-bottom: 18px; }
.avatar-lg { width: 64px; height: 64px; font-size: 26px; }
.m-nick { font-size: 20px; font-family: var(--serif); }
.m-bio { font-size: 13px; color: var(--text-2); margin-top: 4px; }
.m-meta { font-size: 12px; color: var(--text-3); margin-top: 6px; }
.tabs {
  display: flex; gap: 4px;
  border-bottom: 1px solid var(--line);
  margin-bottom: 18px;
}
.tabs button {
  border: none; background: none;
  padding: 8px 16px;
  font-size: 13px; color: var(--text-2);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
}
.tabs button.active { color: var(--accent); border-bottom-color: var(--accent); font-weight: 600; }
.form { max-width: 420px; }
.f-label { display: block; font-size: 12px; color: var(--text-2); margin: 12px 0 6px; }
.submit { margin-top: 18px; padding: 8px 32px; }
.sec-title { font-size: 14px; color: var(--text-2); margin: 4px 2px 12px; }
.pager { text-align: center; padding: 10px 0; }
.link-btn { border: none; background: none; color: var(--accent); font-size: 13px; cursor: pointer; }
</style>
