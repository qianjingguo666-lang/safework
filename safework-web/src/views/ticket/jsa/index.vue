<template>
  <div class="page-card">
    <div v-if="ov" class="stats">
      <div class="stat" @click="filter('PUBLISHED')"><b class="ok">{{ ov.published }}</b><span>已发布</span></div>
      <div class="stat" @click="filter('REVIEWING')"><b :class="{ warn: ov.reviewing }">{{ ov.reviewing }}</b><span>待审核</span></div>
      <div class="stat" @click="filter('PUBLISHED')"><b :class="{ danger: ov.reviewDue }">{{ ov.reviewDue }}</b><span>超过 1 年要复审</span></div>
      <div class="stat" @click="filter('DRAFT')"><b>{{ ov.drafts }}</b><span>草稿 / 被驳回</span></div>
    </div>
    <el-alert type="info" :closable="false" show-icon class="mb"
      title="作业安全分析（JSA）：把一项作业拆成步骤，逐步识别危害、用“可能性 × 严重度”评估风险、写控制措施。审核发布后，开作业票时可以引用，并把控制措施带入作业票的安全措施。" />
    <div class="toolbar">
      <el-select v-model="q.ticketType" placeholder="作业类型" clearable style="width: 140px" @change="search">
        <el-option v-for="(v, k) in TICKET_TYPES" :key="k" :label="v.label" :value="k" />
      </el-select>
      <el-select v-model="q.status" placeholder="状态" clearable style="width: 110px" @change="search">
        <el-option v-for="(v, k) in JSA_STATUS" :key="k" :label="v.label" :value="k" />
      </el-select>
      <el-input v-model="q.keyword" placeholder="名称 / 编号 / 作业内容" clearable style="width: 220px" @keyup.enter="search" @clear="search" />
      <el-button icon="Search" @click="search">查询</el-button>
      <div class="spacer" />
      <el-button v-if="canEdit" type="primary" icon="Plus" @click="openEditor(null)">编写 JSA</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" stripe class="clickable" @row-click="(r) => open(r.jsa.id)">
      <el-table-column label="编号" width="170"><template #default="{ row }">{{ row.jsa.jsaNo }}</template></el-table-column>
      <el-table-column label="名称" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">{{ row.jsa.title }} <el-tag v-if="row.jsa.aiDrafted" size="small" type="success" effect="plain">AI</el-tag></template>
      </el-table-column>
      <el-table-column label="作业类型" width="120"><template #default="{ row }">{{ row.jsa.ticketType ? TICKET_TYPES[row.jsa.ticketType]?.label : '通用' }}</template></el-table-column>
      <el-table-column label="步骤" width="70" align="center"><template #default="{ row }">{{ row.jsa.stepCount }}</template></el-table-column>
      <el-table-column label="最高风险" width="120">
        <template #default="{ row }"><el-tag size="small" :color="RISK_LEVELS[row.maxLevel].color" effect="dark" class="risk">{{ RISK_LEVELS[row.maxLevel].label }}</el-tag></template>
      </el-table-column>
      <el-table-column label="状态" width="150">
        <template #default="{ row }">
          <el-tag size="small" :type="JSA_STATUS[row.jsa.status].type">{{ JSA_STATUS[row.jsa.status].label }}</el-tag>
          <el-tag v-if="row.reviewDue" size="small" type="danger" class="ml">要复审</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="编写 / 审核" width="200"><template #default="{ row }">{{ row.jsa.authorName }}<span v-if="row.jsa.reviewerName" class="muted"> / {{ row.jsa.reviewerName }}</span></template></el-table-column>
      <el-table-column label="被引用" width="80" align="center"><template #default="{ row }">{{ row.ticketCount }}</template></el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination v-model:current-page="q.current" :page-size="q.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
    </div>

    <JsaEditor v-model="eVisible" :data="editing" @saved="afterSave" />
    <JsaDetail ref="detailRef" v-model="dVisible" :id="curId" @changed="reload" @edit="openEditor" @open="open" />
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { jsaOverview, jsaPage } from '@/api'
import { TICKET_TYPES } from '@/utils/dict'
import JsaDetail from './JsaDetail.vue'
import JsaEditor from './JsaEditor.vue'
import { JSA_STATUS, RISK_LEVELS } from './jsa'

const route = useRoute()
const store = useUserStore()
const canEdit = computed(() => store.hasPerm('jsa:edit'))
const ov = ref(null)
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const q = reactive({ current: 1, size: 20, ticketType: '', status: '', keyword: '' })
const eVisible = ref(false)
const editing = ref(null)
const dVisible = ref(false)
const curId = ref(null)
const detailRef = ref()

async function load() {
  loading.value = true
  try {
    const page = await jsaPage(Object.fromEntries(Object.entries(q).filter(([, v]) => v !== '' && v != null)))
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
function filter(s) {
  q.status = s
  search()
}
function reload() {
  load()
  jsaOverview().then((v) => { ov.value = v }).catch(() => {})
}
function open(id) {
  curId.value = id
  if (dVisible.value) nextTick(() => detailRef.value?.load())
  dVisible.value = true
}
function openEditor(detail) {
  editing.value = detail
  dVisible.value = false
  eVisible.value = true
}
function afterSave(id) {
  reload()
  open(id)
}
onMounted(() => {
  reload()
  if (route.query.detail) open(route.query.detail)
})
watch(() => route.query.detail, (id) => { if (id) open(id) })
</script>

<style scoped>
.muted { color: #999; font-size: 12px; }
.mb { margin-bottom: 10px; }
.ml { margin-left: 4px; }
.risk { border: none; }
.stats { display: flex; gap: 12px; margin-bottom: 10px; flex-wrap: wrap; }
.stat { flex: 1; min-width: 130px; padding: 12px 16px; border: 1px solid #ebeef5; border-radius: 8px; display: flex; align-items: baseline; gap: 8px; cursor: pointer; }
.stat b { font-size: 24px; }
.stat span { color: #888; font-size: 13px; }
.ok { color: #52c41a; }
.warn { color: #e6a23c; }
.danger { color: #f56c6c; }
.clickable :deep(tr) { cursor: pointer; }
</style>
