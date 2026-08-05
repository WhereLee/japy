<template>
  <div class="home">
    <header class="topbar">
      <div class="logo">📚 Japy 阅读</div>
      <div class="right">
        <el-input v-model="keyword" placeholder="搜索书名/作者" clearable style="width: 220px" @keyup.enter="search" @clear="load" />
        <el-button type="primary" plain @click="search">搜索</el-button>
        <el-button text @click="onLogout">退出</el-button>
      </div>
    </header>

    <main class="content">
      <h2 class="section-title">书库</h2>
      <div v-loading="loading">
        <div class="book-grid">
          <div v-for="b in books" :key="b.id" class="book-card" @click="$router.push('/novel/' + b.id)">
            <div class="cover" :style="{ background: coverColor(b.title) }">
              <span class="cover-title">{{ b.title.slice(0, 4) }}</span>
            </div>
            <div class="book-info">
              <div class="book-title">{{ b.title }}</div>
              <div class="book-author">{{ b.author }}</div>
              <div class="book-meta">
                <el-tag size="small" type="info">{{ b.category }}</el-tag>
                <span class="chars">{{ (b.totalChars / 1000).toFixed(1) }}k 字</span>
              </div>
            </div>
          </div>
        </div>
        <el-empty v-if="!loading && !books.length" description="暂无书籍" />
      </div>
      <div class="pager" v-if="total > size">
        <el-pagination layout="total, prev, pager, next" :total="total" :page-size="size"
          :current-page="page" @current-change="(p: number) => { page = p; load() }" />
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

function coverColor(title: string) {
  const colors = ['#6366f1', '#0ea5e9', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6']
  let h = 0
  for (const c of title) h = (h * 31 + c.charCodeAt(0)) % colors.length
  return colors[h]
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

function onLogout() {
  userStore.logout()
  router.push('/login')
}

onMounted(load)
</script>

<style scoped>
.home { min-height: 100vh; background: #f8fafc; }
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 32px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.06);
}
.logo { font-size: 18px; font-weight: 700; color: #0f172a; }
.right { display: flex; align-items: center; gap: 10px; }
.content { max-width: 1080px; margin: 0 auto; padding: 24px 20px; }
.section-title { font-size: 18px; color: #0f172a; }
.book-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 18px;
  margin-top: 16px;
}
.book-card {
  background: #fff;
  border-radius: 12px;
  padding: 14px;
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.06);
}
.book-card:hover { transform: translateY(-3px); box-shadow: 0 8px 20px rgba(15, 23, 42, 0.12); }
.cover {
  height: 130px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 700;
}
.cover-title { font-size: 20px; letter-spacing: 2px; }
.book-info { margin-top: 10px; }
.book-title { font-size: 14px; font-weight: 600; color: #0f172a; }
.book-author { font-size: 12px; color: #64748b; margin: 3px 0 6px; }
.book-meta { display: flex; align-items: center; gap: 8px; }
.chars { font-size: 12px; color: #94a3b8; }
.pager { margin-top: 20px; display: flex; justify-content: center; }
</style>
