<template>
  <div class="page-card">
    <div class="toolbar">
      <span class="hint">套餐决定企业能用哪些功能。修改套餐后，使用这个套餐的企业刷新页面即按新功能范围生效</span>
      <div class="spacer" />
      <el-button v-perm="'platform:package:edit'" type="primary" icon="Plus" @click="openEdit(null)">新增套餐</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="packageName" label="套餐名称" width="140" />
      <el-table-column label="包含功能" min-width="320">
        <template #default="{ row }">
          <el-tag v-for="name in pageNames(row)" :key="name" size="small" class="menu-tag" effect="plain">{{ name }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="使用企业" width="100">
        <template #default="{ row }">{{ row.tenantCount }} 家</template>
      </el-table-column>
      <el-table-column prop="remark" label="说明" min-width="180" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-switch v-if="canEdit" :model-value="row.status === 1" @change="(v) => toggle(row, v)" />
          <el-tag v-else :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '正常' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button v-perm="'platform:package:edit'" link type="primary" @click="openEdit(row)">修改</el-button>
          <el-button v-perm="'platform:package:edit'" link type="danger" :disabled="row.tenantCount > 0" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="editId ? '修改套餐' : '新增套餐'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="名称" prop="packageName"><el-input v-model="form.packageName" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="form.remark" /></el-form-item>
        <el-form-item label="功能" required>
          <div class="tree-box">
            <el-tree ref="treeRef" :data="tree" node-key="id" show-checkbox default-expand-all
                     :props="{ label: 'menuName', children: 'children' }">
              <template #default="{ data }">
                <span>{{ data.menuName }}</span>
                <el-tag v-if="data.menuType === 'F'" size="small" type="info" class="perm">按钮</el-tag>
              </template>
            </el-tree>
          </div>
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
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { changePackageStatus, createPackage, listPackages, packageMenuTree, removePackage, updatePackage } from '@/api'

const store = useUserStore()
const canEdit = computed(() => store.hasPerm('platform:package:edit'))
const loading = ref(false)
const saving = ref(false)
const list = ref([])
const tree = ref([])
const treeRef = ref()

// 菜单 ID → 页面名称，用于在列表里显示“包含哪些页面”
const pageMap = computed(() => {
  const map = new Map()
  const walk = (nodes) => nodes.forEach((n) => {
    if (n.menuType === 'C') map.set(n.id, n.menuName)
    if (n.children?.length) walk(n.children)
  })
  walk(tree.value)
  return map
})
const pageNames = (row) => row.menuIds.map((id) => pageMap.value.get(id)).filter(Boolean)

async function load() {
  loading.value = true
  try {
    list.value = await listPackages()
  } finally {
    loading.value = false
  }
}

const visible = ref(false)
const editId = ref(null)
const formRef = ref()
const form = reactive({ packageName: '', sort: 0, remark: '' })
const rules = { packageName: [{ required: true, message: '请输入套餐名称', trigger: 'blur' }] }

/** 只回显叶子节点：父节点如果直接勾选，会把所有子节点一起勾上 */
function leafIds(nodes, set = new Set()) {
  nodes.forEach((n) => (n.children?.length ? leafIds(n.children, set) : set.add(n.id)))
  return set
}

async function openEdit(row) {
  editId.value = row?.id || null
  Object.assign(form, { packageName: row?.packageName || '', sort: row?.sort ?? 0, remark: row?.remark || '' })
  visible.value = true
  await nextTick()
  const leaves = leafIds(tree.value)
  treeRef.value.setCheckedKeys(row ? row.menuIds.filter((id) => leaves.has(id)) : [])
}

async function save() {
  await formRef.value.validate()
  // 保存时把半选中的上级目录也带上（后端要求按钮、页面的上级必须也在套餐里）
  const menuIds = [...treeRef.value.getCheckedKeys(), ...treeRef.value.getHalfCheckedKeys()]
  if (!menuIds.length) {
    ElMessage.warning('请至少选择一个功能')
    return
  }
  saving.value = true
  try {
    const data = { ...form, menuIds }
    if (editId.value) {
      await updatePackage(editId.value, data)
    } else {
      await createPackage(data)
    }
    ElMessage.success('保存成功')
    visible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function toggle(row, enabled) {
  await changePackageStatus(row.id, enabled ? 1 : 0)
  row.status = enabled ? 1 : 0
  ElMessage.success(enabled ? '已启用' : '已停用')
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确定删除套餐【${row.packageName}】吗？`, '删除套餐', { type: 'warning' })
  } catch {
    return
  }
  await removePackage(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(async () => {
  tree.value = await packageMenuTree()
  load()
})
</script>

<style scoped>
.hint { color: #888; font-size: 13px; }
.menu-tag { margin: 2px 4px 2px 0; }
.perm { margin-left: 8px; }
.tree-box { width: 100%; max-height: 360px; overflow: auto; border: 1px solid #ebeef5; border-radius: 4px; padding: 6px 0; }
</style>
