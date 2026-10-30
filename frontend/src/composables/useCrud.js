import { ref, reactive, onMounted, onActivated } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

export function useCrud(api, defaultForm, rules, searchFormTemplate = {}) {
  const tableData = ref([])
  const loading = ref(false)
  const saving = ref(false)
  const total = ref(0)
  const page = ref(1)
  const size = ref(10)
  const dialogVisible = ref(false)
  const isEdit = ref(false)
  const formRef = ref(null)
  let initialLoadDone = false

  const searchForm = reactive({ ...searchFormTemplate })
  const form = reactive({ ...defaultForm })

  function formatParam(v) {
    if (v instanceof Date) {
      const y = v.getFullYear()
      const m = String(v.getMonth() + 1).padStart(2, '0')
      const d = String(v.getDate()).padStart(2, '0')
      return `${y}-${m}-${d}`
    }
    return v
  }

  async function fetchData() {
    loading.value = true
    try {
      const params = { page: page.value, size: size.value }
      for (const [k, v] of Object.entries(searchForm)) {
        if (v !== null && v !== '') params[k] = formatParam(v)
      }
      const res = await api.pageList(params)
      tableData.value = res.data.records
      total.value = res.data.total
    } finally {
      loading.value = false
    }
  }

  function handleSearch() {
    page.value = 1
    fetchData()
  }

  function resetSearch() {
    Object.assign(searchForm, JSON.parse(JSON.stringify(searchFormTemplate)))
    page.value = 1
    fetchData()
  }

  function openDialog(row) {
    isEdit.value = !!row
    Object.assign(form, row ? JSON.parse(JSON.stringify(row)) : JSON.parse(JSON.stringify(defaultForm)))
    dialogVisible.value = true
  }

  async function handleSave() {
    const valid = await formRef.value?.validate().catch(() => {})
    if (!valid) return
    saving.value = true
    try {
      if (isEdit.value) {
        await api.update(form.id, form)
        ElMessage.success('更新成功')
      } else {
        await api.create(form)
        ElMessage.success('新增成功')
      }
      dialogVisible.value = false
      fetchData()
    } finally {
      saving.value = false
    }
  }

  async function handleDelete(id) {
    try {
      await ElMessageBox.confirm('确认删除该记录？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      })
      await api.delete(id)
      ElMessage.success('删除成功')
      fetchData()
    } catch (e) {
      if (e !== 'cancel') throw e
    }
  }

  onMounted(() => {
    if (!initialLoadDone) {
      fetchData()
      initialLoadDone = true
    }
  })

  onActivated(() => {
    if (initialLoadDone && tableData.value.length === 0) {
      fetchData()
    }
  })

  return {
    tableData, loading, saving, total, page, size,
    dialogVisible, isEdit, formRef, searchForm, form,
    fetchData, handleSearch, resetSearch, openDialog, handleSave, handleDelete
  }
}
