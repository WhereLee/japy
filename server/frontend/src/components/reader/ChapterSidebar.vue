<template>
  <aside class="chapter-sidebar" :class="{ open: modelValue }">
    <div class="sidebar-toggle" @click="$emit('update:modelValue', !modelValue)">
      {{ modelValue ? '✕' : '目录' }}
    </div>
    <div class="chapter-list">
      <div
        v-for="(ch, idx) in chapters"
        :key="ch.id"
        class="chapter-item"
        :class="{ active: currentChapterId && ch.id === currentChapterId }"
        @click="$emit('select', ch.id, idx)"
      >
        {{ ch.title }}
      </div>
    </div>
  </aside>
</template>

<script setup>
defineProps({
  modelValue: { type: Boolean, default: false },
  chapters: { type: Array, default: () => [] },
  currentChapterId: { type: Number, default: null }
})
defineEmits(['update:modelValue', 'select'])
</script>

<style scoped>
.chapter-sidebar {
  width: 240px;
  flex-shrink: 0;
  background: #faf6ee;
  border-right: 1px solid #e8e0d0;
  overflow-y: auto;
  height: calc(100vh - 53px);
  position: sticky;
  top: 53px;
}
.sidebar-toggle { display: none; }
.chapter-list { padding: 16px 0; }
.chapter-item {
  padding: 10px 20px;
  font-size: 0.88rem;
  color: #666;
  cursor: pointer;
  border-left: 3px solid transparent;
  transition: all 0.15s;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.chapter-item:hover { background: #f0ebe0; color: #333; }
.chapter-item.active {
  color: #1a1a1a;
  font-weight: 600;
  border-left-color: #8b7355;
  background: #f0ebe0;
}
@media (max-width: 768px) {
  .chapter-sidebar {
    position: fixed;
    left: -240px;
    top: 53px;
    z-index: 50;
    height: calc(100vh - 53px);
    transition: left 0.3s;
  }
  .chapter-sidebar.open { left: 0; }
  .sidebar-toggle {
    display: flex;
    position: absolute;
    right: -36px;
    top: 12px;
    width: 36px;
    height: 36px;
    background: #faf6ee;
    border: 1px solid #e8e0d0;
    border-radius: 0 6px 6px 0;
    align-items: center;
    justify-content: center;
    font-size: 0.8rem;
    cursor: pointer;
    color: #888;
  }
}
</style>
