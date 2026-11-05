<template>
  <div>
    <div class="toolbar">
      <el-select v-model="query.category" placeholder="使用类别" clearable style="width: 170px" @change="search">
        <el-option v-for="c in categories" :key="c.code" :label="c.label" :value="c.code" />
      </el-select>
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 100px" @change="search">
        <el-option label="有效" :value="1" /><el-option label="已作废" :value="0" />
      </el-select>
      <el-input v-model="query.keyword" placeholder="事项 / 编号 / 收款单位 / 凭证号 / 隐患编号" clearable style="width: 280px" @keyup.enter="search" @clear="search" />
      <el-button icon="Search" @click="search">查询</el-button>
      <div class="spacer" />
      <span class="muted">筛选结果有效金额合计 <b>{{ yuan(sum) }}</b> 元</span>
      <el-button icon="Download" :loading="exporting" @click="exportExcel">导出 Excel</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" stripe :row-class-name="({ row }) => (row.status === 0 ? 'voided' : '')">
      <el-table-column prop="expenseDate" label="日期" width="110" />
      <el-table-column prop="expenseNo" label="编号" width="170" />
      <el-table-column label="类别" width="150" show-overflow-tooltip><template #default="{ row }">{{ catLabel(row.category) }}</template></el-table-column>
      <el-table-column label="支出事项" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link type="primary" :underline="false" @click="openDetail(row)">{{ row.title }}</el-link>
          <el-tag v-if="row.hazardNo" size="small" type="warning" class="ml">隐患整改</el-tag>
          <el-tag v-if="row.status === 0" size="small" type="info" class="ml">已作废</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="金额（元）" width="120" align="right"><template #default="{ row }">{{ yuan(row.amount) }}</template></el-table-column>
      <el-table-column prop="deptName" label="使用部门" width="100" show-overflow-tooltip />
      <el-table-column prop="voucherNo" label="凭证号" width="110" show-overflow-tooltip />
      <el-table-column prop="recorderName" label="登记人" width="130" show-overflow-tooltip />
      <el-table-column v-if="canEdit" label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 1">
            <el-button link type="primary" @click="emit('edit', row)">修改</el-button>
            <el-button link type="danger" @click="voidRow(row)">作废</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination v-model:current-page="query.current" :page-size="query.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
    </div>

    <el-drawer v-model="drawer" title="支出详情" size="480px">
      <template v-if="cur">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="编号">{{ cur.expenseNo }}</el-descriptions-item>
          <el-descriptions-item label="使用类别">{{ catLabel(cur.category) }}</el-descriptions-item>
          <el-descriptions-item label="支出事项">{{ cur.title }}</el-descriptions-item>
          <el-descriptions-item label="金额">{{ yuan(cur.amount) }} 元</el-descriptions-item>
          <el-descriptions-item label="支出日期">{{ cur.expenseDate }}</el-descriptions-item>
          <el-descriptions-item label="使用部门">{{ cur.deptName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="收款单位">{{ cur.payee || '-' }}</el-descriptions-item>
          <el-descriptions-item label="凭证号">{{ cur.voucherNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="关联隐患">{{ cur.hazardNo ? `${cur.hazardNo} ${cur.hazardTitle}` : '-' }}</el-descriptions-item>
          <el-descriptions-item label="登记">{{ cur.recorderName }}，{{ cur.createTime?.slice(0, 16) }}</el-descriptions-item>
          <el-descriptions-item v-if="cur.remark" label="备注">{{ cur.remark }}</el-descriptions-item>
          <el-descriptions-item v-if="cur.status === 0" label="作废">
            <span class="danger">{{ cur.voidReason }}</span>（{{ cur.voiderName }}，{{ cur.voidTime?.slice(0, 16) }}）
          </el-descriptions-item>
        </el-descriptions>
        <div class="files">
          <AttachmentPanel :key="cur.id + '-' + cur.status" biz-type="COST" :biz-id="cur.id" tag="VOUCHER" title="凭证附件"
                           empty-text="可以上传发票、合同、验收单的照片或 PDF" />
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { costExpenses, costOptions, getCostExpense, voidCostExpense } from '@/api'
import AttachmentPanel from '@/components/AttachmentPanel.vue'
import { downloadFile } from '@/utils/download'
import { yuan } from './money'

const props = defineProps({ year: { type: Number, required: true }, canEdit: Boolean })
const emit = defineEmits(['edit', 'changed'])
const loading = ref(false)
const exporting = ref(false)
const rows = ref([])
const total = ref(0)
const sum = ref(0)
const categories = ref([])
const drawer = ref(false)
const cur = ref(null)
const query = reactive({ current: 1, size: 20, category: '', status: null, keyword: '' })
const catLabel = (c) => categories.value.find((x) => x.code === c)?.label || c

function params() {
  return Object.fromEntries(Object.entries({ ...query, year: props.year }).filter(([, v]) => v !== '' && v != null))
}
async function load() {
  loading.value = true
  try {
    const page = await costExpenses(params())
    rows.value = page.records
    total.value = page.total
    sum.value = page.sum
  } finally {
    loading.value = false
  }
}
function search() {
  query.current = 1
  load()
}
function filterCategory(code) {
  query.category = code
  query.status = 1
  search()
}
async function openDetail(row) {
  cur.value = await getCostExpense(row.id)
  drawer.value = true
}
async function voidRow(row) {
  const { value } = await ElMessageBox.prompt(`作废“${row.title}”（${yuan(row.amount)} 元）？作废后不计入使用金额，记录保留。`, '作废支出', {
    inputPlaceholder: '作废原因，例如：登记错误、不属于安全费用使用范围',
    inputValidator: (v) => (v && v.trim() ? true : '请填写作废原因'),
    confirmButtonText: '作废',
    type: 'warning'
  })
  await voidCostExpense(row.id, value)
  ElMessage.success('已作废')
  emit('changed')
}
async function exportExcel() {
  exporting.value = true
  try {
    await downloadFile('/safety/cost/expense/export', params(), '安全费用支出台账.xlsx')
  } finally {
    exporting.value = false
  }
}
watch(() => props.year, search)
onMounted(async () => {
  categories.value = (await costOptions()).categories
  load()
})
defineExpose({ reload: load, filterCategory })
</script>

<style scoped>
.muted { color: #888; font-size: 13px; }
.ml { margin-left: 6px; }
.danger { color: #f5222d; }
.files { margin-top: 16px; }
:deep(.el-table .voided) { color: #bbb; text-decoration: line-through; }
</style>
