import request from '../utils/request'

export function createApi(module) {
  return {
    pageList(params) {
      return request.get(`/${module}/list`, { params })
    },
    getById(id) {
      return request.get(`/${module}/${id}`)
    },
    create(data) {
      return request.post(`/${module}`, data)
    },
    update(id, data) {
      return request.put(`/${module}/${id}`, data)
    },
    delete(id) {
      return request.delete(`/${module}/${id}`)
    }
  }
}

export const powerAreaApi = createApi('power-area')
export const substationApi = createApi('substation')
export const equipmentApi = createApi('equipment')
export const customerApi = createApi('customer')
export const meterApi = createApi('meter')
export const billApi = createApi('bill')
export const priceApi = createApi('price')
export const maintenanceApi = createApi('maintenance')
export const staffApi = createApi('staff')

// 告警中心接口字段与通用 CRUD 不同（acknowledge/resolve 是 PUT + 查询参数），单独定义
export { alertApi } from './alert'
