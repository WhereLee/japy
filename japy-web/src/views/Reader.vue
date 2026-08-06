<template>
  <div class="reader" :class="'theme-' + theme">
    <!-- 顶栏 -->
    <header class="bar top" :class="{ show: controlsVisible }">
      <el-button text @click="$router.push('/novel/' + novelId)">‹ 书库</el-button>
      <span class="bar-title">{{ chapter?.title }}</span>
      <span class="bar-spacer" />
      <el-button text @click="controlsVisible = !controlsVisible">设置</el-button>
    </header>

    <!-- 正文 -->
    <main ref="contentRef" class="content" @click="toggleControls" @scroll="onScroll">
      <h1 class="ch-title">{{ chapter?.title }}</h1>
      <p v-for="(para, i) in paragraphs" :key="i" class="para" :style="{ fontSize: fontSize + 'px', lineHeight: lineHeight }">
        {{ para }}
      </p>
      <div v-if="loading" class="loading">加载中…</div>
      <div class="chapter-end" v-if="chapter && !loading">
        <el-button v-if="chapter.prevChapterId" text @click.stop="goto(chapter.prevChapterId)">上一章</el-button>
        <span class="end-tip">— 本章完 —</span>
        <el-button v-if="chapter.nextChapterId" text type="primary" @click.stop="goto(chapter.nextChapterId)">下一章</el-button>
      </div>
    </main>

    <!-- 底栏（进度 + 设置） -->
    <footer class="bar bottom" :class="{ show: controlsVisible }">
      <span class="percent">{{ percentText }}</span>
      <el-slider v-model="fontSize" :min="14" :max="24" class="font-slider" @input="onFontChange" />
      <div class="theme-btns">
        <button class="theme-dot white" :class="{ active: theme === 'white' }" @click="theme = 'white'" />
        <button class="theme-dot sepia" :class="{ active: theme === 'sepia' }" @click="theme = 'sepia'" />
        <button class="theme-dot dark" :class="{ active: theme === 'dark' }" @click="theme = 'dark'" />
      </div>
    </footer>

    <!-- 目录抽屉 -->
    <el-drawer v-model="showCatalog" :title="'目录'" size="300px" direction="ltr">
      <div class="catalog-list">
        <div v-for="c in catalog" :key="c.id" class="catalog-item" :class="{ active: c.id === currentId }" @click="goto(c.id)">
          <span class="c-no">{{ c.chapterNo }}</span>
          <span>{{ c.title }}</span>
        </div>
      </div>
    </el-drawer>

    <!-- 悬浮目录按钮 -->
    <button class="fab" @click="showCatalog = true" v-show="!controlsVisible">目</button>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { chapterContent, saveProgress, getProgress, listChapters } from '@/api/novel'
import type { ChapterContent } from '@/api/novel'

const route = useRoute()
const router = useRouter()
const chapterId = Number(route.params.chapterId)
const novelId = Number(route.query.novel || 1)

const chapter = ref<ChapterContent | null>(null)
const paragraphs = ref<string[]>([])
const loading = ref(false)
const controlsVisible = ref(true)
const showCatalog = ref(false)
const currentId = ref(chapterId)
const catalog = ref<any[]>([])

// 阅读设置（localStorage 持久化）
const fontSize = ref(Number(localStorage.getItem('reader_font') || 18))
const lineHeight = ref(Number(localStorage.getItem('reader_lh') || 1.9))
const theme = ref(localStorage.getItem('reader_theme') || 'white')
const percent = ref(0)

const percentText = computed(() => `${Math.round(percent.value * 100)}%`)
const contentRef = ref<HTMLElement | null>(null)

let scrollTimer: number | null = null
let saveTimer: number | null = null

// ---- 章节加载 ----
async function loadChapter(id: number) {
  loading.value = true
  controlsVisible.value = true
  try {
    const data = await chapterContent(id)
    chapter.value = data
    paragraphs.value = data.paragraphs
    currentId.value = data.id
    document.title = data.title + ' - Japy 阅读'
    restoreScroll()
  } finally {
    loading.value = false
  }
}

// ---- 进度 ----
async function loadProgress() {
  try {
    const p = await getProgress(novelId)
    if (p && p.chapterId === currentId.value) {
      percent.value = p.percent / 100
    }
  } catch { /* 无进度 */ }
}

function onScroll() {
  const el = contentRef.value
  if (!el) return
  const max = el.scrollHeight - el.clientHeight
  percent.value = max > 0 ? el.scrollTop / max : 0
  if (scrollTimer) clearTimeout(scrollTimer)
  scrollTimer = window.setTimeout(() => flushProgress(), 1500)
}

/** 滚动停止后防抖保存（字符偏移 ≈ percent × chars） */
async function flushProgress() {
  if (!chapter.value) return
  const chars = chapter.value.chars || 0
  const offset = Math.round(percent.value * chars)
  saveTimer && clearTimeout(saveTimer)
  saveTimer = window.setTimeout(async () => {
    try {
      await saveProgress(novelId, { chapterId: chapter.value!.id, charOffset: offset, percent: Math.round(percent.value * 100) })
    } catch { /* 静默 */ }
  }, 500)
}

function restoreScroll() {
  // 等待渲染后按百分比恢复位置
  requestAnimationFrame(() => {
    const el = contentRef.value
    if (!el) return
    const max = el.scrollHeight - el.clientHeight
    el.scrollTop = max * percent.value
  })
}

function goto(id: number) {
  showCatalog.value = false
  // 同步 URL（刷新/分享回到正确章节）
  router.replace(`/reader/${id}?novel=${novelId}`)
  loadChapter(id)
  // 切章后滚回顶部
  percent.value = 0
}

function toggleControls() {
  controlsVisible.value = !controlsVisible.value
}

function onFontChange(v: number) {
  lineHeight.value = v >= 20 ? 1.8 : 1.9
  localStorage.setItem('reader_font', String(v))
  localStorage.setItem('reader_lh', String(lineHeight.value))
}

watch(theme, (v) => localStorage.setItem('reader_theme', v))

// 离开页面强制保存
async function beforeUnload() {
  await flushProgress()
}
onMounted(async () => {
  await loadChapter(chapterId)
  await loadProgress()
  try {
    const p = await listChapters(novelId, { page: 1, size: 500 })
    catalog.value = p.list
  } catch { /* 忽略 */ }
  window.addEventListener('beforeunload', beforeUnload)
  // 恢复滚动（进度百分比在 loadProgress 后生效）
  setTimeout(restoreScroll, 300)
})
onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', beforeUnload)
  flushProgress()
})
</script>

<style scoped>
.reader { height: 100vh; display: flex; flex-direction: column; overflow: hidden; position: relative; }
.theme-white { background: #fffdf7; color: var(--ink); }
.theme-sepia { background: var(--paper); color: var(--ink); }
.theme-dark { background: #1f1d1a; color: #bfb8ad; }

.bar {
  position: absolute;
  left: 0; right: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  z-index: 20;
  transition: transform 0.25s;
  background: rgba(255,253,247,0.95);
  backdrop-filter: blur(6px);
}
.theme-dark .bar { background: rgba(30,30,30,0.95); }
.top { top: 0; transform: translateY(-100%); }
.top.show { transform: translateY(0); }
.bottom { bottom: 0; transform: translateY(100%); padding: 6px 16px 10px; }
.bottom.show { transform: translateY(0); }
.bar-title { font-weight: 600; font-size: 15px; }
.bar-spacer { flex: 1; }
.percent { font-size: 12px; color: #94a3b8; min-width: 44px; }
.font-slider { width: 200px; margin: 0 12px; }
.theme-btns { display: flex; gap: 8px; margin-left: auto; }
.theme-dot {
  width: 22px; height: 22px; border-radius: 50%;
  border: 2px solid transparent; cursor: pointer;
}
.theme-dot.white { background: #fffdf7; border-color: var(--line); }
.theme-dot.sepia { background: var(--paper); border-color: var(--line); }
.theme-dot.dark { background: #1a1a1a; border-color: #444; }
.theme-dot.active { border-color: var(--cinnabar); box-shadow: 0 0 0 2px rgba(79,70,229,0.3); }

.content {
  flex: 1;
  overflow-y: auto;
  padding: 64px 24px 72px;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: thin;
}
.ch-title { font-size: 26px; font-weight: 600; font-family: var(--serif); text-align: center; margin: 0 0 30px; letter-spacing: 2px; }
.para {
  margin: 0 0 18px;
  text-indent: 2em;
  text-align: justify;
  letter-spacing: 0.02em;
}
.loading { text-align: center; padding: 40px 0; color: #94a3b8; }
.chapter-end {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20px;
  padding: 30px 0 10px;
}
.end-tip { font-size: 13px; color: #94a3b8; }

.fab {
  font-family: var(--serif);
  position: fixed;
  right: 18px;
  bottom: 80px;
  width: 42px; height: 42px;
  border-radius: 50%;
  border: none;
  background: rgba(179,71,45,0.92);
  color: #fff;
  font-size: 18px;
  cursor: pointer;
  z-index: 15;
  box-shadow: 0 4px 12px rgba(179,71,45,0.35);
}
.catalog-list { display: flex; flex-direction: column; }
.catalog-item {
  padding: 10px 12px;
  border-bottom: 1px solid #f1f5f9;
  cursor: pointer;
  font-size: 14px;
  color: #334155;
  display: flex;
  gap: 10px;
}
.catalog-item:hover { background: #f8fafc; }
.catalog-item.active { color: #4f46e5; font-weight: 600; }
.c-no { color: #94a3b8; font-size: 12px; min-width: 22px; }
</style>
