<template>
  <div class="reader-page" v-if="novel" @click.self="toolbarVisible = false">
    <!-- 顶部栏 -->
    <header class="reader-header">
      <router-link to="/" class="back-btn">← 书架</router-link>
      <h2 class="book-title">{{ novel.title }}</h2>
      <div class="header-user" v-if="currentUser">
        <router-link to="/notifications" class="notification-bell" title="我的通知">
          🔔
          <span v-if="unreadNotificationCount > 0" class="bell-badge">{{ unreadNotificationCount > 99 ? '99+' : unreadNotificationCount }}</span>
        </router-link>
        <span class="header-user-name">{{ currentUser.nickname }}</span>
      </div>
      <div class="chapter-nav-top">
        <button @click="prevChapter" :disabled="!hasPrev">上一章</button>
        <span class="chapter-indicator">{{ currentIndex + 1 }} / {{ chapters.length }}</span>
        <button @click="nextChapter" :disabled="!hasNext">下一章</button>
      </div>
    </header>

    <div class="reader-body">
      <!-- 左侧章节目录 -->
      <ChapterSidebar
        v-model="sidebarOpen"
        :chapters="chapters"
        :current-chapter-id="currentChapter?.id"
        @select="loadChapter"
      />

      <!-- 右侧正文 -->
      <main class="content-area">
        <div v-if="!currentChapter" class="loading">加载中...</div>
        <article v-else class="chapter-content">
          <h3 class="chapter-title">{{ currentChapter.title }}</h3>
          <div class="chapter-text" ref="chapterTextRef" @mouseup="onTextSelect">
            <p v-for="(para, pIdx) in contentSegments" :key="pIdx" :data-offset="para.offset">
              <template v-for="(seg, sIdx) in para.segs" :key="sIdx">
                <template v-if="seg.type === 'text'">{{ seg.text }}</template>
                <span v-else class="annotation-mark" :class="{ 'annotation-validate': seg.annotation?.type === 1 }" :data-offset="seg.offset" @click="handleAnnotationClick(seg.annotation)"><span class="annotated-text">{{ seg.text }}</span><span class="annotation-count">{{ seg.count }}</span></span>
              </template>
            </p>
          </div>
        </article>
        <div class="chapter-nav-bottom" v-if="currentChapter">
          <button @click="prevChapter" :disabled="!hasPrev">← 上一章</button>
          <button @click="nextChapter" :disabled="!hasNext">下一章 →</button>
        </div>
      </main>

      <!-- 右侧批注/讨论面板 -->
      <AnnotationPanel
        v-if="currentChapter"
        ref="annotationPanelRef"
        :annotations="annotations"
        :current-user="currentUser"
        :get-user-nickname="getUserNickname"
        @delete-annotation="handleDeleteAnnotation"
        @report="handleReport"
      />
    </div>

    <!-- 浮动工具栏 -->
    <div v-if="toolbarVisible" class="selection-toolbar" :style="toolbarStyle">
      <button class="toolbar-btn" @click="openAnnotationFromToolbar">写批注</button>
    </div>

    <!-- 批注弹窗 -->
    <AnnotationPopup
      :visible="showAnnotationPopup"
      :selected-text="pendingAnnotation.selectedText"
      :type="pendingAnnotation.type"
      @submit="submitAnnotation"
      @cancel="showAnnotationPopup = false"
    />

    <!-- 举报弹窗 -->
    <ReportPopup
      :visible="showReportPopup"
      :content="pendingReport.content"
      @submit="submitReport"
      @cancel="showReportPopup = false"
    />
  </div>

  <div v-else-if="loadError" class="loading-page">
    <p class="load-error">{{ loadError }}</p>
    <button class="retry-btn" @click="$router.push('/')">返回书架</button>
  </div>
  <div v-else class="loading-page">
    <p>加载中...</p>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getNovelDetail, getChapter, getAnnotations, createAnnotation, deleteAnnotation, createReport, getUnreadCount } from '../api'
import { useCurrentUser } from '../composables/useCurrentUser'
import ChapterSidebar from '../components/reader/ChapterSidebar.vue'
import AnnotationPanel from '../components/reader/AnnotationPanel.vue'
import AnnotationPopup from '../components/reader/AnnotationPopup.vue'
import ReportPopup from '../components/reader/ReportPopup.vue'

const route = useRoute()
const { currentUser, ensureUser, getUserNickname } = useCurrentUser()
const novel = ref(null)
const chapters = ref([])
const currentChapter = ref(null)
const currentIndex = ref(0)
const sidebarOpen = ref(false)
const annotations = ref([])
const chapterTextRef = ref(null)
const annotationPanelRef = ref(null)

// 浮动工具栏
const toolbarVisible = ref(false)
const toolbarStyle = ref({ top: '0px', left: '0px' })
const pendingSelection = ref(null)

// 批注弹窗
const showAnnotationPopup = ref(false)
const pendingAnnotation = ref({ selectedText: '', anchorStart: 0, anchorEnd: 0, content: '', type: 0 })

// 举报
const showReportPopup = ref(false)
const pendingReport = ref({ targetType: '', targetId: null, content: '', reason: '' })

// 通知未读数
const unreadNotificationCount = ref(0)

const hasPrev = computed(() => currentIndex.value > 0)
const hasNext = computed(() => currentIndex.value < chapters.value.length - 1)

// 按段落分组：每个段落包含一组内联的 text/annotation 片段
const contentSegments = computed(() => {
  if (!currentChapter.value?.content) return []
  const content = currentChapter.value.content
  const result = []
  const paragraphs = content.split(/\n+/).filter(line => line.trim())
  const paragraphInfos = []
  let offset = 0
  for (const para of paragraphs) {
    const start = content.indexOf(para, offset)
    paragraphInfos.push({ text: para, start, end: start + para.length })
    offset = start + para.length
  }
  for (const info of paragraphInfos) {
    const coveringAnnotations = annotations.value.filter(ann =>
      ann.anchorStart >= info.start && ann.anchorEnd <= info.end
    )
    const segs = []
    if (coveringAnnotations.length === 0) {
      segs.push({ type: 'text', text: info.text })
    } else {
      coveringAnnotations.sort((a, b) => a.anchorStart - b.anchorStart)
      let pos = info.start
      for (const ann of coveringAnnotations) {
        if (ann.anchorStart > pos) {
          segs.push({ type: 'text', text: content.substring(pos, ann.anchorStart) })
        }
        segs.push({
          type: 'annotation',
          text: content.substring(ann.anchorStart, ann.anchorEnd),
          offset: ann.anchorStart,
          annotation: ann,
          count: coveringAnnotations.filter(a => a.anchorStart === ann.anchorStart).length
        })
        pos = ann.anchorEnd
      }
      if (pos < info.end) {
        segs.push({ type: 'text', text: content.substring(pos, info.end) })
      }
    }
    result.push({ offset: info.start, segs })
  }
  return result
})

const loadError = ref('')

onMounted(async () => {
  await ensureUser()
  if (currentUser.value) {
    try {
      const res = await getUnreadCount()
      unreadNotificationCount.value = res.data.count || 0
    } catch (e) { /* 忽略 */ }
  }
  const novelId = route.params.novelId
  try {
    const res = await getNovelDetail(novelId)
    novel.value = res.data.novel
    chapters.value = res.data.chapters
    if (chapters.value.length > 0) {
      loadChapter(chapters.value[0].id, 0)
    }
  } catch (e) {
    loadError.value = e.message || '加载小说失败'
  }
})

async function loadChapter(chapterId, index) {
  try {
    const res = await getChapter(chapterId)
    currentChapter.value = res.data
    currentIndex.value = index
    sidebarOpen.value = false
    annotationPanelRef.value?.closeDiscussion()
    await loadAnnotations(chapterId)
    window.scrollTo({ top: 0 })
  } catch (e) {
    alert(e.message || '加载章节失败')
  }
}

async function loadAnnotations(chapterId) {
  try {
    const res = await getAnnotations(chapterId)
    for (const ann of res.data) {
      ann._commentCount = ann.commentCount || 0
      ann._likeCount = ann.likeCount || 0
      ann._likedByCurrentUser = ann.likedByCurrentUser || false
    }
    annotations.value = res.data
  } catch (e) {
    console.error('加载批注失败', e)
    annotations.value = []
  }
}

function onTextSelect(e) {
  if (e.button !== 0) { toolbarVisible.value = false; return }
  if (!currentUser.value) { alert('请先登录后再写批注'); return }
  const selection = window.getSelection()
  if (!selection || selection.isCollapsed) { toolbarVisible.value = false; return }
  const selectedText = selection.toString().trim()
  if (!selectedText || selectedText.length < 2) { toolbarVisible.value = false; return }

  const chapterEl = chapterTextRef.value
  if (!chapterEl) return
  const range = selection.getRangeAt(0)

  function getTextOffset(root, targetNode, targetOffset) {
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
      acceptNode(node) {
        // 排除批注计数徽章（如 "2"），这些不属于原始小说内容
        const parent = node.parentElement
        if (parent && parent.classList.contains('annotation-count')) {
          return NodeFilter.FILTER_REJECT
        }
        return NodeFilter.FILTER_ACCEPT
      }
    })
    let offset = 0
    while (walker.nextNode()) {
      if (walker.currentNode === targetNode) return offset + targetOffset
      offset += walker.currentNode.textContent.length
    }
    return offset
  }

  const anchorStart = getTextOffset(chapterEl, range.startContainer, range.startOffset)
  const anchorEnd = getTextOffset(chapterEl, range.endContainer, range.endOffset)
  pendingSelection.value = { selectedText, anchorStart, anchorEnd }
  const rect = range.getBoundingClientRect()
  toolbarStyle.value = {
    top: (rect.top - 44) + 'px',
    left: (rect.left + rect.width / 2 - 40) + 'px'
  }
  toolbarVisible.value = true
}

function openAnnotationFromToolbar() {
  if (!pendingSelection.value) return
  toolbarVisible.value = false
  pendingAnnotation.value = { ...pendingSelection.value, content: '', type: 0 }
  showAnnotationPopup.value = true
  window.getSelection()?.removeAllRanges()
}

async function submitAnnotation({ content, type }) {
  try {
    await createAnnotation({
      chapterId: currentChapter.value.id,
      anchorStart: pendingAnnotation.value.anchorStart,
      anchorEnd: pendingAnnotation.value.anchorEnd,
      selectedText: pendingAnnotation.value.selectedText,
      content,
      type
    })
    showAnnotationPopup.value = false
    await loadAnnotations(currentChapter.value.id)
  } catch (e) {
    alert(e.message || '提交批注失败')
  }
}

function handleAnnotationClick(ann) {
  // 滚动正文到对应位置
  const el = chapterTextRef.value?.querySelector(`[data-offset="${ann.anchorStart}"]`)
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'center' })
    el.classList.add('highlight')
    setTimeout(() => el.classList.remove('highlight'), 2000)
  }
}

async function handleDeleteAnnotation(ann) {
  if (!confirm('确定删除这条批注？')) return
  try {
    await deleteAnnotation(ann.id)
    annotationPanelRef.value?.closeDiscussion()
    await loadAnnotations(currentChapter.value.id)
  } catch (e) {
    alert(e.message || '删除批注失败')
  }
}

function handleReport(targetType, target) {
  pendingReport.value = {
    targetType,
    targetId: target.id,
    content: target.selectedText || target.content || '',
    reason: ''
  }
  showReportPopup.value = true
}

async function submitReport(reason) {
  try {
    await createReport({
      targetType: pendingReport.value.targetType,
      targetId: pendingReport.value.targetId,
      reason
    })
    showReportPopup.value = false
    alert('举报已提交，管理员会尽快处理')
  } catch (e) {
    alert(e.message || '举报失败')
  }
}

function prevChapter() {
  if (hasPrev.value) {
    const ch = chapters.value[currentIndex.value - 1]
    loadChapter(ch.id, currentIndex.value - 1)
  }
}

function nextChapter() {
  if (hasNext.value) {
    const ch = chapters.value[currentIndex.value + 1]
    loadChapter(ch.id, currentIndex.value + 1)
  }
}
</script>

<style scoped>
.reader-page { min-height: 100vh; }
.reader-header {
  position: sticky; top: 0; z-index: 100;
  display: flex; align-items: center; gap: 16px;
  padding: 12px 24px; background: #fffdf7; border-bottom: 1px solid #e8e0d0;
}
.back-btn { font-size: 0.9rem; color: #888; white-space: nowrap; }
.back-btn:hover { color: #333; }
.book-title { font-size: 1.1rem; font-weight: 600; flex-shrink: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.header-user { margin-left: auto; font-size: 0.85rem; color: #666; }
.header-user-name { font-weight: 600; color: #8b7355; }
.notification-bell { position: relative; text-decoration: none; font-size: 1.1rem; margin-right: 8px; cursor: pointer; }
.bell-badge {
  position: absolute; top: -6px; right: -10px; background: #e65100; color: white;
  font-size: 0.6rem; min-width: 16px; height: 16px; line-height: 16px;
  border-radius: 8px; text-align: center; padding: 0 3px;
}
.chapter-nav-top { display: flex; align-items: center; gap: 12px; flex-shrink: 0; }
.chapter-nav-top button {
  padding: 4px 12px; border: 1px solid #d0c8b8; border-radius: 4px;
  background: transparent; cursor: pointer; font-size: 0.85rem; color: #666;
}
.chapter-nav-top button:disabled { opacity: 0.3; cursor: not-allowed; }
.chapter-nav-top button:hover:not(:disabled) { background: #f0ebe0; }
.chapter-indicator { font-size: 0.8rem; color: #aaa; min-width: 50px; text-align: center; }
.reader-body { display: flex; min-height: calc(100vh - 53px); }
.content-area { flex: 1; max-width: 750px; margin: 0 auto; padding: 40px 32px 80px; }
.chapter-title { font-size: 1.5rem; font-weight: 700; text-align: center; margin-bottom: 36px; color: #1a1a1a; }
.chapter-text :deep(p) { text-indent: 2em; margin-bottom: 0.8em; font-size: 1.05rem; line-height: 2; color: #333; }
.selection-toolbar {
  position: fixed; z-index: 200; background: #333; border-radius: 6px;
  padding: 4px; box-shadow: 0 2px 12px rgba(0,0,0,0.25); animation: toolbarFadeIn 0.15s ease-out;
}
@keyframes toolbarFadeIn { from { opacity: 0; transform: translateY(4px); } to { opacity: 1; transform: translateY(0); } }
.toolbar-btn { padding: 6px 16px; background: transparent; color: #fff; border: none; border-radius: 4px; font-size: 0.85rem; cursor: pointer; white-space: nowrap; }
.toolbar-btn:hover { background: rgba(255,255,255,0.15); }
.annotation-mark { display: inline; cursor: pointer; position: relative; }
.annotated-text { background: linear-gradient(to bottom, transparent 60%, #ffd54f 60%); padding: 0 2px; }
.annotation-validate .annotated-text { background: linear-gradient(to bottom, transparent 60%, #ff9800 60%); }
.annotation-validate .annotation-count { background: #e65100; }
.annotation-count {
  display: inline-block; background: #8b7355; color: white; font-size: 0.7rem;
  width: 16px; height: 16px; border-radius: 50%; text-align: center;
  line-height: 16px; margin-left: 2px; vertical-align: super;
}
.chapter-nav-bottom { display: flex; justify-content: space-between; margin-top: 60px; padding-top: 24px; border-top: 1px solid #e8e0d0; }
.chapter-nav-bottom button {
  padding: 10px 24px; border: 1px solid #d0c8b8; border-radius: 6px;
  background: #fffdf7; cursor: pointer; font-size: 0.95rem; color: #666; transition: all 0.15s;
}
.chapter-nav-bottom button:disabled { opacity: 0.3; cursor: not-allowed; }
.chapter-nav-bottom button:hover:not(:disabled) { background: #f0ebe0; color: #333; }
.loading, .loading-page { display: flex; flex-direction: column; justify-content: center; align-items: center; min-height: 60vh; color: #999; font-size: 1rem; gap: 16px; }
.load-error { color: #e74c3c; }
.retry-btn { padding: 8px 24px; border: 1px solid #ddd; border-radius: 4px; background: #fff; cursor: pointer; }
.highlight { animation: highlightFade 2s ease-out; }
@keyframes highlightFade { 0% { background: #ffd54f; } 100% { background: transparent; } }
@media (max-width: 768px) {
  .content-area { padding: 24px 16px 60px; }
  .chapter-text :deep(p) { font-size: 1rem; }
}
</style>
