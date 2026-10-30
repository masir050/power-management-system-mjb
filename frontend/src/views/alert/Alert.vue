<template>
  <div class="page-container">
    <!-- 统计概览：数据来自 /api/alert/statistics，一次聚合查询算出各状态/级别计数 -->
    <div class="stat-grid">
      <div class="stat-card" v-for="card in statCards" :key="card.key">
        <div class="stat-label">
          <span class="stat-mark" :style="{ background: card.color }"></span>
          {{ card.label }}
        </div>
        <div class="stat-value">{{ stat[card.key] ?? 0 }}</div>
      </div>
    </div>

    <div class="filter-bar">
      <el-form :model="searchForm" class="filter-fields">
        <el-form-item label="告警级别">
          <el-select v-model="searchForm.alertLevel" clearable placeholder="全部" style="width:118px">
            <el-option v-for="l in levels" :key="l" :label="l" :value="l" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.alertStatus" clearable placeholder="全部" style="width:118px">
            <el-option label="待处理" value="PENDING" />
            <el-option label="已确认" value="ACKNOWLEDGED" />
            <el-option label="已处理" value="RESOLVED" />
          </el-select>
        </el-form-item>
        <el-form-item label="故障类型">
          <el-input v-model="searchForm.faultType" placeholder="模糊搜索" clearable style="width:150px" />
        </el-form-item>
        <el-form-item label="设备ID">
          <el-input-number v-model="searchForm.equipmentId" :min="1" :controls="false" placeholder="精确" style="width:100px" />
        </el-form-item>
      </el-form>
      <div class="filter-actions">
        <span class="ws-hint" :class="{ online: wsOnline }">
          <span class="status-dot" :class="wsOnline ? 'running' : 'offline'"></span>
          {{ wsOnline ? '实时通道已连接' : '实时通道未连接' }}
        </span>
        <el-button text @click="resetSearch">重置</el-button>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="openCreate"><el-icon><Plus /></el-icon>新增告警</el-button>
      </div>
    </div>

    <el-card class="table-card">
      <div class="table-header">
        <span class="table-title">告警列表</span>
        <span class="table-count">共 {{ total }} 条</span>
      </div>
      <el-table :data="tableData" v-loading="loading" highlight-current-row max-height="560">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="occurrenceTime" label="发生时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.occurrenceTime) }}</template>
        </el-table-column>
        <el-table-column prop="equipmentName" label="设备" min-width="150" show-overflow-tooltip />
        <el-table-column prop="faultType" label="故障类型" width="120" />
        <el-table-column prop="alertDescription" label="告警描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="alertLevel" label="级别" width="90">
          <template #default="{ row }">
            <el-tag :type="levelTag(row.alertLevel)" size="small" effect="plain">{{ row.alertLevel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="alertStatus" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.alertStatus)" size="small">{{ statusText(row.alertStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="handleResult" label="处理结果" min-width="150" show-overflow-tooltip />
        <el-table-column label="操作" width="170" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.alertStatus === 'PENDING'"
              size="small"
              @click="handleAcknowledge(row)"
            >确认</el-button>
            <el-button
              v-if="row.alertStatus !== 'RESOLVED'"
              size="small"
              @click="openResolve(row)"
            >处理</el-button>
            <span v-if="row.alertStatus === 'RESOLVED'" class="done-text">已归档</span>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @change="fetchData"
        class="pagination"
      />
    </el-card>

    <!-- 新增告警：提交后由后端写 alert + local_message 同事务，再异步投递 MQ 推送 -->
    <el-dialog v-model="createVisible" title="新增告警" width="620px" :close-on-click-modal="false">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="100px">
        <el-form-item label="设备ID" prop="equipmentId">
          <el-input-number v-model="createForm.equipmentId" :min="1" style="width:100%" />
        </el-form-item>
        <el-form-item label="故障类型" prop="faultType">
          <el-input v-model="createForm.faultType" placeholder="如 设备过热 / 开关跳闸 / 绝缘降低" />
        </el-form-item>
        <el-form-item label="告警描述" prop="alertDescription">
          <el-input v-model="createForm.alertDescription" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="告警级别" prop="alertLevel">
          <el-select v-model="createForm.alertLevel" style="width:100%">
            <el-option v-for="l in levels" :key="l" :label="l" :value="l" />
          </el-select>
        </el-form-item>
        <el-form-item label="触发时间">
          <el-date-picker v-model="createForm.occurrenceTime" type="datetime" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">提交</el-button>
      </template>
    </el-dialog>

    <!-- 处理告警：状态流转走后端乐观锁，并发操作时后到者会收到"已被他人处理" -->
    <el-dialog v-model="resolveVisible" title="处理告警" width="520px">
      <el-form label-width="100px">
        <el-form-item label="告警ID">{{ resolveTarget.id }}</el-form-item>
        <el-form-item label="处理人ID">
          <el-input-number v-model="resolveForm.handlerId" :min="1" style="width:100%" />
        </el-form-item>
        <el-form-item label="处理结果">
          <el-input v-model="resolveForm.handleResult" type="textarea" :rows="3" placeholder="填写处理措施与结果" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resolveVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitResolve">确认处理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { alertApi } from '../../api/alert'
import { onSocketState } from '../../utils/alertSocket'
import { formatDateTime } from '../../utils/format'

defineOptions({ name: 'Alert' })

const levels = ['紧急', '高', '中', '低']

const stat = ref({})
const statCards = [
  { key: 'totalAlerts', label: '告警总数', color: '#EDEAE4' },
  { key: 'pending', label: '待处理', color: '#F2555A' },
  { key: 'acknowledged', label: '已确认', color: '#F5B942' },
  { key: 'resolved', label: '已处理', color: '#4ADE80' },
  { key: 'urgent', label: '紧急', color: '#F2555A' },
  { key: 'avgResolveMinutes', label: '平均处理(分)', color: '#C4922C' }
]

const tableData = ref([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const size = ref(10)
const searchForm = reactive({ alertLevel: '', alertStatus: '', faultType: '', equipmentId: null })

// 后端 pageDTO 的字段名是 page/size，与其它页面保持一致
async function fetchData() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    for (const [k, v] of Object.entries(searchForm)) {
      if (v !== null && v !== '') params[k] = v
    }
    const res = await alertApi.pageList(params)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

async function fetchStat() {
  const res = await alertApi.statistics()
  stat.value = res.data || {}
}

function handleSearch() {
  page.value = 1
  fetchData()
}

function resetSearch() {
  Object.assign(searchForm, { alertLevel: '', alertStatus: '', faultType: '', equipmentId: null })
  page.value = 1
  fetchData()
}

/* ---------- 新增 ---------- */
const createVisible = ref(false)
const createFormRef = ref(null)
const submitting = ref(false)
const createForm = reactive({
  equipmentId: null, faultType: '', alertDescription: '', alertLevel: '中', occurrenceTime: null
})
const createRules = {
  equipmentId: [{ required: true, message: '请输入设备ID', trigger: 'blur' }],
  faultType: [{ required: true, message: '请输入故障类型', trigger: 'blur' }],
  alertDescription: [{ required: true, message: '请输入告警描述', trigger: 'blur' }],
  alertLevel: [{ required: true, message: '请选择告警级别', trigger: 'change' }]
}

function openCreate() {
  Object.assign(createForm, {
    equipmentId: null, faultType: '', alertDescription: '', alertLevel: '中', occurrenceTime: new Date()
  })
  createVisible.value = true
}

async function submitCreate() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await alertApi.create({ ...createForm, occurrenceTime: toSubmitTime(createForm.occurrenceTime) })
    ElMessage.success('告警已创建，推送将异步送达')
    createVisible.value = false
    fetchData()
    fetchStat()
  } finally {
    submitting.value = false
  }
}

/* ---------- 确认 / 处理 ---------- */
const resolveVisible = ref(false)
const resolveTarget = ref({})
const resolveForm = reactive({ handlerId: 1, handleResult: '' })

async function handleAcknowledge(row) {
  try {
    await alertApi.acknowledge(row.id, 1)
    ElMessage.success('已确认')
    fetchData()
    fetchStat()
  } catch {
    // 拦截器已弹错误提示（含"已被他人处理"）
  }
}

function openResolve(row) {
  resolveTarget.value = row
  resolveForm.handlerId = row.handlerId || 1
  resolveForm.handleResult = ''
  resolveVisible.value = true
}

async function submitResolve() {
  if (!resolveForm.handleResult.trim()) {
    ElMessage.warning('请填写处理结果')
    return
  }
  submitting.value = true
  try {
    await alertApi.resolve(resolveTarget.value.id, resolveForm.handlerId, resolveForm.handleResult)
    ElMessage.success('已处理')
    resolveVisible.value = false
    fetchData()
    fetchStat()
  } finally {
    submitting.value = false
  }
}

/* ---------- 展示辅助 ---------- */
function levelTag(level) {
  return { 紧急: 'danger', 高: 'warning', 中: 'primary', 低: 'info' }[level] || 'info'
}
function statusTag(s) {
  return { PENDING: 'danger', ACKNOWLEDGED: 'warning', RESOLVED: 'success' }[s] || 'info'
}
function statusText(s) {
  return { PENDING: '待处理', ACKNOWLEDGED: '已确认', RESOLVED: '已处理' }[s] || s
}
// 提交表单用：DatePicker 给的是 Date 对象，需要转成后端吃的字符串
function toSubmitTime(d) {
  if (!d) return null
  const date = d instanceof Date ? d : new Date(d)
  const pad = n => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

// WebSocket 是否连通只做状态展示，真正的推送处理在 utils/alertSocket.js（全局一处）
const wsOnline = ref(false)
let unsubSocket = null

onMounted(() => {
  fetchData()
  fetchStat()
  unsubSocket = onSocketState(connected => { wsOnline.value = connected })
})

onUnmounted(() => {
  if (unsubSocket) unsubSocket()
})
</script>

<style scoped>
/* 与概览页 KPI 卡同构：标签在上、数值在下，左侧一根语义色细条 */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 10px;
  margin-bottom: 12px;
}

.stat-card {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 12px 14px;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.045);
  transition: box-shadow 0.2s ease, background 0.2s ease;
}
.stat-card:hover {
  background: var(--bg-raised);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.085);
}

.stat-label {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 11.5px;
  letter-spacing: 0.02em;
  color: var(--text-muted);
  margin-bottom: 7px;
  white-space: nowrap;
}

.stat-mark {
  width: 5px;
  height: 5px;
  border-radius: 1px;
  flex-shrink: 0;
}

.stat-value {
  font-family: var(--font-mono);
  font-size: 21px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}

.ws-hint {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-muted);
  margin-right: 4px;
}
.ws-hint.online { color: var(--green); }

.done-text { font-size: 12px; color: var(--text-muted); }

@media (max-width: 1400px) {
  .stat-grid { grid-template-columns: repeat(3, 1fr); }
}
@media (max-width: 900px) {
  .stat-grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
