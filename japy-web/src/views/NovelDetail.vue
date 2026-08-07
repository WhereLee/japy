<template>
  <div class="detail" v-loading="loading">
    <header class="topbar">
      <el-button text @click="$router.push('/')">← 书库</el-button>
      <span class="topbar-title">{{ novel?.title }}</span>
    </header>

    <div v-if="novel" class="content">
      <!-- 头部信息 -->
      <div class="hero">
        <div class="cover" :style="{ background: coverGradient() }">{{ novel.title.slice(0, 4) }}</div>
        <div class="hero-info">
          <h1>{{ novel.title }}</h1>
          <div class="meta-line">
            <el-tag size="small" type="info">{{ novel.category }}</el-tag>
            <span class="meta-text">{{ novel.author }}</span>
            <span class="meta-text">{{ novel.chapterCount }} 章 · {{ (novel.totalChars / 1000).toFixed(1) }}k 字</span>
          </div>
          <p class="intro">{{ novel.intro }}</p>
          <div class="actions">
            <el-button type="primary" size="large" @click="continueRead">{{ progress ? '继续阅读' : '开始阅读' }}</el-button>
            <el-button size="large" plain class="ask-btn" @click="askDialog = true">AI 问这本书</el-button>
          </div>
        </div>
      </div>

      <!-- AI 问答对话框 -->
      <el-dialog v-model="askDialog" :title="`AI 问这本书 - ${novel?.title}`" width="560px" :close-on-click-modal="false">
        <div v-loading="asking" class="ask-body">
          <el-input v-model="askQuestion" type="textarea" :rows="2" placeholder="例如：晨星号收到了什么信号？" @keyup.ctrl.enter="doAsk" />
          <div class="ask-actions">
            <el-button type="primary" :loading="asking" @click="doAsk">提问</el-button>
          </div>
          <template v-if="askAnswer">
            <div class="ask-answer">{{ askAnswer.answer }}</div>
            <div class="ask-sources" v-if="askAnswer.sources?.length">
              <div class="sources-title">引用片段（{{ askAnswer.sources.length }}）</div>
              <div v-for="(s, i) in askAnswer.sources.slice(0, 5)" :key="i" class="source-item">
                <span class="source-ch">第{{ s.chapter_no }}章</span>
                <span class="source-text">{{ s.content_preview }}</span>
              </div>
            </div>
          </template>
        </div>
        <template #footer>
          <el-button @click="askDialog = false">关闭</el-button>
        </template>
      </el-dialog>

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
import { ElMessage } from 'element-plus'
import { novelDetail, listChapters, getProgress } from '@/api/novel'
import { ragAsk } from '@/api/rag'
import type { Novel, Chapter } from '@/api/novel'
import type { RagAnswer } from '@/api/rag'

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

// AI 问答
const askDialog = ref(false)
const asking = ref(false)
const askQuestion = ref('')
const askAnswer = ref<RagAnswer | null>(null)

async function doAsk() {
  if (!askQuestion.value.trim()) {
    ElMessage.warning('请输入问题')
    return
  }
  asking.value = true
  try {
    askAnswer.value = await ragAsk(novelId, askQuestion.value.trim())
  } catch (e: any) {
    // 拦截器已提示业务错误；此处仅兜底未捕获异常（避免双弹窗）
    console.error('AI 问答失败:', e)
  } finally {
    asking.value = false
  }
}

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

// 封面配色与 Home 一致（暖色五音）
const TONES = [
  ['#3d5a4c', '#2c4438'],
  ['#b3472d', '#93351f'],
  ['#3a4a6b', '#2b3850'],
  ['#8a6a3b', '#6f542e'],
  ['#5a6a72', '#46545c']
]
function coverGradient() {
  const [a, c] = TONES[novelId % TONES.length]
  return `linear-gradient(150deg, ${a} 0%, ${c} 100%)`
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
.detail { min-height: 100vh; background: var(--paper); }
.topbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 24px;
  background: #fffdf7;
  border-bottom: 1px solid var(--line);
  position: sticky;
  top: 0;
  z-index: 10;
}
.topbar-title { font-weight: 600; font-family: var(--serif); color: var(--ink); }
.content { max-width: 860px; margin: 0 auto; padding: 24px 20px 48px; }
.hero { display: flex; gap: 24px; background: #fffdf7; border: 1px solid var(--line); border-radius: 14px; padding: 24px; }
.cover {
  width: 120px;
  height: 160px;
  border-radius: 4px 10px 10px 4px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 2px;
}
.hero-info h1 { margin: 0 0 10px; font-size: 26px; font-family: var(--serif); color: var(--ink); }
.meta-line { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.meta-text { font-size: 13px; color: #64748b; }
.intro { font-size: 14px; color: #475569; line-height: 1.8; margin: 0 0 18px; }
.section-title { font-size: 18px; margin: 28px 0 12px; color: #0f172a; }
.chapter-list { background: #fffdf7; border: 1px solid var(--line); border-radius: 12px; padding: 8px 16px; min-height: 80px; }
.chapter-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 11px 8px;
  border-bottom: 1px solid #f1f5f9;
  cursor: pointer;
  transition: background 0.12s;
}
.chapter-item:hover { background: var(--paper); }
.chapter-item.current { background: rgba(179,71,45,.06); }
.chapter-item.current .ch-title { color: var(--cinnabar); font-weight: 600; }
.ch-no { width: 28px; height: 28px; border-radius: 6px; background: var(--paper-deep); color: var(--ink-faint); font-size: 12px; display: flex; align-items: center; justify-content: center; font-family: var(--serif); }
.ch-title { flex: 1; font-size: 14px; color: var(--ink-soft); font-family: var(--serif); }
.ch-chars { font-size: 12px; color: var(--ink-faint); }
.pager { display: flex; justify-content: center; padding: 12px 0; }
.ask-body { display: flex; flex-direction: column; gap: 10px; }
.ask-actions { display: flex; justify-content: flex-end; }
.ask-answer {
  background: #f8fafc;
  border-left: 3px solid #f59e0b;
  padding: 12px 14px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
  max-height: 300px;
  overflow-y: auto;
}
.ask-sources { margin-top: 6px; }
.sources-title { font-size: 13px; font-weight: 600; color: #64748b; margin-bottom: 6px; }
.source-item {
  display: flex;
  gap: 10px;
  padding: 8px 10px;
  background: #fff;
  border: 1px solid #eef0f4;
  border-radius: 8px;
  margin-bottom: 6px;
  font-size: 13px;
  color: #475569;
}
.source-ch { flex-shrink: 0; color: #f59e0b; font-weight: 600; }
.source-text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
