<template>
  <div class="user-page">
    <div class="page-card dept-panel">
      <div class="panel-title">部门</div>
      <el-tree :data="depts" :props="{ label: 'deptName', children: 'children' }" node-key="id" default-expand-all
               highlight-current :expand-on-click-node="false" @node-click="onDept" />
      <el-button link type="primary" class="all" @click="onDept(null)">查看全部</el-button>
    </div>

    <div class="page-card main-panel">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="用户名 / 昵称" clearable style="width: 200px" @keyup.enter="search" @clear="search" />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 100px" @change="search">
          <el-option label="正常" :value="1" /><el-option label="停用" :value="0" />
        </el-select>
        <el-button icon="Search" @click="search">查询</el-button>
        <div class="spacer" />
        <el-button v-perm="'system:user:add'" type="primary" icon="Plus" @click="openCreate">新增用户</el-button>
      </div>

      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="nickname" label="昵称" min-width="150" />
        <el-table-column prop="deptName" label="部门" width="120" />
        <el-table-column label="角色" min-width="150">
          <template #default="{ row }">
            <el-tag v-for="r in row.roleNames" :key="r" size="small" class="role-tag">{{ r }}</el-tag>
            <span v-if="!row.roleNames?.length" class="muted">未分配</span>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-switch v-if="canEdit" :model-value="row.status === 1" @change="(v) => toggle(row, v)" />
            <el-tag v-else :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '正常' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button v-perm="'system:user:edit'" link type="primary" @click="openEdit(row)">修改</el-button>
            <el-button v-perm="'system:user:role'" link type="primary" @click="openRoles(row)">分配角色</el-button>
            <el-button v-perm="'system:user:reset'" link type="warning" @click="resetPwd(row)">重置密码</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination v-model:current-page="query.current" :page-size="query.size" :total="total"
                       layout="total, prev, pager, next" @current-change="load" />
      </div>
    </div>

    <el-dialog v-model="formVisible" :title="editId ? '修改用户' : '新增用户'" width="480px">
      <el-form ref="formRef" :model="form" :rules="editId ? editRules : createRules" label-width="80px">
        <el-form-item label="用户名" prop="username"><el-input v-model="form.username" :disabled="!!editId" /></el-form-item>
        <el-form-item v-if="!editId" label="密码" prop="password"><el-input v-model="form.password" type="password" show-password /></el-form-item>
        <el-form-item label="昵称" prop="nickname"><el-input v-model="form.nickname" /></el-form-item>
        <el-form-item label="部门">
          <el-tree-select v-model="form.deptId" :data="depts" :props="{ label: 'deptName', children: 'children' }"
                          node-key="id" check-strictly clearable style="width: 100%" />
        </el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item v-if="!editId" label="角色">
          <el-select v-model="form.roleIds" multiple style="width: 100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleVisible" :title="`分配角色：${current?.nickname || ''}`" width="460px">
      <el-checkbox-group v-model="checkedRoles">
        <div v-for="r in roles" :key="r.id" class="role-item">
          <el-checkbox :value="r.id">{{ r.roleName }}</el-checkbox>
          <span class="muted">{{ r.remark }}</span>
        </div>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doAssign">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  assignUserRoles, changeUserStatus, createUser, deptTree, getUserRoles, listRoles, pageUsers, resetUserPassword, updateUser
} from '@/api'

const store = useUserStore()
const canEdit = computed(() => store.hasPerm('system:user:edit'))
const loading = ref(false)
const saving = ref(false)
const list = ref([])
const total = ref(0)
const depts = ref([])
const roles = ref([])
const query = reactive({ current: 1, size: 10, keyword: '', status: null, deptId: null })

async function load() {
  loading.value = true
  try {
    const params = Object.fromEntries(Object.entries(query).filter(([, v]) => v !== '' && v != null))
    const page = await pageUsers(params)
    list.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}
function search() {
  query.current = 1
  load()
}
function onDept(node) {
  query.deptId = node ? node.id : null
  search()
}

const formVisible = ref(false)
const editId = ref(null)
const formRef = ref()
const form = reactive({ username: '', password: '', nickname: '', phone: '', deptId: null, roleIds: [] })
const createRules = {
  username: [{ required: true, min: 3, max: 32, message: '用户名 3-32 位', trigger: 'blur' }],
  password: [{ required: true, min: 6, max: 32, message: '密码 6-32 位', trigger: 'blur' }]
}
const editRules = { nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }] }

function openCreate() {
  editId.value = null
  Object.assign(form, { username: '', password: '', nickname: '', phone: '', deptId: query.deptId, roleIds: [] })
  formVisible.value = true
}
function openEdit(row) {
  editId.value = row.id
  Object.assign(form, { username: row.username, password: '', nickname: row.nickname, phone: row.phone, deptId: row.deptId, roleIds: [] })
  formVisible.value = true
}
async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    if (editId.value) {
      await updateUser(editId.value, { nickname: form.nickname, phone: form.phone, deptId: form.deptId })
    } else {
      await createUser(form)
    }
    ElMessage.success('保存成功')
    formVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function toggle(row, enabled) {
  if (!enabled) {
    try {
      await ElMessageBox.confirm(`停用后【${row.nickname}】会被立即踢下线且无法登录，确认吗？`, '提示', { type: 'warning' })
    } catch {
      return
    }
  }
  await changeUserStatus(row.id, enabled ? 1 : 0)
  row.status = enabled ? 1 : 0
  ElMessage.success(enabled ? '已启用' : '已停用')
}

async function resetPwd(row) {
  let value
  try {
    ({ value } = await ElMessageBox.prompt(`为【${row.nickname}】设置新密码（6-32 位），对方需要重新登录`, '重置密码', {
      inputType: 'password',
      inputValidator: (v) => (v && v.length >= 6 && v.length <= 32) || '密码 6-32 位'
    }))
  } catch {
    return
  }
  await resetUserPassword(row.id, value)
  ElMessage.success('密码已重置')
}

const roleVisible = ref(false)
const checkedRoles = ref([])
const current = ref(null)
async function openRoles(row) {
  current.value = row
  checkedRoles.value = await getUserRoles(row.id)
  roleVisible.value = true
}
async function doAssign() {
  saving.value = true
  try {
    await assignUserRoles(current.value.id, checkedRoles.value)
    ElMessage.success('角色已更新，对方刷新页面后生效')
    roleVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  load()
  const [d, r] = await Promise.all([deptTree(), store.hasPerm('system:role:list') ? listRoles() : Promise.resolve([])])
  depts.value = d
  roles.value = r
})
</script>

<style scoped>
.user-page { display: flex; gap: 16px; align-items: flex-start; }
.dept-panel { width: 220px; flex-shrink: 0; }
.panel-title { font-weight: 600; margin-bottom: 10px; }
.all { margin-top: 8px; }
.main-panel { flex: 1; min-width: 0; }
.role-tag { margin-right: 4px; }
.role-item { display: flex; align-items: center; gap: 12px; line-height: 36px; }
.muted { color: #999; font-size: 12px; }
@media (max-width: 900px) {
  .user-page { flex-direction: column; }
  .dept-panel { width: 100%; box-sizing: border-box; }
}
</style>
