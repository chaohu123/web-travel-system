/**
 * 收藏景点时保存当前页面的展示信息（名称、地址、封面、坐标），
 * 避免同一 ID 因 query 不同显示不同名称时，在私信「发送景点」里显示错误。
 */
const KEY = 'travel_match_spot_fav_display'

export interface SpotFavoriteDisplay {
  name: string
  location: string
  imageUrl?: string
  lng?: number
  lat?: number
}

function getAll(): Record<number, SpotFavoriteDisplay> {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return {}
    const data = JSON.parse(raw)
    return typeof data === 'object' && data !== null ? data : {}
  } catch {
    return {}
  }
}

function setAll(data: Record<number, SpotFavoriteDisplay>) {
  try {
    localStorage.setItem(KEY, JSON.stringify(data))
  } catch {
    // ignore
  }
}

export function getSpotFavoriteDisplay(spotId: number): SpotFavoriteDisplay | null {
  const all = getAll()
  return all[spotId] ?? null
}

export function setSpotFavoriteDisplay(spotId: number, data: SpotFavoriteDisplay) {
  const all = getAll()
  all[spotId] = data
  setAll(all)
}

export function removeSpotFavoriteDisplay(spotId: number) {
  const all = getAll()
  delete all[spotId]
  setAll(all)
}
