<template>
  <div>
    <!-- 时间线 -->
    <div v-if="loading && list.length === 0" class="feed-loading">
      <div v-for="i in 3" :key="i" class="card sk-card">
        <div class="sk-head"><div class="skeleton sk-avatar"></div><div><div class="skeleton sk-line w60"></div><div class="skeleton sk-line w30"></div></div></div>
        <div class="skeleton sk-line w100" style="margin-top:14px;"></div>
        <div class="skeleton sk-line w80" style="margin-top:8px;"></div>
      </div>
    </div>

    <template v-else>
      <MomentCard
        v-for="m in list" :key="m.id" :m="m"
        @deleted="id => (list = list.filter(x => x.id !== id))"
      />
      <div v-if="!list.length" class="empty">
        <div class="icon">📖</div>
        <p>还没有动态，来发布第一条吧</p>
      </div>
      <div v-if="list.length && !finished" ref="sentinel" class="sentinel">
        <span v-if="loadingMore" class="sentinel-text">加载中…</span>
      </div>
      <div v-if="finished && list.length" class="sentinel-text end">— 已经到底啦 —</div>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import http from '../api'
import { useAuthStore } from '../stores/auth'
import { publishTick } from '../stores/publish'
import MomentCard from '../components/MomentCard.vue'

const auth = useAuthStore()
const list = ref([])
const loading = ref(false)
const loadingMore = ref(false)
const finished = ref(false)
const cursor = ref('')
const sentinel = ref(null)
let observer = null

async function loadFirst() {
  loading.value = true
  try {
    const data = await http.get('/api/moments?page=1&size=10')
    list.value = data.list
    cursor.value = lastCursor(data.list)
    finished.value = !data.list.length || data.list.length < 10
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (loadingMore.value || finished.value || !cursor.value) return
  loadingMore.value = true
  try {
    const data = await http.get(`/api/moments?cursor=${encodeURIComponent(cursor.value)}&size=10`)
    if (data.list.length === 0) { finished.value = true; return }
    list.value = list.value.concat(data.list)
    cursor.value = lastCursor(data.list)
    if (data.list.length < 10) finished.value = true
  } finally {
    loadingMore.value = false
  }
}

/** 游标 = 最后一条的 createdAt_id */
function lastCursor(items) {
  if (!items.length) return ''
  const last = items[items.length - 1]
  return `${last.createdAt}_${last.id}`
}

onMounted(() => {
  loadFirst()
  observer = new IntersectionObserver(entries => {
    if (entries[0].isIntersecting) loadMore()
  }, { rootMargin: '200px' })
})

// sentinel 在首屏加载后才渲染，需等它出现再绑定观察
watch(sentinel, el => {
  if (el && observer) observer.observe(el)
})

onUnmounted(() => observer?.disconnect())

// 发布成功后刷新
watch(publishTick, () => loadFirst())
</script>

<style scoped>
.feed-loading .sk-card { margin-bottom: 14px; padding: 16px; }
.sk-head { display: flex; gap: 12px; align-items: center; }
.sk-avatar { width: 38px; height: 38px; border-radius: 50%; }
.sk-line { height: 12px; margin-top: 4px; }
.w100 { width: 100%; } .w80 { width: 80%; } .w60 { width: 60%; } .w30 { width: 30%; }
.sentinel { height: 20px; }
.sentinel-text { text-align: center; color: var(--text-3); font-size: 12px; padding: 16px 0; display: block; }
.end { padding: 24px 0 40px; }
</style>
