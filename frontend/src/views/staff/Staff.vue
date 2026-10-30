<template>
  <div class="page-container">
    <div class="filter-bar">
      <el-form :model="searchForm" class="filter-fields">
        <el-form-item label="姓名"><el-input v-model="searchForm.name" placeholder="搜索" clearable style="width:176px" /></el-form-item>
        <el-form-item label="部门">
          <el-select placeholder="全部" v-model="searchForm.department" clearable style="width:132px">
            <el-option label="运维部" value="运维部" /><el-option label="检修部" value="检修部" />
            <el-option label="技术部" value="技术部" /><el-option label="管理部" value="管理部" />
          </el-select>
        </el-form-item>
        <el-form-item label="岗位">
          <el-select placeholder="全部" v-model="searchForm.position" clearable style="width:132px">
            <el-option label="高级工程师" value="高级工程师" /><el-option label="工程师" value="工程师" />
            <el-option label="助理工程师" value="助理工程师" /><el-option label="技术员" value="技术员" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select placeholder="全部" v-model="searchForm.workStatus" clearable style="width:112px">
            <el-option label="在岗" value="在岗" /><el-option label="休假" value="休假" /><el-option label="离职" value="离职" />
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
      <div class="table-header"><span class="table-title">运维人员列表</span><span class="table-count">{{ total }} 条记录</span></div>
      <el-table :data="tableData" v-loading="loading" highlight-current-row max-height="560">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="姓名" width="120">
          <template #default="{ row }">
            <div class="staff-name">
              <div class="staff-avatar">{{ row.name.charAt(0) }}</div>
              <span>{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="department" label="部门" width="150" />
        <el-table-column prop="position" label="岗位" width="140" />
        <el-table-column prop="phone" label="联系电话" width="140" />
        <el-table-column prop="workStatus" label="状态" width="100">
          <template #default="{ row }"><el-tag :type="statusTag(row.workStatus)" size="small" effect="plain">{{ row.workStatus }}</el-tag></template>
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
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑运维人员' : '新增运维人员'" width="500px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="姓名" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="所属部门" prop="department">
          <el-select v-model="form.department" style="width:100%">
            <el-option label="运维部" value="运维部" /><el-option label="检修部" value="检修部" />
            <el-option label="技术部" value="技术部" /><el-option label="管理部" value="管理部" />
          </el-select>
        </el-form-item>
        <el-form-item label="岗位">
          <el-select v-model="form.position" clearable style="width:100%">
            <el-option label="高级工程师" value="高级工程师" /><el-option label="工程师" value="工程师" />
            <el-option label="助理工程师" value="助理工程师" /><el-option label="技术员" value="技术员" />
          </el-select>
        </el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="在岗状态" prop="workStatus">
          <el-select v-model="form.workStatus" style="width:100%">
            <el-option label="在岗" value="在岗" /><el-option label="休假" value="休假" /><el-option label="离职" value="离职" />
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
import { staffApi } from '../../api'
import { Plus } from '@element-plus/icons-vue'

const rules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  workStatus: [{ required: true, message: '请选择在岗状态', trigger: 'change' }]
}

const crud = useCrud(staffApi,
  { name: '', department: '', position: '', phone: '', workStatus: '在岗' },
  rules,
  { name: '', department: '', position: '', workStatus: '' }
)
const { tableData, loading, saving, total, page, size, dialogVisible, isEdit, formRef, searchForm, form, fetchData, handleSearch, resetSearch, openDialog, handleSave, handleDelete } = crud

function statusTag(s) {
  const map = { '在岗': 'success', '休假': 'warning', '离职': 'info' }
  return map[s] || ''
}
</script>

<style scoped>
.staff-name {
  display: flex;
  align-items: center;
  gap: 8px;
}
.staff-avatar {
  width: 24px;
  height: 24px;
  border-radius: var(--radius-sm);
  background: rgba(245, 185, 66, 0.1);
  box-shadow: 0 0 0 1px rgba(245, 185, 66, 0.22);
  color: var(--gold);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 600;
  flex-shrink: 0;
}
</style>
