/**
 * 告警 WebSocket 客户端
 *
 * 后端链路：alert-service 落库 → 本地消息表 → RabbitMQ → 消费者 → WebSocket 广播。
 * 前端只管"连上、收、断线重连"，不轮询。
 *
 * 为什么 URL 上要带 token：
 *   浏览器的 WebSocket 构造函数不能自定义请求头，带不了 Authorization，
 *   所以 token 只能放在查询串里，由服务端 AlertWebSocketEndpoint#verifyToken 校验。
 */
import { ElNotification } from 'element-plus'

/** 开发环境走 Vite 代理（/ws → 网关 8080），生产同源部署直接连当前 host */
function buildUrl(token) {
  const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${proto}//${window.location.host}/ws/alert?token=${encodeURIComponent(token)}`
}

const LEVEL_TEXT = {
  CRITICAL: '紧急',
  MAJOR: '重要',
  MINOR: '次要',
  WARNING: '警告'
}

let socket = null
/** 重连定时器：必须保存，否则组件卸载后仍然在后台重连 */
let reconnectTimer = null
/** 重连退避：1s 起步，翻倍到 30s 封顶，防止服务端挂掉时前端狂刷握手 */
let retryDelay = 1000
/** 主动关闭标记：登出/卸载时置 true，避免 onclose 触发无意义重连 */
let destroyed = false
/** 连接状态订阅者（页面上的"实时通道已连接"指示用） */
const listeners = new Set()

export function onSocketState(callback) {
  listeners.add(callback)
  callback(!!socket && socket.readyState === WebSocket.OPEN)
  return () => listeners.delete(callback)
}

function emit(connected) {
  listeners.forEach(fn => fn(connected))
}

function connect() {
  const token = localStorage.getItem('token')
  if (!token) return

  socket = new WebSocket(buildUrl(token))

  socket.onopen = () => {
    // 连上后重置退避，下次断线仍从 1s 开始
    retryDelay = 1000
    emit(true)
  }

  socket.onmessage = event => {
    let data
    try {
      data = JSON.parse(event.data)
    } catch {
      return
    }
    // CONNECTED / PONG 是心跳与握手确认，不弹通知
    if (data.type === 'CONNECTED' || data.type === 'PONG') return
    if (data.type !== 'ALERT') return

    const level = LEVEL_TEXT[data.priority] || data.priority || '告警'
    ElNotification({
      title: data.title || `${level}告警`,
      message: data.faultDescription || `${data.equipmentName || '未知设备'}发生${data.faultType || ''}`,
      type: data.priority === 'CRITICAL' ? 'error' : 'warning',
      duration: 8000,
      position: 'bottom-right'
    })
  }

  socket.onclose = () => {
    socket = null
    emit(false)
    if (destroyed) return
    reconnectTimer = setTimeout(connect, retryDelay)
    retryDelay = Math.min(retryDelay * 2, 30000)
  }

  // onerror 之后浏览器一定会触发 onclose，重连逻辑只写在 onclose 里，避免两处重复建连
  socket.onerror = () => {}
}

/** 心跳：25s 发一次 ping，防止中间代理按空闲超时掐断连接 */
let heartbeatTimer = null

export function startAlertSocket() {
  destroyed = false
  if (socket) return
  connect()
  clearInterval(heartbeatTimer)
  heartbeatTimer = setInterval(() => {
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send('ping')
    }
  }, 25000)
}

export function stopAlertSocket() {
  destroyed = true
  clearTimeout(reconnectTimer)
  clearInterval(heartbeatTimer)
  reconnectTimer = null
  heartbeatTimer = null
  if (socket) {
    socket.close()
    socket = null
  }
  emit(false)
}
