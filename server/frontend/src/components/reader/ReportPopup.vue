<template>
  <div v-if="visible" class="annotation-popup-overlay" @click.self="$emit('cancel')">
    <div class="annotation-popup">
      <h4>举报内容</h4>
      <div class="popup-selected">「{{ content }}」</div>
      <textarea v-model="reason" placeholder="请描述举报原因（如：涉黄、涉恐、广告、辱骂等）..." rows="4"></textarea>
      <div class="popup-actions">
        <button class="btn-cancel" @click="$emit('cancel')">取消</button>
        <button class="btn-submit" @click="handleSubmit">提交举报</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  content: { type: String, default: '' }
})
const emit = defineEmits(['submit', 'cancel'])

const reason = ref('')

watch(() => props.visible, (val) => {
  if (val) reason.value = ''
})

function handleSubmit() {
  if (!reason.value.trim()) {
    alert('请填写举报原因')
    return
  }
  emit('submit', reason.value.trim())
}
</script>

<style scoped>
.annotation-popup-overlay {
  position: fixed; top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.4);
  display: flex; align-items: center; justify-content: center;
  z-index: 1000;
}
.annotation-popup {
  background: #fffdf7; border-radius: 8px; padding: 24px;
  width: 90%; max-width: 500px;
  box-shadow: 0 10px 40px rgba(0,0,0,0.2);
}
.annotation-popup h4 { font-size: 1.1rem; margin-bottom: 16px; color: #1a1a1a; }
.popup-selected {
  background: #f5f0e8; padding: 12px; border-radius: 4px;
  font-size: 0.9rem; color: #8b7355; margin-bottom: 16px; line-height: 1.6;
}
.annotation-popup textarea {
  width: 100%; padding: 12px; border: 1px solid #e8e0d0; border-radius: 4px;
  font-size: 0.95rem; font-family: inherit; resize: vertical; margin-bottom: 16px;
}
.popup-actions { display: flex; gap: 12px; justify-content: flex-end; }
.btn-cancel, .btn-submit {
  padding: 8px 20px; border-radius: 4px; font-size: 0.9rem; cursor: pointer; border: none;
}
.btn-cancel { background: #f0ebe0; color: #666; }
.btn-cancel:hover { background: #e8e0d0; }
.btn-submit { background: #8b7355; color: white; }
.btn-submit:hover { background: #7a6349; }
</style>
