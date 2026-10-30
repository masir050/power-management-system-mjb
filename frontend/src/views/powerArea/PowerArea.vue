<template>
  <div class="page-container">
    <div class="filter-bar">
      <el-form :model="searchForm" class="filter-fields">
        <el-form-item label="区域名称">
          <el-input v-model="searchForm.areaName" placeholder="搜索区域名称" clearable style="width:176px" />
        </el-form-item>
        <el-form-item label="行政等级">
          <el-select v-model="searchForm.adminLevel" placeholder="全部" clearable style="width:132px">
            <el-option label="省级" value="省级" />
            <el-option label="市级" value="市级" />
            <el-option label="县级" value="县级" />
            <el-option label="乡镇级" value="乡镇级" />
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
        <span class="table-title">供电区域列表</span>
        <span class="table-count">{{ total }} 条记录</span>
      </div>
      <el-table :data="tableData" v-loading="loading" highlight-current-row max-height="560">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="areaName" label="区域名称" min-width="140" />
        <el-table-column prop="adminLevel" label="行政等级" width="120">
          <template #default="{ row }">
            <el-tag :type="levelTag(row.adminLevel)" size="small" effect="plain">
              {{ row.adminLevel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="jurisdiction" label="管辖范围" min-width="180" show-overflow-tooltip />
        <el-table-column prop="leader" label="负责人" width="120" />
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" @click="openDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="page" v-model:page-size="size"
        :total="total" :page-sizes="[10,20,50,100]"
        layout="total, sizes, prev, pager, next, jumper"
        @change="fetchData" class="pagination"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑供电区域' : '新增供电区域'" width="520px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="区域名称" prop="areaName">
          <el-input v-model="form.areaName" placeholder="请输入区域名称" />
        </el-form-item>
        <el-form-item label="行政等级" prop="adminLevel">
          <el-select v-model="form.adminLevel" placeholder="请选择" style="width:100%">
            <el-option label="省级" value="省级" />
            <el-option label="市级" value="市级" />
            <el-option label="县级" value="县级" />
            <el-option label="乡镇级" value="乡镇级" />
          </el-select>
        </el-form-item>
        <el-form-item label="管辖范围" prop="jurisdiction">
          <el-input v-model="form.jurisdiction" type="textarea" :rows="3" placeholder="请输入管辖范围" />
        </el-form-item>
        <el-form-item label="负责人" prop="leader">
          <el-input v-model="form.leader" placeholder="请输入负责人" />
        </el-form-item>
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
import { powerAreaApi } from '../../api'
import { Plus } from '@element-plus/icons-vue'

const rules = {
  areaName: [{ required: true, message: '请输入区域名称', trigger: 'blur' }],
  adminLevel: [{ required: true, message: '请选择行政等级', trigger: 'change' }]
}

const defaultForm = { areaName: '', adminLevel: '', jurisdiction: '', leader: '' }
const searchFormTemplate = { areaName: '', adminLevel: '' }

const crud = useCrud(powerAreaApi, defaultForm, rules, searchFormTemplate)
const {
  tableData, loading, saving, total, page, size,
  dialogVisible, isEdit, formRef, searchForm, form,
  fetchData, handleSearch, resetSearch, openDialog, handleSave, handleDelete
} = crud

function levelTag(level) {
  const map = { '省级': 'danger', '市级': 'primary', '县级': 'warning', '乡镇级': 'info' }
  return map[level] || ''
}
</script>
