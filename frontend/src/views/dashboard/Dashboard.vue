<template>
  <div class="page-container">
    <!-- ============ 顶部身份条：明确"你现在看的是谁的数据" ============ -->
    <header class="page-head">
      <div class="head-left">
        <h1 class="head-title">{{ profile.title || '总览' }}</h1>
        <p class="head-sub">
          <span class="scope-chip">{{ profile.scopeName || '加载中' }}</span>
          <span class="head-hint">{{ roleHint }}</span>
        </p>
      </div>
      <div class="head-right">
        <span class="src-badge" v-if="dataSource">
          <span class="dot"></span>数据源 {{ dataSource }}
        </span>
      </div>
    </header>

    <!-- ============ KPI：内容按角色不同 ============ -->
    <div class="kpi-grid" :class="{ 'kpi-4': kpiList.length === 4 }">
      <div class="kpi-card" v-for="kpi in kpiList" :key="kpi.label">
        <div class="kpi-top">
          <span class="kpi-name">{{ kpi.label }}</span>
          <el-icon class="kpi-glyph" :size="12"><component :is="kpi.icon" /></el-icon>
        </div>
        <div class="kpi-num" :class="kpi.tone">
          {{ kpi.value }}<i class="kpi-unit" v-if="kpi.unit">{{ kpi.unit }}</i>
        </div>
        <div class="kpi-bar" :style="{ width: kpi.pct + '%' }" :class="kpi.tone"></div>
        <div class="kpi-hint">{{ kpi.hint }}</div>
      </div>
    </div>

    <!-- ============ 主区：客户看用电，运维/管理员看设备与告警 ============ -->
    <div class="row">
      <!-- 日用电曲线（所有角色都有，只是数据范围不同） -->
      <section class="panel chart-panel">
        <header class="panel-head">
          <div class="ph-left">
            <h3>{{ isCustomerView ? '我的日用电量' : '全站日用电量' }}</h3>
            <span class="ph-note">{{ windowLabel }}</span>
          </div>
          <div class="seg">
            <button
              v-for="w in windows" :key="w.value"
              class="seg-btn" :class="{ on: days === w.value }"
              @click="switchDays(w.value)"
            >{{ w.label }}</button>
          </div>
        </header>
        <div class="chart-box" v-loading="loading">
          <div v-if="!loading && loadLabels.length === 0" class="empty">
            暂无用电数据
            <span class="empty-sub">electricity_daily_usage 表在该时间窗口内没有记录</span>
          </div>
          <v-chart v-else :option="loadOption" autoresize class="chart" />
        </div>
      </section>

      <!-- 右侧：客户看账单摘要，其他人看设备健康 + 告警 -->
      <section v-if="isCustomerView" class="panel side-panel">
        <header class="panel-head">
          <h3>我的待缴</h3>
          <span class="panel-count">{{ overdueList.length }}</span>
        </header>
        <div class="bill-mini" v-if="overdueList.length > 0">
          <article class="bill-row" v-for="b in overdueList.slice(0, 5)" :key="b.id">
            <div class="bill-main">
              <span class="bill-year">{{ b.billingYear }} 年</span>
              <span class="bill-status" :class="statusClass(b.paymentStatus)">{{ b.paymentStatus }}</span>
            </div>
            <div class="bill-foot">
              <span class="bill-money">¥{{ Number(b.arrearsAmount || 0).toFixed(2) }}</span>
              <span class="bill-date">{{ formatDate(b.paymentDeadline) }} 到期</span>
            </div>
          </article>
          <router-link to="/my-bills" class="more-link">查看全部账单 →</router-link>
        </div>
        <div v-else class="side-empty">
          <el-icon :size="20" color="#4ADE80"><CircleCheckFilled /></el-icon>
          <span>暂无待缴账单</span>
        </div>
      </section>

      <section v-else class="panel side-panel">
        <header class="panel-head">
          <h3>设备风险分布</h3>
          <span class="ph-note">按危害等级</span>
        </header>
        <div class="chart-box short" v-loading="loading">
          <v-chart v-if="hazardList.length" :option="hazardOption" autoresize class="chart" />
          <div v-else class="empty">暂无设备数据</div>
        </div>
      </section>
    </div>

    <!-- ============ 第二行：用户构成 / 告警列表 ============ -->
    <div class="row">
      <section class="panel chart-panel">
        <header class="panel-head">
          <div class="ph-left">
            <h3>用电时段构成</h3>
            <span class="ph-note">尖 / 峰 / 平 / 谷 电量占比</span>
          </div>
        </header>
        <div class="chart-box short" v-loading="loading">
          <v-chart v-if="touTotal > 0" :option="touOption" autoresize class="chart" />
          <div v-else class="empty">暂无数据</div>
        </div>
      </section>

      <section v-if="!isCustomerView" class="panel alert-panel">
        <header class="panel-head">
          <h3>最近检修记录</h3>
          <span class="panel-count">{{ alertList.length }}</span>
        </header>
        <div class="alert-list">
          <article
            v-for="(item, i) in alertList" :key="item.id ?? i"
            class="alert-row" :class="`lv-${priorityClass(item.repairPriority)}`"
          >
            <span class="lv-bar"></span>
            <div class="alert-body">
              <div class="alert-top">
                <span class="alert-type">{{ item.faultType }}</span>
                <span class="alert-time">{{ formatTime(item.occurrenceTime) }}</span>
              </div>
              <div class="alert-desc">{{ item.faultDescription }}</div>
              <div class="alert-foot">
                <span>设备 #{{ item.equipmentId }}</span>
                <span v-if="item.staffName">· {{ item.staffName }}</span>
              </div>
            </div>
            <span class="lv-tag">{{ item.repairPriority }}</span>
          </article>
          <div v-if="alertList.length === 0" class="side-empty">
            <el-icon :size="20" color="#4ADE80"><CircleCheckFilled /></el-icon>
            <span>暂无检修记录</span>
          </div>
        </div>
      </section>

      <!-- 客户没有告警，这一半改成"月度用电趋势"，也全是真数据 -->
      <section v-else class="panel">
        <header class="panel-head">
          <h3>月度用电趋势</h3>
          <span class="ph-note">按月汇总</span>
        </header>
        <div class="chart-box short" v-loading="loading">
          <v-chart v-if="monthly.length" :option="monthlyOption" autoresize class="chart" />
          <div v-else class="empty">暂无数据</div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CircleCheckFilled } from '@element-plus/icons-vue'
import { dashboardApi, usageApi, myBillApi } from '../../api/dashboard'
import { useUserStore } from '../../stores/user'
import { formatDate, formatDateTime } from '../../utils/format'

use([CanvasRenderer, LineChart, BarChart, PieChart,
     GridComponent, TooltipComponent, LegendComponent])

defineOptions({ name: 'Dashboard' })

const router = useRouter()
const userStore = useUserStore()

/* ---------- 主题色（与 global.css 同源，图表里只能写死） ---------- */
const GOLD = '#F5B942'
const GREEN = '#4ADE80'
const RED = '#F2555A'
const ORANGE = '#E8913A'
const BLUE = '#7A9CC6'
const TEXT = '#EDEAE4'
const BODY = '#A8A5A0'
const MUTED = '#6E6B67'
const FAINT = '#4A4844'

/* ---------- 状态 ---------- */
const profile = ref({})
const kpi = ref({})
const alertList = ref([])
const loadLabels = ref([])
const loadValues = ref([])
const loadPeak = ref([])
const loadValley = ref([])
const tou = ref({})
const hazardList = ref([])
const monthly = ref([])
const overdueList = ref([])
const dataSource = ref('')
const loading = ref(false)
const days = ref(30)

/** 视为"还没结清"的账单状态 */
const UNPAID = ['未缴', '逾期', '部分缴纳']

const windows = [
  { label: '7天', value: 7 },
  { label: '30天', value: 30 },
  { label: '90天', value: 90 }
]

const isCustomerView = computed(() => profile.value.role === 'CUSTOMER')

const windowLabel = computed(() => {
  const w = windows.find(x => x.value === days.value)
  return w ? w.label : ''
})

const roleHint = computed(() => {
  if (profile.value.role === 'CUSTOMER') return '仅显示本户数据，其他用电户不可见'
  if (profile.value.role === 'OPERATOR') return '设备与工单全域可见，客户账务不在此视图'
  return '全站数据'
})

/* ---------- KPI ---------- */
const kpiList = computed(() => {
  const raw = kpi.value.raw || {}
  if (isCustomerView.value) {
    return [
      { label: '近 30 天用电', value: fmtNum(kpi.value.monthUsage), unit: ' kWh', icon: 'TrendCharts', pct: 100, hint: '本户两块电表合计', tone: '' },
      { label: '日均用电', value: fmtNum(kpi.value.avgDaily), unit: ' kWh', icon: 'Odometer', pct: 62, hint: '按 30 天窗口计算', tone: '' },
      { label: '待缴金额', value: fmtNum(kpi.value.arrears), unit: ' 元', icon: 'Wallet', pct: Number(kpi.value.arrears) > 0 ? 100 : 0, hint: '未缴 + 逾期合计', tone: Number(kpi.value.arrears) > 0 ? 'warn' : '' },
      { label: '待缴笔数', value: String(kpi.value.unpaidBills ?? 0), unit: ' 笔', icon: 'Tickets', pct: Number(kpi.value.unpaidBills) > 0 ? 100 : 0, hint: '含部分缴纳', tone: Number(kpi.value.unpaidBills) > 0 ? 'warn' : '' }
    ]
  }
  return [
    { label: '在册设备', value: kpi.value.totalEquipment ?? '--', unit: ' 台', icon: 'Monitor', pct: 100, hint: '含变压器/断路器/隔离开关', tone: '' },
    { label: '变电站运行率', value: kpi.value.onlineRate ?? '--', unit: '', icon: 'Connection', pct: Number(raw.onlineRate ?? 0), hint: `${raw.onlineSubstations ?? 0} / ${raw.totalSubstations ?? 0} 座运行中`, tone: '' },
    { label: '今日检修', value: kpi.value.todayAlerts ?? '--', unit: ' 条', icon: 'Warning', pct: Number(kpi.value.todayAlerts) > 0 ? 100 : 0, hint: '按发生时间统计', tone: Number(kpi.value.todayAlerts) > 0 ? 'warn' : '' },
    { label: '待处理告警', value: kpi.value.pendingAlerts ?? '--', unit: ' 条', icon: 'Bell', pct: Number(kpi.value.pendingAlerts) > 0 ? 100 : 0, hint: 'PENDING 状态', tone: Number(kpi.value.pendingAlerts) > 0 ? 'danger' : '' },
    { label: '异常电表', value: kpi.value.abnormalMeters ?? '--', unit: ' 块', icon: 'DataBoard', pct: Number(kpi.value.abnormalMeters) > 0 ? 100 : 0, hint: '故障 + 待检定', tone: Number(kpi.value.abnormalMeters) > 0 ? 'warn' : '' }
  ]
})

/* ---------- 图表配置 ---------- */
const axisCommon = {
  axisLine: { lineStyle: { color: 'rgba(255,255,255,0.07)' } },
  axisTick: { show: false },
  axisLabel: { color: FAINT, fontSize: 9.5 },
  splitLine: { lineStyle: { color: 'rgba(255,255,255,0.035)' } }
}

const loadOption = computed(() => ({
  grid: { left: 44, right: 14, top: 16, bottom: 22 },
  tooltip: {
    trigger: 'axis',
    backgroundColor: '#1C1C1F',
    borderColor: 'rgba(255,255,255,0.08)',
    textStyle: { color: TEXT, fontSize: 11 },
    formatter(ps) {
      const head = `<div style="color:${MUTED};font-size:10px;margin-bottom:3px">${ps[0].axisValue}</div>`
      return head + ps.map(p =>
        `<div style="display:flex;justify-content:space-between;gap:16px">
           <span style="color:${BODY}">${p.seriesName}</span>
           <strong style="font-family:monospace;color:${TEXT}">${p.value} kWh</strong>
         </div>`).join('')
    }
  },
  xAxis: {
    type: 'category', data: loadLabels.value, boundaryGap: false, ...axisCommon,
    axisLabel: {
      ...axisCommon.axisLabel,
      interval: Math.max(0, Math.ceil(loadLabels.value.length / 8) - 1)
    }
  },
  yAxis: { type: 'value', ...axisCommon },
  series: [
    {
      name: '总用电', type: 'line', smooth: true, symbol: 'none',
      data: loadValues.value,
      lineStyle: { color: GOLD, width: 1.7 },
      areaStyle: {
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(245,185,66,0.2)' },
            { offset: 1, color: 'rgba(245,185,66,0)' }
          ]
        }
      }
    },
    { name: '尖峰段', type: 'line', smooth: true, symbol: 'none', data: loadPeak.value, lineStyle: { color: ORANGE, width: 1, type: 'dashed' } },
    { name: '低谷段', type: 'line', smooth: true, symbol: 'none', data: loadValley.value, lineStyle: { color: GREEN, width: 1, type: 'dashed' } }
  ]
}))

const touTotal = computed(() => {
  const t = tou.value || {}
  return Number(t.peakUsage || 0) + Number(t.highUsage || 0) + Number(t.flatUsage || 0) + Number(t.valleyUsage || 0)
})

const touOption = computed(() => {
  const t = tou.value || {}
  return {
    tooltip: {
      trigger: 'item',
      backgroundColor: '#1C1C1F',
      borderColor: 'rgba(255,255,255,0.08)',
      textStyle: { color: TEXT, fontSize: 11 },
      formatter: p => `${p.name}<br/><strong style="font-family:monospace">${p.value} kWh · ${p.percent}%</strong>`
    },
    legend: { right: 6, top: 'middle', orient: 'vertical', itemWidth: 6, itemHeight: 6, itemGap: 10, textStyle: { color: BODY, fontSize: 10.5 } },
    series: [{
      type: 'pie', radius: ['50%', '72%'], center: ['36%', '50%'],
      itemStyle: { borderColor: '#141416', borderWidth: 2 },
      label: { show: false },
      data: [
        { name: '尖峰', value: Number(t.peakUsage || 0), itemStyle: { color: GOLD } },
        { name: '高峰', value: Number(t.highUsage || 0), itemStyle: { color: ORANGE } },
        { name: '平段', value: Number(t.flatUsage || 0), itemStyle: { color: BLUE } },
        { name: '低谷', value: Number(t.valleyUsage || 0), itemStyle: { color: GREEN } }
      ]
    }]
  }
})

const hazardOption = computed(() => {
  const names = hazardList.value.map(h => h.hazardLevel || h.hazard_level || '未分级')
  const values = hazardList.value.map(h => Number(h.count || h.total || 0))
  return {
    grid: { left: 40, right: 18, top: 12, bottom: 20 },
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#1C1C1F',
      borderColor: 'rgba(255,255,255,0.08)',
      textStyle: { color: TEXT, fontSize: 11 },
      axisPointer: { type: 'shadow' }
    },
    xAxis: { type: 'category', data: names, ...axisCommon, splitLine: { show: false } },
    yAxis: { type: 'value', ...axisCommon },
    series: [{
      type: 'bar', data: values, barWidth: '42%',
      itemStyle: {
        borderRadius: [2, 2, 0, 0],
        color: params => [RED, ORANGE, GOLD, MUTED][params.dataIndex % 4]
      }
    }]
  }
})

const monthlyOption = computed(() => ({
  grid: { left: 46, right: 14, top: 14, bottom: 22 },
  tooltip: {
    trigger: 'axis',
    backgroundColor: '#1C1C1F',
    borderColor: 'rgba(255,255,255,0.08)',
    textStyle: { color: TEXT, fontSize: 11 },
    axisPointer: { type: 'shadow' }
  },
  // 后端返回的是 "2026-08"，只取月份数字更省横向空间
  xAxis: {
    type: 'category',
    data: monthly.value.map(m => String(m.month || '').slice(5) + ' 月'),
    ...axisCommon,
    splitLine: { show: false }
  },
  yAxis: { type: 'value', ...axisCommon },
  series: [{
    type: 'bar',
    data: monthly.value.map(m => Number(m.totalUsage || 0)),
    barWidth: '40%',
    itemStyle: { color: GOLD, borderRadius: [2, 2, 0, 0] }
  }]
}))

/* ---------- 工具 ---------- */
function fmtNum(v) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '--'
  return n >= 10000 ? (n / 10000).toFixed(1) + ' 万' : n.toFixed(1)
}

function priorityClass(p) {
  return { 紧急: 'urgent', 高: 'high', 中: 'mid', 低: 'low' }[p] || 'low'
}

function statusClass(s) {
  return { 已缴: 'ok', 逾期: 'bad', 部分缴纳: 'warn' }[s] || 'info'
}

function formatTime(v) {
  const s = formatDateTime(v)
  return s.length >= 16 ? s.slice(5, 16) : s
}

/* ---------- 数据加载 ---------- */
async function loadCore() {
  loading.value = true
  try {
    // profile 必须先拿到：它是"这个人是客户还是运维"的唯一依据，
    // 而后面的分支全看这个身份。之前把它塞进 Promise.all 一起并发，
    // 结果 isCustomerView 在首屏还是 false（profile 尚为空），
    // 客户也会被当成运维去要设备健康数据。多一次串行往返，换分支正确。
    const pf = await dashboardApi.profile()
    profile.value = pf.data || {}

    const jobs = [dashboardApi.kpi(), usageApi.touSplit(days.value)]
    if (isCustomerView.value) {
      // 客户：不请求内部告警与设备数据，改取月度趋势与自己的待缴账单
      jobs.push(usageApi.monthly(6), myOverdue())
    } else {
      jobs.push(dashboardApi.alerts(), dashboardApi.equipmentHealth())
    }
    const [k, t, a, extra] = await Promise.all(jobs)

    kpi.value = k.data || {}
    tou.value = t.data || {}
    dataSource.value = 'electricity_daily_usage / power_*'

    if (isCustomerView.value) {
      alertList.value = []
      monthly.value = a?.data || []
      overdueList.value = extra?.data || []
    } else {
      alertList.value = a.data || []
      hazardList.value = extra?.data || []
    }

    await loadCurve()
  } finally {
    loading.value = false
  }
}

async function myOverdue() {
  // 只看本户的账单：服务端按登录身份过滤，这里不传也不该传 customerId
  const res = await myBillApi.page({ page: 1, size: 50 })
  const rows = res.data?.records || res.data?.list || []
  return { data: rows.filter(b => UNPAID.includes(b.paymentStatus)) }
}

async function loadCurve() {
  const res = await dashboardApi.loadChart(days.value)
  const d = res.data || {}
  loadLabels.value = d.labels || []
  loadValues.value = d.values || []
  loadPeak.value = d.peak || []
  loadValley.value = d.valley || []
}

async function switchDays(d) {
  days.value = d
  loading.value = true
  try {
    await Promise.all([loadCurve(), usageApi.touSplit(d).then(r => { tou.value = r.data || {} })])
  } finally {
    loading.value = false
  }
}

onMounted(loadCore)
</script>

<style scoped>
/* ---------- 页头 ---------- */
.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 12px;
}

.head-title {
  margin: 0 0 6px;
  font-size: 19px;
  font-weight: 600;
  color: var(--text-primary);
  letter-spacing: -0.01em;
}

.head-sub {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0;
}

.scope-chip {
  font-size: 10.5px;
  padding: 2px 7px;
  border-radius: 3px;
  color: var(--gold);
  background: rgba(245, 185, 66, 0.07);
  box-shadow: 0 0 0 1px rgba(245, 185, 66, 0.18);
}

.head-hint {
  font-size: 11px;
  color: var(--text-faint);
}

.src-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 10px;
  font-family: var(--font-mono);
  color: var(--text-faint);
}

.src-badge .dot {
  width: 4px;
  height: 4px;
  border-radius: 1px;
  background: var(--green);
}

/* ---------- KPI ---------- */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px;
  margin-bottom: 10px;
}
.kpi-grid.kpi-4 { grid-template-columns: repeat(4, 1fr); }

.kpi-card {
  position: relative;
  overflow: hidden;
  padding: 12px 14px 14px;
  background: var(--bg-card);
  border-radius: var(--radius-md);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.045);
  transition: box-shadow 0.18s ease, background 0.18s ease;
}

.kpi-card:hover {
  background: var(--bg-raised);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.085);
}

.kpi-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 9px;
}

.kpi-name { font-size: 11.5px; color: var(--text-muted); }
.kpi-glyph { color: var(--text-faint); }

.kpi-num {
  font-family: var(--font-mono);
  font-size: 23px;
  font-weight: 600;
  line-height: 1.05;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}
.kpi-num.warn { color: var(--orange); }
.kpi-num.danger { color: var(--red); }

.kpi-unit {
  font-size: 11px;
  font-style: normal;
  font-weight: 400;
  color: var(--text-faint);
  margin-left: 3px;
}

.kpi-bar {
  position: absolute;
  left: 0;
  bottom: 0;
  height: 1.5px;
  background: var(--gold);
  opacity: 0.55;
  transition: width 0.5s ease;
}
.kpi-bar.warn { background: var(--orange); }
.kpi-bar.danger { background: var(--red); }

.kpi-hint {
  margin-top: 7px;
  font-size: 10px;
  color: var(--text-faint);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ---------- 面板 ---------- */
.row {
  display: grid;
  grid-template-columns: minmax(0, 1.65fr) minmax(0, 1fr);
  gap: 10px;
  margin-bottom: 10px;
}

.panel {
  display: flex;
  flex-direction: column;
  background: var(--bg-card);
  border-radius: var(--radius-md);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.045);
  padding: 13px 16px 10px;
  min-width: 0;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.ph-left { display: flex; align-items: baseline; gap: 9px; }

.panel-head h3 {
  margin: 0;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--text-primary);
}

.ph-note { font-size: 10px; color: var(--text-faint); }

.panel-count {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-faint);
}

.chart-box { flex: 1; min-height: 230px; }
.chart-box.short { min-height: 168px; }
.chart { width: 100%; height: 100%; }

.empty {
  height: 100%;
  min-height: 168px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--text-faint);
}
.empty-sub { font-size: 10px; color: #3A3835; font-family: var(--font-mono); }

/* ---------- 分段按钮 ---------- */
.seg {
  display: inline-flex;
  padding: 2px;
  background: var(--bg-inset);
  border-radius: 4px;
}

.seg-btn {
  padding: 3px 10px;
  font-size: 11px;
  color: var(--text-muted);
  background: transparent;
  border: none;
  border-radius: 3px;
  cursor: pointer;
  transition: all 0.14s ease;
}
.seg-btn:hover { color: var(--text-primary); }
.seg-btn.on { color: #14100A; background: var(--gold); font-weight: 600; }

/* ---------- 告警列表 ---------- */
.alert-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  overflow-y: auto;
  max-height: 300px;
}

.alert-row {
  display: flex;
  align-items: stretch;
  gap: 9px;
  padding: 8px 10px 8px 0;
  background: var(--bg-inset);
  border-radius: var(--radius-sm);
}

.lv-bar {
  width: 2px;
  border-radius: 1px;
  flex-shrink: 0;
  background: var(--gray);
}
.lv-urgent .lv-bar { background: var(--red); }
.lv-high .lv-bar { background: var(--orange); }
.lv-mid .lv-bar { background: var(--gold); }
.lv-low .lv-bar { background: var(--gray); }

.alert-body { flex: 1; min-width: 0; }

.alert-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 3px;
}

.alert-type { font-size: 11.5px; font-weight: 600; color: var(--text-primary); }
.alert-time { font-size: 9.5px; color: var(--text-faint); font-family: var(--font-mono); flex-shrink: 0; }

.alert-desc {
  font-size: 10.5px;
  color: var(--text-muted);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.alert-foot {
  margin-top: 3px;
  font-size: 9.5px;
  color: #3F3D3A;
}

.lv-tag {
  align-self: flex-start;
  font-size: 9.5px;
  padding: 1px 5px;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.04);
  color: var(--text-muted);
}
.lv-urgent .lv-tag { color: var(--red); background: rgba(242, 85, 90, 0.1); }
.lv-high .lv-tag { color: var(--orange); background: rgba(232, 145, 58, 0.1); }

/* ---------- 客户待缴 ---------- */
.bill-mini { display: flex; flex-direction: column; gap: 6px; }

.bill-row {
  padding: 9px 11px;
  background: var(--bg-inset);
  border-radius: var(--radius-sm);
  box-shadow: inset 2px 0 0 rgba(242, 85, 90, 0.5);
}

.bill-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 5px;
}

.bill-year { font-size: 11.5px; font-weight: 600; color: var(--text-primary); }

.bill-status { font-size: 9.5px; padding: 1px 5px; border-radius: 2px; }
.bill-status.bad { color: var(--red); background: rgba(242, 85, 90, 0.1); }
.bill-status.warn { color: var(--orange); background: rgba(232, 145, 58, 0.1); }
.bill-status.ok { color: var(--green); background: rgba(74, 222, 128, 0.1); }
.bill-status.info { color: var(--gray); background: rgba(110, 110, 118, 0.12); }

.bill-foot {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.bill-money {
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 600;
  color: var(--red);
}

.bill-date { font-size: 9.5px; color: var(--text-faint); }

.more-link {
  margin-top: 6px;
  font-size: 11px;
  color: var(--text-muted);
  text-decoration: none;
  text-align: right;
  transition: color 0.14s ease;
}
.more-link:hover { color: var(--gold); }

.side-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 7px;
  font-size: 11.5px;
  color: var(--text-faint);
  min-height: 150px;
}

@media (max-width: 1400px) {
  .kpi-grid { grid-template-columns: repeat(3, 1fr); }
}
@media (max-width: 1100px) {
  .row { grid-template-columns: 1fr; }
  .kpi-grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
