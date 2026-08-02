<template>
  <article class="card moment-card">
    <!-- 头部：头像 + 昵称 + 时间 -->
    <header class="m-head">
      <router-link :to="`/user/${m.userId}`" class="m-user">
        <span class="avatar avatar-sm">{{ avatarChar(m.nickname) }}</span>
        <span class="m-nick">{{ m.nickname }}</span>
      </router-link>
      <div class="m-right">
        <span v-if="m.pinned === 1" class="pin-tag">置顶</span>
        <span class="m-time" :title="fullTime(m.createdAt)">{{ timeAgo(m.createdAt) }}</span>
        <button class="more-btn" @click.stop="openMenu($event)">···</button>
      </div>
    </header>

    <!-- 内容 -->
    <div class="m-content" @click="toggleComments">{{ m.content }}</div>

    <!-- 点赞区 -->
    <div v-if="m.likeCount > 0 || m.liked" class="m-like-row" @click="showLikes">
      <span class="like-icon" :class="{ liked: m.liked }">♡</span>
      <span v-if="m.likeCount > 0" class="like-text">
        <template v-if="likedNames.length">
          <b>{{ likedNames[0] }}</b><template v-if="m.likeCount > 1">、<b>{{ likedNames[1] }}</b></template>
          <template v-if="m.likeCount > 2">等</template>
          {{ m.likeCount > 2 ? `${m.likeCount} 人赞过` : (m.likeCount > 1 ? '赞过' : '赞过') }}
        </template>
        <template v-else>{{ m.likeCount }} 人赞过</template>
      </span>
    </div>

    <!-- 操作栏 -->
    <footer class="m-actions">
      <button class="act" :class="{ liked: m.liked }" @click="toggleLike">
        <span class="act-icon">{{ m.liked ? '❤' : '♡' }}</span>
        <span>{{ m.liked ? '已赞' : '点赞' }}</span>
      </button>
      <button class="act" @click="toggleComments">
        <span class="act-icon">💬</span>
        <span>评论{{ m.commentCount > 0 ? ` ${fmtNum(m.commentCount)}` : '' }}</span>
      </button>
      <button class="act" @click="showLikes">
        <span class="act-icon">👥</span>
        <span>赞列表</span>
      </button>
    </footer>

    <!-- 评论展开区 -->
    <div v-if="commentsOpen" class="m-comments" @click.stop>
      <CommentList
        :moment-id="m.id"
        :author-id="m.userId"
        @comment-count="c => (m.commentCount = c)"
      />
    </div>
  </article>

  <!-- 赞列表弹层 -->
  <teleport to="body">
    <div v-if="likesVisible" class="modal-mask" @click.self="likesVisible = false">
      <div class="modal likes-modal">
        <div class="modal-head"><h3>赞过的人</h3><button class="modal-close" @click="likesVisible = false">✕</button></div>
        <div class="modal-body">
          <div v-if="likes.length === 0" class="empty"><div class="icon">♡</div><p>还没有人赞过</p></div>
          <div v-for="l in likes" :key="l.id" class="like-item">
            <router-link :to="`/user/${l.userId}`" class="like-user">
              <span class="avatar avatar-xs">{{ avatarChar(l.nickname) }}</span>
              <span>{{ l.nickname }}</span>
            </router-link>
            <span class="like-time">{{ timeAgo(l.createdAt) }}</span>
          </div>
        </div>
      </div>
    </div>
  </teleport>

  <!-- 更多菜单 -->
  <teleport to="body">
    <div v-if="menuVisible" class="menu-pop" :style="{ top: menuPos.y + 'px', left: menuPos.x + 'px' }">
      <template v-if="auth.isLogin && m.userId === auth.userId">
        <button class="danger" @click="del">删除动态</button>
      </template>
      <template v-else>
        <button @click="report">举报</button>
      </template>
    </div>
  </teleport>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import http from '../api'
import { useAuthStore } from '../stores/auth'
import { timeAgo, fullTime, fmtNum, avatarChar, toast } from '../utils/format'
import CommentList from './CommentList.vue'

const props = defineProps({ m: { type: Object, required: true } })
const emit = defineEmits(['deleted'])

const auth = useAuthStore()
const commentsOpen = ref(false)
const likesVisible = ref(false)
const likes = ref([])
const menuVisible = ref(false)
const menuPos = ref({ x: 0, y: 0 })

const likedNames = computed(() => (props.m.likedBy || []).slice(0, 2).map(l => l.nickname))

/* ---------- 点赞（乐观更新） ---------- */
let likeRollback = null
async function toggleLike() {
  if (!auth.isLogin) { toast('请先登录', 'error'); return }
  const was = props.m.liked
  props.m.liked = !was
  props.m.likeCount = Math.max(0, props.m.likeCount + (was ? -1 : 1))
  try {
    const data = await http.post(`/api/moments/${props.m.id}/like`)
    // 以后端为准，避免并发不一致
    props.m.liked = data.liked
    props.m.likeCount = Math.max(0, props.m.likeCount + (data.liked === was ? (data.liked ? 1 : -1) : 0))
  } catch (e) {
    props.m.liked = was
    props.m.likeCount = Math.max(0, props.m.likeCount + (was ? 1 : -1))
    toast(e.response?.data?.msg || '操作失败', 'error')
  }
}

/* ---------- 赞列表 ---------- */
async function showLikes() {
  try {
    const data = await http.get(`/api/moments/${props.m.id}/likes?page=1&size=50`)
    likes.value = data.list
    likesVisible.value = true
  } catch { /* 动态已删除等 */ }
}

/* ---------- 评论展开 ---------- */
function toggleComments() {
  commentsOpen.value = !commentsOpen.value
}

/* ---------- 更多菜单 ---------- */
function openMenu(e) {
  menuVisible.value = !menuVisible.value
  menuPos.value = { x: Math.min(e.clientX, window.innerWidth - 130), y: e.clientY }
}
function closeMenu() { menuVisible.value = false }
onMounted(() => window.addEventListener('click', closeMenu))
onUnmounted(() => window.removeEventListener('click', closeMenu))

async function del() {
  menuVisible.value = false
  if (!confirm('确定删除这条动态吗？其下评论将一并删除。')) return
  try {
    await http.delete(`/api/moments/${props.m.id}`)
    toast('已删除')
    emit('deleted', props.m.id)
  } catch (e) {
    toast(e.response?.data?.msg || '删除失败', 'error')
  }
}

async function report() {
  menuVisible.value = false
  if (!auth.isLogin) { toast('请先登录', 'error'); return }
  const reason = prompt('举报理由（选填）') ?? ''
  try {
    await http.post('/api/reports', { targetType: 'moment', targetId: props.m.id, reason })
    toast('举报已提交，管理员会尽快处理')
  } catch (e) {
    toast(e.response?.data?.msg || '举报失败', 'error')
  }
}
</script>

<style scoped>
.moment-card { margin-bottom: 14px; overflow: hidden; }
.m-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 16px 0;
}
.m-user { display: flex; align-items: center; gap: 10px; }
.avatar-sm { width: 38px; height: 38px; font-size: 16px; }
.m-nick { font-size: 14px; font-weight: 600; color: var(--text); }
.m-right { display: flex; align-items: center; gap: 8px; }
.pin-tag {
  font-size: 10px; color: var(--accent);
  border: 1px solid var(--accent);
  border-radius: 4px; padding: 0 5px;
}
.m-time { font-size: 12px; color: var(--text-3); }
.more-btn {
  border: none; background: none;
  color: var(--text-3); font-size: 16px;
  cursor: pointer; padding: 0 2px; line-height: 1;
}
.more-btn:hover { color: var(--text); }
.m-content {
  padding: 12px 16px 10px;
  font-size: 15px;
  color: var(--text);
  white-space: pre-wrap;
  word-break: break-word;
  cursor: pointer;
  line-height: 1.8;
}
.m-like-row {
  display: flex; align-items: center; gap: 6px;
  padding: 6px 16px;
  cursor: pointer;
  font-size: 12px; color: var(--text-2);
}
.like-icon { color: var(--text-3); font-size: 11px; }
.like-icon.liked { color: var(--danger); }
.m-like-row:hover .like-text { color: var(--accent); }
.m-actions {
  display: flex;
  border-top: 1px solid var(--line-soft);
  padding: 4px 8px;
}
.act {
  flex: 1;
  border: none; background: none;
  padding: 8px 0;
  font-size: 13px; color: var(--text-2);
  cursor: pointer;
  display: flex; align-items: center; justify-content: center; gap: 6px;
  border-radius: 6px;
  transition: all .15s;
}
.act:hover { background: var(--accent-soft); color: var(--accent); }
.act.liked { color: var(--danger); }
.act-icon { font-size: 14px; }
.m-comments {
  border-top: 1px solid var(--line-soft);
  background: var(--bg-soft);
  padding: 4px 16px 14px;
}
.likes-modal { max-width: 360px; }
.like-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 2px;
}
.like-user { display: flex; align-items: center; gap: 10px; font-size: 14px; }
.avatar-xs { width: 30px; height: 30px; font-size: 13px; }
.like-time { font-size: 12px; color: var(--text-3); }
</style>
