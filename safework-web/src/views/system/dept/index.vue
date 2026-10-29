<template>
  <div class="page-card">
    <div class="toolbar">
      <span class="hint">部门用于组织用户和受限空间；删除前需要先移走下级部门和用户</span>
      <div class="spacer" />
      <el-button v-perm="'system:dept:add'" type="primary" icon="Plus" @click="openForm(null, 0)">新增部门</el-button>
    </div>
    <el-table v-loading="loading" :data="tree" row-key="id" default-expand-all border>
      <el-table-column prop="deptName" label="部门名称" min-width="200" />
      <el-table-column prop="leader" label="负责人" width="140" />
      <el-table-column prop="phone" label="联系电话" width="140" />
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '正常' : '停用' }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button v-perm="'system:dept:add'" link type="primary" @click="openForm(null, row.id)">新增下级</el-button>
          <el-button v-perm="'system:dept:edit'" link type="primary" @click="openForm(row)">修改</el-button>
          <el-button v-perm="'system:dept:remove'" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="editId ? '修改部门' : '新增部门'" width="460px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="上级部门">
          <el-tree-select v-model="form.parentId" :data="parentOptions" :props="{ label: 'deptName', children: 'children' }"
                          node-key="id" check-strictly style="width: 100%" />
        </el-form-item>
        <el-form-item label="部门名称" prop="deptName"><el-input v-model="form.deptName" /></el-form-item>
        <el-form-item label="负责人"><el-input v-model="form.leader" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" /></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status"><el-radio :value="1">正常</el-radio><el-radio :value="0">停用</el-radio></el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createDept, deptTree, removeDept, updateDept } from '@/api'

const loading = ref(false)
const tree = ref([])
async function load() {
  loading.value = true
  try {
    tree.value = await deptTree()
  } finally {
    loading.value = false
  }
}

// 上级部门选项：最前面加一个“顶级”
const parentOptions = computed(() => [{ id: 0, deptName: '（顶级部门）', children: tree.value }])

const visible = ref(false)
const saving = ref(false)
const editId = ref(null)
const formRef = ref()
const form = reactive({ parentId: 0, deptName: '', leader: '', phone: '', sort: 0, status: 1 })
const rules = { deptName: [{ required: true, message: '请输入部门名称', trigger: 'blur' }] }

function openForm(row, parentId) {
  editId.value = row?.id || null
  Object.assign(form, row
    ? { parentId: row.parentId, deptName: row.deptName, leader: row.leader, phone: row.phone, sort: row.sort, status: row.status }
    : { parentId, deptName: '', leader: '', phone: '', sort: 0, status: 1 })
  visible.value = true
}
async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    if (editId.value) await updateDept(editId.value, form)
    else await createDept(form)
    ElMessage.success('保存成功')
    visible.value = false
    load()
  } finally {
    saving.value = false
  }
}
async function remove(row) {
  try {
    await ElMessageBox.confirm(`确认删除部门【${row.deptName}】？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  await removeDept(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.hint { color: #888; font-size: 13px; }
</style>
