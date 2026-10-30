/**
 * 统一的时间展示格式。
 *
 * 后端 Java LocalDateTime 经 Jackson 默认序列化成 "2025-05-12T07:00:00"，
 * 直接把字符串丢进表格会露出中间的 T，这里统一裁成 "2025-05-12 07:00:00"。
 * 不做时区换算（后端返回的就是本地时间，没有时区信息），只处理字符串形态。
 */

function pad(n) {
  return String(n).padStart(2, '0')
}

/** 补零：2025-5-1 7:0:0 -> 2025-05-01 07:00:00 */
function normalize(s) {
  return s
    .replace('T', ' ')
    .replace(/\.\d+$/, '')          // 去掉毫秒
    .replace(/(\d{4})-(\d{1,2})-(\d{1,2})/, (_, y, m, d) => `${y}-${pad(m)}-${pad(d)}`)
    .replace(/ (\d{1,2}):(\d{1,2})(?::(\d{1,2}))?/, (_, h, mi, se) => {
      return ` ${pad(h)}:${pad(mi)}:${pad(se ?? 0)}`
    })
}

/** 2025-05-12T07:00:00 -> 2025-05-12 07:00:00 */
export function formatDateTime(v) {
  if (v === null || v === undefined || v === '') return ''
  if (typeof v === 'string') return normalize(v)
  const d = v instanceof Date ? v : new Date(v)
  if (Number.isNaN(d.getTime())) return String(v)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/** 2025-05-12T07:00:00 -> 2025-05-12 */
export function formatDate(v) {
  if (v === null || v === undefined || v === '') return ''
  const s = formatDateTime(v)
  return s.length >= 10 ? s.slice(0, 10) : s
}

/** 表格里用：日期型字段统一过一遍 */
export const dateColumns = [
  'occurrenceTime', 'installDate', 'manufactureDate', 'paymentDeadline',
  'effectiveDate', 'generateTime', 'meterReadingTime', 'bindDate',
  'createTime', 'updateTime', 'repairTime'
]
