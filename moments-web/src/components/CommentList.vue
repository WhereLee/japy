<template>
  <div class="comment-box">
    <!-- 发表评论 -->
    <div v-if="auth.isLogin" class="c-input-row">
      <span class="avatar avatar-xs">{{ avatarChar(auth.nickname) }}</span>
      <input
        v-model="draft" class="input c-input" :placeholder="replying ? `回复 ${replying.nickname}…` : '写下你的评论…'"
        maxlength="500" @keyup.enter="submit"
      />
      <button class="btn accent btn-xs" :disabled="!draft.trim()" @click="submit">发送</button>
    </div>
    <div v-else class="c-login-tip">
      <router-link to="/login" class="link">登录</router-link> 后参与评论
    </div>

    <!-- 评论列表 -->
    <div v-if="comments.length" class="c-list">
      <div v-for="c in comments" :key="c.id" class="c-item">
        <span class="avatar avatar-xs">{{ avatarChar(c.nickname) }}</span>
        <div class="c-body">
          <div class="c-line">
            <router-link :to="`/user/${c.userId}`" class="c-nick">{{ c.nickname }}</router-link>
            <span class="c-time">{{ timeAgo(c.createdAt) }}</span>
          </div>
          <div class="c-text">{{ c.content }}</div>
          <div class="c-sub">
            <button v-if="auth.isLogin" class="c-act" @click="startReply(c)">回复</button>
            <button v-if="auth.isLogin && c.userId === auth.userId" class="c-act danger" @click="delComment(c)">删除</button>
          </div>
          <!-- 楼中楼 -->
          <div v-if="c.replies && c.replies.length" class="c-replies">
            <div v-for="r in c.replies" :key="r.id" class="c-reply">
              <router-link :to="`/user/${r.userId}`" class="c-nick">{{ r.nickname }}</router-link>
              <span v-if="r.replyTo" class="c-to">回复 {{ r.replyTo }}</span>
              <span class="c-reply-text">{{ r.content }}</span>
              <button v-if="auth.isLogin && r.userId === auth.userId" class="c-act danger" @click="delComment(r)">删除</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="total > comments.length" class="c-more">
      <button class="link-btn" @click="loadMore">查看更多评论（{{ total - comments.length }}）</button>
    </div>
    <div v-else-if="loaded && comments.length === 0" class="c-empty">还没有评论，来抢沙发～</div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import http from '../api'
import { useAuthStore } from '../stores/auth'
import { timeAgo, avatarChar, toast } from '../utils/format'

const props = defineProps({
  momentId: { type: Number, required: true },
  authorId: { type: Number, required: true }
})
const emit = defineEmits(['comment-count'])

const auth = useAuthStore()
const comments = ref([])
const total = ref(0)
const page = ref(1)
const loaded = ref(false)
const loading = ref(false)
const draft = ref('')
const replying = ref(null)

async function load(reset = true) {
  if (loading.value) return
  loading.value = true
  try {
    const data = await http.get('/api/comments', {
      params: { momentId: props.momentId, page: reset ? 1 : page.value, size: 20, replySize: 20 }
    })
    if (reset) {
      comments.value = data.list
      page.value = 1
    } else {
      comments.value = comments.value.concat(data.list)
    }
    total.value = data.total
    loaded.value = true
    emit('comment-count', data.total)
  } catch (e) {
    toast(e.response?.data?.msg || '评论加载失败', 'error')
  } finally {
    loading.value = false
  }
}
function loadMore() { page.value += 1; load(false) }

function startReply(c) { replying.value = c; draft.value = '' }

async function submit() {
  const text = draft.value.trim()
  if (!text) return
  const body = { momentId: props.momentId, content: text }
  if (replying.value) {
    body.parentId = replying.value.id
    body.replyTo = replying.value.nickname
  }
  try {
    await http.post('/api/comments', body)
    draft.value = ''
    replying.value = null
    toast('评论成功')
    await load(true)
  } catch (e) {
    toast(e.response?.data?.msg || e.message || '评论失败', 'error')
  }
}

async function delComment(c) {
  if (!confirm('确定删除这条评论吗？')) return
  try {
    await http.delete(`/api/comments/${c.id}`)
    toast('已删除')
    await load(true)
  } catch (e) {
    toast(e.response?.data?.msg || '删除失败', 'error')
  }
}

onMounted(() => load(true))
</script>

<style scoped>
.comment-box { padding-top: 10px; }
.c-input-row { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.avatar-xs { width: 28px; height: 28px; font-size: 12px; }
.c-input { flex: 1; padding: 7px 12px; font-size: 13px; border-radius: 999px; }
.btn-xs { padding: 5px 14px; font-size: 12px; }
.c-login-tip { font-size: 12px; color: var(--text-3); padding: 8px 0; }
.link { color: var(--accent); }
.c-item { display: flex; gap: 10px; padding: 10px 0; }
.c-body { flex: 1; min-width: 0; }
.c-line { display: flex; align-items: baseline; gap: 8px; }
.c-nick { font-size: 13px; font-weight: 600; color: var(--text); }
.c-time { font-size: 11px; color: var(--text-3); }
.c-text { font-size: 14px; margin-top: 2px; word-break: break-word; }
.c-sub { display: flex; gap: 10px; margin-top: 4px; }
.c-act {
  border: none; background: none;
  font-size: 12px; color: var(--text-3);
  cursor: pointer; padding: 0;
}
.c-act:hover { color: var(--accent); }
.c-act.danger:hover { color: var(--danger); }
.c-replies {
  margin-top: 6px;
  background: var(--card);
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  padding: 6px 10px;
}
.c-reply { font-size: 13px; padding: 3px 0; word-break: break-word; }
.c-to { color: var(--text-3); font-size: 12px; }
.c-reply-text { margin-left: 4px; }
.c-more { text-align: center; padding: 8px 0; }
.link-btn {
  border: none; background: none;
  color: var(--accent); font-size: 12px; cursor: pointer;
}
.c-empty { text-align: center; color: var(--text-3); font-size: 12px; padding: 10px 0; }
</style>
