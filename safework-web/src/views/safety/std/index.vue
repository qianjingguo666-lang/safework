<template>
  <div class="page-card">
    <Assess v-if="openId" :id="openId" @back="close" @changed="load" />
    <template v-else>
      <div class="toolbar">
        <span class="muted">按《企业安全生产标准化基本规范》（GB/T 33000）8 个一级要素、27 个考评项目自评；系统从各模块取证给出建议扣分，每年至少自评一次</span>
        <div class="spacer" />
        <el-button v-if="canEdit && !hasDraft" type="primary" icon="Plus" @click="openCreate">新建自评</el-button>
      </div>
      <el-alert v-if="!loading && !rows.length" type="info" :closable="false" show-icon title="还没有开展过标准化自评。点右上角“新建自评”，系统会自动取证并给出建议扣分。" />
      <el-table v-else v-loading="loading" :data="rows" stripe>
        <el-table-column label="自评" min-width="220">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="openId = row.assessment.id">{{ row.assessment.title }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.assessment.status === 'DONE' ? 'success' : 'warning'" size="small">{{ row.assessment.status === 'DONE' ? '已完成' : '自评中' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="得分" width="90"><template #default="{ row }"><b>{{ row.assessment.score ?? '-' }}</b></template></el-table-column>
        <el-table-column label="等级" width="90">
          <template #default="{ row }"><el-tag v-if="row.assessment.grade" :type="GRADE_TYPES[row.assessment.grade]" effect="dark" size="small">{{ row.assessment.grade }}</el-tag></template>
        </el-table-column>
        <el-table-column label="扣分项 / 已转整改" width="140"><template #default="{ row }">{{ row.deductCount }} / {{ row.hazardCount }}</template></el-table-column>
        <el-table-column prop="assessment.leaderName" label="自评组长" width="140" show-overflow-tooltip />
        <el-table-column label="时间" width="200">
          <template #default="{ row }">{{ row.assessment.startDate }} ~ {{ row.assessment.finishDate || '进行中' }}</template>
        </el-table-column>
      </el-table>
    </template>

    <el-dialog v-model="cVisible" title="新建标准化自评" width="520px">
      <el-form label-width="90px">
        <el-form-item label="名称"><el-input v-model="form.title" maxlength="60" /></el-form-item>
        <el-form-item label="自评组成员"><el-input v-model="form.members" maxlength="200" placeholder="例如：张三（组长）、李四、赵六、设备部王工" /></el-form-item>
      </el-form>
      <div class="muted">新建后系统会从隐患、检查、设备、培训、责任书、安全投入等模块取证，自动项目先填上建议扣分，你再逐项确认或调整。</div>
      <template #footer>
        <el-button @click="cVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="create">开始自评</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { createStd, stdList } from '@/api'
import Assess from './Assess.vue'
import { GRADE_TYPES } from './std'

const store = useUserStore()
const canEdit = computed(() => store.hasPerm('std:edit'))
const rows = ref([])
const loading = ref(false)
const openId = ref(null)
const cVisible = ref(false)
const creating = ref(false)
const form = reactive({ title: '', members: '' })
const hasDraft = computed(() => rows.value.some((r) => r.assessment.status === 'DRAFT'))

async function load() {
  loading.value = true
  try {
    rows.value = await stdList()
  } finally {
    loading.value = false
  }
}
function openCreate() {
  form.title = `${new Date().getFullYear()} 年度安全生产标准化自评`
  form.members = ''
  cVisible.value = true
}
async function create() {
  creating.value = true
  try {
    const id = await createStd(form)
    ElMessage.success('已创建，系统已自动取证')
    cVisible.value = false
    await load()
    openId.value = id
  } finally {
    creating.value = false
  }
}
function close() {
  openId.value = null
  load()
}
onMounted(load)
</script>

<style scoped>
.muted { color: #999; font-size: 12px; }
</style>
