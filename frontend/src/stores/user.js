import { defineStore } from 'pinia'
import { login } from '../api/auth'

/**
 * 当前登录用户
 *
 * <p>角色与数据范围都落在这里：登录时服务端返回什么就存什么，
 * 菜单、路由守卫、按钮显隐全部从这里派生，页面代码里不再出现
 * "用户名是不是 admin" 这类判断。</p>
 *
 * <p>持久化到 localStorage：刷新页面不能把角色丢掉，
 * 否则 F5 之后菜单会短暂退回全量再收敛，很难看。</p>
 */
const ROLE_TEXT = {
  ADMIN: '总负责人',
  OPERATOR: '运维人员',
  CUSTOMER: '用电客户'
}

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    username: localStorage.getItem('username') || '',
    nickname: localStorage.getItem('nickname') || '',
    role: localStorage.getItem('role') || '',
    customerId: localStorage.getItem('customerId') || '',
    staffId: localStorage.getItem('staffId') || ''
  }),

  getters: {
    isLoggedIn: (s) => !!s.token,
    isAdmin: (s) => s.role === 'ADMIN',
    isOperator: (s) => s.role === 'OPERATOR',
    isCustomer: (s) => s.role === 'CUSTOMER',
    roleText: (s) => ROLE_TEXT[s.role] || '未知角色',
    /** 首字母头像 */
    initial: (s) => (s.nickname || s.username || '?').charAt(0).toUpperCase()
  },

  actions: {
    async login(loginData) {
      const res = await login(loginData)
      const d = res.data
      this.token = d.token
      this.username = d.username
      this.nickname = d.nickname || d.username
      this.role = d.role
      this.customerId = d.customerId ?? ''
      this.staffId = d.staffId ?? ''

      localStorage.setItem('token', this.token)
      localStorage.setItem('username', this.username)
      localStorage.setItem('nickname', this.nickname)
      localStorage.setItem('role', this.role)
      localStorage.setItem('customerId', this.customerId)
      localStorage.setItem('staffId', this.staffId)
      return res
    },

    logout() {
      this.token = ''
      this.username = ''
      this.nickname = ''
      this.role = ''
      this.customerId = ''
      this.staffId = ''
      ;['token', 'username', 'nickname', 'role', 'customerId', 'staffId']
        .forEach(k => localStorage.removeItem(k))
    }
  }
})
