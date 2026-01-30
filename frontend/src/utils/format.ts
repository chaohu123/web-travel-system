/**
 * 将后端 ISO 时间字符串 2026-01-30T13:02:09 格式化为 2026-01-30-13:02:09
 */
export function formatDateTime(iso: string | null | undefined): string {
  if (iso == null || iso === '') return ''
  return iso.replace('T', '-').replace(/\.\d{3,}/, '')
}

/** 日期显示：2026-01-30 → 2026年1月30日 */
export function formatDate(dateStr: string | null | undefined): string {
  if (dateStr == null || dateStr === '') return ''
  const d = new Date(dateStr)
  if (Number.isNaN(d.getTime())) return dateStr
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`
}
