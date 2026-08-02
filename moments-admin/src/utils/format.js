/** 数字千分位格式化 */
export function fmtNum(n) {
  if (n === null || n === undefined || n === '') return '-'
  return Number(n).toLocaleString('zh-CN')
}

/** 时间格式化：LocalDateTime 字符串 → yyyy-MM-dd HH:mm:ss */
export function fmtTime(t) {
  if (!t) return '-'
  // 后端 LocalDateTime 序列化为 "2026-08-03T01:32:06.126306"
  const s = String(t).replace('T', ' ')
  const m = s.match(/^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})/)
  return m ? m[1] : s.slice(0, 19)
}
