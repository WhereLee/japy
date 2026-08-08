import request from './request'

export function ragStatus(novelId?: number) {
  return request.get<any, any>('/admin/rag/status', { params: novelId ? { novelId } : {} })
}

export function ragSync(novelId?: number) {
  return request.post<any, any>('/admin/rag/sync', { novel_id: novelId })
}
export function ragSyncStatus(novelId?: number) {
  return request.get<any, any>('/admin/rag/sync/status', { params: novelId ? { novelId } : {} })
}

export function ragHealth() {
  return request.get<boolean, boolean>('/admin/rag/health')
}
