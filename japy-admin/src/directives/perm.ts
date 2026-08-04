import type { Directive } from 'vue'
import { useUserStore } from '@/stores/user'

/** 按钮级权限指令：v-perm="'ai:event:run'"，无权限则移除元素 */
export const permDirective: Directive<HTMLElement, string> = {
  mounted(el, binding) {
    const userStore = useUserStore()
    const perm = binding.value
    if (perm && !userStore.hasPerm(perm)) {
      el.parentNode?.removeChild(el)
    }
  }
}
