import { createRouter, createWebHistory } from 'vue-router'

/**
 * 角色可见范围
 *
 * <ul>
 *   <li>ADMIN 总负责人 —— 全部</li>
 *   <li>OPERATOR 运维人员 —— 设备台账、告警、检修、人员；<b>不碰账单与客户隐私</b></li>
 *   <li>CUSTOMER 用电客户 —— 只看自己的用电与账单</li>
 * </ul>
 *
 * <p>这里的 roles 只决定"菜单显不显示、路由拦不拦"。
 * 真正的数据边界在后端 SQL 里（见 DataScope），
 * 前端拦不住的人直接把接口地址敲进浏览器也没用。</p>
 */
const A = ['ADMIN']
const AO = ['ADMIN', 'OPERATOR']
const ALL = ['ADMIN', 'OPERATOR', 'CUSTOMER']

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/login/Login.vue'),
    meta: { requiresAuth: false, title: '登录' }
  },
  {
    path: '/',
    component: () => import('../views/layout/Layout.vue'),
    meta: { requiresAuth: true },
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('../views/dashboard/Dashboard.vue'), meta: { title: '总览', roles: ALL } },

      // —— 客户自助（只有客户看，管理员排障时可进） ——
      { path: 'my-usage', name: 'MyUsage', component: () => import('../views/self/MyUsage.vue'), meta: { title: '我的用电', roles: ['CUSTOMER', 'ADMIN'] } },
      { path: 'my-bills', name: 'MyBills', component: () => import('../views/self/MyBills.vue'), meta: { title: '我的账单', roles: ['CUSTOMER', 'ADMIN'] } },

      // —— 资产台账：运维域 ——
      { path: 'power-area', name: 'PowerArea', component: () => import('../views/powerArea/PowerArea.vue'), meta: { title: '供电区域管理', roles: AO } },
      { path: 'substation', name: 'Substation', component: () => import('../views/substation/Substation.vue'), meta: { title: '变电站管理', roles: AO } },
      { path: 'equipment', name: 'Equipment', component: () => import('../views/equipment/Equipment.vue'), meta: { title: '电力设备管理', roles: AO } },
      { path: 'meter', name: 'Meter', component: () => import('../views/meter/Meter.vue'), meta: { title: '智能电表管理', roles: AO } },

      // —— 用电计费：账务域。运维人员看不到客户电费，这是隐私边界不是权限洁癖 ——
      { path: 'customer', name: 'Customer', component: () => import('../views/customer/Customer.vue'), meta: { title: '用电客户管理', roles: A } },
      { path: 'bill', name: 'Bill', component: () => import('../views/bill/Bill.vue'), meta: { title: '电费账单管理', roles: A } },
      { path: 'price', name: 'Price', component: () => import('../views/price/Price.vue'), meta: { title: '电价标准管理', roles: A } },

      // —— 运维域 ——
      { path: 'alert', name: 'Alert', component: () => import('../views/alert/Alert.vue'), meta: { title: '告警中心', roles: AO } },
      { path: 'maintenance', name: 'Maintenance', component: () => import('../views/maintenance/Maintenance.vue'), meta: { title: '检修记录管理', roles: AO } },
      { path: 'staff', name: 'Staff', component: () => import('../views/staff/Staff.vue'), meta: { title: '运维人员管理', roles: A } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

const VALID_ROLES = ['ADMIN', 'OPERATOR', 'CUSTOMER']

/** 清空本地登录痕迹（角色是脏数据时视为未登录，走重新登录） */
function clearAuth() {
  ;['token', 'username', 'nickname', 'role', 'customerId', 'staffId']
    .forEach(k => localStorage.removeItem(k))
}

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  const role = localStorage.getItem('role')

  // 有 token 但角色不是合法值（旧版本登录写入的 "undefined" 等脏数据）：
  // 必须先清掉再送回登录页，否则下方"角色不符送回 /dashboard"会和 /dashboard 的
  // 角色校验互相重定向，形成 Infinite redirect 死循环
  if (token && !VALID_ROLES.includes(role)) {
    clearAuth()
    if (to.path === '/login') { next(); return }
    next('/login')
    return
  }

  if (to.meta.requiresAuth && !token) {
    next('/login')
    return
  }
  if (to.path === '/login' && token) {
    next('/dashboard')
    return
  }

  // 角色不符：不要跳 404，直接送回他自己的首页——
  // 客户手滑点了别人分享的 /bill 链接，回到"我的用电"比看到一个空白页友好。
  // 走到这里 role 一定是三个合法值之一，而 /dashboard 对三者全开放，故不会自环
  const roles = to.meta.roles
  if (roles && token && !roles.includes(role)) {
    next(to.path === '/dashboard' ? false : '/dashboard')
    return
  }

  next()
})

export default router
