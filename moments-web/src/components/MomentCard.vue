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

    <!-- 关联小说标签 -->
    <router-link v-if="m.novelTitle" to="/novels" class="m-novel">
      <span class="novel-icon">📖</span>
      <span class="novel-name">{{ m.novelTitle }}</span>
    </router-link>

    <!-- 点赞区（QQ空间式：前5个名字可点击跳个人页，超出显示"等N人"） -->
    <Transition name="like">
      <div v-if="m.likeCount > 0 || m.liked" class="m-like-row">
        <span class="like-heart" :class="{ liked: m.liked }">❤</span>
        <span v-if="m.likedBy && m.likedBy.length" class="like-text">
          <template v-for="(l, i) in m.likedBy" :key="l.userId">
            <router-link v-if="i > 0" :to="`/user/${l.userId}`" class="like-name">、{{ l.nickname }}</router-link>
            <router-link v-else :to="`/user/${l.userId}`" class="like-name">{{ l.nickname }}</router-link>
          </template>
          <template v-if="m.likeCount > 5">等 {{ m.likeCount }} 人</template>
          觉得很赞
        </span>
        <span v-else class="like-text">{{ m.likeCount }} 人觉得很赞</span>
      </div>
    </Transition>

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
import { ref, onMounted, onUnmounted } from 'vue'
import http from '../api'
import { useAuthStore } from '../stores/auth'
import { timeAgo, fullTime, fmtNum, avatarChar, toast } from '../utils/format'
import CommentList from './CommentList.vue'

const props = defineProps({ m: { type: Object, required: true } })
const emit = defineEmits(['deleted'])

const auth = useAuthStore()
const commentsOpen = ref(false)
const menuVisible = ref(false)
const menuPos = ref({ x: 0, y: 0 })

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
    // 乐观期间刷新"XX觉得很赞"前5人
    refreshLikedBy()
  } catch (e) {
    props.m.liked = was
    props.m.likeCount = Math.max(0, props.m.likeCount + (was ? 1 : -1))
    toast(e.response?.data?.msg || '操作失败', 'error')
  }
}

/** 点赞后刷新点赞者（前5个，含 userId 供跳转） */
async function refreshLikedBy() {
  try {
    const data = await http.get(`/api/moments/${props.m.id}/likes?page=1&size=5`)
    props.m.likedBy = data.list.map(l => ({ userId: l.userId, nickname: l.nickname }))
  } catch { /* 忽略 */ }
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
.m-nick { font-size: 14px; font-weight: 600; color: var(--text); transition: color .15s; }
.m-nick:hover { color: var(--accent); }
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
.m-novel {
  display: inline-flex; align-items: center; gap: 5px;
  margin: 0 16px 8px;
  padding: 3px 10px;
  background: var(--accent-soft);
  border: 1px solid #ecd9a8;
  border-radius: 999px;
  font-size: 12px;
  color: #8a6d1f;
  transition: all .15s;
}
.m-novel:hover { background: #f5e8c6; color: var(--accent); }
.novel-icon { font-size: 11px; }
.m-like-row {
  display: flex; align-items: center; gap: 6px;
  margin: 0 16px;
  padding: 6px 10px;
  background: var(--bg-soft);
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px; color: var(--text-2);
  transition: background .15s;
}
.m-like-row:hover { background: var(--line-soft); }
.like-heart { color: var(--text-3); font-size: 11px; }
.like-heart.liked { color: var(--danger); }
.like-name { color: var(--text-2); transition: color .15s; }
.like-name:hover { color: var(--accent); }
/* 点赞区平滑出现/消失 */
.like-enter-active, .like-leave-active { transition: all .25s ease; overflow: hidden; }
.like-enter-from, .like-leave-to { opacity: 0; max-height: 0; padding-top: 0; padding-bottom: 0; }
.like-enter-to, .like-leave-from { opacity: 1; }
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