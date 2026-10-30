import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

request.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      if (res.code === 401) {
        localStorage.removeItem('token')
        window.location.href = '/login'
      }
      return Promise.reject(new Error(res.message))
    }
    return res
  },
  error => {
    const status = error.response?.status
    const msg = error.response?.data?.message
    if (status === 401) {
      // 401 = 没登或登过期，清 token 回登录页
      localStorage.removeItem('token')
      window.location.href = '/login'
    } else if (status === 403) {
      // 403 = 登着但没权限。不能清 token——清了会把"越权访问"变成"被踢下线"，
      // 用户会以为是登录过期，反复重登却始终进不去。
      ElMessage.warning(msg || '无权访问该数据')
      return Promise.reject(error)
    }
    ElMessage.error(msg || error.message || '网络错误')
    return Promise.reject(error)
  }
)

export default request
