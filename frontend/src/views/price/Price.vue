<template>
  <div class="page-container">
    <div class="filter-bar">
      <el-form :model="searchForm" class="filter-fields">
        <el-form-item label="用电分类">
          <el-select placeholder="全部" v-model="searchForm.electricityCategory" clearable style="width:132px">
            <el-option label="居民" value="居民" /><el-option label="商业" value="商业" />
            <el-option label="工业" value="工业" /><el-option label="农业" value="农业" />
          </el-select>
        </el-form-item>
        <el-form-item label="执行日期"><el-date-picker v-model="searchForm.effectiveDate" type="date" clearable style="width:132px" /></el-form-item>
      </el-form>
      <div class="filter-actions">
        <el-button text @click="resetSearch">重置</el-button>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="openDialog()"><el-icon><Plus /></el-icon>新增</el-button>
      </div>
    </div>
    <el-card class="table-card">
      <div class="table-header"><span class="table-title">电价标准列表</span><span class="table-count">{{ total }} 条记录</span></div>
      <el-table :data="tableData" v-loading="loading" highlight-current-row max-height="560">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="electricityCategory" label="用电分类" width="120">
          <template #default="{ row }"><el-tag :type="catTag(row.electricityCategory)" size="small" effect="plain">{{ row.electricityCategory }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="basePrice" label="基础电价" width="110">
          <template #default="{ row }"><strong>{{ row.basePrice }}</strong> 元/kWh</template>
        </el-table-column>
        <el-table-column prop="peakPrice" label="高峰电价" width="110">
          <template #default="{ row }">{{ row.peakPrice }} 元/kWh</template>
        </el-table-column>
        <el-table-column prop="valleyPrice" label="低谷电价" width="110">
          <template #default="{ row }">{{ row.valleyPrice }} 元/kWh</template>
        </el-table-column>
        <el-table-column prop="effectiveDate" label="执行日期" width="110">
          <template #default="{ row }">{{ formatDate(row.effectiveDate) }}</template>
        </el-table-column>
        <el-table-column prop="pricingMethod" label="计价方式" width="100" />
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" @click="openDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" :page-sizes="[10,20,50,100]" layout="total, sizes, prev, pager, next, jumper" @change="fetchData" class="pagination" />
    </el-card>
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑电价标准' : '新增电价标准'" width="550px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="用电分类" prop="electricityCategory">
          <el-select v-model="form.electricityCategory" style="width:100%">
            <el-option label="居民" value="居民" /><el-option label="商业" value="商业" />
            <el-option label="工业" value="工业" /><el-option label="农业" value="农业" />
          </el-select>
        </el-form-item>
        <el-form-item label="基础电价" prop="basePrice"><el-input-number v-model="form.basePrice" :precision="4" :min="0" :step="0.01" style="width:100%" /></el-form-item>
        <el-form-item label="高峰电价"><el-input-number v-model="form.peakPrice" :precision="4" :min="0" :step="0.01" style="width:100%" /></el-form-item>
        <el-form-item label="低谷电价"><el-input-number v-model="form.valleyPrice" :precision="4" :min="0" :step="0.01" style="width:100%" /></el-form-item>
        <el-form-item label="执行日期" prop="effectiveDate"><el-date-picker v-model="form.effectiveDate" type="date" style="width:100%" /></el-form-item>
        <el-form-item label="计价方式">
          <el-select v-model="form.pricingMethod" clearable style="width:100%">
            <el-option label="单一制" value="单一制" /><el-option label="两部制" value="两部制" />
          </el-select>
        </el-form-item>
        <el-form-item label="优惠政策"><el-input v-model="form.preferentialPolicy" type="textarea" :rows="2" /></el-form-item>
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
import { priceApi } from '../../api'
import { formatDate } from '../../utils/format'
import { Plus } from '@element-plus/icons-vue'

const rules = {
  electricityCategory: [{ required: true, message: '请选择用电分类', trigger: 'change' }],
  basePrice: [{ required: true, message: '请输入基础电价', trigger: 'blur' }],
  effectiveDate: [{ required: true, message: '请选择执行日期', trigger: 'change' }]
}

const crud = useCrud(priceApi,
  { electricityCategory: '', basePrice: null, peakPrice: null, valleyPrice: null, effectiveDate: '', pricingMethod: '', preferentialPolicy: '' },
  rules,
  { electricityCategory: '', effectiveDate: '' }
)
const { tableData, loading, saving, total, page, size, dialogVisible, isEdit, formRef, searchForm, form, fetchData, handleSearch, resetSearch, openDialog, handleSave, handleDelete } = crud

function catTag(c) {
  const map = { '居民': '', '商业': 'primary', '工业': 'danger', '农业': 'success' }
  return map[c] || ''
}
</script>
