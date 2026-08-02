/** 相对时间：刚刚 / N分钟前 / N小时前 / 昨天 / M月D日 */
export function timeAgo(t) {
  if (!t) return ''
  // 手动解析（兼容 Safari，避免 new Date("2026-08-03 01:32:06.126") 返回 NaN）
  const m = String(t).match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})/)
  if (!m) return String(t).slice(5, 16).replace('-', '/')
  const d = new Date(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], +m[6])
  if (isNaN(d.getTime())) return String(t).slice(5, 16)
  const diff = (Date.now() - d.getTime()) / 1000
  if (diff < 60) return '刚刚'
  if (diff < 3600) return `${Math.floor(diff / 60)}分钟前`
  if (diff < 86400) return `${Math.floor(diff / 3600)}小时前`
  if (diff < 172800) return '昨天'
  const now = new Date()
  const sameYear = d.getFullYear() === now.getFullYear()
  const pad = n => String(n).padStart(2, '0')
  return sameYear ? `${d.getMonth() + 1}月${d.getDate()}日` : `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** 完整时间 */
export function fullTime(t) {
  if (!t) return ''
  const s = String(t).replace('T', ' ')
  const m = s.match(/^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})/)
  return m ? m[1] : s.slice(0, 19)
}

/** 数字格式化 */
export function fmtNum(n) {
  if (n === null || n === undefined) return 0
  return Number(n) >= 10000 ? (Number(n) / 10000).toFixed(1).replace(/\.0$/, '') + '万' : String(n)
}

/** 头像字符 */
export function avatarChar(name) {
  return (name || '?').charAt(0)
}

/** 弹层/轻提示 */
export function toast(msg, type = 'info') {
  const el = document.createElement('div')
  el.className = `toast toast-${type}`
  el.textContent = msg
  document.body.appendChild(el)
  setTimeout(() => el.classList.add('show'), 10)
  setTimeout(() => {
    el.classList.remove('show')
    setTimeout(() => el.remove(), 300)
  }, 2400)
}
