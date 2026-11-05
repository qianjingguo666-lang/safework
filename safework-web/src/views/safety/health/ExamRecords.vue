<template>
  <div>
    <div class="toolbar">
      <el-input v-if="canEdit" v-model="query.keyword" placeholder="姓名 / 体检机构 / 危害因素" clearable style="width: 200px" @keyup.enter="search" @clear="search" />
      <el-select v-model="query.conclusion" placeholder="结论" clearable style="width: 140px" @change="search">
        <el-option v-for="(v, k) in OH_CONCLUSIONS" :key="k" :label="v.label" :value="k" />
      </el-select>
      <el-checkbox v-if="canEdit" v-model="query.open" @change="search">只看待处理的异常</el-checkbox>
      <el-button icon="Search" @click="search">查询</el-button>
    </div>
    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="examDate" label="体检日期" width="110" />
      <el-table-column v-if="canEdit" prop="userName" label="人员" width="140" show-overflow-tooltip />
      <el-table-column label="类型" width="90"><template #default="{ row }">{{ OH_EXAM_TYPES[row.examType] }}</template></el-table-column>
      <el-table-column label="结论" width="130">
        <template #default="{ row }"><el-tag size="small" :type="OH_CONCLUSIONS[row.conclusion]?.type">{{ OH_CONCLUSIONS[row.conclusion]?.label }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="factors" label="接触的危害因素" min-width="150" show-overflow-tooltip />
      <el-table-column label="说明" min-width="200">
        <template #default="{ row }">
          <div v-if="row.detail" class="detail">{{ row.detail }}</div>
          <div v-if="row.recheckDate" class="muted">复查日期 {{ row.recheckDate }}</div>
          <div v-if="row.handleStatus === 'CLOSED'" class="handled">已处理：{{ row.handleMeasure }}（{{ row.handlerName }}）</div>
        </template>
      </el-table-column>
      <el-table-column prop="org" label="体检机构" width="150" show-overflow-tooltip />
      <el-table-column label="处理" width="100">
        <template #default="{ row }">
          <template v-if="row.handleStatus === 'OPEN'">
            <el-button v-if="canEdit" type="warning" size="small" @click="handle(row)">登记处理</el-button>
            <el-tag v-else size="small" type="warning">待处理</el-tag>
          </template>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination v-model:current-page="query.current" :page-size="query.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { handleHealthExam, healthExamPage } from '@/api'
import { OH_CONCLUSIONS, OH_EXAM_TYPES } from '@/utils/dict'

defineProps({ canEdit: Boolean })
const emit = defineEmits(['changed'])
const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, keyword: '', conclusion: '', open: false })

async function load() {
  loading.value = true
  try {
    const params = Object.fromEntries(Object.entries(query).filter(([, v]) => v !== '' && v != null && v !== false))
    const page = await healthExamPage(params)
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
function showOpen() {
  query.open = true
  search()
}
async function handle(row) {
  let value
  try {
    ({ value } = await ElMessageBox.prompt(`${row.userName} ${row.examDate} 体检：${OH_CONCLUSIONS[row.conclusion].label}。请填写处理措施`, '登记处理', {
      inputType: 'textarea', inputPlaceholder: '如：已复查，听阈恢复正常 / 已调离噪声岗位 / 已申请职业病诊断',
      inputValidator: (v) => (v && v.trim().length > 0) || '请填写处理措施'
    }))
  } catch {
    return
  }
  await handleHealthExam(row.id, value)
  ElMessage.success('已处理')
  load()
  emit('changed')
}
onMounted(load)
defineExpose({ load, showOpen })
</script>

<style scoped>
.muted { color: #999; font-size: 12px; }
.detail { line-height: 1.5; }
.handled { color: #67c23a; font-size: 12px; margin-top: 2px; }
</style>
