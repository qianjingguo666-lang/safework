<template>
  <div class="page-card">
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="编号 / 名称 / 位置" clearable style="width: 200px" @keyup.enter="search" @clear="search" />
      <el-select v-model="query.spaceType" placeholder="类型" clearable style="width: 130px" @change="search">
        <el-option v-for="t in SPACE_TYPES" :key="t" :label="t" :value="t" />
      </el-select>
      <el-select v-model="query.riskLevel" placeholder="风险等级" clearable style="width: 120px" @change="search">
        <el-option v-for="(v, k) in RISK_LEVELS" :key="k" :label="v.label" :value="k" />
      </el-select>
      <el-tree-select v-model="query.deptId" :data="depts" :props="deptProps" node-key="id" check-strictly
                      placeholder="所属部门" clearable style="width: 160px" @change="search" />
      <el-button icon="Search" @click="search">查询</el-button>
      <div class="spacer" />
      <el-button v-perm="'safety:space:import'" icon="Upload" @click="openImport">导入</el-button>
      <el-button v-perm="'safety:space:import'" icon="Download" :loading="exporting" @click="doExport">导出</el-button>
      <el-button v-perm="'safety:space:add'" type="primary" icon="Plus" @click="openForm()">新增受限空间</el-button>
    </div>

    <el-dialog v-model="importVisible" title="导入受限空间台账" width="600px">
      <el-steps :active="importResult ? 2 : 1" finish-status="success" simple class="steps">
        <el-step title="下载模板填写" /><el-step title="上传文件" /><el-step title="查看结果" />
      </el-steps>
      <div class="import-row">
        <el-button icon="Download" @click="downloadTemplate">下载导入模板</el-button>
        <span class="muted">模板第二页有填写说明</span>
      </div>
      <el-upload drag :show-file-list="false" accept=".xlsx,.xls" :http-request="doImport" :disabled="importing">
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">{{ importing ? '正在导入…' : '把填好的 Excel 拖到这里，或点击选择文件' }}</div>
      </el-upload>
      <template v-if="importResult">
        <el-alert v-if="!importResult.errors.length" type="success" :closable="false" show-icon
                  :title="`导入成功：共 ${importResult.success} 条`" class="result" />
        <template v-else>
          <el-alert type="error" :closable="false" show-icon class="result"
                    :title="`共 ${importResult.total} 行，其中 ${importResult.errors.length} 行有错误，全部未导入。改完后重新上传`" />
          <el-table :data="importResult.errors" size="small" max-height="260">
            <el-table-column prop="row" label="Excel 行号" width="100" />
            <el-table-column prop="message" label="问题" />
          </el-table>
        </template>
      </template>
    </el-dialog>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="spaceCode" label="编号" width="100" />
      <el-table-column prop="spaceName" label="名称" min-width="160" />
      <el-table-column prop="spaceType" label="类型" width="120" />
      <el-table-column prop="location" label="位置" min-width="130" />
      <el-table-column prop="deptName" label="所属部门" width="120" />
      <el-table-column prop="responsibleName" label="责任人" width="150" />
      <el-table-column prop="medium" label="危险介质" min-width="130" show-overflow-tooltip />
      <el-table-column label="风险" width="90">
        <template #default="{ row }"><el-tag :type="RISK_LEVELS[row.riskLevel]?.type">{{ RISK_LEVELS[row.riskLevel]?.label }}</el-tag></template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-switch v-if="canEdit" :model-value="row.status === 1" @change="(v) => toggle(row, v)" />
          <el-tag v-else :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="canEdit" label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)">修改</el-button>
          <el-button link type="primary" @click="openQr(row)">二维码</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination v-model:current-page="query.current" :page-size="query.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
    </div>

    <el-dialog v-model="formVisible" :title="editId ? '修改受限空间' : '新增受限空间'" width="600px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="空间编号" prop="spaceCode"><el-input v-model="form.spaceCode" placeholder="如 CS-006" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="类型" prop="spaceType">
              <el-select v-model="form.spaceType" style="width: 100%"><el-option v-for="t in SPACE_TYPES" :key="t" :label="t" :value="t" /></el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="空间名称" prop="spaceName"><el-input v-model="form.spaceName" /></el-form-item>
        <el-form-item label="位置" prop="location"><el-input v-model="form.location" /></el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="所属部门">
              <el-tree-select v-model="form.deptId" :data="depts" :props="deptProps" node-key="id" check-strictly clearable style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="责任人">
              <el-select v-model="form.responsibleId" clearable filterable style="width: 100%">
                <el-option v-for="u in users" :key="u.id" :label="u.nickname" :value="u.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="危险介质"><el-input v-model="form.medium" placeholder="如：硫化氢、甲醇、可能缺氧" /></el-form-item>
        <el-form-item label="风险等级" prop="riskLevel">
          <el-radio-group v-model="form.riskLevel">
            <el-radio-button v-for="(v, k) in RISK_LEVELS" :key="k" :value="k">{{ v.label }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="qrVisible" title="现场二维码" width="380px">
      <div class="qr">
        <img v-if="qrImage" :src="qrImage" alt="二维码" />
        <div class="qr-name">{{ qrSpace?.spaceCode }} {{ qrSpace?.spaceName }}</div>
        <div class="qr-tip">打印后张贴在受限空间入口，扫码即可查看危险介质和当前作业情况（无需登录）</div>
        <el-link :href="qrUrl" target="_blank" type="primary">在新窗口打开扫码页</el-link>
      </div>
      <template #footer>
        <el-button @click="resetQr">重新生成</el-button>
        <el-button type="primary" @click="downloadQr">下载图片</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import QRCode from 'qrcode'
import { useUserStore } from '@/stores/user'
import {
  changeSpaceStatus, createSpace, deptTree, getSpaceQrToken, importSpaces, pageSpaces, resetSpaceQrToken, updateSpace, userOptions
} from '@/api'
import { RISK_LEVELS, SPACE_TYPES } from '@/utils/dict'
import { downloadFile } from '@/utils/download'

const store = useUserStore()
const canEdit = computed(() => store.hasPerm('safety:space:edit'))
const deptProps = { label: 'deptName', children: 'children' }
const loading = ref(false)
const list = ref([])
const total = ref(0)
const depts = ref([])
const users = ref([])
const query = reactive({ current: 1, size: 10, keyword: '', spaceType: '', riskLevel: '', deptId: null })

async function load() {
  loading.value = true
  try {
    const params = Object.fromEntries(Object.entries(query).filter(([, v]) => v !== '' && v != null))
    const page = await pageSpaces(params)
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

const formVisible = ref(false)
const saving = ref(false)
const editId = ref(null)
const formRef = ref()
const empty = () => ({ spaceCode: '', spaceName: '', spaceType: '储罐', location: '', deptId: null, responsibleId: null, medium: '', riskLevel: 'MEDIUM', remark: '' })
const form = reactive(empty())
const rules = {
  spaceCode: [{ required: true, message: '请输入编号', trigger: 'blur' }],
  spaceName: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  spaceType: [{ required: true, message: '请选择类型' }],
  location: [{ required: true, message: '请输入位置', trigger: 'blur' }],
  riskLevel: [{ required: true, message: '请选择风险等级' }]
}
async function openForm(row) {
  Object.assign(form, empty())
  editId.value = row?.id || null
  if (row) Object.keys(form).forEach((k) => { if (row[k] != null) form[k] = row[k] })
  formVisible.value = true
  if (!users.value.length) users.value = await userOptions()
}
async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    if (editId.value) await updateSpace(editId.value, form)
    else await createSpace(form)
    ElMessage.success('保存成功')
    formVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}
async function toggle(row, enabled) {
  await changeSpaceStatus(row.id, enabled ? 1 : 0)
  row.status = enabled ? 1 : 0
  ElMessage.success(enabled ? '已启用' : '已停用，扫码和新建作业票将不可用')
}

const qrVisible = ref(false)
const qrSpace = ref(null)
const qrImage = ref('')
const qrUrl = ref('')
async function openQr(row) {
  qrSpace.value = row
  const token = await getSpaceQrToken(row.id)
  qrUrl.value = `${window.location.origin}/scan/${token}`
  qrImage.value = await QRCode.toDataURL(qrUrl.value, { width: 260, margin: 1 })
  qrVisible.value = true
}
async function resetQr() {
  await ElMessageBox.confirm('重新生成后，已经张贴的旧二维码将立即失效，确认吗？', '提示', { type: 'warning' })
  await resetSpaceQrToken(qrSpace.value.id)
  ElMessage.success('已重新生成')
  openQr(qrSpace.value)
}
function downloadQr() {
  const a = document.createElement('a')
  a.href = qrImage.value
  a.download = `${qrSpace.value.spaceCode}-${qrSpace.value.spaceName}.png`
  a.click()
}

// ---------- 导入导出 ----------
const exporting = ref(false)
const importVisible = ref(false)
const importing = ref(false)
const importResult = ref(null)
const queryParams = () => Object.fromEntries(Object.entries(query).filter(([k, v]) => v !== '' && v != null && !['current', 'size'].includes(k)))
async function doExport() {
  exporting.value = true
  try {
    await downloadFile('/safety/space/export', queryParams(), '受限空间台账.xlsx')
  } finally {
    exporting.value = false
  }
}
function openImport() {
  importResult.value = null
  importVisible.value = true
}
const downloadTemplate = () => downloadFile('/safety/space/import-template', {}, '受限空间台账导入模板.xlsx')
async function doImport({ file }) {
  importing.value = true
  try {
    importResult.value = await importSpaces(file)
    if (!importResult.value.errors.length) {
      ElMessage.success(`导入成功 ${importResult.value.success} 条`)
      search()
    }
  } finally {
    importing.value = false
  }
}

onMounted(async () => {
  load()
  depts.value = await deptTree()
})
</script>

<style scoped>
.qr { text-align: center; }
.qr img { width: 260px; height: 260px; }
.qr-name { font-weight: 600; margin: 8px 0; }
.qr-tip { color: #888; font-size: 12px; margin-bottom: 8px; line-height: 1.6; }
.steps { margin-bottom: 16px; }
.import-row { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.muted { color: #999; font-size: 12px; }
.result { margin: 12px 0 8px; }
</style>
