<template>
  <div class="page-card">
    <div v-if="ov" class="stats">
      <div class="stat" @click="filterStatus('')"><b>{{ ov.yearCases }}</b><span>今年工伤（人）</span></div>
      <div class="stat" @click="filterStatus('REPORTED')"><b :class="{ warn: ov.pendingApply }">{{ ov.pendingApply }}</b><span>待申请认定</span></div>
      <div class="stat" @click="filterStatus('REPORTED')"><b :class="{ danger: ov.overdueApply }">{{ ov.overdueApply }}</b><span>申请认定已超期</span></div>
      <div class="stat"><b>{{ ov.open }}</b><span>处理中</span></div>
      <div class="stat"><b class="money">{{ money(ov.yearFund) }}</b><span>今年基金支付（元）</span></div>
      <div class="stat"><b class="money">{{ money(ov.yearCompany) }}</b><span>今年单位支付（元）</span></div>
    </div>
    <el-alert type="info" :closable="false" class="mb" show-icon
      title="《工伤保险条例》第十七条：职工受伤后，单位应在 30 日内向社会保险行政部门申请工伤认定；没按期申请的，这期间发生的工伤待遇等费用由单位负担。承包商人员由其所在单位申请，本企业督促并跟踪。" />

    <div class="toolbar">
      <el-select v-model="q.year" placeholder="年份" clearable style="width: 110px" @change="search">
        <el-option v-for="y in years" :key="y" :label="`${y} 年`" :value="y" />
      </el-select>
      <el-select v-model="q.status" placeholder="状态" clearable style="width: 140px" @change="search">
        <el-option v-for="(v, k) in INJURY_STATUS" :key="k" :label="v.label" :value="k" />
      </el-select>
      <el-input v-model="q.keyword" placeholder="姓名 / 单位 / 编号" clearable style="width: 200px" @keyup.enter="search" @clear="search" />
      <el-button icon="Search" @click="search">查询</el-button>
      <div class="spacer" />
      <el-button v-if="canEdit" type="primary" icon="Plus" @click="openForm(null)">登记工伤</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" stripe @row-click="(r) => open(r.injury.id)" class="clickable">
      <el-table-column label="编号" width="160"><template #default="{ row }">{{ row.injury.caseNo }}</template></el-table-column>
      <el-table-column label="受伤人员" min-width="170">
        <template #default="{ row }">
          {{ row.injury.injuredName }}
          <div class="muted">{{ row.injury.employerType === 'CONTRACTOR' ? '承包商：' + row.injury.employerName : row.injury.deptName || '本企业' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="受伤时间" width="140"><template #default="{ row }">{{ row.injury.injuryTime?.slice(0, 16) }}</template></el-table-column>
      <el-table-column label="地点 / 伤情" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ row.injury.location }} · {{ [row.injury.injuryPart, row.injury.injuryNature].filter(Boolean).join(' ') || row.injury.description }}</template>
      </el-table-column>
      <el-table-column label="状态" width="170">
        <template #default="{ row }">
          <el-tag :type="INJURY_STATUS[row.injury.status]?.type" size="small">{{ INJURY_STATUS[row.injury.status]?.label }}</el-tag>
          <el-tag v-if="deadlineTag(row.deadline)" :type="deadlineTag(row.deadline).type" size="small" effect="dark" class="ml">{{ deadlineTag(row.deadline).text }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="伤残等级" width="110"><template #default="{ row }">{{ row.injury.grade == null ? '-' : GRADES[row.injury.grade].label }}</template></el-table-column>
      <el-table-column label="费用合计（元）" width="130" align="right"><template #default="{ row }">{{ money(row.costTotal) }}</template></el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination v-model:current-page="q.current" :page-size="q.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
    </div>

    <InjuryForm v-model="fVisible" :data="editing" @saved="afterSave" />
    <InjuryDetail v-model="dVisible" :id="curId" @changed="reload" @edit="openForm" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useUserStore } from '@/stores/user'
import { injuryOverview, injuryPage } from '@/api'
import InjuryDetail from './InjuryDetail.vue'
import InjuryForm from './InjuryForm.vue'
import { deadlineTag, GRADES, INJURY_STATUS, money } from './injury'

const store = useUserStore()
const canEdit = computed(() => store.hasPerm('injury:edit'))
const thisYear = new Date().getFullYear()
const years = [thisYear, thisYear - 1, thisYear - 2]
const ov = ref(null)
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const q = reactive({ current: 1, size: 20, year: null, status: '', keyword: '' })
const fVisible = ref(false)
const editing = ref(null)
const dVisible = ref(false)
const curId = ref(null)

async function load() {
  loading.value = true
  try {
    const page = await injuryPage(Object.fromEntries(Object.entries(q).filter(([, v]) => v !== '' && v != null)))
    rows.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}
function search() {
  q.current = 1
  load()
}
function filterStatus(s) {
  q.status = s
  search()
}
function reload() {
  load()
  injuryOverview().then((v) => { ov.value = v }).catch(() => {})
}
function open(id) {
  curId.value = id
  dVisible.value = true
}
function afterSave() {
  dVisible.value = false
  reload()
}
function openForm(injury) {
  editing.value = injury
  fVisible.value = true
}
onMounted(reload)
</script>

<style scoped>
.muted { color: #999; font-size: 12px; }
.mb { margin-bottom: 10px; }
.ml { margin-left: 4px; }
.stats { display: flex; gap: 12px; margin-bottom: 10px; flex-wrap: wrap; }
.stat { flex: 1; min-width: 130px; padding: 12px 16px; border: 1px solid #ebeef5; border-radius: 8px; display: flex; align-items: baseline; gap: 8px; cursor: pointer; }
.stat b { font-size: 24px; }
.stat b.money { font-size: 18px; }
.stat span { color: #888; font-size: 13px; }
.warn { color: #e6a23c; }
.danger { color: #f56c6c; }
.clickable :deep(tr) { cursor: pointer; }
</style>
