<template>
  <div>
    <h2 class="page-title">小说馆</h2>
    <p class="page-sub">已入库的小说 · 讨论大厅建设中，敬请期待</p>

    <div v-if="loading" class="grid">
      <div v-for="i in 4" :key="i" class="card sk-book">
        <div class="skeleton sk-cover"></div>
        <div class="skeleton sk-line w80" style="margin-top:10px;"></div>
        <div class="skeleton sk-line w50" style="margin-top:6px;"></div>
      </div>
    </div>

    <div v-else-if="list.length" class="grid">
      <router-link v-for="n in list" :key="n.id" to="/novels" class="card book">
        <div class="cover" :style="{ background: coverBg(n.id) }">
          <span>{{ n.title.charAt(0) }}</span>
        </div>
        <div class="book-info">
          <h3 class="book-title">{{ n.title }}</h3>
          <p class="book-author">{{ n.author || '佚名' }}</p>
          <p class="book-meta">{{ fmtNum(n.chapterCount) }} 章 · {{ fmtNum(n.paragraphCount) }} 段 · {{ fmtNum(n.totalChars) }} 字</p>
        </div>
      </router-link>
    </div>

    <div v-else class="empty"><div class="icon">📚</div><p>暂无小说入库</p></div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import http from '../api'
import { fmtNum } from '../utils/format'

const list = ref([])
const loading = ref(false)

const palette = ['#b8860b', '#6b4f3a', '#4a6b52', '#7a4a5a', '#3d5a80', '#8a6d3b']
function coverBg(id) { return `linear-gradient(135deg, ${palette[id % palette.length]}, ${palette[(id + 2) % palette.length]})` }

onMounted(async () => {
  loading.value = true
  try {
    const data = await http.get('/api/novels?page=1&size=50')
    list.value = data.list
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.page-title { font-family: var(--serif); font-size: 22px; color: var(--ink); letter-spacing: 2px; }
.page-sub { font-size: 13px; color: var(--text-2); margin: 6px 0 18px; }
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 14px;
}
.book { padding: 18px; display: flex; gap: 14px; transition: all .15s; }
.book:hover { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(60,50,30,.1); border-color: var(--accent); }
.cover {
  width: 56px; height: 76px;
  border-radius: 6px;
  color: rgba(255,255,255,.92);
  display: flex; align-items: center; justify-content: center;
  font-size: 24px; font-family: var(--serif);
  flex-shrink: 0;
  box-shadow: 0 2px 8px rgba(0,0,0,.15);
}
.book-info { min-width: 0; }
.book-title { font-size: 15px; font-family: var(--serif); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.book-author { font-size: 12px; color: var(--text-2); margin-top: 4px; }
.book-meta { font-size: 12px; color: var(--text-3); margin-top: 8px; }
.sk-book { padding: 18px; }
.sk-cover { width: 56px; height: 76px; border-radius: 6px; }
</style>
