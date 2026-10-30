<template>
  <div class="page-container">
    <div class="filter-bar">
      <el-form :model="searchForm" class="filter-fields">
        <el-form-item label="电表型号"><el-input v-model="searchForm.model" placeholder="搜索" clearable style="width:176px" /></el-form-item>
        <el-form-item label="表计状态">
          <el-select placeholder="全部" v-model="searchForm.meterStatus" clearable style="width:112px">
            <el-option label="正常" value="正常" /><el-option label="故障" value="故障" /><el-option label="待检定" value="待检定" />
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
      <div class="table-header"><span class="table-title">智能电表列表</span><span class="table-count">{{ total }} 条记录</span></div>
      <el-table :data="tableData" v-loading="loading" highlight-current-row max-height="560">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="model" label="电表型号" width="140" />
        <el-table-column prop="installDate" label="安装日期" width="110">
          <template #default="{ row }">{{ formatDate(row.installDate) }}</template>
        </el-table-column>
        <el-table-column prop="meterStatus" label="状态" width="100">
          <template #default="{ row }"><el-tag :type="statusTag(row.meterStatus)" size="small" effect="plain">{{ row.meterStatus }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="ratio" label="倍率" width="90" />
        <el-table-column prop="installLocation" label="安装位置" min-width="160" show-overflow-tooltip />
        <el-table-column prop="customerId" label="客户ID" width="90" />
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" @click="openDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" :page-sizes="[10,20,50,100]" layout="total, sizes, prev, pager, next, jumper" @change="fetchData" class="pagination" />
    </el-card>
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑智能电表' : '新增智能电表'" width="550px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="电表型号" prop="model"><el-input v-model="form.model" /></el-form-item>
        <el-form-item label="安装日期"><el-date-picker v-model="form.installDate" type="date" style="width:100%" /></el-form-item>
        <el-form-item label="检定周期(月)"><el-input-number v-model="form.verificationCycle" :min="0" style="width:100%" /></el-form-item>
        <el-form-item label="表计状态" prop="meterStatus">
          <el-select v-model="form.meterStatus" style="width:100%">
            <el-option label="正常" value="正常" /><el-option label="故障" value="故障" /><el-option label="待检定" value="待检定" />
          </el-select>
        </el-form-item>
        <el-form-item label="安装位置"><el-input v-model="form.installLocation" /></el-form-item>
        <el-form-item label="表计倍率"><el-input-number v-model="form.ratio" :precision="2" :step="0.1" style="width:100%" /></el-form-item>
        <el-form-item label="所属客户" prop="customerId"><el-input-number v-model="form.customerId" :min="1" style="width:100%" /></el-form-item>
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
import { meterApi } from '../../api'
import { formatDate } from '../../utils/format'
import { Plus } from '@element-plus/icons-vue'

const rules = {
  model: [{ required: true, message: '请输入电表型号', trigger: 'blur' }],
  meterStatus: [{ required: true, message: '请选择表计状态', trigger: 'change' }],
  customerId: [{ required: true, message: '请输入客户ID', trigger: 'blur' }]
}

const crud = useCrud(meterApi,
  { model: '', installDate: '', verificationCycle: null, meterStatus: '正常', installLocation: '', bindDate: '', ratio: null, customerId: null },
  rules,
  { model: '', meterStatus: '' }
)
const { tableData, loading, saving, total, page, size, dialogVisible, isEdit, formRef, searchForm, form, fetchData, handleSearch, resetSearch, openDialog, handleSave, handleDelete } = crud

function statusTag(s) {
  const map = { '正常': 'success', '故障': 'danger', '待检定': 'warning' }
  return map[s] || ''
}
</script>
