import axios from 'axios'

/**
 * 规程问答（power-rag）专用实例。
 * 不走 utils/request：那个按 {code,message,data} 解包，而 power-rag 回的是原生 JSON。
 * 路径 /api/rag/** 由网关 StripPrefix 后转发，鉴权仍走网关。
 */
const ragRequest = axios.create({
  baseURL: '/api/rag',
  timeout: 60000 // 研判要连调 4~6 次工具，10 秒级
})

ragRequest.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/** 单轮规程问答：返回 { answer, sources } */
export function askRag(q) {
  return ragRequest.get('/ask', { params: { q } }).then(res => res.data)
}

/** 告警研判：返回 { answer, steps, trace } */
export function diagnoseAlarm(alarm) {
  return ragRequest.get('/diagnose', { params: { alarm } }).then(res => res.data)
}

/** 知识库规模：返回 { blocks, sources: [{ source, blocks }], ... 缓存与 token 指标 } */
export function ragStats() {
  return ragRequest.get('/stats').then(res => res.data)
}
