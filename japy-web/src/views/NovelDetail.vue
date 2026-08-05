<template>
  <div class="detail" v-loading="loading">
    <header class="topbar">
      <el-button text @click="$router.push('/')">← 书库</el-button>
      <span class="topbar-title">{{ novel?.title }}</span>
    </header>

    <div v-if="novel" class="content">
      <!-- 头部信息 -->
      <div class="hero">
        <div class="cover" :style="{ background: '#6366f1' }">{{ novel.title.slice(0, 4) }}</div>
        <div class="hero-info">
          <h1>{{ novel.title }}</h1>
          <div class="meta-line">
            <el-tag size="small" type="info">{{ novel.category }}</el-tag>
            <el-tag size="small" :type="novel.status === 0 ? 'success' : 'warning'">{{ novel.status === 0 ? '连载' : '完结' }}</el-tag>
            <span class="meta-text">{{ novel.author }}</span>
            <span class="meta-text">{{ novel.chapterCount }} 章 · {{ (novel.totalChars / 1000).toFixed(1) }}k 字</span>
          </div>
          <p class="intro">{{ novel.intro }}</p>
          <div class="actions">
            <el-button type="primary" size="large" @click="continueRead">📖 {{ progress ? '继续阅读' : '开始阅读' }}</el-button>
          </div>
        </div>
      </div>

      <!-- 目录 -->
      <h2 class="section-title">目录（{{ chapterTotal }} 章）</h2>
      <div class="chapter-list" v-loading="chapterLoading">
        <div v-for="c in chapters" :key="c.id"
             class="chapter-item"
             :class="{ current: c.id === progress?.chapterId }"
             @click="read(c.id)">
          <span class="ch-no">{{ c.chapterNo }}</span>
          <span class="ch-title">{{ c.title }}</span>
          <span class="ch-chars">{{ (c.chars / 100).toFixed(1) }}百字</span>
        </div>
        <div class="pager" v-if="chapterTotal > chapterSize">
          <el-pagination layout="prev, pager, next" :total="chapterTotal" :page-size="chapterSize"
            :current-page="chapterPage" @current-change="(p: number) => { chapterPage = p; loadChapters() }" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { novelDetail, listChapters, getProgress } from '@/api/novel'
import type { Novel, Chapter } from '@/api/novel'

const route = useRoute()
const router = useRouter()
const novelId = Number(route.params.id)

const novel = ref<Novel | null>(null)
const chapters = ref<Chapter[]>([])
const chapterTotal = ref(0)
const chapterPage = ref(1)
const chapterSize = ref(30)
const progress = ref<any>(null)
const loading = ref(false)
const chapterLoading = ref(false)

async function loadChapters() {
  chapterLoading.value = true
  try {
    const p = await listChapters(novelId, { page: chapterPage.value, size: chapterSize.value })
    chapters.value = p.list
    chapterTotal.value = p.total
  } finally {
    chapterLoading.value = false
  }
}

function continueRead() {
  if (progress.value) {
    read(progress.value.chapterId)
  } else if (chapters.value.length) {
    read(chapters.value[0].id)
  }
}

function read(chapterId: number) {
  router.push(`/reader/${chapterId}?novel=${novelId}`)
}

onMounted(async () => {
  loading.value = true
  try {
    novel.value = await novelDetail(novelId)
    await loadChapters()
    try {
      progress.value = await getProgress(novelId)
    } catch { /* 无进度 */ }
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.detail { min-height: 100vh; background: #f8fafc; }
.topbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 24px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.06);
  position: sticky;
  top: 0;
  z-index: 10;
}
.topbar-title { font-weight: 600; color: #0f172a; }
.content { max-width: 860px; margin: 0 auto; padding: 24px 20px 48px; }
.hero { display: flex; gap: 24px; background: #fff; border-radius: 14px; padding: 24px; box-shadow: 0 1px 3px rgba(15,23,42,0.06); }
.cover {
  width: 120px;
  height: 160px;
  border-radius: 10px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 2px;
}
.hero-info h1 { margin: 0 0 10px; font-size: 24px; color: #0f172a; }
.meta-line { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.meta-text { font-size: 13px; color: #64748b; }
.intro { font-size: 14px; color: #475569; line-height: 1.8; margin: 0 0 18px; }
.section-title { font-size: 18px; margin: 28px 0 12px; color: #0f172a; }
.chapter-list { background: #fff; border-radius: 12px; padding: 8px 16px; box-shadow: 0 1px 3px rgba(15,23,42,0.06); min-height: 80px; }
.chapter-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 11px 8px;
  border-bottom: 1px solid #f1f5f9;
  cursor: pointer;
  transition: background 0.12s;
}
.chapter-item:hover { background: #f8fafc; }
.chapter-item.current { background: #eef2ff; }
.chapter-item.current .ch-title { color: #4f46e5; font-weight: 600; }
.ch-no { width: 28px; height: 28px; border-radius: 6px; background: #f1f5f9; color: #64748b; font-size: 12px; display: flex; align-items: center; justify-content: center; }
.ch-title { flex: 1; font-size: 14px; color: #334155; }
.ch-chars { font-size: 12px; color: #94a3b8; }
.pager { display: flex; justify-content: center; padding: 12px 0; }
</style>
