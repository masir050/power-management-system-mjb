import request from '../utils/request'

/**
 * 告警中心接口
 *
 * 注意成功时的返回结构：request.js 的响应拦截器在 code === 200 时返回的是整个
 * { code, message, data }，所以这里 await 之后还要取一层 .data。
 */
export const alertApi = {
  pageList(params) {
    return request.get('/alert/list', { params })
  },
  getById(id) {
    return request.get(`/alert/${id}`)
  },
  create(data) {
    return request.post('/alert', data)
  },
  /** 确认：PENDING → ACKNOWLEDGED */
  acknowledge(id, handlerId) {
    return request.put(`/alert/${id}/acknowledge`, null, { params: { handlerId } })
  },
  /** 处理：→ RESOLVED，handleResult 必填 */
  resolve(id, handlerId, handleResult) {
    return request.put(`/alert/${id}/resolve`, null, { params: { handlerId, handleResult } })
  },
  statistics() {
    return request.get('/alert/statistics')
  },
  groupByFaultType() {
    return request.get('/alert/group/fault-type')
  },
  trend(hours = 24) {
    return request.get('/alert/trend', { params: { hours } })
  },
  recent(limit = 20) {
    return request.get('/alert/recent', { params: { limit } })
  }
}
