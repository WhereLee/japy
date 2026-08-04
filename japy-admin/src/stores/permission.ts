import { defineStore } from 'pinia'
import { getRouters } from '@/api/auth'
import type { RouterItem } from '@/api/auth'
import Layout from '@/layouts/index.vue'

// 组件映射（若依模式：后端下发 component 字符串，前端 import.meta.glob 懒加载）
const viewModules = import.meta.glob('@/views/**/*.vue')

function loadView(component: string | null | undefined) {
  if (!component) return undefined
  if (component === 'Layout') return Layout
  const key = `/src/views/${component}.vue`
  const loader = viewModules[key]
  return loader || undefined
}

export const usePermissionStore = defineStore('permission', {
  state: () => ({
    /** 后端原始菜单树（用于侧边栏渲染） */
    menuTree: [] as RouterItem[],
    /** 转换后的路由表（用于 router.addRoute） */
    routes: [] as any[],
    loaded: false
  }),
  actions: {
    async generateRoutes() {
      const routers = await getRouters()
      this.menuTree = routers
      this.routes = convert(routers)
      this.loaded = true
      return this.routes
    },
    reset() {
      this.menuTree = []
      this.routes = []
      this.loaded = false
    }
  }
})

/** 后端 RouterVo → 前端路由对象 */
function convert(list: RouterItem[]): any[] {
  return list.map((item) => {
    const children = item.children && item.children.length ? convert(item.children) : []
    const route: any = {
      path: item.path,
      name: item.name,
      meta: { title: item.meta?.title, icon: item.meta?.icon }
    }
    const comp = loadView(item.component)
    if (item.component === 'Layout' && children.length) {
      route.component = comp
      route.redirect = item.redirect || join(item.path, children[0]?.path)
      route.children = children
    } else if (children.length) {
      route.component = Layout
      route.redirect = item.redirect || join(item.path, children[0]?.path)
      route.children = children.map((c: any) => ({ ...c, path: c.path }))
    } else if (comp && comp !== Layout) {
      // 单页顶级菜单：包 Layout 保证侧边栏存在（若依模式）
      route.component = Layout
      route.redirect = item.redirect
      route.children = [{ path: '', component: comp, meta: route.meta }]
    } else {
      route.component = comp || Layout
    }
    return route
  })
}

function join(parent: string, child?: string) {
  if (!child) return parent
  if (child.startsWith('/')) return child
  return `${parent.replace(/\/$/, '')}/${child}`
}
