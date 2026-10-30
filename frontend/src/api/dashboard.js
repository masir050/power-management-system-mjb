import request from '../utils/request'

/**
 * 客户自助 / 用电数据 / 总览看板接口
 *
 * <p>注意这些接口<b>都不传 customerId</b>：看谁的数据由服务端按登录身份决定。
 * 这也是这套权限设计的关键——前端就算被改也没法"指定看别人"。</p>
 */
export const usageApi = {
  /** 日用电曲线（自己 or 全站，取决于登录身份） */
  daily(days = 30) {
    return request.get('/usage/daily', { params: { days } })
  },
  /** 分表明细 */
  meterDaily(meterId, days = 30) {
    return request.get('/usage/meter-daily', { params: { meterId, days } })
  },
  /** 近 N 天汇总 */
  summary(days = 30) {
    return request.get('/usage/summary', { params: { days } })
  },
  /** 尖峰平谷构成 */
  touSplit(days = 30) {
    return request.get('/usage/tou-split', { params: { days } })
  },
  /** 月度趋势 */
  monthly(months = 6) {
    return request.get('/usage/monthly', { params: { months } })
  }
}

export const dashboardApi = {
  /** 当前身份对应的视图配置（标题、显示哪些区块） */
  profile() {
    return request.get('/dashboard/profile')
  },
  kpi() {
    return request.get('/dashboard/kpi')
  },
  alerts() {
    return request.get('/dashboard/alerts')
  },
  /** 日用电曲线（真实数据） */
  loadChart(days = 30) {
    return request.get('/dashboard/load-chart', { params: { days } })
  },
  /** 尖峰平谷构成（真实数据，替代原来的功率因数模拟曲线） */
  touChart(days = 30) {
    return request.get('/dashboard/tou-chart', { params: { days } })
  },
  /** 设备危害等级分布 */
  equipmentHealth() {
    return request.get('/dashboard/equipment-health')
  }
}

/** 我的账单：走通用分页接口，服务端按身份过滤 */
export const myBillApi = {
  page(params) {
    return request.get('/bill/list', { params })
  },
  detail(id) {
    return request.get('/bill/vo/' + id)
  },
  statistics() {
    return request.get('/bill/statistics')
  },
  /**
   * 缴费
   * 金额不传表示一次结清。服务端会再校验一次：不能超欠费、不能缴已结清的账单。
   */
  pay(id, payload) {
    return request.post(`/bill/${id}/pay`, payload)
  },
  /** 某张账单的缴费流水 */
  payments(id) {
    return request.get(`/bill/${id}/payments`)
  },
  /** 最近几笔缴费（客户首页/账单页用） */
  recentPayments(limit = 5) {
    return request.get('/bill/payments/recent', { params: { limit } })
  }
}
