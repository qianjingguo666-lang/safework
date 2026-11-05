<template>
  <div class="page-card">
    <div v-if="overview" class="stats">
      <div v-if="canEdit" class="stat" @click="filter('ABNORMAL')"><b class="danger">{{ overview.contraindication }}</b><span>禁忌证仍在岗</span></div>
      <div class="stat" @click="filter('OVERDUE')"><b class="danger">{{ overview.overdue }}</b><span>体检逾期</span></div>
      <div class="stat" @click="filter('NEVER')"><b class="danger">{{ overview.never }}</b><span>从未体检</span></div>
      <div class="stat" @click="filter('DUE_SOON')"><b class="warn">{{ overview.dueSoon }}</b><span>30 天内到期</span></div>
      <div class="stat" @click="tab = 'exam'; examRef?.showOpen()"><b class="warn">{{ overview.abnormalOpen }}</b><span>异常未处理</span></div>
      <div class="stat" @click="filter('')"><b>{{ overview.exposed }}</b><span>{{ canEdit ? '接害人数' : '我接触危害因素' }}</span></div>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane :label="canEdit ? '监护台账' : '我的职业健康'" name="ledger">
        <div class="toolbar">
          <el-input v-if="canEdit" v-model="query.keyword" placeholder="姓名 / 岗位 / 危害因素" clearable style="width: 190px" @keyup.enter="search" @clear="search" />
          <el-select v-model="query.state" placeholder="状态" clearable style="width: 170px" @change="search">
            <el-option label="需要体检（逾期 + 从未体检）" value="NEED" />
            <el-option label="有异常 / 禁忌证" value="ABNORMAL" />
            <el-option v-for="(v, k) in OH_STATES" :key="k" :label="v.label" :value="k" />
          </el-select>
          <el-button icon="Search" @click="search">查询</el-button>
          <div class="spacer" />
          <el-button v-if="canEdit" type="primary" icon="Plus" @click="examDialog.open()">登记体检</el-button>
        </div>
        <el-table v-loading="loading" :data="rows" stripe>
          <el-table-column v-if="canEdit" prop="userName" label="人员" width="150" show-overflow-tooltip />
          <el-table-column prop="posts" label="岗位" width="130" show-overflow-tooltip />
          <el-table-column prop="factors" label="接触的危害因素" min-width="180" show-overflow-tooltip />
          <el-table-column label="周期" width="80"><template #default="{ row }">{{ row.cycleMonths }} 个月</template></el-table-column>
          <el-table-column label="最近体检" width="190">
            <template #default="{ row }">
              <template v-if="row.lastExamDate">
                {{ row.lastExamDate }}
                <el-tag size="small" :type="OH_CONCLUSIONS[row.lastConclusion]?.type">{{ OH_CONCLUSIONS[row.lastConclusion]?.label }}</el-tag>
              </template>
              <span v-else class="muted">-</span>
            </template>
          </el-table-column>
          <el-table-column label="下次体检" width="110"><template #default="{ row }">{{ row.nextExamDate || '-' }}</template></el-table-column>
          <el-table-column label="状态" width="200">
            <template #default="{ row }">
              <el-tag v-if="row.contraindication" size="small" type="danger" effect="dark">禁忌证，应调岗</el-tag>
              <el-tag size="small" :type="OH_STATES[row.state]?.type">{{ OH_STATES[row.state]?.label }}</el-tag>
              <span v-if="row.daysLeft != null && row.state !== 'OK'" class="days">{{ row.daysLeft < 0 ? `逾期 ${-row.daysLeft} 天` : `剩 ${row.daysLeft} 天` }}</span>
              <el-tag v-if="row.openAbnormal" size="small" type="warning" effect="plain">{{ row.openAbnormal }} 条异常待处理</el-tag>
            </template>
          </el-table-column>
          <el-table-column v-if="canEdit" label="操作" width="80">
            <template #default="{ row }"><el-button link type="primary" @click="examDialog.open(row.userId)">登记</el-button></template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!loading && !rows.length && !canEdit" description="你所在的岗位没有接触职业病危害因素" />
        <div v-if="canEdit" class="pager">
          <el-pagination v-model:current-page="query.current" :page-size="query.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
        </div>
      </el-tab-pane>

      <el-tab-pane :label="canEdit ? '体检记录' : '我的体检记录'" name="exam" lazy>
        <ExamRecords ref="examRef" :can-edit="canEdit" @changed="reload" />
      </el-tab-pane>
      <el-tab-pane v-if="canEdit" label="危害因素" name="factor" lazy>
        <FactorList @changed="reload" />
      </el-tab-pane>
      <el-tab-pane v-if="canEdit" label="岗位接害" name="post" lazy>
        <PostFactors @changed="reload" />
      </el-tab-pane>
    </el-tabs>

    <ExamDialog ref="examDialog" @saved="afterExam" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useUserStore } from '@/stores/user'
import { healthLedger, healthOverview } from '@/api'
import { OH_CONCLUSIONS, OH_STATES } from '@/utils/dict'
import ExamDialog from './ExamDialog.vue'
import ExamRecords from './ExamRecords.vue'
import FactorList from './FactorList.vue'
import PostFactors from './PostFactors.vue'

const store = useUserStore()
const canEdit = computed(() => store.hasPerm('health:edit'))
const tab = ref('ledger')
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const overview = ref(null)
const examDialog = ref()
const examRef = ref()
const query = reactive({ current: 1, size: 20, keyword: '', state: '' })

async function load() {
  loading.value = true
  try {
    const params = Object.fromEntries(Object.entries(query).filter(([, v]) => v !== '' && v != null))
    const page = await healthLedger(params)
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
  tab.value = 'ledger'
  query.state = state
  search()
}
function reload() {
  load()
  healthOverview().then((v) => { overview.value = v })
}
function afterExam() {
  reload()
  examRef.value?.load()
}
onMounted(reload)
</script>

<style scoped>
.stats { display: flex; gap: 12px; margin-bottom: 8px; flex-wrap: wrap; }
.stat { flex: 1; min-width: 110px; padding: 12px 16px; border: 1px solid #ebeef5; border-radius: 8px; display: flex; align-items: baseline; gap: 8px; cursor: pointer; }
.stat b { font-size: 24px; }
.stat span { color: #888; font-size: 13px; }
.warn { color: #e6a23c; }
.danger { color: #f56c6c; }
.muted { color: #999; }
.days { margin: 0 6px; font-size: 12px; color: #999; }
.el-tag + .el-tag { margin-left: 4px; }
</style>
