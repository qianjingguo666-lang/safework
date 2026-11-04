<template>
  <div>
    <div class="toolbar">
      <span class="muted">计划按周期自动生成检查任务（每天 7:00）；新建计划时如果开始日期已经到了，立即生成第一张</span>
      <div class="spacer" />
      <el-button type="primary" icon="Plus" @click="openForm()">新建计划</el-button>
    </div>
    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="name" label="计划名称" min-width="170" show-overflow-tooltip />
      <el-table-column prop="templateName" label="检查表" min-width="160" show-overflow-tooltip />
      <el-table-column label="周期" width="80"><template #default="{ row }">{{ CHECK_CYCLES[row.cycle] }}</template></el-table-column>
      <el-table-column prop="checkerName" label="检查人" width="140" show-overflow-tooltip />
      <el-table-column prop="location" label="检查区域" min-width="150" show-overflow-tooltip />
      <el-table-column label="执行期间" width="190"><template #default="{ row }">{{ row.startDate }} ~ {{ row.endDate || '长期' }}</template></el-table-column>
      <el-table-column label="下次生成" width="110">
        <template #default="{ row }">{{ row.status === 1 ? row.nextDate || '已结束' : '-' }}</template>
      </el-table-column>
      <el-table-column label="启用" width="70">
        <template #default="{ row }"><el-switch :model-value="row.status === 1" @change="(v) => toggle(row, v)" /></template>
      </el-table-column>
      <el-table-column label="操作" width="70"><template #default="{ row }"><el-button link type="primary" @click="openForm(row)">修改</el-button></template></el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="form.id ? '修改检查计划' : '新建检查计划'" width="min(560px, 94vw)">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="计划名称" prop="name"><el-input v-model="form.name" maxlength="60" placeholder="如：罐区每日巡回检查" /></el-form-item>
        <el-form-item label="检查表" prop="templateId">
          <el-select v-model="form.templateId" filterable style="width: 100%" @change="onTemplate">
            <el-option v-for="t in templates" :key="t.id" :label="`${t.name}（${CHECK_TYPES[t.checkType]}，${t.itemCount} 项）`" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="检查周期" prop="cycle">
          <el-radio-group v-model="form.cycle">
            <el-radio-button v-for="(v, k) in CHECK_CYCLES" :key="k" :value="k">{{ v }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="检查人" prop="checkerId">
          <el-select v-model="form.checkerId" filterable style="width: 100%">
            <el-option v-for="u in users" :key="u.id" :label="u.nickname" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="检查区域" prop="location"><el-input v-model="form.location" maxlength="100" placeholder="如：罐区（1#~4# 储罐）" /></el-form-item>
        <el-form-item label="开始日期" prop="startDate">
          <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%"
                          :placeholder="form.cycle === 'ONCE' ? '不填给 7 天时间' : '不填一直执行'" />
        </el-form-item>
        <div class="hint">{{ hint }}</div>
        <el-form-item label="备注"><el-input v-model="form.remark" maxlength="200" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { checkPlanStatus, checkPlans, checkTemplates, saveCheckPlan, userOptions } from '@/api'
import { CHECK_CYCLES, CHECK_TYPES } from '@/utils/dict'

const emit = defineEmits(['changed'])
const loading = ref(false)
const list = ref([])
const templates = ref([])
const users = ref([])
const visible = ref(false)
const saving = ref(false)
const formRef = ref()
const form = reactive({})
const rules = {
  name: [{ required: true, message: '请填写计划名称', trigger: 'blur' }],
  templateId: [{ required: true, message: '请选择检查表' }],
  cycle: [{ required: true, message: '请选择检查周期' }],
  checkerId: [{ required: true, message: '请选择检查人' }],
  location: [{ required: true, message: '请填写检查区域', trigger: 'blur' }],
  startDate: [{ required: true, message: '请选择开始日期' }]
}
const today = () => {
  const d = new Date(Date.now() - new Date().getTimezoneOffset() * 60000)
  return d.toISOString().slice(0, 10)
}
const hint = computed(() => ({
  DAILY: '每天生成一张任务，当天截止',
  WEEKLY: '从开始日期起每 7 天生成一张，7 天内完成',
  MONTHLY: '从开始日期起每月生成一张，一个月内完成',
  ONCE: '只生成一张，到结束日期截止'
}[form.cycle] || ''))

async function load() {
  loading.value = true
  try {
    list.value = await checkPlans()
  } finally {
    loading.value = false
  }
}
async function openForm(p) {
  Object.assign(form, {
    id: p?.id || null, name: p?.name || '', templateId: p?.templateId || null, cycle: p?.cycle || 'DAILY', checkerId: p?.checkerId || null,
    location: p?.location || '', startDate: p?.startDate || today(), endDate: p?.endDate || null, remark: p?.remark || ''
  })
  visible.value = true
  formRef.value?.clearValidate()
  templates.value = await checkTemplates(true)
  if (!users.value.length) users.value = await userOptions()
}
function onTemplate(id) {
  const t = templates.value.find((x) => x.id === id)
  if (t && !form.name) form.name = t.name.replace(/表$/, '')
}
async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    const { id, ...data } = form
    await saveCheckPlan(id, data)
    ElMessage.success(id ? '已保存' : '已创建，开始日期已到的会立即生成第一张检查任务')
    visible.value = false
    load()
    emit('changed')
  } finally {
    saving.value = false
  }
}
async function toggle(row, v) {
  await checkPlanStatus(row.id, v ? 1 : 0)
  ElMessage.success(v ? '已启用' : '已停用，不再生成新的检查任务')
  load()
  emit('changed')
}
onMounted(load)
</script>

<style scoped>
.muted { color: #999; font-size: 12px; }
.hint { margin: -10px 0 14px 90px; color: #999; font-size: 12px; }
</style>
