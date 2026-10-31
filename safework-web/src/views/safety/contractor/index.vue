<template>
  <div class="page-card">
    <div v-if="overview" class="stats">
      <div class="stat" :class="{ on: query.status === 'APPROVED' }" @click="filter('APPROVED')"><b class="ok">{{ overview.approved }}</b><span>合格单位</span></div>
      <div class="stat" :class="{ on: query.status === 'PENDING' }" @click="filter('PENDING')"><b class="warn">{{ overview.pending }}</b><span>待审核</span></div>
      <div class="stat" :class="{ on: query.status === 'BLACKLISTED' }" @click="filter('BLACKLISTED')"><b class="danger">{{ overview.blacklisted }}</b><span>黑名单</span></div>
      <div class="stat plain"><b class="warn">{{ overview.expiring }}</b><span>资质 / 保险快到期</span></div>
      <div class="stat plain"><b class="warn">{{ overview.workerWarnings }}</b><span>人员培训快到期</span></div>
    </div>

    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="单位名称 / 信用代码" clearable style="width: 220px" @keyup.enter="search" @clear="search" />
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="search">
        <el-option v-for="(v, k) in CONTRACTOR_STATUS" :key="k" :label="v.label" :value="k" />
      </el-select>
      <el-button icon="Search" @click="search">查询</el-button>
      <div class="spacer" />
      <el-button v-if="canEdit" type="primary" icon="Plus" @click="formRef.open()">登记承包商</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe @row-click="(r) => detailRef.open(r.id)">
      <el-table-column prop="name" label="单位名称" min-width="200" show-overflow-tooltip />
      <el-table-column prop="businessScope" label="业务范围" min-width="150" show-overflow-tooltip />
      <el-table-column label="联系人" width="150"><template #default="{ row }">{{ row.contactName }} {{ row.contactPhone }}</template></el-table-column>
      <el-table-column label="在场人员" width="90" align="center"><template #default="{ row }">{{ row.workerCount }}</template></el-table-column>
      <el-table-column label="安全评分" width="100" align="center">
        <template #default="{ row }"><span class="score" :class="scoreClass(row.score)">{{ row.score }}</span></template>
      </el-table-column>
      <el-table-column label="状态" width="200">
        <template #default="{ row }">
          <el-tag size="small" :type="CONTRACTOR_STATUS[row.status]?.type" :effect="row.status === 'BLACKLISTED' ? 'dark' : 'light'">{{ CONTRACTOR_STATUS[row.status]?.label }}</el-tag>
          <div v-for="w in row.warnings" :key="w" class="warning">{{ w }}</div>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination v-model:current-page="query.current" :page-size="query.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
    </div>

    <ContractorForm ref="formRef" @saved="(id) => { reload(); detailRef.open(id) }" />
    <ContractorDetail ref="detailRef" @changed="reload" @edit="(c) => formRef.open(c)" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useUserStore } from '@/stores/user'
import { contractorOverview, contractorPage } from '@/api'
import { CONTRACTOR_STATUS } from '@/utils/dict'
import ContractorDetail from './ContractorDetail.vue'
import ContractorForm from './ContractorForm.vue'

const store = useUserStore()
const canEdit = computed(() => store.hasPerm('contractor:edit'))
const loading = ref(false)
const list = ref([])
const total = ref(0)
const overview = ref(null)
const formRef = ref()
const detailRef = ref()
const query = reactive({ current: 1, size: 10, keyword: '', status: '' })
const scoreClass = (s) => (s < 60 ? 'danger' : s < 80 ? 'warn' : 'ok')

async function load() {
  loading.value = true
  try {
    const params = Object.fromEntries(Object.entries(query).filter(([, v]) => v !== '' && v != null))
    const page = await contractorPage(params)
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
  contractorOverview().then((v) => { overview.value = v })
}
function filter(status) {
  query.status = query.status === status ? '' : status
  search()
}
onMounted(reload)
</script>

<style scoped>
.stats { display: flex; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.stat { flex: 1; min-width: 120px; padding: 12px 16px; border: 1px solid #ebeef5; border-radius: 8px; cursor: pointer; display: flex; align-items: baseline; gap: 8px; }
.stat.plain { cursor: default; }
.stat b { font-size: 24px; }
.stat span { color: #888; font-size: 13px; }
.stat.on { border-color: #409eff; background: #f5faff; }
.ok { color: #67c23a; }
.warn { color: #e6a23c; }
.danger { color: #f56c6c; }
.score { font-weight: 600; font-size: 15px; }
.warning { color: #e6a23c; font-size: 12px; line-height: 1.5; }
:deep(.el-table__row) { cursor: pointer; }
</style>
