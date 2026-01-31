/**
 * 高德地图工具类
 * 用于初始化和操作高德地图
 */

declare global {
  interface Window {
    AMap: any
    _AMapSecurityConfig?: { securityJsCode: string }
  }
}

// 高德地图API Key（请替换为你的实际Key）
// 获取方式：https://console.amap.com/dev/key/app
// 从环境变量读取，如果没有配置则使用默认值
const AMAP_KEY = import.meta.env.VITE_AMAP_KEY || 'f9ad382e7f9340dcde9c1fe8dff00b4d'
// 如果你在高德控制台启用了「安全密钥」，需要配置这个值，否则地图可能不出图
const AMAP_SECURITY_JS_CODE = import.meta.env.VITE_AMAP_SECURITY_JS_CODE || ''

async function waitForAmapReady(maxMs = 8000): Promise<void> {
  const start = Date.now()
  // 等待 AMap 及其 Map 构造器可用（避免脚本 onload 触发但 AMap 尚未挂载完成）
  while (Date.now() - start < maxMs) {
    if (window.AMap && (window.AMap as any).Map) return
    await new Promise<void>((r) => setTimeout(r, 50))
  }
  throw new Error('AMap loaded but not ready (AMap.Map not available)')
}

/**
 * 加载高德地图API
 */
export function loadAmapScript(): Promise<void> {
  return new Promise((resolve, reject) => {
    if (window.AMap && (window.AMap as any).Map) {
      resolve()
      return
    }

    // 从环境变量或全局配置读取Key
    const key = AMAP_KEY || (window as any).__AMAP_KEY__
    
    if (!key || key === 'YOUR_AMAP_KEY') {
      console.warn('高德地图API Key未配置，请在 .env 文件中设置 VITE_AMAP_KEY')
      reject(new Error('AMap API Key not configured'))
      return
    }

    // 如果配置了安全密钥，需在加载脚本前注入全局配置
    if (AMAP_SECURITY_JS_CODE) {
      window._AMapSecurityConfig = { securityJsCode: AMAP_SECURITY_JS_CODE }
    }

    const src = `https://webapi.amap.com/maps?v=2.0&key=${key}&plugin=AMap.Geocoder`

    // 避免重复插入脚本（多页面/多次调用时）
    const existing = document.querySelector<HTMLScriptElement>(`script[data-amap="true"]`)
    if (existing) {
      waitForAmapReady()
        .then(resolve)
        .catch(reject)
      return
    }

    const script = document.createElement('script')
    script.type = 'text/javascript'
    script.async = true
    script.setAttribute('data-amap', 'true')
    // 预加载常用插件（避免出现 AMap.Geocoder is not a constructor）
    // 文档：https://lbs.amap.com/api/javascript-api/guide/abc/plugins
    script.src = src
    script.onerror = () => {
      // 脚本加载失败常见原因：网络问题、API Key 无效、控制台未添加当前域名白名单、浏览器/扩展拦截
      console.warn(
        '[高德地图] 脚本加载失败。请检查：1) 网络是否正常 2) .env 中 VITE_AMAP_KEY 是否正确 3) 高德控制台是否已添加当前访问域名（如 localhost）到 Key 白名单'
      )
      reject(new Error('Failed to load AMap script'))
    }

    script.onload = () => {
      waitForAmapReady()
        .then(resolve)
        .catch(reject)
    }

    document.head.appendChild(script)
  })
}

/**
 * 确保某个插件已加载
 * @param pluginName 例如：'AMap.Geocoder'
 */
export async function ensureAmapPlugin(pluginName: string): Promise<void> {
  if (!window.AMap) {
    await loadAmapScript()
  }
  if (!window.AMap) throw new Error('AMap not loaded')

  // 已存在则直接返回（如 AMap.Geocoder）
  const shortName = pluginName.split('.').pop() || pluginName
  if ((window.AMap as any)[shortName]) return

  // 通过 AMap.plugin 动态加载
  await new Promise<void>((resolve, reject) => {
    let done = false
    const timer = window.setTimeout(() => {
      if (done) return
      done = true
      reject(new Error(`AMap plugin load timeout: ${pluginName}`))
    }, 5000)

    try {
      window.AMap.plugin([pluginName], () => {
        if (done) return
        window.clearTimeout(timer)
        done = true
        resolve()
      })
    } catch (e) {
      if (done) return
      window.clearTimeout(timer)
      done = true
      reject(e as any)
    }
  })
}

/**
 * 初始化高德地图
 * @param container 地图容器DOM元素
 * @param center 中心点坐标 [lng, lat]
 * @param zoom 缩放级别
 */
export function initAmapMap(
  container: HTMLElement,
  center: [number, number] = [116.397428, 39.90923], // 默认北京
  zoom: number = 13,
  opts?: {
    viewMode?: '2D' | '3D'
    mapStyle?: string
    forceTileLayer?: boolean
  }
): any {
  if (!window.AMap) {
    throw new Error('AMap is not loaded. Please call loadAmapScript() first.')
  }

  // 防止重复初始化导致容器黑屏
  container.innerHTML = ''

  const AMapAny = window.AMap as any

  // 某些环境默认底图可能空白；显式挂载 TileLayer 可确保底图瓦片渲染
  const layers =
    opts?.forceTileLayer === false
      ? undefined
      : [
          // 标准底图
          new AMapAny.TileLayer(),
        ]

  return new AMapAny.Map(container, {
    zoom,
    center,
    // 默认用 2D + normal，更稳（部分环境 3D/WebGL 会出现“底图黑屏但 marker 可见”）
    viewMode: opts?.viewMode ?? '2D',
    resizeEnable: true,
    mapStyle: opts?.mapStyle ?? 'amap://styles/normal',
    layers,
    // 显式开启常用要素，避免部分样式/配置下只剩空白底色
    features: ['bg', 'road', 'point', 'building'],
  })
}

/**
 * 添加标记点
 * @param map 地图实例
 * @param position 位置 [lng, lat]
 * @param title 标题
 * @param content 信息窗口内容
 */
export function addMarker(
  map: any,
  position: [number, number],
  title: string,
  content?: string
): any {
  const marker = new window.AMap.Marker({
    position,
    title,
  })

  map.add(marker)

  if (content) {
    const infoWindow = new window.AMap.InfoWindow({
      content: `<div style="padding: 8px;"><b>${title}</b><br/>${content}</div>`,
      offset: new window.AMap.Pixel(0, -30),
    })

    marker.on('click', () => {
      infoWindow.open(map, marker.getPosition())
    })
  }

  return marker
}

/**
 * 添加路线
 * @param map 地图实例
 * @param path 路径点数组 [[lng, lat], ...]
 * @param color 路线颜色
 */
export function addPolyline(
  map: any,
  path: [number, number][],
  color: string = '#22c55e'
): any {
  const polyline = new window.AMap.Polyline({
    path,
    isOutline: true,
    outlineColor: '#fff',
    borderWeight: 2,
    strokeColor: color,
    strokeOpacity: 1,
    strokeWeight: 3,
    lineJoin: 'round',
    lineCap: 'round',
    zIndex: 50,
  })

  map.add(polyline)
  map.setFitView([polyline])

  return polyline
}

/**
 * 地理编码：将地址转换为坐标
 * @param address 地址
 */
export async function geocode(address: string): Promise<[number, number] | null> {
  if (!window.AMap) {
    await loadAmapScript()
  }

  // Geocoder 属于插件，需确保已加载
  await ensureAmapPlugin('AMap.Geocoder')

  return new Promise((resolve) => {
    const GeocoderCtor = (window.AMap as any).Geocoder
    if (!GeocoderCtor) {
      resolve(null)
      return
    }
    const geocoder = new GeocoderCtor()

    let done = false
    const timer = window.setTimeout(() => {
      if (done) return
      done = true
      resolve(null)
    }, 8000)

    geocoder.getLocation(address, (status: string, result: any) => {
      if (done) return
      window.clearTimeout(timer)
      done = true

      if (status === 'complete' && result?.geocodes?.length > 0) {
        const location = result.geocodes[0].location
        const coords: [number, number] = [location.lng, location.lat]
        resolve(coords)
      } else {
        resolve(null)
      }
    })
  })
}

/**
 * 批量地理编码
 * @param addresses 地址数组（结果 Map 的 key 仍然使用这些原始地址）
 * @param options   可选：对实际参与地理编码的地址做转换，例如为海外目的地加上“日本”“英国”等前缀
 */
export async function batchGeocode(
  addresses: string[],
  options?: {
    transform?: (address: string) => string
  }
): Promise<Map<string, [number, number]>> {
  const results = new Map<string, [number, number]>()

  for (const raw of addresses) {
    const target = options?.transform ? options.transform(raw) : raw
    const coords = await geocode(target)
    if (coords) {
      results.set(raw, coords)
    }
    // 避免请求过快，添加小延迟
    await new Promise((resolve) => setTimeout(resolve, 100))
  }

  return results
}
