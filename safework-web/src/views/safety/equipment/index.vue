<template>
  <div class="page-card">
    <div v-if="overview" class="stats">
      <div class="stat" :class="{ on: !query.inspectState && !query.status }" @click="filter('', '')"><b>{{ overview.inUse }}</b><span>在用设备</span></div>
      <div class="stat" :class="{ on: query.inspectState === 'OVERDUE' }" @click="filter('OVERDUE')"><b class="danger">{{ overview.overdue }}</b><span>检验逾期</span></div>
      <div class="stat" :class="{ on: query.inspectState === 'DUE_SOON' }" @click="filter('DUE_SOON')"><b class="warn">{{ overview.dueSoon }}</b><span>30 天内到期</span></div>
      <div class="stat" :class="{ on: query.inspectState === 'NEVER' }" @click="filter('NEVER')"><b class="danger">{{ overview.never }}</b><span>从未检验</span></div>
      <div class="stat" :class="{ on: query.status === 'STOPPED' }" @click="filter('', 'STOPPED')"><b class="warn">{{ overview.stopped }}</b><span>停用</span></div>
    </div>

    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="位号 / 名称 / 位置" clearable style="width: 180px" @keyup.enter="search" @clear="search" />
      <el-select v-model="query.category" placeholder="大类" clearable style="width: 130px" @change="search">
        <el-option v-for="(v, k) in EQUIP_CATEGORIES" :key="k" :label="v" :value="k" />
      </el-select>
      <el-select v-model="query.equipType" placeholder="设备类型" clearable filterable style="width: 170px" @change="search">
        <el-option v-for="t in types.filter((x) => !query.category || x.category === query.category)" :key="t.value" :label="t.label" :value="t.value" />
      </el-select>
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 110px" @change="search">
        <el-option v-for="(v, k) in EQUIP_STATUS" :key="k" :label="v.label" :value="k" />
      </el-select>
      <el-button icon="Search" @click="search">查询</el-button>
      <div class="spacer" />
      <el-button v-if="canEdit" type="primary" icon="Plus" @click="formRef.open()">登记设备</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe @row-click="(r) => detailRef.open(r.id)">
      <el-table-column prop="equipCode" label="位号" width="100" />
      <el-table-column prop="name" label="名称" min-width="170" show-overflow-tooltip />
      <el-table-column label="类型" width="150"><template #default="{ row }">{{ typeLabel(row.equipType) }}</template></el-table-column>
      <el-table-column prop="location" label="位置" min-width="150" show-overflow-tooltip />
      <el-table-column prop="responsibleName" label="责任人" width="130" show-overflow-tooltip />
      <el-table-column label="下次检验" width="110"><template #default="{ row }">{{ row.nextInspectDate || '-' }}</template></el-table-column>
      <el-table-column label="检验状态" width="150">
        <template #default="{ row }">
          <template v-if="row.status === 'IN_USE'">
            <el-tag size="small" :type="EQUIP_STATES[row.inspectState]?.type">{{ EQUIP_STATES[row.inspectState]?.label }}</el-tag>
            <span v-if="row.daysLeft != null && row.inspectState !== 'OK'" class="days" :class="row.inspectState">{{ daysText(row.daysLeft) }}</span>
          </template>
          <el-tag v-else size="small" :type="EQUIP_STATUS[row.status]?.type">{{ EQUIP_STATUS[row.status]?.label }}</el-tag>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination v-model:current-page="query.current" :page-size="query.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
    </div>

    <EquipmentForm ref="formRef" :types="types" @saved="(id) => { reload(); detailRef.open(id) }" />
    <EquipmentDetail ref="detailRef" :types="types" @changed="reload" @edit="(e) => formRef.open(e)" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useUserStore } from '@/stores/user'
import { equipmentOverview, equipmentPage, equipmentTypes } from '@/api'
import { EQUIP_CATEGORIES, EQUIP_STATES, EQUIP_STATUS } from '@/utils/dict'
import EquipmentDetail from './EquipmentDetail.vue'
import EquipmentForm from './EquipmentForm.vue'

const store = useUserStore()
const canEdit = computed(() => store.hasPerm('equipment:edit'))
const loading = ref(false)
const list = ref([])
const total = ref(0)
const overview = ref(null)
const types = ref([])
const formRef = ref()
const detailRef = ref()
const query = reactive({ current: 1, size: 10, keyword: '', category: '', equipType: '', status: '', inspectState: '' })
const typeLabel = (v) => types.value.find((t) => t.value === v)?.label || v
const daysText = (d) => (d < 0 ? `逾期 ${-d} 天` : d === 0 ? '今天到期' : `剩 ${d} 天`)

async function load() {
  loading.value = true
  try {
    const params = Object.fromEntries(Object.entries(query).filter(([, v]) => v !== '' && v != null))
    const page = await equipmentPage(params)
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
  equipmentOverview().then((v) => { overview.value = v })
}
function filter(state, status = '') {
  query.inspectState = state
  query.status = status
  search()
}
onMounted(async () => {
  reload()
  types.value = await equipmentTypes()
})
</script>

<style scoped>
.stats { display: flex; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.stat { flex: 1; min-width: 110px; padding: 12px 16px; border: 1px solid #ebeef5; border-radius: 8px; cursor: pointer; display: flex; align-items: baseline; gap: 8px; }
.stat b { font-size: 24px; }
.stat span { color: #888; font-size: 13px; }
.stat.on { border-color: #409eff; background: #f5faff; }
.warn { color: #e6a23c; }
.danger { color: #f56c6c; }
.days { margin-left: 6px; font-size: 12px; }
.days.OVERDUE { color: #f56c6c; }
.days.DUE_SOON { color: #e6a23c; }
:deep(.el-table__row) { cursor: pointer; }
</style>
