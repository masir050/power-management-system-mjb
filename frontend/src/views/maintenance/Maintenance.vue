<template>
  <div class="page-container">
    <div class="filter-bar">
      <el-form :model="searchForm" class="filter-fields">
        <el-form-item label="故障类型"><el-input v-model="searchForm.faultType" placeholder="搜索" clearable style="width:176px" /></el-form-item>
        <el-form-item label="优先级">
          <el-select placeholder="全部" v-model="searchForm.repairPriority" clearable style="width:112px">
            <el-option label="紧急" value="紧急" /><el-option label="高" value="高" />
            <el-option label="中" value="中" /><el-option label="低" value="低" />
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
      <div class="table-header"><span class="table-title">检修记录列表</span><span class="table-count">{{ total }} 条记录</span></div>
      <el-table :data="tableData" v-loading="loading" highlight-current-row max-height="560">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="occurrenceTime" label="发生时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.occurrenceTime) }}</template>
        </el-table-column>
        <el-table-column prop="faultType" label="故障类型" width="120">
          <template #default="{ row }"><el-tag :type="faultTag(row.faultType)" size="small" effect="plain">{{ row.faultType }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="faultDescription" label="故障描述" min-width="180" show-overflow-tooltip />
        <el-table-column prop="repairResult" label="检修结果" min-width="160" show-overflow-tooltip />
        <el-table-column prop="repairPriority" label="优先级" width="90">
          <template #default="{ row }"><el-tag :type="priorityTag(row.repairPriority)" size="small" effect="plain">{{ row.repairPriority }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="equipmentId" label="设备ID" width="80" />
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" @click="openDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" :page-sizes="[10,20,50,100]" layout="total, sizes, prev, pager, next, jumper" @change="fetchData" class="pagination" />
    </el-card>
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑检修记录' : '新增检修记录'" width="600px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="故障类型" prop="faultType"><el-input v-model="form.faultType" /></el-form-item>
        <el-form-item label="故障描述"><el-input v-model="form.faultDescription" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="检修结果"><el-input v-model="form.repairResult" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="完成时间"><el-date-picker v-model="form.completionTime" type="datetime" style="width:100%" /></el-form-item>
        <el-form-item label="检修工时"><el-input-number v-model="form.repairHours" :precision="2" :min="0" :step="0.5" style="width:100%" /></el-form-item>
        <el-form-item label="抢修优先级">
          <el-select v-model="form.repairPriority" clearable style="width:100%">
            <el-option label="紧急" value="紧急" /><el-option label="高" value="高" /><el-option label="中" value="中" /><el-option label="低" value="低" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联设备" prop="equipmentId"><el-input-number v-model="form.equipmentId" :min="1" style="width:100%" /></el-form-item>
        <el-form-item label="运维人员" prop="staffId"><el-input-number v-model="form.staffId" :min="1" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { useCrud } from '../../composables/useCrud'
import { maintenanceApi } from '../../api'
import { formatDateTime } from '../../utils/format'
import { Plus } from '@element-plus/icons-vue'

const rules = {
  faultType: [{ required: true, message: '请输入故障类型', trigger: 'blur' }],
  equipmentId: [{ required: true, message: '请输入设备ID', trigger: 'blur' }],
  staffId: [{ required: true, message: '请输入运维人员ID', trigger: 'blur' }]
}

const crud = useCrud(maintenanceApi,
  { faultType: '', faultDescription: '', repairResult: '', completionTime: '', repairHours: null, repairPriority: '', equipmentId: null, staffId: null, occurrenceTime: new Date().toISOString() },
  rules,
  { faultType: '', repairPriority: '' }
)
const { tableData, loading, saving, total, page, size, dialogVisible, isEdit, formRef, searchForm, form, fetchData, handleSearch, resetSearch, openDialog, handleSave, handleDelete } = crud

function faultTag(f) {
  const map = { '设备过热': 'danger', '开关跳闸': 'warning', '表计故障': 'primary', '绝缘降低': 'info', '设备故障': 'danger' }
  return map[f] || ''
}
function priorityTag(p) {
  const map = { '紧急': 'danger', '高': 'warning', '中': 'primary', '低': 'info' }
  return map[p] || ''
}
</script>
