<template>
  <div class="page-card">
    <div v-if="ov" class="stats">
      <div class="stat"><b>{{ ov.today }}</b><span>今日预约</span></div>
      <div class="stat" @click="tab = 'onsite'"><b class="ok">{{ ov.onsitePeople }}</b><span>在厂外来人员</span></div>
      <div class="stat" @click="tab = 'onsite'"><b>{{ ov.onsiteVehicles }}</b><span>在厂车辆（危化品 {{ ov.onsiteHazmat }}）</span></div>
      <div class="stat" @click="filterStatus('PENDING')"><b :class="{ warn: ov.pending }">{{ ov.pending }}</b><span>危化品车辆待审批</span></div>
      <div class="stat" @click="tab = 'onsite'"><b :class="{ danger: ov.overstay }">{{ ov.overstay }}</b><span>超时未离厂</span></div>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane label="预约记录" name="list">
        <div class="toolbar">
          <el-date-picker v-model="q.date" type="date" value-format="YYYY-MM-DD" placeholder="来访日期" style="width: 150px" @change="search" />
          <el-select v-model="q.visitType" placeholder="类型" clearable style="width: 140px" @change="search">
            <el-option v-for="(v, k) in VISIT_TYPES" :key="k" :label="v.label" :value="k" />
          </el-select>
          <el-select v-model="q.status" placeholder="状态" clearable style="width: 110px" @change="search">
            <el-option v-for="(v, k) in VISIT_STATUS" :key="k" :label="v.label" :value="k" />
          </el-select>
          <el-input v-model="q.keyword" placeholder="姓名 / 单位 / 车牌 / 手机尾号" clearable style="width: 220px" @keyup.enter="search" @clear="search" />
          <el-button icon="Search" @click="search">查询</el-button>
          <div class="spacer" />
          <el-button type="primary" icon="Plus" @click="fVisible = true">预约</el-button>
        </div>
        <VisitTable :rows="rows" :loading="loading" @open="open" />
        <div class="pager">
          <el-pagination v-model:current-page="q.current" :page-size="q.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
        </div>
      </el-tab-pane>
      <el-tab-pane v-if="canGate" label="门岗（今天）" name="gate" lazy>
        <div class="muted tip">今天要来的和在厂的。点一行打开详情：核对证件，让来访人阅读《入厂安全须知》并手写签名后登记入厂；走的时候登记离厂。手机上用“我的 → 门岗登记”更方便。</div>
        <VisitTable :rows="gateRows" :loading="gLoading" @open="open" />
      </el-tab-pane>
      <el-tab-pane v-if="canGate" label="在厂清单" name="onsite" lazy>
        <div class="toolbar">
          <span class="muted">发生事故时按这个清单清点外来人员，共 {{ onsite.reduce((s, x) => s + (x.peopleCount || 1), 0) }} 人</span>
          <div class="spacer" />
          <el-button icon="Printer" @click="print">打印</el-button>
        </div>
        <el-table id="onsite-table" :data="onsite" stripe>
          <el-table-column label="类型" width="110"><template #default="{ row }">{{ VISIT_TYPES[row.visitType]?.label }}</template></el-table-column>
          <el-table-column label="姓名（人数）" width="140"><template #default="{ row }">{{ row.visitorName }}（{{ row.peopleCount }}）</template></el-table-column>
          <el-table-column prop="phone" label="手机" width="130" />
          <el-table-column prop="company" label="单位" min-width="180" show-overflow-tooltip />
          <el-table-column prop="plateNo" label="车牌" width="100" />
          <el-table-column label="位置 / 事由" min-width="180" show-overflow-tooltip><template #default="{ row }">{{ row.destination || row.purpose }}</template></el-table-column>
          <el-table-column prop="hostName" label="接待人" width="140" />
          <el-table-column label="入厂" width="140"><template #default="{ row }"><span :class="{ danger: row.planDate !== todayStr }">{{ row.enterTime?.slice(5, 16) }}</span></template></el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <VisitForm v-model="fVisible" @saved="reload" />
    <VisitDetail v-model="dVisible" :id="curId" @changed="reload" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { visitGate, visitOnsite, visitOverview, visitPage } from '@/api'
import VisitDetail from './VisitDetail.vue'
import VisitForm from './VisitForm.vue'
import VisitTable from './VisitTable.vue'
import { VISIT_STATUS, VISIT_TYPES } from './visit'

const store = useUserStore()
const canGate = computed(() => store.hasPerm('visit:gate'))
const d0 = new Date()
const todayStr = `${d0.getFullYear()}-${String(d0.getMonth() + 1).padStart(2, '0')}-${String(d0.getDate()).padStart(2, '0')}`
const tab = ref('list')
const ov = ref(null)
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const q = reactive({ current: 1, size: 20, date: null, visitType: '', status: '', keyword: '' })
const gateRows = ref([])
const gLoading = ref(false)
const onsite = ref([])
const fVisible = ref(false)
const dVisible = ref(false)
const curId = ref(null)

async function load() {
  loading.value = true
  try {
    const page = await visitPage(Object.fromEntries(Object.entries(q).filter(([, v]) => v !== '' && v != null)))
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
  tab.value = 'list'
  q.status = s
  search()
}
async function loadGate() {
  gLoading.value = true
  try {
    gateRows.value = await visitGate()
  } finally {
    gLoading.value = false
  }
}
async function loadOnsite() {
  onsite.value = await visitOnsite()
}
function reload() {
  load()
  if (canGate.value) {
    loadGate()
    loadOnsite()
  }
  visitOverview().then((v) => { ov.value = v }).catch(() => {})
}
function open(id) {
  curId.value = id
  dVisible.value = true
}
function print() {
  window.print()
}
watch(tab, (t) => {
  if (t === 'gate') loadGate()
  if (t === 'onsite') loadOnsite()
})
onMounted(reload)
</script>

<style scoped>
.muted { color: #999; font-size: 12px; }
.tip { margin-bottom: 8px; }
.stats { display: flex; gap: 12px; margin-bottom: 8px; flex-wrap: wrap; }
.stat { flex: 1; min-width: 130px; padding: 12px 16px; border: 1px solid #ebeef5; border-radius: 8px; display: flex; align-items: baseline; gap: 8px; cursor: pointer; }
.stat b { font-size: 24px; }
.stat span { color: #888; font-size: 13px; }
.ok { color: #52c41a; }
.warn { color: #e6a23c; }
.danger { color: #f56c6c; }
</style>
