<template>
  <aside class="annotation-sidebar">
    <!-- 列表态 -->
    <template v-if="!activeDiscussion">
      <h4 class="annotation-sidebar-title">本章批注 ({{ annotations.length }})</h4>
      <div v-if="annotations.length === 0" class="annotation-empty">暂无批注</div>
      <div
        v-for="ann in annotations"
        :key="ann.id"
        class="annotation-item"
        :class="{ 'annotation-item-validate': ann.type === 1 }"
        @click="openDiscussion(ann)"
      >
        <div class="annotation-original">
          <span v-if="ann.type === 1" class="type-badge">数据校验</span>
          「{{ ann.selectedText }}」
        </div>
        <div class="annotation-content">{{ ann.content }}</div>
        <div class="annotation-footer">
          <span class="annotation-meta">—— {{ getUserNickname(ann.userId) }} · {{ formatTime(ann.createdAt) }}</span>
          <div class="annotation-actions">
            <span v-if="ann._likeCount" class="like-count">👍 {{ ann._likeCount }}</span>
            <span v-if="ann._commentCount" class="comment-count">💬 {{ ann._commentCount }}</span>
            <button v-if="currentUser && currentUser.id === ann.userId" class="annotation-delete" @click.stop="$emit('delete-annotation', ann)" title="删除批注">删除</button>
            <button v-if="currentUser && currentUser.id !== ann.userId" class="annotation-report" @click.stop="$emit('report', 'annotation', ann)" title="举报">举报</button>
          </div>
        </div>
      </div>
    </template>

    <!-- 讨论态 -->
    <template v-else>
      <div class="discussion-header">
        <button class="back-to-list" @click="activeDiscussion = null">← 返回</button>
      </div>
      <div class="discussion-annotation" :class="{ 'annotation-item-validate': activeDiscussion.type === 1 }">
        <div class="annotation-original">
          <span v-if="activeDiscussion.type === 1" class="type-badge">数据校验</span>
          「{{ activeDiscussion.selectedText }}」
        </div>
        <div class="annotation-content">{{ activeDiscussion.content }}</div>
        <div class="annotation-bottom">
          <span class="annotation-meta">—— {{ getUserNickname(activeDiscussion.userId) }}</span>
          <button v-if="currentUser" class="like-btn" :class="{ 'liked': discussionLiked }" @click="handleToggleLike">👍 {{ discussionLikeCount }}</button>
          <span v-else class="like-count">👍 {{ discussionLikeCount }}</span>
        </div>
      </div>

      <div class="discussion-comments">
        <h5 class="comments-title">讨论 ({{ discussionComments.length }})</h5>
        <div v-if="discussionComments.length === 0" class="comments-empty">暂无讨论</div>
        <div v-for="c in discussionComments" :key="c.id" class="comment-item">
          <div class="comment-content">
            <span v-if="c.replyToId" class="reply-hint">回复 {{ getUserNickname(getCommentById(c.replyToId)?.userId) }}：</span>
            {{ c.content }}
          </div>
          <div class="comment-footer">
            <span class="comment-meta">{{ getUserNickname(c.userId) }} · {{ formatTime(c.createdAt) }}</span>
            <div class="comment-actions">
              <button v-if="currentUser" class="comment-reply-btn" @click="replyTo = { id: c.id, nickname: getUserNickname(c.userId) }">回复</button>
              <button v-if="currentUser && currentUser.id === c.userId" class="comment-delete" @click="handleDeleteComment(c)">删除</button>
            </div>
          </div>
        </div>
      </div>

      <div class="discussion-input" v-if="currentUser">
        <div v-if="replyTo" class="reply-indicator">
          回复 {{ replyTo.nickname }}：
          <button class="reply-cancel" @click="replyTo = null">✕</button>
        </div>
        <textarea v-model="newCommentContent" :placeholder="replyTo ? '回复 ' + replyTo.nickname + '...' : '说说你的看法...'" rows="3"></textarea>
        <button class="btn-comment-submit" @click="submitComment" :disabled="!newCommentContent.trim()">发表</button>
      </div>
      <div v-else class="discussion-login-hint">请先登录后再参与讨论</div>
    </template>
  </aside>
</template>

<script setup>
import { ref } from 'vue'
import { getComments, createComment, deleteComment, toggleLike } from '../../api'

const props = defineProps({
  annotations: { type: Array, default: () => [] },
  currentUser: { type: Object, default: null },
  getUserNickname: { type: Function, required: true }
})
const emit = defineEmits(['delete-annotation', 'report', 'annotations-updated'])

const activeDiscussion = ref(null)
const discussionComments = ref([])
const newCommentContent = ref('')
const replyTo = ref(null)
const discussionLiked = ref(false)
const discussionLikeCount = ref(0)

function formatTime(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mi = String(d.getMinutes()).padStart(2, '0')
  return `${mm}-${dd} ${hh}:${mi}`
}

function getCommentById(id) {
  return discussionComments.value.find(c => c.id === id)
}

async function openDiscussion(ann) {
  activeDiscussion.value = ann
  newCommentContent.value = ''
  replyTo.value = null
  discussionLiked.value = ann._likedByCurrentUser || false
  discussionLikeCount.value = ann._likeCount || 0
  try {
    const res = await getComments(ann.id)
    discussionComments.value = res.data
  } catch (e) {
    console.error('加载评论失败', e)
    discussionComments.value = []
  }
}

async function handleToggleLike() {
  if (!activeDiscussion.value || !props.currentUser) return
  const prevLiked = discussionLiked.value
  const prevCount = discussionLikeCount.value
  discussionLiked.value = !prevLiked
  discussionLikeCount.value = prevLiked ? prevCount - 1 : prevCount + 1
  const ann = props.annotations.find(a => a.id === activeDiscussion.value.id)
  if (ann) ann._likeCount = discussionLikeCount.value
  try {
    const res = await toggleLike(activeDiscussion.value.id)
    discussionLiked.value = res.data.liked
    discussionLikeCount.value = res.data.likeCount
    if (ann) ann._likeCount = res.data.likeCount
  } catch (e) {
    discussionLiked.value = prevLiked
    discussionLikeCount.value = prevCount
    if (ann) ann._likeCount = prevCount
    alert(e.message || '点赞失败')
  }
}

async function submitComment() {
  if (!newCommentContent.value.trim() || !activeDiscussion.value) return
  const content = newCommentContent.value.trim()
  const replyToId = replyTo.value ? replyTo.value.id : null
  try {
    const res = await createComment({ annotationId: activeDiscussion.value.id, replyToId, content })
    discussionComments.value.push(res.data)
    newCommentContent.value = ''
    replyTo.value = null
    const ann = props.annotations.find(a => a.id === activeDiscussion.value.id)
    if (ann) ann._commentCount = (ann._commentCount || 0) + 1
  } catch (e) {
    alert(e.message || '发表评论失败')
  }
}

async function handleDeleteComment(comment) {
  if (!confirm('确定删除这条评论？')) return
  try {
    await deleteComment(comment.id)
    const res = await getComments(activeDiscussion.value.id)
    discussionComments.value = res.data
    const ann = props.annotations.find(a => a.id === activeDiscussion.value.id)
    if (ann) ann._commentCount = res.data.length
  } catch (e) {
    alert(e.message || '删除评论失败')
  }
}

/** 供父组件调用：关闭讨论面板 */
function closeDiscussion() {
  activeDiscussion.value = null
}

defineExpose({ closeDiscussion })
</script>

<style scoped>
.annotation-sidebar {
  width: 320px; flex-shrink: 0; background: #faf6ee;
  border-left: 1px solid #e8e0d0; padding: 20px;
  overflow-y: auto; height: calc(100vh - 53px);
  position: sticky; top: 53px;
}
.annotation-sidebar-title {
  font-size: 0.95rem; font-weight: 600; color: #555;
  margin-bottom: 16px; padding-bottom: 8px; border-bottom: 1px solid #e8e0d0;
}
.annotation-empty { text-align: center; color: #999; font-size: 0.9rem; padding: 40px 0; }
.annotation-item {
  background: #fffdf7; border: 1px solid #e8e0d0; border-radius: 6px;
  padding: 12px; margin-bottom: 12px; cursor: pointer; transition: box-shadow 0.2s;
}
.annotation-item:hover { box-shadow: 0 2px 8px rgba(0,0,0,0.08); }
.annotation-original { font-size: 0.85rem; color: #8b7355; margin-bottom: 8px; line-height: 1.5; }
.annotation-content { font-size: 0.9rem; color: #333; line-height: 1.6; margin-bottom: 8px; }
.annotation-meta { font-size: 0.8rem; color: #999; }
.annotation-footer { display: flex; align-items: center; justify-content: space-between; }
.annotation-actions { display: flex; align-items: center; gap: 8px; }
.comment-count, .like-count { font-size: 0.75rem; color: #888; }
.annotation-delete, .annotation-report {
  padding: 2px 8px; font-size: 0.75rem; border: 1px solid #e0d5c5;
  border-radius: 3px; background: transparent; color: #999; cursor: pointer; transition: all 0.15s;
}
.annotation-delete:hover { background: #f5e6e6; color: #c0392b; border-color: #c0392b; }
.annotation-report:hover { background: #fff3e0; color: #e65100; border-color: #e65100; }
.annotation-item-validate { border-color: #ffcc80; background: #fff8f0; }
.type-badge {
  display: inline-block; font-size: 0.7rem; color: #e65100; background: #fff3e0;
  border: 1px solid #ffcc80; border-radius: 3px; padding: 0 5px; margin-right: 4px; vertical-align: middle;
}
.like-btn {
  background: none; border: 1px solid #e8e0d0; border-radius: 12px;
  padding: 2px 10px; font-size: 0.75rem; color: #888; cursor: pointer; transition: all 0.15s;
}
.like-btn:hover { border-color: #8b7355; color: #8b7355; }
.like-btn.liked { background: #f5f0e8; border-color: #8b7355; color: #8b7355; font-weight: 600; }
.annotation-bottom { display: flex; align-items: center; justify-content: space-between; margin-top: 8px; }
.discussion-header { margin-bottom: 16px; padding-bottom: 12px; border-bottom: 1px solid #e8e0d0; }
.back-to-list { background: none; border: none; font-size: 0.85rem; color: #888; cursor: pointer; padding: 0; }
.back-to-list:hover { color: #333; }
.discussion-annotation {
  background: #fffdf7; border: 1px solid #e8e0d0; border-radius: 6px; padding: 14px; margin-bottom: 16px;
}
.discussion-comments { margin-bottom: 16px; }
.comments-title {
  font-size: 0.9rem; font-weight: 600; color: #555;
  margin-bottom: 12px; padding-bottom: 6px; border-bottom: 1px solid #e8e0d0;
}
.comments-empty { text-align: center; color: #bbb; font-size: 0.85rem; padding: 24px 0; }
.comment-item { padding: 10px 12px; background: #fff; border: 1px solid #f0ece4; border-radius: 6px; margin-bottom: 8px; }
.comment-content { font-size: 0.88rem; color: #333; line-height: 1.6; margin-bottom: 6px; }
.comment-footer { display: flex; align-items: center; justify-content: space-between; }
.comment-meta { font-size: 0.75rem; color: #aaa; }
.comment-delete { background: none; border: none; font-size: 0.72rem; color: #ccc; cursor: pointer; padding: 0; }
.comment-delete:hover { color: #c0392b; }
.reply-hint { color: #8b7355; font-size: 0.8rem; font-weight: 600; }
.comment-reply-btn { background: none; border: none; font-size: 0.72rem; color: #aaa; cursor: pointer; padding: 0; margin-right: 8px; }
.comment-reply-btn:hover { color: #8b7355; }
.comment-actions { display: flex; align-items: center; gap: 4px; }
.reply-indicator {
  font-size: 0.82rem; color: #8b7355; padding: 6px 10px; background: #f5f0e8;
  border-radius: 4px; display: flex; align-items: center; justify-content: space-between;
}
.reply-cancel { background: none; border: none; color: #999; cursor: pointer; font-size: 0.85rem; padding: 0 2px; }
.reply-cancel:hover { color: #c0392b; }
.discussion-input { display: flex; flex-direction: column; gap: 8px; }
.discussion-input textarea {
  width: 100%; padding: 10px; border: 1px solid #e8e0d0; border-radius: 6px;
  font-size: 0.88rem; font-family: inherit; resize: vertical; line-height: 1.5;
}
.discussion-input textarea:focus { outline: none; border-color: #8b7355; }
.btn-comment-submit {
  align-self: flex-end; padding: 6px 18px; background: #8b7355; color: white;
  border: none; border-radius: 4px; font-size: 0.85rem; cursor: pointer; transition: background 0.15s;
}
.btn-comment-submit:hover:not(:disabled) { background: #7a6349; }
.btn-comment-submit:disabled { opacity: 0.4; cursor: not-allowed; }
.discussion-login-hint { text-align: center; color: #bbb; font-size: 0.85rem; padding: 16px 0; }
@media (max-width: 768px) {
  .annotation-sidebar { display: none; }
}
</style>
