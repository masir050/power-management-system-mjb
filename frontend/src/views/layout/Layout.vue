<template>
  <div class="layout">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#F5B942" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
            <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z" />
          </svg>
        </div>
        <div class="brand-text">
          <span class="brand-name">POWERGRID</span>
          <span class="brand-sub">电力管理系统</span>
        </div>
      </div>

      <el-menu
        :default-active="activeMenu"
        text-color="#A8A5A0"
        active-text-color="#F5B942"
        router
        class="sidebar-menu"
      >
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <span>{{ dashboardLabel }}</span>
        </el-menu-item>

        <!-- 菜单由 roleMenus 派生，不再写死。换角色登录，这里整块跟着变。 -->
        <template v-for="group in roleMenus" :key="group.title">
          <el-menu-item-group :title="group.title">
            <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path">
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.label }}</span>
            </el-menu-item>
          </el-menu-item-group>
        </template>
      </el-menu>

      <div class="sidebar-foot">
        <span class="status-dot running"></span>
        <span class="foot-text">服务运行中</span>
        <span class="foot-ver">{{ userStore.roleText }}</span>
      </div>
    </aside>

    <div class="layout-right">
      <header class="topbar">
        <div class="crumbs">
          <span class="crumb-root">{{ userStore.isCustomer ? '我的账户' : 'PowerGrid' }}</span>
          <span class="crumb-sep">/</span>
          <span class="crumb-current">{{ route.meta.title || '总览' }}</span>
        </div>

        <div class="topbar-right">
          <div class="clock">
            <span class="clock-time">{{ clock }}</span>
            <span class="clock-date">{{ today }}</span>
          </div>
          <span class="vsep"></span>
          <el-dropdown @command="handleCommand" trigger="click">
            <div class="user-chip">
              <span class="user-avatar">{{ userStore.initial }}</span>
              <span class="username">{{ userStore.nickname || userStore.username }}</span>
              <span class="role-badge">{{ userStore.roleText }}</span>
              <el-icon class="caret"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>
                  {{ userStore.username }} · {{ userStore.roleText }}
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="main-area">
        <router-view v-slot="{ Component: ChildComponent }">
          <transition name="fade-slide">
            <keep-alive :include="keepAliveViews">
              <component :is="ChildComponent" />
            </keep-alive>
          </transition>
        </router-view>
      </main>
    </div>

    <!-- 规程问答角标：固定右下角，任何页面都能用；面板自己处理 loading / 401 / 服务未启动 -->
    <RagAssistant />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '../../stores/user'
import { startAlertSocket, stopAlertSocket } from '../../utils/alertSocket'
import RagAssistant from '../../components/RagAssistant.vue'
import {
  Odometer, Location, Connection, Monitor, User, DataBoard, Document,
  Coin, Tools, Avatar, ArrowDown, SwitchButton, Bell, TrendCharts, Wallet
} from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

/**
 * 菜单按角色生成
 *
 * <p>三份菜单互不重叠，因为它们对应三种完全不同的工作：</p>
 * <ul>
 *   <li>客户只关心"我用了多少电、我该交多少钱"，给他看设备台账毫无意义；</li>
 *   <li>运维关心"哪些设备故障、哪些工单没处理"，不该看到客户电费与联系方式；</li>
 *   <li>总负责人两边都要，外加人员与定价。</li>
 * </ul>
 *
 * <p>图标用字符串而不是组件引用，因为这份配置要能在 computed 里按角色过滤后直接喂给菜单。</p>
 */
const MENUS = {
  CUSTOMER: [
    {
      title: '我的账户',
      items: [
        { path: '/my-usage', label: '我的用电', icon: 'TrendCharts' },
        { path: '/my-bills', label: '我的账单', icon: 'Wallet' }
      ]
    }
  ],
  OPERATOR: [
    {
      title: '资产台账',
      items: [
        { path: '/equipment', label: '电力设备', icon: 'Monitor' },
        { path: '/meter', label: '智能电表', icon: 'DataBoard' },
        { path: '/substation', label: '变电站', icon: 'Connection' },
        { path: '/power-area', label: '供电区域', icon: 'Location' }
      ]
    },
    {
      title: '运维',
      items: [
        { path: '/alert', label: '告警中心', icon: 'Bell' },
        { path: '/maintenance', label: '检修记录', icon: 'Tools' }
      ]
    }
  ],
  ADMIN: [
    {
      title: '资产台账',
      items: [
        { path: '/equipment', label: '电力设备', icon: 'Monitor' },
        { path: '/meter', label: '智能电表', icon: 'DataBoard' },
        { path: '/substation', label: '变电站', icon: 'Connection' },
        { path: '/power-area', label: '供电区域', icon: 'Location' }
      ]
    },
    {
      title: '用电计费',
      items: [
        { path: '/customer', label: '用电客户', icon: 'User' },
        { path: '/bill', label: '电费账单', icon: 'Document' },
        { path: '/price', label: '电价标准', icon: 'Coin' }
      ]
    },
    {
      title: '运维',
      items: [
        { path: '/alert', label: '告警中心', icon: 'Bell' },
        { path: '/maintenance', label: '检修记录', icon: 'Tools' },
        { path: '/staff', label: '运维人员', icon: 'Avatar' }
      ]
    }
  ]
}

const roleMenus = computed(() => MENUS[userStore.role] || MENUS.OPERATOR)
const dashboardLabel = computed(() => {
  if (userStore.isCustomer) return '我的概览'
  if (userStore.isOperator) return '运维工作台'
  return '运行概览'
})

const keepAliveViews = [
  'Dashboard', 'PowerArea', 'Substation', 'Equipment', 'Customer', 'Meter',
  'Bill', 'Price', 'Maintenance', 'Staff', 'Alert', 'MyUsage', 'MyBills'
]

const activeMenu = computed(() => route.path)

const clock = ref('')
const today = ref('')
let timer = null

const WEEK = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

function tick() {
  const now = new Date()
  const p = n => String(n).padStart(2, '0')
  clock.value = `${p(now.getHours())}:${p(now.getMinutes())}:${p(now.getSeconds())}`
  today.value = `${now.getMonth() + 1}月${now.getDate()}日 ${WEEK[now.getDay()]}`
}

onMounted(() => {
  tick()
  timer = setInterval(tick, 1000)
  // 登录后进入主布局即建立告警通道，全局只此一处，其它页面不再各自连
  startAlertSocket()
})

onUnmounted(() => {
  clearInterval(timer)
  stopAlertSocket()
})

function handleCommand(command) {
  if (command === 'logout') {
    userStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.layout {
  display: flex;
  min-height: 100vh;
  background: var(--bg-base);
}

/* ---------- 侧栏 ---------- */
.sidebar {
  width: var(--sidebar-w);
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  z-index: 100;
  background: #0D0D0F;
  border-right: 1px solid rgba(255, 255, 255, 0.05);
  display: flex;
  flex-direction: column;
}

.brand {
  height: var(--header-h);
  display: flex;
  align-items: center;
  padding: 0 16px;
  gap: 9px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  flex-shrink: 0;
}

.brand-mark {
  width: 26px;
  height: 26px;
  border-radius: 5px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(245, 185, 66, 0.1);
  box-shadow: 0 0 0 1px rgba(245, 185, 66, 0.25);
  flex-shrink: 0;
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
  min-width: 0;
}

.brand-name {
  font-size: 12px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: 0.12em;
}

.brand-sub {
  font-size: 10.5px;
  color: var(--text-faint);
  letter-spacing: 0.02em;
}

.sidebar-menu {
  flex: 1;
  overflow-y: auto;
  padding: 6px 0 10px;
  background: transparent !important;
}

.sidebar-menu :deep(.el-menu-item) {
  height: 34px;
  line-height: 34px;
  border-radius: var(--radius-sm);
  margin: 0 8px;
  width: calc(100% - 16px);
  font-size: 12.5px;
  position: relative;
}

.sidebar-menu :deep(.el-menu-item-group__title) {
  padding: 16px 16px 5px !important;
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background: rgba(245, 185, 66, 0.09) !important;
  color: var(--gold) !important;
  font-weight: 500;
}

.sidebar-menu :deep(.el-menu-item.is-active::before) {
  content: '';
  position: absolute;
  left: -8px;
  top: 50%;
  transform: translateY(-50%);
  width: 2px;
  height: 14px;
  background: var(--gold);
  border-radius: 0 2px 2px 0;
  box-shadow: 0 0 8px rgba(245, 185, 66, 0.6);
}

.sidebar-menu :deep(.el-icon) {
  margin-right: 8px;
  font-size: 14px;
  width: 14px;
}

.sidebar-foot {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 11px 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  flex-shrink: 0;
}

.foot-text {
  font-size: 11px;
  color: var(--text-faint);
}

.foot-ver {
  margin-left: auto;
  font-size: 10.5px;
  color: var(--text-faint);
  font-family: var(--font-mono);
  opacity: 0.6;
}

/* ---------- 右侧 ---------- */
.layout-right {
  margin-left: var(--sidebar-w);
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  min-width: 0;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 50;
  height: var(--header-h);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background: rgba(10, 10, 11, 0.9);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}

.crumbs {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 12.5px;
  min-width: 0;
}

.crumb-root {
  color: var(--text-faint);
  font-family: var(--font-mono);
  font-size: 11.5px;
  letter-spacing: 0.02em;
}

.crumb-sep {
  color: var(--text-faint);
  opacity: 0.5;
}

.crumb-current {
  color: var(--text-primary);
  font-weight: 500;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.clock {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.clock-time {
  font-family: var(--font-mono);
  font-size: 12.5px;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.03em;
}

.clock-date {
  font-size: 11px;
  color: var(--text-faint);
}

.vsep {
  width: 1px;
  height: 16px;
  background: rgba(255, 255, 255, 0.08);
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 7px;
  cursor: pointer;
  padding: 3px 8px 3px 3px;
  border-radius: 4px;
  transition: background 0.14s ease;
}

.user-chip:hover { background: rgba(255, 255, 255, 0.06); }

.user-avatar {
  width: 22px;
  height: 22px;
  border-radius: 4px;
  background: rgba(245, 185, 66, 0.14);
  box-shadow: 0 0 0 1px rgba(245, 185, 66, 0.28);
  color: var(--gold);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
}

.username {
  font-size: 12.5px;
  color: var(--text-body);
}

/* 角色徽章：让"我现在是以什么身份在看"一眼可辨，避免拿错账号演示时说不清 */
.role-badge {
  font-size: 10px;
  line-height: 1;
  padding: 3px 5px;
  border-radius: 3px;
  color: var(--gold);
  background: rgba(245, 185, 66, 0.08);
  box-shadow: 0 0 0 1px rgba(245, 185, 66, 0.2);
  letter-spacing: 0.02em;
}

.caret {
  font-size: 11px;
  color: var(--text-faint);
}

.main-area {
  flex: 1;
  padding: 16px 20px 24px;
  max-width: 1680px;
  width: 100%;
  margin: 0 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
/* 页面根节点不拉伸（内容多高就多高），列表页靠 .table-card 内部滚动承载长表 */
.main-area > * { flex: 0 0 auto; }

@media (max-width: 900px) {
  .sidebar { display: none; }
  .layout-right { margin-left: 0; }
  .topbar { padding: 0 12px; }
  .main-area { padding: 12px; }
  .clock-date, .crumb-root, .crumb-sep { display: none; }
}
</style>
