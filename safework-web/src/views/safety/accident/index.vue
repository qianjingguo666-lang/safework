<template>
  <div class="page-card">
    <div v-if="ov" class="stats">
      <div class="stat" @click="filter('INVESTIGATING')"><b>{{ ov.investigating }}</b><span>调查中</span></div>
      <div class="stat" @click="filter('OVERDUE')"><b class="danger">{{ ov.overdue }}</b><span>调查超期</span></div>
      <div class="stat"><b class="warn">{{ ov.openMeasures }}</b><span>整改措施未完成</span></div>
      <div class="stat"><b class="danger">{{ ov.overdueMeasures }}</b><span>整改措施逾期</span></div>
      <div class="stat" @click="filter('CLOSED')"><b class="ok">{{ ov.closedThisYear }}</b><span>今年结案</span></div>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane label="事故调查" name="list">
        <div class="toolbar">
          <el-input v-model="query.keyword" placeholder="事故名称 / 编号 / 组长" clearable style="width: 200px" @keyup.enter="search" @clear="search" />
          <el-select v-model="query.state" placeholder="状态" clearable style="width: 120px" @change="search">
            <el-option v-for="(v, k) in ACCIDENT_STATES" :key="k" :label="v.label" :value="k" />
          </el-select>
          <el-button icon="Search" @click="search">查询</el-button>
          <div class="spacer" />
          <el-button v-if="canEdit" type="primary" icon="Plus" @click="startRef.open()">启动调查</el-button>
        </div>
        <el-table v-loading="loading" :data="rows" stripe @row-click="(r) => detailRef.open(r.investigation.id)">
          <el-table-column label="事故" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="title">{{ row.investigation.eventTitle }}</div>
              <div class="muted">{{ row.investigation.eventNo }} · {{ row.investigation.occurTime?.slice(0, 16) }}</div>
            </template>
          </el-table-column>
          <el-table-column label="等级" width="100">
            <template #default="{ row }"><el-tag size="small" :type="EMERGENCY_LEVELS[row.investigation.level]?.type">{{ EMERGENCY_LEVELS[row.investigation.level]?.label }}</el-tag></template>
          </el-table-column>
          <el-table-column label="调查组长" width="140" show-overflow-tooltip><template #default="{ row }">{{ row.investigation.leaderName }}</template></el-table-column>
          <el-table-column label="调查期限" width="110"><template #default="{ row }">{{ row.investigation.deadline }}</template></el-table-column>
          <el-table-column label="四不放过" width="140">
            <template #default="{ row }">
              <span class="dots"><i v-for="n in 4" :key="n" :class="{ on: n <= row.fourOk }" /></span>
              {{ row.fourOk }}/4
            </template>
          </el-table-column>
          <el-table-column label="整改措施" width="130">
            <template #default="{ row }">
              <template v-if="row.measureTotal">
                {{ row.measureDone }}/{{ row.measureTotal }}
                <span v-if="row.measureOverdue" class="danger small">{{ row.measureOverdue }} 项逾期</span>
              </template>
              <span v-else class="muted">-</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }"><el-tag size="small" :type="ACCIDENT_STATES[row.investigation.state]?.type">{{ ACCIDENT_STATES[row.investigation.state]?.label }}</el-tag></template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!loading && !rows.length" description="还没有事故调查" />
        <div class="pager">
          <el-pagination v-model:current-page="query.current" :page-size="query.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
        </div>
      </el-tab-pane>
      <el-tab-pane name="mine" lazy>
        <template #label>我的整改措施<el-badge v-if="myOpen" :value="myOpen" class="badge" /></template>
        <MyMeasures @changed="reload" />
      </el-tab-pane>
    </el-tabs>

    <StartDialog ref="startRef" @started="afterStart" />
    <AccidentDetail ref="detailRef" @changed="reload" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useUserStore } from '@/stores/user'
import { accidentOverview, accidentPage, myAccidentMeasures } from '@/api'
import { ACCIDENT_STATES, EMERGENCY_LEVELS } from '@/utils/dict'
import AccidentDetail from './AccidentDetail.vue'
import MyMeasures from './MyMeasures.vue'
import StartDialog from './StartDialog.vue'

const store = useUserStore()
const canEdit = computed(() => store.hasPerm('accident:edit'))
const tab = ref('list')
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const ov = ref(null)
const myOpen = ref(0)
const startRef = ref()
const detailRef = ref()
const query = reactive({ current: 1, size: 20, keyword: '', state: '' })

async function load() {
  loading.value = true
  try {
    const params = Object.fromEntries(Object.entries(query).filter(([, v]) => v !== '' && v != null))
    const page = await accidentPage(params)
    rows.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}
function search() {
  query.current = 1
  load()
}
function filter(state) {
  tab.value = 'list'
  query.state = state
  search()
}
function reload() {
  load()
  accidentOverview().then((v) => { ov.value = v })
  myAccidentMeasures().then((l) => { myOpen.value = l.filter((m) => m.status === 'OPEN').length })
}
function afterStart(id) {
  reload()
  detailRef.value.open(id)
}
onMounted(async () => {
  reload()
  const mine = await myAccidentMeasures()
  if (!canEdit.value && mine.some((m) => m.status === 'OPEN')) tab.value = 'mine'
})
</script>

<style scoped>
.stats { display: flex; gap: 12px; margin-bottom: 8px; flex-wrap: wrap; }
.stat { flex: 1; min-width: 110px; padding: 12px 16px; border: 1px solid #ebeef5; border-radius: 8px; display: flex; align-items: baseline; gap: 8px; cursor: pointer; }
.stat b { font-size: 24px; }
.stat span { color: #888; font-size: 13px; }
.warn { color: #e6a23c; }
.danger { color: #f56c6c; }
.ok { color: #67c23a; }
.small { font-size: 12px; margin-left: 4px; }
.muted { color: #999; font-size: 12px; }
.title { font-weight: 500; }
.dots { display: inline-flex; gap: 3px; margin-right: 6px; vertical-align: middle; }
.dots i { width: 10px; height: 10px; border-radius: 50%; background: #e4e7ed; }
.dots i.on { background: #67c23a; }
.badge { margin-left: 4px; }
:deep(.el-table__row) { cursor: pointer; }
</style>
