<template>
  <div v-if="user" class="profile">
    <!-- 资料卡 -->
    <div class="card profile-card">
      <div class="p-row">
        <span class="avatar avatar-lg">{{ avatarChar(user.nickname) }}</span>
        <div class="p-info">
          <h2 class="p-nick">{{ user.nickname }}</h2>
          <p class="p-bio">{{ user.bio || '这个人很神秘，什么也没写' }}</p>
          <p class="p-meta">加入于 {{ fullTime(user.createdAt) }}</p>
        </div>
      </div>
    </div>

    <!-- TA 的动态 -->
    <h3 class="sec-title">TA 的动态</h3>
    <MomentCard
      v-for="m in moments" :key="m.id" :m="m"
      @deleted="id => (moments = moments.filter(x => x.id !== id))"
    />
    <div v-if="!moments.length && !loading" class="empty">
      <div class="icon">📭</div><p>还没有动态</p>
    </div>
    <div class="pager">
      <button v-if="total > moments.length" class="link-btn" @click="loadMore">加载更多</button>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import http from '../api'
import { avatarChar, fullTime } from '../utils/format'
import MomentCard from '../components/MomentCard.vue'

const route = useRoute()
const user = ref(null)
const moments = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)

async function load() {
  const id = route.params.id
  loading.value = true
  try {
    const data = await http.get(`/api/users/${id}?page=1&size=10`)
    user.value = data
    moments.value = data.moments.list || []
    total.value = data.moments.total || 0
    page.value = 1
  } finally {
    loading.value = false
  }
}
async function loadMore() {
  page.value += 1
  const data = await http.get(`/api/users/${route.params.id}?page=${page.value}&size=10`)
  moments.value = moments.value.concat(data.moments.list || [])
  total.value = data.moments.total || 0
}

watch(() => route.params.id, () => { if (route.name === 'UserProfile') load() }, { immediate: true })
</script>

<style scoped>
.profile-card { padding: 22px; margin-bottom: 20px; }
.p-row { display: flex; gap: 18px; align-items: center; }
.avatar-lg { width: 68px; height: 68px; font-size: 28px; }
.p-nick { font-size: 20px; font-family: var(--serif); }
.p-bio { font-size: 13px; color: var(--text-2); margin-top: 6px; }
.p-meta { font-size: 12px; color: var(--text-3); margin-top: 8px; }
.sec-title { font-size: 14px; color: var(--text-2); margin: 4px 2px 12px; }
.pager { text-align: center; padding: 10px 0; }
.link-btn { border: none; background: none; color: var(--accent); font-size: 13px; cursor: pointer; }
</style>
