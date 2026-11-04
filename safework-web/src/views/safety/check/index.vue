<template>
  <div class="page-card">
    <div v-if="overview" class="stats">
      <div class="stat" @click="showTasks('PENDING')"><b>{{ overview.pending }}</b><span>待检查</span></div>
      <div class="stat" @click="showTasks('OVERDUE')"><b class="danger">{{ overview.overdue }}</b><span>逾期未检查</span></div>
      <div class="stat" @click="showTasks('DONE')"><b class="ok">{{ overview.monthDone }}</b><span>本月完成</span></div>
      <div class="stat"><b class="warn">{{ overview.monthFail }}</b><span>本月不合格项</span></div>
      <div class="stat"><b class="warn">{{ overview.monthHazard }}</b><span>本月转隐患</span></div>
      <div class="stat"><b>{{ overview.onTimeRate == null ? '-' : overview.onTimeRate + '%' }}</b><span>按时完成率</span></div>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane label="检查任务" name="task">
        <div class="toolbar">
          <el-input v-model="query.keyword" placeholder="编号 / 计划 / 区域 / 检查人" clearable style="width: 200px" @keyup.enter="search" @clear="search" />
          <el-select v-model="query.state" placeholder="状态" clearable style="width: 120px" @change="search">
            <el-option v-for="(v, k) in CHECK_TASK_STATES" :key="k" :label="k === 'PENDING' ? '待检查（含逾期）' : v.label" :value="k" />
          </el-select>
          <el-checkbox v-if="canEdit" v-model="query.mine" @change="search">只看我的</el-checkbox>
          <el-button icon="Search" @click="search">查询</el-button>
        </div>
        <el-table v-loading="loading" :data="list" stripe @row-click="(r) => detailRef.open(r.id)">
          <el-table-column prop="taskNo" label="编号" width="150" />
          <el-table-column prop="planName" label="检查计划" min-width="170" show-overflow-tooltip />
          <el-table-column label="类型" width="100"><template #default="{ row }">{{ CHECK_TYPES[row.checkType] }}</template></el-table-column>
          <el-table-column prop="location" label="检查区域" min-width="150" show-overflow-tooltip />
          <el-table-column prop="checkerName" label="检查人" width="140" show-overflow-tooltip />
          <el-table-column label="截止日期" width="110"><template #default="{ row }">{{ row.dueDate }}</template></el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }"><el-tag size="small" :type="CHECK_TASK_STATES[row.state]?.type">{{ CHECK_TASK_STATES[row.state]?.label }}</el-tag></template>
          </el-table-column>
          <el-table-column label="结果" width="150">
            <template #default="{ row }">
              <template v-if="row.status === 'DONE'">
                <span v-if="!row.failCount" class="ok">全部合格</span>
                <span v-else class="danger">{{ row.failCount }} 项不合格<template v-if="row.hazardCount">，转隐患 {{ row.hazardCount }}</template></span>
              </template>
              <span v-else class="muted">共 {{ row.itemCount }} 项</span>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination v-model:current-page="query.current" :page-size="query.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
        </div>
      </el-tab-pane>

      <el-tab-pane v-if="canEdit" label="检查计划" name="plan" lazy>
        <PlanList ref="planRef" @changed="reload" />
      </el-tab-pane>
      <el-tab-pane v-if="canEdit" label="检查表" name="template" lazy>
        <TemplateList />
      </el-tab-pane>
    </el-tabs>

    <CheckTaskDetail ref="detailRef" @changed="reload" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { checkOverview, checkTaskPage } from '@/api'
import { CHECK_TASK_STATES, CHECK_TYPES } from '@/utils/dict'
import CheckTaskDetail from './CheckTaskDetail.vue'
import PlanList from './PlanList.vue'
import TemplateList from './TemplateList.vue'

const route = useRoute()
const store = useUserStore()
const canEdit = computed(() => store.hasPerm('check:edit'))
const tab = ref('task')
const loading = ref(false)
const list = ref([])
const total = ref(0)
const overview = ref(null)
const detailRef = ref()
const planRef = ref()
const query = reactive({ current: 1, size: 10, keyword: '', state: 'PENDING', mine: false })

async function load() {
  loading.value = true
  try {
    const params = Object.fromEntries(Object.entries(query).filter(([, v]) => v !== '' && v != null && v !== false))
    const page = await checkTaskPage(params)
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
function reload() {
  load()
  checkOverview().then((v) => { overview.value = v })
}
function showTasks(state) {
  tab.value = 'task'
  query.state = state
  search()
}

onMounted(reload)
// 从消息点进来：/safety/check?detail=ID 直接打开任务
const openFromQuery = () => { if (route.query.detail) detailRef.value?.open(route.query.detail) }
onMounted(openFromQuery)
watch(() => route.query.detail, openFromQuery)
</script>

<style scoped>
.stats { display: flex; gap: 12px; margin-bottom: 8px; flex-wrap: wrap; }
.stat { flex: 1; min-width: 110px; padding: 12px 16px; border: 1px solid #ebeef5; border-radius: 8px; display: flex; align-items: baseline; gap: 8px; cursor: pointer; }
.stat b { font-size: 24px; }
.stat span { color: #888; font-size: 13px; }
.warn { color: #e6a23c; }
.danger { color: #f56c6c; }
.ok { color: #67c23a; }
.muted { color: #999; font-size: 12px; }
:deep(.el-table__row) { cursor: pointer; }
</style>
