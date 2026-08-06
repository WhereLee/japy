import request from './request'

export interface RagSource {
  chunk_id: number
  chapter_no: number
  content_preview: string
  score: number
}

export interface RagAnswer {
  answer: string
  sources: RagSource[]
  meta: Record<string, any>
}

export function ragAsk(novelId: number, question: string) {
  return request.post<RagAnswer, RagAnswer>('/rag/ask', { novelId, question })
}
