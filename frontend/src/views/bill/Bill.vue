<template>
  <div class="page-container">
    <div class="filter-bar">
      <el-form :model="searchForm" class="filter-fields">
        <el-form-item label="计费年份"><el-input-number v-model="searchForm.billingYear" :min="2000" :max="2100" :controls="false" clearable placeholder="年份" style="width:124px" /></el-form-item>
        <el-form-item label="缴费状态">
          <el-select placeholder="全部" v-model="searchForm.paymentStatus" clearable style="width:112px">
            <el-option label="已缴" value="已缴" /><el-option label="未缴" value="未缴" />
            <el-option label="部分缴纳" value="部分缴纳" /><el-option label="逾期" value="逾期" />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="filter-actions">
        <el-button text @click="resetSearch">重置</el-button>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="openDialog()"><el-icon><Plus /></el-icon>新增</el-button>
      </div>
    </div>
    <el-card class="table-card">
      <div class="table-header"><span class="table-title">电费账单列表</span><span class="table-count">{{ total }} 条记录</span></div>
      <el-table :data="tableData" v-loading="loading" highlight-current-row max-height="560">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="billingYear" label="年份" width="80" />
        <el-table-column prop="totalConsumption" label="总用电量(kWh)" width="130">
          <template #default="{ row }">{{ row.totalConsumption?.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column label="应缴(元)" width="106" align="right">
          <template #default="{ row }">
            <span style="font-family: var(--font-mono)">{{ money(row.billAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="已缴(元)" width="106" align="right">
          <template #default="{ row }">
            <span style="font-family: var(--font-mono); color: var(--green)">{{ money(row.paidAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="尚欠(元)" width="112" align="right">
          <template #default="{ row }">
            <span :style="{ color: Number(row.arrearsAmount) > 0 ? 'var(--red)' : 'var(--green)', fontWeight: 600, fontFamily: 'var(--font-mono)' }">
              {{ money(row.arrearsAmount) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="paymentStatus" label="缴费状态" width="104">
          <template #default="{ row }"><el-tag :type="payTag(row.paymentStatus)" size="small" effect="plain">{{ row.paymentStatus }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="paymentDeadline" label="缴费期限" width="106">
          <template #default="{ row }">{{ formatDate(row.paymentDeadline) }}</template>
        </el-table-column>
        <el-table-column prop="meterId" label="电表ID" width="76" />
        <el-table-column label="操作" width="196" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="Number(row.arrearsAmount) > 0" size="small" type="primary" @click="openPay(row)">代收</el-button>
            <el-button size="small" @click="openDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" :page-sizes="[10,20,50,100]" layout="total, sizes, prev, pager, next, jumper" @change="fetchData" class="pagination" />
    </el-card>
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑电费账单' : '新增电费账单'" width="600px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="计费年份" prop="billingYear"><el-input-number v-model="form.billingYear" :min="2000" :max="2100" style="width:100%" /></el-form-item>
        <el-form-item label="总用电量" prop="totalConsumption"><el-input-number v-model="form.totalConsumption" :precision="2" :min="0" style="width:100%" /></el-form-item>
        <el-form-item label="欠费金额"><el-input-number v-model="form.arrearsAmount" :precision="2" :min="0" style="width:100%" /></el-form-item>
        <el-form-item label="缴费状态" prop="paymentStatus">
          <el-select v-model="form.paymentStatus" style="width:100%">
            <el-option label="已缴" value="已缴" /><el-option label="未缴" value="未缴" />
            <el-option label="部分缴纳" value="部分缴纳" /><el-option label="逾期" value="逾期" />
          </el-select>
        </el-form-item>
        <el-form-item label="缴费期限"><el-date-picker v-model="form.paymentDeadline" type="date" style="width:100%" /></el-form-item>
        <el-form-item label="本期止码" prop="currentReading"><el-input-number v-model="form.currentReading" :precision="2" :min="0" style="width:100%" /></el-form-item>
        <el-form-item label="上期止码" prop="previousReading"><el-input-number v-model="form.previousReading" :precision="2" :min="0" style="width:100%" /></el-form-item>
        <el-form-item label="所属电表" prop="meterId"><el-input-number v-model="form.meterId" :min="1" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 柜台代收：客户交现金/刷卡时由总负责人录入，与客户自助缴费走同一条后端逻辑 -->
    <el-dialog v-model="payVisible" title="柜台代收" width="440px" :close-on-click-modal="false">
      <div v-if="payRow" class="pay-box">
        <div class="pay-line">
          <span>账单 #{{ payRow.id }} · 客户 {{ payRow.customerId }}</span>
          <el-tag :type="payTag(payRow.paymentStatus)" size="small" effect="plain">{{ payRow.paymentStatus }}</el-tag>
        </div>
        <div class="pay-figures">
          <div class="pf"><span class="pf-l">应缴</span><span class="pf-v">¥{{ money(payRow.billAmount) }}</span></div>
          <div class="pf"><span class="pf-l">已缴</span><span class="pf-v" style="color: var(--green)">¥{{ money(payRow.paidAmount) }}</span></div>
          <div class="pf"><span class="pf-l">尚欠</span><span class="pf-v" style="color: var(--red)">¥{{ money(payRow.arrearsAmount) }}</span></div>
        </div>
        <el-form label-width="96px" style="margin-top: 14px">
          <el-form-item label="本次收款">
            <el-input-number v-model="payAmount" :min="0.01" :max="Number(payRow.arrearsAmount)" :precision="2" :step="10" controls-position="right" style="width: 100%" />
          </el-form-item>
          <el-form-item label="收款方式">
            <el-radio-group v-model="payMethod">
              <el-radio-button v-for="m in payMethods" :key="m" :value="m">{{ m }}</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-form>
        <p class="pay-note">
          代收会写入缴费流水并同步更新客户侧账单状态，与客户自己缴费完全一致。
        </p>
      </div>
      <template #footer>
        <el-button @click="payVisible = false">取消</el-button>
        <el-button type="primary" :loading="paying" @click="submitPay">确认收款 ¥{{ money(payAmount) }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { useCrud } from '../../composables/useCrud'
import { billApi } from '../../api'
import { formatDate } from '../../utils/format'
import { Plus } from '@element-plus/icons-vue'
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'

const rules = {
  billingYear: [{ required: true, message: '请输入计费年份', trigger: 'blur' }],
  totalConsumption: [{ required: true, message: '请输入总用电量', trigger: 'blur' }],
  paymentStatus: [{ required: true, message: '请选择缴费状态', trigger: 'change' }],
  currentReading: [{ required: true, message: '请输入本期止码', trigger: 'blur' }],
  previousReading: [{ required: true, message: '请输入上期止码', trigger: 'blur' }],
  meterId: [{ required: true, message: '请输入电表ID', trigger: 'blur' }]
}

const crud = useCrud(billApi,
  { billingYear: new Date().getFullYear(), totalConsumption: null, arrearsAmount: null, paymentStatus: '', paymentDeadline: '', currentReading: null, previousReading: null, meterId: null, generateTime: new Date().toISOString(), meterReadingTime: new Date().toISOString() },
  rules,
  { billingYear: null, paymentStatus: '' }
)
const { tableData, loading, saving, total, page, size, dialogVisible, isEdit, formRef, searchForm, form, fetchData, handleSearch, resetSearch, openDialog, handleSave, handleDelete } = crud

function payTag(s) {
  const map = { '已缴': 'success', '未缴': 'danger', '部分缴纳': 'warning', '逾期': 'info' }
  return map[s] || ''
}

/* ---------- 柜台代收 ---------- */
const payVisible = ref(false)
const payRow = ref(null)
const payAmount = ref(0)
const payMethod = ref('现金')
const paying = ref(false)
const payMethods = ['现金', '银行卡', '微信', '支付宝']

function money(v) {
  return Number(v || 0).toFixed(2)
}

function openPay(row) {
  payRow.value = row
  payAmount.value = Number(Number(row.arrearsAmount || 0).toFixed(2))
  payMethod.value = '现金'
  payVisible.value = true
}

async function submitPay() {
  if (!payRow.value) return
  const amount = Number(payAmount.value || 0)
  const max = Number(payRow.value.arrearsAmount || 0)
  if (amount <= 0) {
    ElMessage.warning('收款金额必须大于 0')
    return
  }
  if (amount > max + 1e-6) {
    ElMessage.warning('收款金额不能超过尚欠金额')
    return
  }
  paying.value = true
  try {
    const res = await request.post(`/bill/${payRow.value.id}/pay`, {
      amount,
      paymentMethod: payMethod.value
    })
    ElMessage.success(`代收成功，流水号 ${res.data?.paymentNo || '—'}`)
    payVisible.value = false
    await fetchData()
  } finally {
    paying.value = false
  }
}
</script>

<style scoped>
.pay-box { display: flex; flex-direction: column; }

.pay-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12.5px;
  color: var(--text-primary);
  margin-bottom: 12px;
}

.pay-figures {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.pf {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 10px 12px;
  background: var(--bg-card);
}

.pf-l { font-size: 11px; color: var(--text-muted); }

.pf-v {
  font-family: var(--font-mono);
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}

.pay-note {
  margin: 12px 0 0;
  padding: 9px 11px;
  font-size: 11px;
  line-height: 1.6;
  color: var(--text-muted);
  background: rgba(255, 255, 255, 0.03);
  border-radius: var(--radius-sm);
}
</style>
