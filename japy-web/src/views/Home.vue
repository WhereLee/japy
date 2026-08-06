<template>
  <div class="home">
    <!-- 顶栏：克制，书名 + 搜索 -->
    <header class="topbar">
      <div class="brand serif">Japy · 阅</div>
      <div class="search">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="11" cy="11" r="7" /><path d="m21 21-4.3-4.3" />
        </svg>
        <input v-model="keyword" placeholder="搜书名 / 作者" @keyup.enter="search" @input="debouncedSearch" />
      </div>
      <button class="logout" @click="onLogout">退出</button>
    </header>

    <main class="content">
      <!-- 书架标题 -->
      <div class="shelf-head">
        <h2 class="shelf-title serif">{{ keyword ? '搜索结果' : '书架' }}</h2>
        <span class="shelf-count">{{ total }} 本</span>
      </div>

      <!-- 书籍陈列：封面墙 -->
      <div v-loading="loading" class="book-wall">
        <div v-for="b in books" :key="b.id" class="book" @click="$router.push('/novel/' + b.id)">
          <div class="cover" :class="'tone-' + (b.id % 5)" :style="{ background: coverGradient(b) }">
            <span class="cover-title serif">{{ b.title }}</span>
            <span class="cover-author">{{ b.author }}</span>
          </div>
          <div class="book-meta">
            <div class="book-title serif">{{ b.title }}</div>
            <div class="book-sub">
              <span>{{ b.category }}</span>
              <span>·</span>
              <span>{{ (b.totalChars / 1000).toFixed(1) }}k 字</span>
            </div>
          </div>
        </div>
        <el-empty v-if="!loading && !books.length" description="书架上空空如也" />
      </div>

      <div class="pager" v-if="total > size">
        <el-pagination layout="prev, pager, next" :total="total" :page-size="size"
          :current-page="page" @current-change="(p: number) => { page = p; load() }" background />
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listNovels } from '@/api/novel'
import type { Novel } from '@/api/novel'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const books = ref<Novel[]>([])
const keyword = ref('')
const page = ref(1)
const size = ref(12)
const total = ref(0)
const loading = ref(false)
let timer: number | null = null

// 封面配色：暖色系五音（墨绿/朱砂/藏蓝/赭石/青灰），每本书稳定
const TONES = [
  ['#3d5a4c', '#2c4438'],   // 墨绿
  ['#b3472d', '#93351f'],   // 朱砂
  ['#3a4a6b', '#2b3850'],   // 藏蓝
  ['#8a6a3b', '#6f542e'],   // 赭石
  ['#5a6a72', '#46545c']    // 青灰
]
function coverGradient(b: Novel) {
  const [a, c] = TONES[b.id % TONES.length]
  return `linear-gradient(150deg, ${a} 0%, ${c} 100%)`
}

async function load() {
  loading.value = true
  try {
    const p = await listNovels({ page: page.value, size: size.value, keyword: keyword.value || undefined })
    books.value = p.list
    total.value = p.total
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}
function debouncedSearch() {
  if (timer) clearTimeout(timer)
  timer = window.setTimeout(search, 400)
}

function onLogout() {
  userStore.logout()
  router.push('/login')
}

onMounted(load)
</script>

<style scoped>
.home { min-height: 100vh; }
.topbar {
  display: flex;
  align-items: center;
  gap: 32px;
  padding: 18px 48px;
  border-bottom: 1px solid var(--line);
}
.brand { font-size: 20px; font-weight: 700; letter-spacing: 2px; color: var(--ink); }
.search {
  flex: 1;
  max-width: 360px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border: 1px solid var(--line);
  border-radius: 8px;
  color: var(--ink-faint);
  background: #fffdf7;
  transition: border-color .2s;
}
.search:focus-within { border-color: var(--cinnabar); }
.search input {
  border: none; outline: none; background: transparent;
  font-size: 14px; color: var(--ink); width: 100%;
}
.search input::placeholder { color: var(--ink-faint); }
.logout {
  margin-left: auto;
  border: none; background: none;
  color: var(--ink-faint); font-size: 13px; cursor: pointer;
  padding: 6px 10px; border-radius: 6px;
}
.logout:hover { color: var(--cinnabar); }

.content { max-width: 1200px; margin: 0 auto; padding: 40px 48px 64px; }
.shelf-head {
  display: flex; align-items: baseline; gap: 12px;
  margin-bottom: 28px;
}
.shelf-title { font-size: 26px; font-weight: 600; margin: 0; }
.shelf-count { color: var(--ink-faint); font-size: 13px; }

.book-wall {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 28px 24px;
  min-height: 200px;
}
.book { cursor: pointer; }
.cover {
  aspect-ratio: 3 / 4;
  border-radius: 4px 10px 10px 4px;   /* 书脊感：左直角右圆角 */
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 20px 14px;
  color: #f5efe2;
  box-shadow: 0 4px 14px rgba(45, 42, 38, .18), inset -6px 0 12px rgba(0,0,0,.12);
  transition: transform .18s ease, box-shadow .18s ease;
  position: relative;
}
.cover::before {           /* 书脊阴影线 */
  content: '';
  position: absolute; left: 6px; top: 0; bottom: 0;
  width: 1px; background: rgba(255,255,255,.25);
}
.book:hover .cover {
  transform: translateY(-4px) rotate(-.5deg);
  box-shadow: 0 10px 24px rgba(45, 42, 38, .28), inset -6px 0 12px rgba(0,0,0,.12);
}
.cover-title {
  font-size: 19px; font-weight: 600; line-height: 1.5;
  text-align: center; letter-spacing: 3px;
  display: -webkit-box; -webkit-line-clamp: 4; -webkit-box-orient: vertical; overflow: hidden;
}
.cover-author { margin-top: 10px; font-size: 11px; opacity: .75; letter-spacing: 1px; }
.book-meta { margin-top: 10px; }
.book-title { font-size: 15px; font-weight: 600; color: var(--ink); }
.book-sub { margin-top: 3px; font-size: 12px; color: var(--ink-faint); display: flex; gap: 6px; }

.pager { margin-top: 40px; display: flex; justify-content: center; }
.pager :deep(.el-pagination.is-background .el-pager li) {
  background: transparent; border: 1px solid var(--line); color: var(--ink-soft);
}
.pager :deep(.el-pagination.is-background .el-pager li.is-active) {
  background: var(--ink); border-color: var(--ink); color: var(--paper);
}
</style>
