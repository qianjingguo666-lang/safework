<template>
  <div class="page-card">
    <div v-if="ov" class="stats">
      <div class="stat" @click="filter({ todo: true })"><b class="primary">{{ ov.myTodo }}</b><span>要我处理</span></div>
      <div class="stat" @click="filter({ status: 'ASSESSING' })"><b class="warn">{{ ov.assessing }}</b><span>风险评估</span></div>
      <div class="stat" @click="filter({ status: 'APPROVING' })"><b class="warn">{{ ov.approving }}</b><span>待审批</span></div>
      <div class="stat" @click="filter({ status: 'IMPLEMENTING' })"><b>{{ ov.implementing }}</b><span>实施中</span></div>
      <div class="stat" @click="filter({ status: 'PSSR' })"><b>{{ ov.pssr }}</b><span>投用前检查</span></div>
      <div class="stat"><b :class="ov.restoreOverdue ? 'danger' : ''">{{ ov.temporaryOpen }}</b><span>临时变更未恢复<template v-if="ov.restoreOverdue">（{{ ov.restoreOverdue }} 项超期）</template></span></div>
    </div>

    <div class="toolbar">
      <el-radio-group v-model="query.todo" @change="search">
        <el-radio-button :value="true">要我处理</el-radio-button>
        <el-radio-button :value="false">全部</el-radio-button>
      </el-radio-group>
      <el-input v-model="query.keyword" placeholder="名称 / 编号 / 申请人 / 地点" clearable style="width: 210px" @keyup.enter="search" @clear="search" />
      <el-select v-model="query.changeType" placeholder="类型" clearable style="width: 140px" @change="search">
        <el-option v-for="(v, k) in MOC_TYPES" :key="k" :label="v" :value="k" />
      </el-select>
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="search">
        <el-option v-for="(v, k) in MOC_STATUS" :key="k" :label="v.label" :value="k" />
      </el-select>
      <el-button icon="Search" @click="search">查询</el-button>
      <div class="spacer" />
      <el-button type="primary" icon="Plus" @click="formRef.open()">申请变更</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" stripe @row-click="(r) => detailRef.open(r.change.id)">
      <el-table-column label="变更" min-width="240">
        <template #default="{ row }">
          <div class="title">
            <el-badge v-if="row.todo" is-dot class="dot" />{{ row.change.title }}
            <el-tag v-if="row.change.temporary" size="small" type="warning" effect="plain">临时</el-tag>
          </div>
          <div class="muted">{{ row.change.changeNo }} · {{ row.change.location }}</div>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="130"><template #default="{ row }">{{ MOC_TYPES[row.change.changeType] }}</template></el-table-column>
      <el-table-column label="申请人" width="140" show-overflow-tooltip><template #default="{ row }">{{ row.change.applicantName }}</template></el-table-column>
      <el-table-column label="风险" width="110">
        <template #default="{ row }">
          <span v-if="row.change.riskLevel" class="grade" :style="{ background: RISK_GRADES[row.change.riskLevel].color, color: RISK_GRADES[row.change.riskLevel].text }">{{ RISK_GRADES[row.change.riskLevel].short }} {{ row.change.lecD }}</span>
          <span v-else class="muted">未评估</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="170">
        <template #default="{ row }">
          <el-tag size="small" :type="MOC_STATUS[row.change.status]?.type">{{ MOC_STATUS[row.change.status]?.label }}</el-tag>
          <span v-if="row.change.status === 'APPROVING'" class="muted small">{{ row.approved }}/{{ row.change.approvalsNeeded }} 人已批</span>
          <el-tag v-if="row.restoreOverdue" size="small" type="danger" effect="dark">超期未恢复</el-tag>
          <span v-else-if="row.change.temporary && row.change.status === 'CLOSED'" class="muted small">{{ row.change.restored ? '已恢复' : `${row.change.restoreDate} 恢复` }}</span>
        </template>
      </el-table-column>
      <el-table-column label="申请时间" width="110"><template #default="{ row }">{{ row.change.createTime?.slice(0, 10) }}</template></el-table-column>
    </el-table>
    <el-empty v-if="!loading && !rows.length" :description="query.todo ? '没有要你处理的变更' : '还没有变更'" />
    <div class="pager">
      <el-pagination v-model:current-page="query.current" :page-size="query.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
    </div>

    <MocForm ref="formRef" @saved="afterSave" />
    <MocDetail ref="detailRef" @changed="reload" @edit="(c) => formRef.open(c)" />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { mocOverview, mocPage } from '@/api'
import { MOC_STATUS, MOC_TYPES, RISK_GRADES } from '@/utils/dict'
import MocDetail from './MocDetail.vue'
import MocForm from './MocForm.vue'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const ov = ref(null)
const formRef = ref()
const detailRef = ref()
const query = reactive({ current: 1, size: 20, todo: true, keyword: '', status: '', changeType: '' })

async function load() {
  loading.value = true
  try {
    const params = Object.fromEntries(Object.entries(query).filter(([, v]) => v !== '' && v != null))
    const page = await mocPage(params)
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
function filter(f) {
  Object.assign(query, { todo: false, status: '', changeType: '' }, f)
  search()
}
function reload() {
  load()
  mocOverview().then((v) => { ov.value = v })
}
function afterSave(id) {
  reload()
  detailRef.value.open(id)
}
onMounted(async () => {
  ov.value = await mocOverview()
  if (!ov.value.myTodo) query.todo = false
  load()
})
</script>

<style scoped>
.stats { display: flex; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; }
.stat { flex: 1; min-width: 110px; padding: 12px 16px; border: 1px solid #ebeef5; border-radius: 8px; display: flex; align-items: baseline; gap: 8px; cursor: pointer; }
.stat b { font-size: 24px; }
.stat span { color: #888; font-size: 13px; }
.primary { color: #409eff; }
.warn { color: #e6a23c; }
.danger { color: #f56c6c; }
.title { font-weight: 500; display: flex; align-items: center; gap: 6px; }
.dot { margin-right: 2px; }
.muted { color: #999; font-size: 12px; }
.small { margin-left: 6px; }
.grade { display: inline-block; padding: 1px 8px; border-radius: 4px; font-size: 12px; }
:deep(.el-table__row) { cursor: pointer; }
</style>
