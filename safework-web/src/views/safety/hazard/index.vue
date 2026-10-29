<template>
  <div class="page-card">
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="编号 / 标题 / 位置" clearable style="width: 200px" @keyup.enter="search" @clear="search" />
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="search">
        <el-option v-for="(v, k) in HAZARD_STATUS" :key="k" :label="v.label" :value="k" />
      </el-select>
      <el-select v-model="query.level" placeholder="等级" clearable style="width: 120px" @change="search">
        <el-option v-for="(v, k) in HAZARD_LEVELS" :key="k" :label="v.label" :value="k" />
      </el-select>
      <el-checkbox v-model="query.overdue" @change="search">只看逾期</el-checkbox>
      <el-button icon="Search" @click="search">查询</el-button>
      <div class="spacer" />
      <el-button v-perm="'safety:hazard:report'" type="danger" icon="Warning" @click="openReport">上报隐患</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe @row-click="(r) => detailRef.open(r.id)">
      <el-table-column prop="hazardNo" label="编号" width="160" />
      <el-table-column label="等级" width="100">
        <template #default="{ row }"><el-tag :type="HAZARD_LEVELS[row.level]?.type" effect="dark">{{ HAZARD_LEVELS[row.level]?.label }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="title" label="隐患" min-width="200" show-overflow-tooltip />
      <el-table-column prop="category" label="类别" width="100" />
      <el-table-column prop="location" label="位置" min-width="120" show-overflow-tooltip />
      <el-table-column prop="reporterName" label="上报人" width="140" />
      <el-table-column prop="rectifierName" label="整改人" width="140" />
      <el-table-column label="整改期限" width="170">
        <template #default="{ row }">
          <span :class="{ overdue: row.overdue }">{{ row.deadline || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110" fixed="right">
        <template #default="{ row }">
          <el-tag :type="HAZARD_STATUS[row.status]?.type">{{ HAZARD_STATUS[row.status]?.label }}</el-tag>
          <el-tag v-if="row.overdue" type="danger" effect="dark" size="small" class="od">逾期</el-tag>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination v-model:current-page="query.current" :page-size="query.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
    </div>

    <el-dialog v-model="reportVisible" title="上报隐患" width="560px">
      <el-form ref="reportRef" :model="report" :rules="rules" label-width="90px">
        <el-form-item label="隐患标题" prop="title"><el-input v-model="report.title" maxlength="100" placeholder="一句话描述问题" /></el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="类别" prop="category">
              <el-select v-model="report.category" style="width: 100%"><el-option v-for="c in HAZARD_CATEGORIES" :key="c" :label="c" :value="c" /></el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="等级" prop="level">
              <el-radio-group v-model="report.level">
                <el-radio v-for="(v, k) in HAZARD_LEVELS" :key="k" :value="k">{{ v.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="关联空间">
          <el-select v-model="report.spaceId" clearable filterable placeholder="可选：隐患位于某个受限空间" style="width: 100%" @change="onSpace">
            <el-option v-for="sp in spaces" :key="sp.id" :label="`${sp.spaceCode} ${sp.spaceName}`" :value="sp.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="位置" prop="location"><el-input v-model="report.location" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="report.description" type="textarea" :rows="3" maxlength="1000" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reportVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doReport">提交</el-button>
      </template>
    </el-dialog>

    <HazardDetail ref="detailRef" @changed="load" />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { pageHazards, reportHazard, spaceOptions } from '@/api'
import { HAZARD_CATEGORIES, HAZARD_LEVELS, HAZARD_STATUS } from '@/utils/dict'
import HazardDetail from './HazardDetail.vue'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const detailRef = ref()
const query = reactive({ current: 1, size: 10, keyword: '', status: '', level: '', overdue: false })

async function load() {
  loading.value = true
  try {
    const params = Object.fromEntries(Object.entries(query).filter(([, v]) => v !== '' && v != null && v !== false))
    const page = await pageHazards(params)
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

const reportVisible = ref(false)
const saving = ref(false)
const reportRef = ref()
const spaces = ref([])
const empty = () => ({ title: '', category: '作业环境', level: 'GENERAL', location: '', spaceId: null, description: '' })
const report = reactive(empty())
const rules = {
  title: [{ required: true, message: '请输入隐患标题', trigger: 'blur' }],
  category: [{ required: true, message: '请选择类别' }],
  level: [{ required: true, message: '请选择等级' }],
  location: [{ required: true, message: '请输入位置', trigger: 'blur' }]
}
async function openReport() {
  Object.assign(report, empty())
  reportVisible.value = true
  spaces.value = await spaceOptions()
}
function onSpace(id) {
  const sp = spaces.value.find((x) => x.id === id)
  if (sp && !report.location) report.location = sp.location
}
async function doReport() {
  await reportRef.value.validate()
  saving.value = true
  try {
    const id = await reportHazard(report)
    ElMessage.success('已上报，等待安全员指派整改')
    reportVisible.value = false
    load()
    detailRef.value.open(id)
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
:deep(.el-table__row) { cursor: pointer; }
.overdue { color: #f56c6c; font-weight: 600; }
.od { margin-left: 4px; }
</style>
