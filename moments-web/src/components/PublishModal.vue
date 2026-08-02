<template>
  <teleport to="body">
    <div v-if="publishOpen" class="modal-mask" @click.self="close">
      <div class="modal publish-modal">
        <div class="modal-head">
          <h3>发布动态</h3>
          <button class="modal-close" @click="close">✕</button>
        </div>
        <div class="modal-body">
          <div class="publish-user">
            <span class="avatar avatar-sm">{{ avatarChar(auth.nickname) }}</span>
            <span class="publish-name">{{ auth.nickname }}</span>
          </div>
          <textarea
            ref="ta" v-model="content" class="input publish-input"
            :maxlength="2000" placeholder="分享此刻的想法……"
            @keydown.meta.enter="submit" @keydown.ctrl.enter="submit"
          ></textarea>
          <div class="publish-novel">
            <select v-model="novelId" class="input novel-select">
              <option :value="null">不关联小说</option>
              <option v-for="n in novels" :key="n.id" :value="n.id">{{ n.title }}</option>
            </select>
          </div>
          <div class="publish-count" :class="{ over: content.length >= 2000 }">
            {{ content.length }} / 2000
          </div>
        </div>
        <div class="modal-foot">
          <button class="btn" @click="close">取消</button>
          <button class="btn accent" :disabled="sending || !content.trim()" @click="submit">
            {{ sending ? '发布中…' : '发布' }}
          </button>
        </div>
      </div>
    </div>
  </teleport>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import http from '../api'
import { useAuthStore } from '../stores/auth'
import { publishOpen, closePublish, published } from '../stores/publish'
import { avatarChar, toast } from '../utils/format'

const auth = useAuthStore()
const content = ref('')
const novelId = ref(null)
const novels = ref([])
const sending = ref(false)
const ta = ref(null)

watch(publishOpen, async open => {
  if (open) {
    content.value = ''
    novelId.value = null
    await nextTick()
    ta.value?.focus()
    if (novels.value.length === 0) {
      try {
        const data = await http.get('/api/novels?page=1&size=50')
        novels.value = data.list
      } catch { /* 忽略 */ }
    }
  }
})

function close() { closePublish() }

async function submit() {
  const text = content.value.trim()
  if (!text) return
  sending.value = true
  try {
    const body = { content: text }
    if (novelId.value) body.novelId = novelId.value
    await http.post('/api/moments', body)
    toast('发布成功')
    close()
    published()
  } catch (e) {
    toast(e.response?.data?.msg || e.message || '发布失败', 'error')
  } finally {
    sending.value = false
  }
}
</script>

<style scoped>
.publish-modal { max-width: 520px; }
.publish-user { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; }
.avatar-sm { width: 34px; height: 34px; font-size: 15px; }
.publish-name { font-size: 14px; font-weight: 600; }
.publish-input { min-height: 140px; }
.publish-novel { margin-top: 10px; }
.novel-select { max-width: 240px; padding: 7px 10px; font-size: 13px; }
.publish-count {
  text-align: right; font-size: 12px; color: var(--text-3);
  margin-top: 6px;
}
.publish-count.over { color: var(--danger); }
</style>
