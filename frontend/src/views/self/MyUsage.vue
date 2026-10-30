<template>
  <div class="page-container">
    <!-- 顶部：本户身份 + 关键数字 -->
    <section class="hero">
      <div class="hero-left">
        <span class="hero-label">当前账户</span>
        <h1 class="hero-name">{{ userStore.nickname || userStore.username }}</h1>
        <p class="hero-sub">
          户号 #{{ userStore.customerId || '--' }} · 数据窗口 {{ windowDays }} 天
        </p>
      </div>
      <div class="hero-metrics">
        <div class="metric" v-for="m in metrics" :key="m.label">
          <span class="metric-label">{{ m.label }}</span>
          <span class="metric-value">
            {{ m.value }}<span class="metric-unit" v-if="m.unit">{{ m.unit }}</span>
          </span>
        </div>
      </div>
    </section>

    <div class="toolbar">
      <div class="seg">
        <button
          v-for="d in windows" :key="d.value"
          class="seg-btn" :class="{ on: windowDays === d.value }"
          @click="switchWindow(d.value)"
        >{{ d.label }}</button>
      </div>
      <span class="src-hint">
        <span class="dot"></span>
        数据源 electricity_daily_usage · {{ rows.length }} 天
      </span>
    </div>

    <!-- 日用电曲线 -->
    <el-card class="panel">
      <div class="panel-head">
        <span class="panel-title">日用电量</span>
        <span class="panel-legend">
          <i class="lg lg-total"></i>总用电
          <i class="lg lg-peak"></i>尖峰段
          <i class="lg lg-valley"></i>低谷段
        </span>
      </div>
      <div class="chart-box" v-loading="loading">
        <div v-if="!loading && rows.length === 0" class="empty">暂无用电数据</div>
        <v-chart v-else :option="dailyOption" autoresize class="chart" />
      </div>
    </el-card>

    <div class="row">
      <!-- 尖峰平谷构成 -->
      <el-card class="panel panel-half">
        <div class="panel-head">
          <span class="panel-title">用电时段构成</span>
          <span class="panel-note">近 {{ windowDays }} 天</span>
        </div>
        <div class="chart-box short" v-loading="loading">
          <v-chart v-if="touTotal > 0" :option="touOption" autoresize class="chart" />
          <div v-else class="empty">暂无数据</div>
        </div>
      </el-card>

      <!-- 分表明细 -->
      <el-card class="panel panel-half">
        <div class="panel-head">
          <span class="panel-title">分表用电</span>
          <span class="panel-note">共 {{ meterList.length }} 块表</span>
        </div>
        <el-table :data="meterList" size="small" max-height="240" class="meter-table">
          <el-table-column prop="meterId" label="表号" width="80" />
          <el-table-column label="近 30 天用电" align="right">
            <template #default="{ row }">
              <span class="num">{{ row.total.toFixed(1) }}</span>
              <span class="unit">kWh</span>
            </template>
          </el-table-column>
          <el-table-column label="日均" align="right" width="110">
            <template #default="{ row }">
              <span class="num">{{ (row.total / Math.max(row.days, 1)).toFixed(1) }}</span>
              <span class="unit">kWh</span>
            </template>
          </el-table-column>
          <el-table-column label="占比" width="90" align="right">
            <template #default="{ row }">
              <span class="num">{{ meterPercent(row) }}%</span>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart, PieChart } from 'echarts/charts'
import {
  GridComponent, TooltipComponent, LegendComponent, TitleComponent
} from 'echarts/components'
import { usageApi } from '../../api/dashboard'
import { useUserStore } from '../../stores/user'

use([CanvasRenderer, LineChart, BarChart, PieChart,
     GridComponent, TooltipComponent, LegendComponent, TitleComponent])

defineOptions({ name: 'MyUsage' })

const userStore = useUserStore()

const GOLD = '#F5B942'
const GREEN = '#4ADE80'
const ORANGE = '#E8913A'
const MUTED = '#6E6B67'
const FAINT = '#4A4844'
const TEXT = '#EDEAE4'
const BODY = '#A8A5A0'

const windows = [
  { label: '近 7 天', value: 7 },
  { label: '近 30 天', value: 30 },
  { label: '近 90 天', value: 90 }
]
const windowDays = ref(30)
const loading = ref(false)
const rows = ref([])
const tou = ref({})
const meterRows = ref([])

const metrics = computed(() => {
  const total = rows.value.reduce((s, r) => s + Number(r.totalUsage || 0), 0)
  const days = rows.value.length || 1
  const max = rows.value.reduce((m, r) => Math.max(m, Number(r.totalUsage || 0)), 0)
  return [
    { label: '累计用电', value: total.toFixed(1), unit: ' kWh' },
    { label: '日均用电', value: (total / days).toFixed(1), unit: ' kWh' },
    { label: '单日峰值', value: max.toFixed(1), unit: ' kWh' },
    { label: '统计天数', value: String(days), unit: ' 天' }
  ]
})

const touTotal = computed(() => {
  const t = tou.value || {}
  return Number(t.peakUsage || 0) + Number(t.highUsage || 0)
       + Number(t.flatUsage || 0) + Number(t.valleyUsage || 0)
})

const meterList = computed(() => {
  const map = new Map()
  for (const r of meterRows.value) {
    const k = r.meterId
    const cur = map.get(k) || { meterId: k, total: 0, days: 0 }
    cur.total += Number(r.totalUsage || 0)
    cur.days += 1
    map.set(k, cur)
  }
  return [...map.values()].sort((a, b) => a.meterId - b.meterId)
})

const grandTotal = computed(() => meterList.value.reduce((s, m) => s + m.total, 0))

function meterPercent(row) {
  return grandTotal.value > 0 ? ((row.total / grandTotal.value) * 100).toFixed(1) : '0.0'
}

const axisCommon = {
  axisLine: { lineStyle: { color: 'rgba(255,255,255,0.08)' } },
  axisTick: { show: false },
  axisLabel: { color: FAINT, fontSize: 10 },
  splitLine: { lineStyle: { color: 'rgba(255,255,255,0.04)' } }
}

const dailyOption = computed(() => {
  const labels = rows.value.map(r => String(r.usageDate).slice(5))
  return {
    grid: { left: 46, right: 16, top: 18, bottom: 26 },
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#1C1C1F',
      borderColor: 'rgba(255,255,255,0.08)',
      textStyle: { color: TEXT, fontSize: 11 },
      formatter(ps) {
        const head = `<div style="color:${MUTED};font-size:10.5px;margin-bottom:3px">${ps[0].axisValue}</div>`
        return head + ps.map(p =>
          `<div style="display:flex;justify-content:space-between;gap:14px">
             <span style="color:${BODY}">${p.seriesName}</span>
             <strong style="font-family:monospace;color:${TEXT}">${p.value} kWh</strong>
           </div>`).join('')
      }
    },
    xAxis: { type: 'category', data: labels, boundaryGap: false, ...axisCommon,
             axisLabel: { ...axisCommon.axisLabel, interval: Math.max(0, Math.floor(labels.length / 8) - 1) } },
    yAxis: { type: 'value', ...axisCommon },
    series: [
      {
        name: '总用电', type: 'line', smooth: true, symbol: 'none',
        data: rows.value.map(r => Number(r.totalUsage)),
        lineStyle: { color: GOLD, width: 1.8 },
        itemStyle: { color: GOLD },
        areaStyle: {
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(245,185,66,0.22)' },
              { offset: 1, color: 'rgba(245,185,66,0)' }
            ]
          }
        }
      },
      {
        name: '尖峰段', type: 'line', smooth: true, symbol: 'none',
        data: rows.value.map(r => Number(r.peakUsage)),
        lineStyle: { color: ORANGE, width: 1, type: 'dashed' }
      },
      {
        name: '低谷段', type: 'line', smooth: true, symbol: 'none',
        data: rows.value.map(r => Number(r.valleyUsage)),
        lineStyle: { color: GREEN, width: 1, type: 'dashed' }
      }
    ]
  }
})

const touOption = computed(() => {
  const t = tou.value || {}
  return {
    tooltip: {
      trigger: 'item',
      backgroundColor: '#1C1C1F',
      borderColor: 'rgba(255,255,255,0.08)',
      textStyle: { color: TEXT, fontSize: 11 },
      formatter: p => `${p.name}<br/><strong style="font-family:monospace">${p.value} kWh (${p.percent}%)</strong>`
    },
    legend: {
      right: 4, top: 'middle', orient: 'vertical',
      itemWidth: 7, itemHeight: 7, itemGap: 12,
      textStyle: { color: BODY, fontSize: 11 }
    },
    series: [{
      type: 'pie',
      radius: ['52%', '74%'],
      center: ['34%', '50%'],
      avoidLabelOverlap: true,
      itemStyle: { borderColor: '#141416', borderWidth: 2 },
      label: { show: false },
      data: [
        { name: '尖峰', value: Number(t.peakUsage || 0), itemStyle: { color: GOLD } },
        { name: '高峰', value: Number(t.highUsage || 0), itemStyle: { color: ORANGE } },
        { name: '平段', value: Number(t.flatUsage || 0), itemStyle: { color: '#7A9CC6' } },
        { name: '低谷', value: Number(t.valleyUsage || 0), itemStyle: { color: GREEN } }
      ]
    }]
  }
})

async function load() {
  loading.value = true
  try {
    const [daily, split, meters] = await Promise.all([
      usageApi.daily(windowDays.value),
      usageApi.touSplit(windowDays.value),
      usageApi.meterDaily(null, windowDays.value)
    ])
    rows.value = daily.data || []
    tou.value = split.data || {}
    meterRows.value = meters.data || []
  } finally {
    loading.value = false
  }
}

function switchWindow(d) {
  windowDays.value = d
  load()
}

onMounted(load)
</script>

<style scoped>
.hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding: 18px 20px;
  margin-bottom: 10px;
  background: var(--bg-card);
  border-radius: var(--radius-md);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.045);
}

.hero-label {
  font-size: 10.5px;
  letter-spacing: 0.08em;
  color: var(--text-faint);
}

.hero-name {
  margin: 6px 0 4px;
  font-size: 22px;
  font-weight: 600;
  color: var(--text-primary);
  letter-spacing: -0.01em;
}

.hero-sub {
  margin: 0;
  font-size: 11.5px;
  color: var(--text-muted);
}

.hero-metrics {
  display: flex;
  gap: 34px;
}

.metric {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.metric-label {
  font-size: 11px;
  color: var(--text-faint);
}

.metric-value {
  font-family: var(--font-mono);
  font-size: 21px;
  font-weight: 600;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}

.metric-unit {
  font-size: 11px;
  font-weight: 400;
  color: var(--text-faint);
  margin-left: 2px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.seg {
  display: inline-flex;
  padding: 2px;
  background: var(--bg-card);
  border-radius: var(--radius-sm);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.045);
}

.seg-btn {
  padding: 5px 12px;
  font-size: 11.5px;
  color: var(--text-muted);
  background: transparent;
  border: none;
  border-radius: 3px;
  cursor: pointer;
  transition: all 0.14s ease;
}

.seg-btn:hover { color: var(--text-primary); }

.seg-btn.on {
  color: #14100A;
  background: var(--gold);
  font-weight: 600;
}

.src-hint {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 10.5px;
  color: var(--text-faint);
  font-family: var(--font-mono);
}

.src-hint .dot {
  width: 4px;
  height: 4px;
  border-radius: 1px;
  background: var(--green);
}

.panel { margin-bottom: 10px; }
.panel :deep(.el-card__body) { padding: 13px 16px 8px; }

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.panel-title {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--text-primary);
}

.panel-legend {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 10.5px;
  color: var(--text-muted);
}

.lg {
  width: 8px;
  height: 2px;
  border-radius: 1px;
  margin-left: 8px;
}
.lg-total { background: var(--gold); }
.lg-peak { background: #E8913A; }
.lg-valley { background: var(--green); }

.panel-note { font-size: 10.5px; color: var(--text-faint); }

.chart-box { height: 250px; }
.chart-box.short { height: 200px; }
.chart { width: 100%; height: 100%; }

.empty {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--text-faint);
}

.row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.num {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
  color: var(--text-primary);
}
.unit { font-size: 10.5px; color: var(--text-faint); margin-left: 2px; }

@media (max-width: 1100px) {
  .row { grid-template-columns: 1fr; }
  .hero { flex-direction: column; align-items: flex-start; }
  .hero-metrics { gap: 22px; flex-wrap: wrap; }
}
</style>
