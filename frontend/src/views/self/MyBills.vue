<template>
  <div class="page-container">
    <!-- 缴费提醒：只在这个人真的欠费时出现，不欠费就不打扰 -->
    <section v-if="overdueBills.length > 0" class="notice">
      <div class="notice-left">
        <span class="notice-mark"></span>
        <div>
          <p class="notice-title">您有 {{ overdueBills.length }} 笔账单待缴，合计 ¥{{ overdueTotal }}</p>
          <p class="notice-sub">
            最近到期日 {{ overdueBills[0].paymentDeadline || '—' }}
            <template v-if="overdueBills[0].reminderCount > 0">
              · 已催缴 {{ overdueBills[0].reminderCount }} 次
            </template>
          </p>
        </div>
      </div>
      <el-button type="primary" size="small" @click="openPay(overdueBills[0])">立即缴费</el-button>
    </section>

    <!-- 汇总 -->
    <div class="sum-grid">
      <div class="sum-card" v-for="s in summary" :key="s.label">
        <span class="sum-label">{{ s.label }}</span>
        <span class="sum-value" :class="s.tone">{{ s.value }}<i class="sum-unit">{{ s.unit }}</i></span>
      </div>
    </div>

    <el-card class="table-card">
      <div class="table-header">
        <span class="table-title">账单明细</span>
        <span class="table-count">共 {{ total }} 条 · 仅显示本户</span>
      </div>
      <el-table :data="tableData" v-loading="loading" highlight-current-row max-height="560">
        <el-table-column prop="id" label="账单号" width="82" />
        <el-table-column prop="billingYear" label="计费年份" width="92" />
        <el-table-column label="用电量" width="118" align="right">
          <template #default="{ row }">
            <span class="num">{{ Number(row.totalConsumption).toFixed(2) }}</span>
            <span class="unit">kWh</span>
          </template>
        </el-table-column>
        <el-table-column label="应缴" width="104" align="right">
          <template #default="{ row }">
            <span class="num">¥{{ money(row.billAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="已缴" width="104" align="right">
          <template #default="{ row }">
            <span class="num paid">¥{{ money(row.paidAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="尚欠" width="112" align="right">
          <template #default="{ row }">
            <span class="num" :class="{ owe: Number(row.arrearsAmount) > 0 }">
              ¥{{ money(row.arrearsAmount) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="paymentStatus" label="状态" width="98">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.paymentStatus)" size="small" effect="plain">
              {{ row.paymentStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="缴费期限" width="110">
          <template #default="{ row }">{{ formatDate(row.paymentDeadline) }}</template>
        </el-table-column>
        <el-table-column label="" width="140" align="center">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="Number(row.arrearsAmount) > 0"
              size="small"
              type="primary"
              @click="openPay(row)"
            >缴费</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @change="load"
        class="pagination"
      />
    </el-card>

    <!-- 缴费弹窗 -->
    <el-dialog v-model="payVisible" title="账单缴费" width="440px" :close-on-click-modal="false">
      <div v-if="payTarget" class="pay-body">
        <div class="pay-head">
          <div class="pay-bill">
            <span class="pay-bill-no">账单 #{{ payTarget.id }}</span>
            <span class="pay-bill-meta">
              {{ payTarget.billingYear }} 年 · {{ Number(payTarget.totalConsumption).toFixed(2) }} kWh
            </span>
          </div>
          <el-tag :type="statusTag(payTarget.paymentStatus)" size="small" effect="plain">
            {{ payTarget.paymentStatus }}
          </el-tag>
        </div>

        <div class="pay-figures">
          <div class="pf-item">
            <span class="pf-label">应缴</span>
            <span class="pf-value">¥{{ money(payTarget.billAmount) }}</span>
          </div>
          <div class="pf-item">
            <span class="pf-label">已缴</span>
            <span class="pf-value">¥{{ money(payTarget.paidAmount) }}</span>
          </div>
          <div class="pf-item">
            <span class="pf-label">尚欠</span>
            <span class="pf-value owe">¥{{ money(payTarget.arrearsAmount) }}</span>
          </div>
        </div>

        <div class="pay-field">
          <div class="pf-head">
            <span class="pf-title">本次缴费金额</span>
            <button class="pf-link" type="button" @click="fillFull">全部结清</button>
          </div>
          <el-input-number
            v-model="payAmount"
            :min="0.01"
            :max="payMax"
            :precision="2"
            :step="10"
            controls-position="right"
            style="width: 100%"
          />
          <span class="pf-hint">可只缴一部分，剩余金额继续挂在账上（部分缴纳）</span>
        </div>

        <div class="pay-field">
          <span class="pf-title">支付方式</span>
          <el-radio-group v-model="payMethod" class="pay-methods">
            <el-radio-button v-for="m in methods" :key="m" :value="m">{{ m }}</el-radio-button>
          </el-radio-group>
        </div>

        <p class="pay-tip">
          演示环境不接真实支付网关，确认后直接把账单状态写入数据库：
          缴清转为「已缴」，未缴清按期限转为「部分缴纳」或「逾期」。
        </p>
      </div>

      <template #footer>
        <el-button @click="payVisible = false">取消</el-button>
        <el-button type="primary" :loading="paying" @click="submitPay">
          确认缴费 ¥{{ money(payAmount) }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="账单详情" width="540px">
      <div v-if="detail" class="detail">
        <div class="d-row" v-for="d in detailRows" :key="d.label">
          <span class="d-label">{{ d.label }}</span>
          <span class="d-value">{{ d.value }}</span>
        </div>

        <div class="d-pay">
          <span class="d-pay-title">缴费记录</span>
          <div v-if="payRecords.length === 0" class="d-empty">暂无缴费记录</div>
          <div v-else class="d-pay-list">
            <div class="dp-item" v-for="p in payRecords" :key="p.id">
              <div class="dp-main">
                <span class="dp-amount">¥{{ money(p.amount) }}</span>
                <span class="dp-method">{{ p.paymentMethod }}</span>
                <span class="dp-type">{{ p.payerType === 'AGENT' ? '柜台代收' : '自助缴费' }}</span>
              </div>
              <div class="dp-meta">
                <span>{{ formatDateTime(p.payTime) }}</span>
                <span class="dp-no">{{ p.paymentNo }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { myBillApi } from '../../api/dashboard'
import { formatDate, formatDateTime } from '../../utils/format'

defineOptions({ name: 'MyBills' })

const tableData = ref([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const size = ref(10)

const detailVisible = ref(false)
const detail = ref(null)
const payRecords = ref([])

const payVisible = ref(false)
const payTarget = ref(null)
const payAmount = ref(0)
const payMethod = ref('微信')
const paying = ref(false)
const methods = ['微信', '支付宝', '银行卡', '现金']

const payMax = computed(() => Number(payTarget.value?.arrearsAmount || 0))

const summary = computed(() => {
  const rows = tableData.value
  const bill = rows.reduce((s, r) => s + Number(r.billAmount || 0), 0)
  const paid = rows.reduce((s, r) => s + Number(r.paidAmount || 0), 0)
  const owed = rows.filter(r => Number(r.arrearsAmount) > 0)
  const owedSum = rows.reduce((s, r) => s + Number(r.arrearsAmount || 0), 0)
  return [
    { label: '本页应缴合计', value: bill.toFixed(2), unit: ' 元', tone: '' },
    { label: '本页已缴合计', value: paid.toFixed(2), unit: ' 元', tone: 'paid' },
    { label: '本页尚欠', value: owedSum.toFixed(2), unit: ' 元', tone: owed.length ? 'warn' : '' },
    { label: '待缴笔数', value: String(owed.length), unit: ' 笔', tone: owed.length ? 'warn' : '' }
  ]
})

const overdueBills = computed(() =>
  tableData.value.filter(r => ['未缴', '逾期', '部分缴纳'].includes(r.paymentStatus))
)
const overdueTotal = computed(() =>
  overdueBills.value.reduce((s, r) => s + Number(r.arrearsAmount || 0), 0).toFixed(2)
)

const detailRows = computed(() => {
  const d = detail.value
  if (!d) return []
  return [
    { label: '账单号', value: '#' + d.id },
    { label: '计费年份', value: d.billingYear + ' 年' },
    { label: '所属客户', value: d.customerName || '—' },
    { label: '用电类别', value: d.electricityCategory || '—' },
    { label: '用电量', value: Number(d.totalConsumption || 0).toFixed(2) + ' kWh' },
    { label: '本期应缴', value: '¥' + money(d.billAmount) },
    { label: '累计已缴', value: '¥' + money(d.paidAmount) },
    { label: '尚欠', value: '¥' + money(d.arrearsAmount) },
    { label: '缴费状态', value: d.paymentStatus },
    { label: '缴费期限', value: formatDate(d.paymentDeadline) },
    { label: '上期止码', value: Number(d.previousReading || 0).toFixed(2) },
    { label: '本期止码', value: Number(d.currentReading || 0).toFixed(2) },
    { label: '抄表时间', value: formatDateTime(d.meterReadingTime) }
  ]
})

function money(v) {
  return Number(v || 0).toFixed(2)
}

function statusTag(s) {
  if (s === '已缴') return 'success'
  if (s === '逾期') return 'danger'
  if (s === '部分缴纳') return 'warning'
  return 'info'
}

async function load() {
  loading.value = true
  try {
    const res = await myBillApi.page({ page: page.value, size: size.value })
    const d = res.data || {}
    tableData.value = d.records || []
    total.value = d.total || 0
  } finally {
    loading.value = false
  }
}

async function openDetail(row) {
  const res = await myBillApi.detail(row.id)
  detail.value = res.data
  detailVisible.value = true
  payRecords.value = []
  const pr = await myBillApi.payments(row.id)
  payRecords.value = pr.data || []
}

function openPay(row) {
  payTarget.value = row
  payAmount.value = Number(Number(row.arrearsAmount || 0).toFixed(2))
  payMethod.value = '微信'
  payVisible.value = true
}

function fillFull() {
  payAmount.value = payMax.value
}

async function submitPay() {
  if (!payTarget.value) return
  const amount = Number(payAmount.value || 0)
  if (amount <= 0) {
    ElMessage.warning('缴费金额必须大于 0')
    return
  }
  if (amount > payMax.value + 1e-6) {
    ElMessage.warning('缴费金额不能超过尚欠金额')
    return
  }
  paying.value = true
  try {
    const res = await myBillApi.pay(payTarget.value.id, {
      amount,
      paymentMethod: payMethod.value
    })
    const d = res.data || {}
    ElMessage.success(`缴费成功，流水号 ${d.paymentNo || '—'}`)
    payVisible.value = false
    // 重新拉一遍：状态、已缴、尚欠都以服务端为准，不在前端改数字
    await load()
  } finally {
    paying.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.notice {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 13px 16px;
  margin-bottom: 10px;
  background: rgba(242, 85, 90, 0.06);
  border-radius: var(--radius-md);
  box-shadow: 0 0 0 1px rgba(242, 85, 90, 0.18);
}

.notice-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.notice-mark {
  width: 3px;
  height: 30px;
  border-radius: 2px;
  background: var(--red);
  flex-shrink: 0;
}

.notice-title {
  margin: 0 0 3px;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--text-primary);
}

.notice-sub {
  margin: 0;
  font-size: 11px;
  color: var(--text-muted);
}

.sum-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin-bottom: 10px;
}

.sum-card {
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding: 12px 14px;
  background: var(--bg-card);
  border-radius: var(--radius-md);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.045);
}

.sum-label { font-size: 11.5px; color: var(--text-muted); }

.sum-value {
  font-family: var(--font-mono);
  font-size: 21px;
  font-weight: 600;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}

.sum-value.warn { color: var(--red); }
.sum-value.paid { color: var(--green); }

.sum-unit {
  font-size: 11px;
  font-style: normal;
  font-weight: 400;
  color: var(--text-faint);
  margin-left: 3px;
}

.num {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
  color: var(--text-primary);
}
.num.owe { color: var(--red); }
.num.paid { color: var(--green); }
.unit { font-size: 10.5px; color: var(--text-faint); margin-left: 2px; }

/* ---------- 缴费弹窗 ---------- */
.pay-body { display: flex; flex-direction: column; gap: 16px; }

.pay-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.pay-bill { display: flex; flex-direction: column; gap: 3px; }

.pay-bill-no { font-size: 13px; font-weight: 600; color: var(--text-primary); }

.pay-bill-meta { font-size: 11px; color: var(--text-muted); }

.pay-figures {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.pf-item {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 10px 12px;
  background: var(--bg-card);
}

.pf-label { font-size: 11px; color: var(--text-muted); }

.pf-value {
  font-family: var(--font-mono);
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}

.pf-value.owe { color: var(--red); }

.pay-field { display: flex; flex-direction: column; gap: 8px; }

.pf-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.pf-title { font-size: 12px; color: var(--text-secondary); }

.pf-link {
  border: none;
  background: none;
  padding: 0;
  font-size: 11.5px;
  color: var(--gold);
  cursor: pointer;
}

.pf-link:hover { text-decoration: underline; }

.pf-hint { font-size: 10.5px; color: var(--text-faint); }

.pay-methods { display: flex; flex-wrap: wrap; gap: 6px; }

.pay-tip {
  margin: 0;
  padding: 9px 11px;
  font-size: 11px;
  line-height: 1.6;
  color: var(--text-muted);
  background: rgba(255, 255, 255, 0.03);
  border-radius: var(--radius-sm);
}

/* ---------- 详情 ---------- */
.detail { display: flex; flex-direction: column; }

.d-row {
  display: flex;
  justify-content: space-between;
  padding: 9px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.04);
  font-size: 12px;
}
.d-row:last-of-type { border-bottom: none; }
.d-label { color: var(--text-muted); }
.d-value { color: var(--text-primary); font-family: var(--font-mono); }

.d-pay {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.07);
}

.d-pay-title {
  display: block;
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
}

.d-empty { font-size: 11.5px; color: var(--text-faint); padding: 6px 0; }

.d-pay-list { display: flex; flex-direction: column; gap: 6px; }

.dp-item {
  padding: 9px 11px;
  background: rgba(255, 255, 255, 0.03);
  border-radius: var(--radius-sm);
}

.dp-main {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 3px;
}

.dp-amount {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 600;
  color: var(--gold);
}

.dp-method { font-size: 11px; color: var(--text-secondary); }
.dp-type { font-size: 10.5px; color: var(--text-faint); }

.dp-meta {
  display: flex;
  justify-content: space-between;
  font-size: 10.5px;
  color: var(--text-faint);
  font-family: var(--font-mono);
}

.dp-no { letter-spacing: 0.2px; }

@media (max-width: 1100px) {
  .sum-grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
