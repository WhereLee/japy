import request from './request'
import type { ListPage } from './system'

export interface Novel {
  id: number
  title: string
  author: string
  intro: string
  cover: string | null
  category: string
  status: number
  chapterCount: number
  totalChars: number
}

export function adminListNovels(params: { page: number; size: number; keyword?: string }) {
  return request.get<ListPage<Novel>, ListPage<Novel>>('/admin/novel/list', { params })
}

export function uploadNovel(formData: FormData) {
  return request.post<Novel, Novel>('/admin/novel/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function changeNovelStatus(id: number, status: number) {
  return request.put(`/admin/novel/${id}/status`, null, { params: { status } })
}

export function deleteNovel(id: number) {
  return request.delete(`/admin/novel/${id}`)
}
