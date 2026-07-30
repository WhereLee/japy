<template>
  <div v-if="visible" class="annotation-popup-overlay" @click.self="$emit('cancel')">
    <div class="annotation-popup">
      <h4>写批注</h4>
      <div class="popup-selected">「{{ selectedText }}」</div>
      <div class="type-toggle">
        <button :class="{ active: localType === 0 }" @click="localType = 0">普通批注</button>
        <button :class="{ active: localType === 1 }" @click="localType = 1">⚠ 数据校验</button>
      </div>
      <textarea v-model="localContent" :placeholder="localType === 1 ? '描述数据问题（乱码、广告、内容错误等）...' : '写下你的批注...'" rows="4"></textarea>
      <div class="popup-actions">
        <button class="btn-cancel" @click="$emit('cancel')">取消</button>
        <button class="btn-submit" :class="{ 'btn-validate': localType === 1 }" @click="handleSubmit">提交</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  selectedText: { type: String, default: '' },
  type: { type: Number, default: 0 }
})
const emit = defineEmits(['submit', 'cancel'])

const localContent = ref('')
const localType = ref(0)

watch(() => props.visible, (val) => {
  if (val) {
    localContent.value = ''
    localType.value = props.type
  }
})

function handleSubmit() {
  if (!localContent.value.trim()) return
  emit('submit', { content: localContent.value, type: localType.value })
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
.type-toggle {
  display: flex; gap: 0; margin-bottom: 12px;
  border: 1px solid #e8e0d0; border-radius: 6px; overflow: hidden;
}
.type-toggle button {
  flex: 1; padding: 8px 0; border: none; background: transparent;
  font-size: 0.85rem; cursor: pointer; color: #888; transition: all 0.15s;
}
.type-toggle button:first-child { border-right: 1px solid #e8e0d0; }
.type-toggle button.active { background: #f5f0e8; color: #333; font-weight: 600; }
.type-toggle button:last-child.active { background: #fff3e0; color: #e65100; }
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
.btn-validate { background: #e65100 !important; }
.btn-validate:hover { background: #bf360c !important; }
</style>
