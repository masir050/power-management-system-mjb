<template>
  <div class="page-container">
    <div class="filter-bar">
      <el-form :model="searchForm" class="filter-fields">
        <el-form-item label="客户名称"><el-input v-model="searchForm.customerName" placeholder="搜索" clearable style="width:176px" /></el-form-item>
        <el-form-item label="用电类别">
          <el-select placeholder="全部" v-model="searchForm.electricityCategory" clearable style="width:124px">
            <el-option label="居民" value="居民" /><el-option label="商业" value="商业" />
            <el-option label="工业" value="工业" /><el-option label="农业" value="农业" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select placeholder="全部" v-model="searchForm.electricityStatus" clearable style="width:112px">
            <el-option label="正常用电" value="正常用电" /><el-option label="暂停" value="暂停" /><el-option label="销户" value="销户" />
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
      <div class="table-header"><span class="table-title">用电客户列表</span><span class="table-count">{{ total }} 条记录</span></div>
      <el-table :data="tableData" v-loading="loading" highlight-current-row max-height="560">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="customerName" label="客户名称" min-width="160" />
        <el-table-column prop="electricityCategory" label="用电类别" width="120">
          <template #default="{ row }"><el-tag :type="catTag(row.electricityCategory)" size="small" effect="plain">{{ row.electricityCategory }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="address" label="地址" min-width="180" show-overflow-tooltip />
        <el-table-column prop="contact" label="联系方式" width="130" />
        <el-table-column prop="electricityStatus" label="状态" width="100">
          <template #default="{ row }"><el-tag :type="row.electricityStatus === '正常用电' ? 'success' : 'info'" size="small" effect="plain">{{ row.electricityStatus }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" @click="openDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" :page-sizes="[10,20,50,100]" layout="total, sizes, prev, pager, next, jumper" @change="fetchData" class="pagination" />
    </el-card>
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑用电客户' : '新增用电客户'" width="550px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="客户名称" prop="customerName"><el-input v-model="form.customerName" /></el-form-item>
        <el-form-item label="用电类别" prop="electricityCategory">
          <el-select v-model="form.electricityCategory" style="width:100%">
            <el-option label="居民" value="居民" /><el-option label="商业" value="商业" />
            <el-option label="工业" value="工业" /><el-option label="农业" value="农业" />
          </el-select>
        </el-form-item>
        <el-form-item label="地址"><el-input v-model="form.address" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="联系方式"><el-input v-model="form.contact" /></el-form-item>
        <el-form-item label="用电状态" prop="electricityStatus">
          <el-select v-model="form.electricityStatus" style="width:100%">
            <el-option label="正常用电" value="正常用电" /><el-option label="暂停" value="暂停" /><el-option label="销户" value="销户" />
          </el-select>
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
import { customerApi } from '../../api'
import { Plus } from '@element-plus/icons-vue'

const rules = {
  customerName: [{ required: true, message: '请输入客户名称', trigger: 'blur' }],
  electricityCategory: [{ required: true, message: '请选择用电类别', trigger: 'change' }],
  electricityStatus: [{ required: true, message: '请选择用电状态', trigger: 'change' }]
}

const crud = useCrud(customerApi,
  { customerName: '', electricityCategory: '', address: '', contact: '', electricityStatus: '正常用电' },
  rules,
  { customerName: '', electricityCategory: '', electricityStatus: '' }
)
const { tableData, loading, saving, total, page, size, dialogVisible, isEdit, formRef, searchForm, form, fetchData, handleSearch, resetSearch, openDialog, handleSave, handleDelete } = crud

function catTag(c) {
  const map = { '居民': '', '商业': 'primary', '工业': 'danger', '农业': 'success' }
  return map[c] || ''
}
</script>
