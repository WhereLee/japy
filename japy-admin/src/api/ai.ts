import request from './request'

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export interface MonitorEvent {
  id: number
  monitorCode: string
  monitorName: string
  status: number
  confidence: number
  summary: string
  evidence: string
  insight: string | null
  rootCause: string | null
  suggestion: string | null
  fingerprint: string
  notified: boolean
  createTime: string
}

export interface AiSuggestion {
  id: number
  targetType: string
  targetId: number
  title: string
  content: string
  status: number
  createTime: string
}

export interface ReportData {
  eventTotal: number
  pendingEvents: number
  pendingSuggestions: number
  feedbackTotal: number
  llmAvailable: boolean
  insightCount: number
}

// 报告
export function getReport() {
  return request.get<ReportData, ReportData>('/ai/report')
}

// 信号
export function runMonitor() {
  return request.post<number, number>('/ai/events/run')
}
export function listEvents(params: { page: number; size: number; status?: number; monitorCode?: string }) {
  return request.get<PageResult<MonitorEvent>, PageResult<MonitorEvent>>('/ai/events', { params })
}
export function confirmEvent(id: number) {
  return request.post(`/ai/events/${id}/confirm`)
}
export function ignoreEvent(id: number) {
  return request.post(`/ai/events/${id}/ignore`)
}

// 建议卡
export function listSuggestions(params: { page: number; size: number; status?: number }) {
  return request.get<PageResult<AiSuggestion>, PageResult<AiSuggestion>>('/ai/suggestions', { params })
}
export function approveSuggestion(id: number) {
  return request.post(`/ai/suggestions/${id}/approve`)
}
export function rejectSuggestion(id: number) {
  return request.post(`/ai/suggestions/${id}/reject`)
}
export function executeSuggestion(id: number) {
  return request.post(`/ai/suggestions/${id}/execute`)
}

// 反馈闭环
export interface FeedbackStats {
  total: number
  positive: number
  negative: number
}
export function getFeedbackStats() {
  return request.get<FeedbackStats, FeedbackStats>('/ai/feedback/stats')
}
export function submitFeedback(data: { targetType: string; targetId: number; rating: number; reasonTag?: string; comment?: string }) {
  return request.post('/ai/feedback', data)
}
export function analyzeInsight() {
  return request.post<any, any>('/ai/insight/analyze')
}
export function listInsights(params: { page: number; size: number }) {
  return request.get<PageResult<any>, PageResult<any>>('/ai/insight/list', { params })
}
export function getFeedbackHint(targetType: string, targetId: number) {
  return request.get('/ai/feedback/hint', { params: { targetType, targetId } })
}
