import { ref } from 'vue'

/** 发动态弹层全局状态：任何页面可打开，发布成功后 Feed 刷新 */
export const publishOpen = ref(false)
export const publishTick = ref(0)

export function openPublish() { publishOpen.value = true }
export function closePublish() { publishOpen.value = false }
/** 发布成功：通知 Feed 刷新 */
export function published() { publishTick.value++ }
