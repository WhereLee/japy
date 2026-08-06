import request from './request'
import type { ListPage } from './system'

export interface AuditRecord {
  id: number
  novelId: number
  auditType: string
  ruleHits: string
  result: string
  auditorId: number | null
  auditTime: string | null
  remark: string | null
  createTime: string
}

export interface RuleHit {
  word: string
  count: number
  category: string
}

export function listAudits(params: { page: number; size: number; result?: string }) {
  return request.get<ListPage<AuditRecord>, ListPage<AuditRecord>>('/audit/list', { params })
}

export function pendingCount() {
  return request.get<number, number>('/audit/pending-count')
}

export function auditPass(id: number, remark?: string) {
  return request.post(`/audit/${id}/pass`, { remark })
}

export function auditTakedown(id: number, remark?: string) {
  return request.post(`/audit/${id}/takedown`, { remark })
}

export function rescan(novelId: number) {
  return request.post<number, number>(`/audit/${novelId}/rescan`)
}

export function parseHits(json: string): RuleHit[] {
  try {
    return JSON.parse(json || '[]')
  } catch {
    return []
  }
}
