<template>
  <div class="page-container">
    <div class="filter-bar">
      <el-form :model="searchForm" class="filter-fields">
        <el-form-item label="名称"><el-input v-model="searchForm.name" placeholder="搜索" clearable style="width:176px" /></el-form-item>
        <el-form-item label="电压等级">
          <el-select v-model="searchForm.voltageLevel" placeholder="全部" clearable style="width:112px">
            <el-option label="10kV" value="10kV" /><el-option label="35kV" value="35kV" />
            <el-option label="110kV" value="110kV" /><el-option label="220kV" value="220kV" />
            <el-option label="500kV" value="500kV" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.runStatus" placeholder="全部" clearable style="width:112px">
            <el-option label="运行中" value="运行中" /><el-option label="停运" value="停运" /><el-option label="检修中" value="检修中" />
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
      <div class="table-header">
        <span class="table-title">变电站列表</span>
        <span class="table-count">{{ total }} 条记录</span>
      </div>
      <el-table :data="tableData" v-loading="loading" highlight-current-row max-height="560">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="voltageLevel" label="电压等级" width="110">
          <template #default="{ row }">
            <el-tag :type="voltageTag(row.voltageLevel)" size="small" effect="plain">{{ row.voltageLevel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="commissionDate" label="投运时间" width="120" />
        <el-table-column prop="runStatus" label="运行状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.runStatus)" size="small" effect="plain">{{ row.runStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="areaId" label="区域ID" width="80" />
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" @click="openDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" :page-sizes="[10,20,50,100]" layout="total, sizes, prev, pager, next, jumper" @change="fetchData" class="pagination" />
    </el-card>
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑变电站' : '新增变电站'" width="560px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="电压等级" prop="voltageLevel">
          <el-select v-model="form.voltageLevel" style="width:100%">
            <el-option label="10kV" value="10kV" /><el-option label="35kV" value="35kV" />
            <el-option label="110kV" value="110kV" /><el-option label="220kV" value="220kV" />
            <el-option label="500kV" value="500kV" /><el-option label="750kV" value="750kV" />
          </el-select>
        </el-form-item>
        <el-form-item label="投运时间" prop="commissionDate"><el-date-picker v-model="form.commissionDate" type="date" style="width:100%" /></el-form-item>
        <el-form-item label="运行状态" prop="runStatus">
          <el-select v-model="form.runStatus" style="width:100%">
            <el-option label="运行中" value="运行中" /><el-option label="停运" value="停运" />
            <el-option label="检修中" value="检修中" /><el-option label="备用" value="备用" />
          </el-select>
        </el-form-item>
        <el-form-item label="管辖年限"><el-input-number v-model="form.jurisdictionYears" :min="0" style="width:100%" /></el-form-item>
        <el-form-item label="管理等级"><el-input v-model="form.managementLevel" /></el-form-item>
        <el-form-item label="所属区域" prop="areaId"><el-input-number v-model="form.areaId" :min="1" style="width:100%" /></el-form-item>
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
import { substationApi } from '../../api'
import { Plus } from '@element-plus/icons-vue'

const rules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  voltageLevel: [{ required: true, message: '请选择电压等级', trigger: 'change' }],
  commissionDate: [{ required: true, message: '请选择投运时间', trigger: 'change' }],
  runStatus: [{ required: true, message: '请选择运行状态', trigger: 'change' }],
  areaId: [{ required: true, message: '请输入区域ID', trigger: 'blur' }]
}

const crud = useCrud(substationApi,
  { name: '', voltageLevel: '', commissionDate: '', runStatus: '运行中', jurisdictionYears: null, managementLevel: '', areaId: null },
  rules,
  { name: '', voltageLevel: '', runStatus: '' }
)
const { tableData, loading, saving, total, page, size, dialogVisible, isEdit, formRef, searchForm, form, fetchData, handleSearch, resetSearch, openDialog, handleSave, handleDelete } = crud

function voltageTag(v) {
  const n = parseInt(v)
  if (n >= 220) return 'danger'
  if (n >= 110) return 'warning'
  return 'primary'
}
function statusTag(s) {
  const map = { '运行中': 'success', '停运': 'info', '检修中': 'warning', '备用': 'primary' }
  return map[s] || ''
}
</script>
