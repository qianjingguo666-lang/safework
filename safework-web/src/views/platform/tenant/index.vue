<template>
  <div class="page-card">
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="企业编码 / 名称" clearable style="width: 200px" @keyup.enter="search" @clear="search" />
      <el-select v-model="query.packageId" placeholder="套餐" clearable style="width: 120px" @change="search">
        <el-option v-for="p in packages" :key="p.id" :label="p.packageName" :value="p.id" />
      </el-select>
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 100px" @change="search">
        <el-option label="正常" :value="1" /><el-option label="停用" :value="0" />
      </el-select>
      <el-checkbox v-model="query.expiring" @change="search">只看 30 天内到期</el-checkbox>
      <el-button icon="Search" @click="search">查询</el-button>
      <div class="spacer" />
      <el-button v-perm="'platform:tenant:add'" type="primary" icon="Plus" @click="openCreate">开通企业</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column label="企业" min-width="200">
        <template #default="{ row }">
          <div class="name">{{ row.tenantName }}</div>
          <div class="muted">编码：{{ row.tenantCode }}</div>
        </template>
      </el-table-column>
      <el-table-column label="套餐" width="100">
        <template #default="{ row }"><el-tag effect="plain">{{ row.packageName || '未设置' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="账号" width="110">
        <template #default="{ row }">
          <span :class="{ full: row.accountLimit && row.userCount >= row.accountLimit }">{{ row.userCount }}</span>
          <span class="muted"> / {{ row.accountLimit || '不限' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="在线" width="70">
        <template #default="{ row }">{{ row.onlineCount }}</template>
      </el-table-column>
      <el-table-column label="服务到期" width="150">
        <template #default="{ row }">
          <template v-if="row.expireTime">
            <div>{{ row.expireTime.slice(0, 10) }}</div>
            <el-tag v-if="row.daysLeft <= 0" size="small" type="danger">已到期</el-tag>
            <el-tag v-else-if="row.daysLeft <= 30" size="small" type="warning">剩 {{ row.daysLeft }} 天</el-tag>
          </template>
          <span v-else class="muted">不限期</span>
        </template>
      </el-table-column>
      <el-table-column label="联系人" width="140">
        <template #default="{ row }">
          <div>{{ row.contactName || '-' }}</div>
          <div class="muted">{{ row.contactPhone }}</div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-switch v-if="canEdit" :model-value="row.status === 1" @change="(v) => toggle(row, v)" />
          <el-tag v-else :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '正常' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="开通时间" width="170" />
      <el-table-column label="操作" width="200" fixed="right" class-name="ops">
        <template #default="{ row }">
          <el-button v-perm="'platform:tenant:edit'" link type="primary" @click="openEdit(row)">修改</el-button>
          <el-dropdown v-if="canEdit" trigger="click" @command="(c) => onMore(c, row)">
            <el-button link type="primary">续费<el-icon><ArrowDown /></el-icon></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item :command="1">续费 1 个月</el-dropdown-item>
                <el-dropdown-item :command="12">续费 1 年</el-dropdown-item>
                <el-dropdown-item :command="36">续费 3 年</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button v-perm="'platform:tenant:edit'" link type="warning" title="重置企业管理员的登录密码" @click="resetAdmin(row)">重置密码</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination v-model:current-page="query.current" :page-size="query.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
    </div>

    <el-dialog v-model="formVisible" :title="editId ? '修改企业' : '开通企业'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-divider v-if="!editId" content-position="left">企业信息</el-divider>
        <el-form-item label="企业编码" prop="tenantCode">
          <el-input v-model="form.tenantCode" :disabled="!!editId" placeholder="员工登录时填写，如 hfchem" />
        </el-form-item>
        <el-form-item label="企业名称" prop="tenantName"><el-input v-model="form.tenantName" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contactName" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.contactPhone" /></el-form-item>
        <el-form-item label="套餐" prop="packageId">
          <el-radio-group v-model="form.packageId">
            <el-radio-button v-for="p in packages" :key="p.id" :value="p.id">{{ p.packageName }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="服务到期">
          <el-date-picker v-model="form.expireTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                          placeholder="不选表示不限期" :disabled-date="(d) => !editId && d < Date.now() - 86400000" style="width: 220px" />
          <el-button-group class="quick">
            <el-button size="small" @click="form.expireTime = afterMonths(null, 12)">1 年</el-button>
            <el-button size="small" @click="form.expireTime = afterMonths(null, 36)">3 年</el-button>
            <el-button size="small" @click="form.expireTime = null">不限</el-button>
          </el-button-group>
        </el-form-item>
        <el-form-item label="账号上限">
          <el-input-number v-model="form.accountLimit" :min="1" :max="100000" placeholder="不限" />
          <span class="muted tip">留空表示不限</span>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
        <template v-if="!editId">
          <el-divider content-position="left">企业管理员账号</el-divider>
          <el-form-item label="用户名" prop="adminUsername"><el-input v-model="form.adminUsername" /></el-form-item>
          <el-form-item label="初始密码" prop="adminPassword">
            <el-input v-model="form.adminPassword" type="password" show-password placeholder="6-32 位，交给企业后请提醒对方修改" />
          </el-form-item>
          <el-alert type="info" :closable="false" show-icon
                    title="开通后会自动创建：根部门、企业管理员、安全员 / 作业负责人 / 监护人三个预置角色（按套餐分配权限）" />
        </template>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ editId ? '保存' : '开通' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  changeTenantStatus, createTenant, packageOptions, pageTenants, resetTenantAdminPassword, updateTenant
} from '@/api'

const store = useUserStore()
const route = useRoute()
const canEdit = computed(() => store.hasPerm('platform:tenant:edit'))
const loading = ref(false)
const saving = ref(false)
const list = ref([])
const total = ref(0)
const packages = ref([])
const query = reactive({ current: 1, size: 10, keyword: '', packageId: null, status: null, expiring: route.query.expiring === '1' })

async function load() {
  loading.value = true
  try {
    const params = Object.fromEntries(Object.entries(query).filter(([, v]) => v !== '' && v != null && v !== false))
    const page = await pageTenants(params)
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

/** 从 base（为空或已过期则从现在）开始往后推 months 个月 */
function afterMonths(base, months) {
  let d = base ? new Date(base.replace(' ', 'T')) : new Date()
  if (d < new Date()) d = new Date()
  d.setMonth(d.getMonth() + months)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} 23:59:59`
}

const formVisible = ref(false)
const editId = ref(null)
const formRef = ref()
const emptyForm = () => ({
  tenantCode: '', tenantName: '', contactName: '', contactPhone: '', packageId: packages.value[0]?.id ?? null,
  expireTime: afterMonths(null, 12), accountLimit: 50, remark: '', adminUsername: 'admin', adminPassword: ''
})
const form = reactive(emptyForm())
const rules = {
  tenantCode: [{ required: true, pattern: /^[a-z][a-z0-9_]{2,31}$/, message: '3-32 位，小写字母开头，只能包含小写字母、数字、下划线', trigger: 'blur' }],
  tenantName: [{ required: true, message: '请输入企业名称', trigger: 'blur' }],
  packageId: [{ required: true, message: '请选择套餐', trigger: 'change' }],
  adminUsername: [{ required: true, min: 3, max: 32, message: '用户名 3-32 位', trigger: 'blur' }],
  adminPassword: [{ required: true, min: 6, max: 32, message: '密码 6-32 位', trigger: 'blur' }]
}

function openCreate() {
  editId.value = null
  Object.assign(form, emptyForm())
  formVisible.value = true
}
function openEdit(row) {
  editId.value = row.id
  Object.assign(form, {
    tenantCode: row.tenantCode, tenantName: row.tenantName, contactName: row.contactName, contactPhone: row.contactPhone,
    packageId: row.packageId, expireTime: row.expireTime, accountLimit: row.accountLimit, remark: row.remark,
    adminUsername: '', adminPassword: ''
  })
  formVisible.value = true
}
function updatePayload(src) {
  return {
    tenantName: src.tenantName, contactName: src.contactName, contactPhone: src.contactPhone, packageId: src.packageId,
    expireTime: src.expireTime || null, accountLimit: src.accountLimit || null, remark: src.remark
  }
}
async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    if (editId.value) {
      await updateTenant(editId.value, updatePayload(form))
      ElMessage.success('已保存，企业刷新页面后按新套餐生效')
    } else {
      await createTenant({ ...form, accountLimit: form.accountLimit || null, expireTime: form.expireTime || null })
      await ElMessageBox.alert(
        `企业编码：${form.tenantCode}\n管理员：${form.adminUsername}\n请把以上信息和初始密码交给企业联系人，登录后在右上角修改密码。`,
        '开通成功', { type: 'success' })
    }
    formVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function onMore(months, row) {
  const expireTime = afterMonths(row.expireTime, months)
  try {
    await ElMessageBox.confirm(`【${row.tenantName}】服务到期时间将改为 ${expireTime.slice(0, 10)}，确认续费吗？`, '续费', { type: 'info' })
  } catch {
    return
  }
  await updateTenant(row.id, { ...updatePayload(row), expireTime })
  ElMessage.success('续费成功')
  load()
}

async function toggle(row, enabled) {
  if (!enabled) {
    try {
      await ElMessageBox.confirm(`停用后【${row.tenantName}】的所有员工会被立即踢下线，并且无法登录。确认停用吗？`, '停用企业', { type: 'warning' })
    } catch {
      return
    }
  }
  await changeTenantStatus(row.id, enabled ? 1 : 0)
  row.status = enabled ? 1 : 0
  if (!enabled) row.onlineCount = 0
  ElMessage.success(enabled ? '已启用' : '已停用')
}

async function resetAdmin(row) {
  let value
  try {
    ({ value } = await ElMessageBox.prompt(`为【${row.tenantName}】的企业管理员设置新密码（6-32 位），同时会解除账号锁定`, '重置管理员密码', {
      inputType: 'password',
      inputValidator: (v) => (v && v.length >= 6 && v.length <= 32) || '密码 6-32 位'
    }))
  } catch {
    return
  }
  const username = await resetTenantAdminPassword(row.id, value)
  ElMessage.success(`管理员 ${username} 的密码已重置`)
}

onMounted(async () => {
  packages.value = await packageOptions()
  load()
  if (route.query.create === '1') openCreate()
})
</script>

<style scoped>
.name { font-weight: 500; }
.muted { color: #999; font-size: 12px; }
.full { color: #f56c6c; font-weight: 600; }
.quick { margin-left: 8px; }
.tip { margin-left: 8px; }
:deep(.ops .cell) { display: flex; align-items: center; gap: 12px; white-space: nowrap; }
:deep(.ops .el-button + .el-button) { margin-left: 0; }
</style>
