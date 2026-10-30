<template>
  <div class="page-container">
    <div class="filter-bar">
      <el-form :model="searchForm" class="filter-fields">
        <el-form-item label="设备名称"><el-input v-model="searchForm.equipmentName" placeholder="搜索" clearable style="width:176px" /></el-form-item>
        <el-form-item label="设备类型">
          <el-select v-model="searchForm.equipmentType" clearable placeholder="全部" style="width:132px">
            <el-option label="变压器" value="变压器" /><el-option label="断路器" value="断路器" />
            <el-option label="隔离开关" value="隔离开关" /><el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="隐患等级">
          <el-select v-model="searchForm.hazardLevel" clearable placeholder="全部" style="width:112px">
            <el-option label="一级" value="一级" /><el-option label="二级" value="二级" /><el-option label="三级" value="三级" />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="filter-actions">
        <el-button text @click="resetSearch">重置</el-button>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="openDialog()"><el-icon><Plus /></el-icon>新增设备</el-button>
      </div>
    </div>

    <el-card class="table-card">
      <div class="table-header"><span class="table-title">电力设备</span><span class="table-count">{{ total }} 条记录</span></div>
      <el-table :data="tableData" v-loading="loading" highlight-current-row max-height="560">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="equipmentName" label="设备名称" min-width="150" />
        <el-table-column prop="equipmentType" label="设备类型" width="120">
          <template #default="{ row }"><el-tag :type="typeTag(row.equipmentType)" size="small" effect="plain">{{ row.equipmentType }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="ratedParams" label="额定参数" min-width="160" show-overflow-tooltip />
        <el-table-column prop="hazardLevel" label="隐患等级" width="100">
          <template #default="{ row }"><el-tag :type="hazardTag(row.hazardLevel)" size="small" effect="plain">{{ row.hazardLevel }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="70" />
        <el-table-column prop="substationId" label="变电站ID" width="100" />
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" @click="openDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" :page-sizes="[10,20,50,100]" layout="total, sizes, prev, pager, next, jumper" @change="fetchData" class="pagination" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑电力设备' : '新增电力设备'" width="600px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="设备名称" prop="equipmentName"><el-input v-model="form.equipmentName" /></el-form-item>
        <el-form-item label="设备类型" prop="equipmentType">
          <el-select v-model="form.equipmentType" style="width:100%">
            <el-option label="变压器" value="变压器" /><el-option label="断路器" value="断路器" />
            <el-option label="隔离开关" value="隔离开关" /><el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="额定参数"><el-input v-model="form.ratedParams" /></el-form-item>
        <el-form-item label="安装位置"><el-input v-model="form.installLocation" /></el-form-item>
        <el-form-item label="出厂日期"><el-date-picker v-model="form.manufactureDate" type="date" style="width:100%" /></el-form-item>
        <el-form-item label="隐患等级"><el-select v-model="form.hazardLevel" clearable style="width:100%"><el-option label="一级（重大隐患）" value="一级" /><el-option label="二级（较大隐患）" value="二级" /><el-option label="三级（一般隐患）" value="三级" /></el-select></el-form-item>
        <el-form-item label="设备数量"><el-input-number v-model="form.quantity" :min="0" style="width:100%" /></el-form-item>
        <el-form-item label="所属变电站" prop="substationId"><el-input-number v-model="form.substationId" :min="1" style="width:100%" /></el-form-item>
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
import { equipmentApi } from '../../api'
import { Plus } from '@element-plus/icons-vue'

const rules = {
  equipmentName: [{ required: true, message: '请输入设备名称', trigger: 'blur' }],
  equipmentType: [{ required: true, message: '请输入设备类型', trigger: 'blur' }],
  substationId: [{ required: true, message: '请输入所属变电站ID', trigger: 'blur' }]
}

const crud = useCrud(equipmentApi,
  { equipmentName: '', equipmentType: '', ratedParams: '', installLocation: '', manufactureDate: '', hazardLevel: '', quantity: null, substationId: null },
  rules,
  { equipmentName: '', equipmentType: '', hazardLevel: '' }
)
const { tableData, loading, saving, total, page, size, dialogVisible, isEdit, formRef, searchForm, form, fetchData, handleSearch, resetSearch, openDialog, handleSave, handleDelete } = crud

function typeTag(t) {
  const map = { '变压器': 'danger', '断路器': 'warning', '隔离开关': 'primary' }
  return map[t] || ''
}
function hazardTag(h) {
  const map = { '一级': 'danger', '二级': 'warning', '三级': 'info' }
  return map[h] || ''
}
</script>
