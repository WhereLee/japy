import request from './request'

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

export interface Chapter {
  id: number
  novelId: number
  chapterNo: number
  title: string
  chars: number
  paragraphCount: number
}

export interface ChapterContent {
  id: number
  novelId: number
  chapterNo: number
  title: string
  chars: number
  paragraphs: string[]
  prevChapterId: number | null
  nextChapterId: number | null
}

export interface ReadProgress {
  id: number
  userId: number
  novelId: number
  chapterId: number
  charOffset: number
  percent: number
}

export interface ListPage<T> {
  list: T[]
  total: number
  page: number
  size: number
}

export function listNovels(params: { page: number; size: number; keyword?: string; category?: string }) {
  return request.get<ListPage<Novel>, ListPage<Novel>>('/novel/list', { params })
}

export function novelDetail(id: number) {
  return request.get<Novel, Novel>(`/novel/${id}`)
}

export function listChapters(novelId: number, params: { page: number; size: number }) {
  return request.get<ListPage<Chapter>, ListPage<Chapter>>(`/novel/${novelId}/chapters`, { params })
}

export function chapterContent(chapterId: number) {
  return request.get<ChapterContent, ChapterContent>(`/novel/chapter/${chapterId}`)
}

export function getProgress(novelId: number) {
  return request.get<ReadProgress, ReadProgress>(`/novel/${novelId}/progress`)
}

export function saveProgress(novelId: number, data: { chapterId: number; charOffset: number; percent: number }) {
  return request.put(`/novel/${novelId}/progress`, data)
}
