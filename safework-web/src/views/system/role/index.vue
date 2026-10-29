<template>
  <div class="page-card">
    <div class="toolbar">
      <span class="hint">企业管理员（admin）默认拥有本企业套餐内的全部权限，不需要分配；可分配的功能由平台开通的套餐决定</span>
      <div class="spacer" />
      <el-button v-perm="'system:role:add'" type="primary" icon="Plus" @click="openCreate">新增角色</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="roleName" label="角色名称" width="160" />
      <el-table-column prop="roleCode" label="角色编码" width="140" />
      <el-table-column prop="remark" label="说明" min-width="200" />
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.roleCode !== 'admin'" v-perm="'system:role:menu'" link type="primary" @click="openMenus(row)">分配权限</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="createVisible" title="新增角色" width="460px">
      <el-form ref="createRef" :model="createForm" :rules="createRules" label-width="80px">
        <el-form-item label="角色编码" prop="roleCode"><el-input v-model="createForm.roleCode" placeholder="小写字母开头，如 inspector" /></el-form-item>
        <el-form-item label="角色名称" prop="roleName"><el-input v-model="createForm.roleName" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="createForm.sort" :min="0" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="createForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doCreate">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="menuVisible" :title="`分配权限：${current?.roleName || ''}`" width="480px">
      <el-tree ref="treeRef" :data="tree" node-key="id" show-checkbox default-expand-all
               :props="{ label: 'menuName', children: 'children' }">
        <template #default="{ data }">
          <span>{{ data.menuName }}</span>
          <el-tag v-if="data.menuType === 'F'" size="small" type="info" class="perm">{{ data.perms }}</el-tag>
        </template>
      </el-tree>
      <template #footer>
        <el-button @click="menuVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doAssign">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { assignRoleMenus, createRole, getRoleMenus, listRoles, menuTree } from '@/api'

const loading = ref(false)
const saving = ref(false)
const list = ref([])

async function load() {
  loading.value = true
  try {
    list.value = await listRoles()
  } finally {
    loading.value = false
  }
}

const createVisible = ref(false)
const createRef = ref()
const createForm = reactive({ roleCode: '', roleName: '', sort: 99, remark: '' })
const createRules = {
  roleCode: [{ required: true, pattern: /^[a-z][a-z0-9_]{1,31}$/, message: '小写字母开头，只能包含小写字母、数字、下划线', trigger: 'blur' }],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }]
}
function openCreate() {
  Object.assign(createForm, { roleCode: '', roleName: '', sort: 99, remark: '' })
  createVisible.value = true
}
async function doCreate() {
  await createRef.value.validate()
  saving.value = true
  try {
    await createRole(createForm)
    ElMessage.success('新增成功')
    createVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

const menuVisible = ref(false)
const tree = ref([])
const treeRef = ref()
const current = ref(null)

/** 只回显叶子节点：父节点如果直接勾选，会把所有子节点一起勾上 */
function leafIds(nodes, set = new Set()) {
  nodes.forEach((n) => (n.children?.length ? leafIds(n.children, set) : set.add(n.id)))
  return set
}

async function openMenus(row) {
  current.value = row
  const [all, mine] = await Promise.all([menuTree(), getRoleMenus(row.id)])
  tree.value = all
  menuVisible.value = true
  await nextTick()
  const leaves = leafIds(all)
  treeRef.value.setCheckedKeys(mine.filter((id) => leaves.has(id)))
}

async function doAssign() {
  // 保存时把半选中的父节点也带上，否则菜单树缺少上级目录
  const ids = [...treeRef.value.getCheckedKeys(), ...treeRef.value.getHalfCheckedKeys()]
  saving.value = true
  try {
    await assignRoleMenus(current.value.id, ids)
    ElMessage.success('权限已更新，相关用户刷新页面后生效')
    menuVisible.value = false
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.hint { color: #888; font-size: 13px; }
.perm { margin-left: 8px; }
</style>
