<template>
  <el-dialog :model-value="modelValue" :title="data ? '修改演练安排' : '安排演练'" width="min(680px, 96vw)" @update:model-value="emit('update:modelValue', $event)" @open="init">
    <el-form ref="formRef" :model="f" :rules="rules" label-width="86px">
      <el-form-item label="预案" prop="planId">
        <el-select v-model="f.planId" filterable style="width: 100%" @change="onPlan">
          <el-option v-for="p in plans.filter((x) => x.status === 1 || x.id === f.planId)" :key="p.id" :label="`${p.planCode} ${p.title}`" :value="p.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="演练名称" prop="title"><el-input v-model="f.title" maxlength="60" /></el-form-item>
      <el-row :gutter="12">
        <el-col :xs="24" :span="12">
          <el-form-item label="形式" prop="drillType">
            <el-radio-group v-model="f.drillType"><el-radio v-for="(v, k) in DRILL_TYPES" :key="k" :value="k">{{ v }}</el-radio></el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :xs="24" :span="12">
          <el-form-item label="计划时间" prop="planTime">
            <el-date-picker v-model="f.planTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" format="YYYY-MM-DD HH:mm" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="地点"><el-input v-model="f.location" maxlength="100" /></el-form-item>
      <el-form-item label="演练情景">
        <el-input v-model="f.scenario" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="模拟什么事故：如“清淤人员在调节池内晕倒，监护人发现后呼救”" />
      </el-form-item>
      <el-form-item label="参演人员">
        <div class="ps">
          <div v-for="(p, i) in f.participants" :key="i" class="p-row">
            <el-select v-model="p.userId" filterable placeholder="人员" style="flex: 1">
              <el-option v-for="u in users" :key="u.id" :label="u.deptName ? `${u.nickname}（${u.deptName}）` : u.nickname" :value="u.id"
                         :disabled="f.participants.some((x, j) => j !== i && x.userId === u.id)" />
            </el-select>
            <el-select v-model="p.role" style="width: 120px">
              <el-option v-for="(v, k) in DRILL_ROLES" :key="k" :label="v" :value="k" />
            </el-select>
            <el-button link type="danger" icon="Delete" @click="f.participants.splice(i, 1)" />
          </div>
          <div class="p-tools">
            <el-button size="small" icon="Plus" @click="f.participants.push({ userId: null, role: 'RESCUE' })">添加</el-button>
            <el-button size="small" @click="addTeam">加入预案的应急小组</el-button>
            <span class="muted">参演人员会收到通知，演练当天在手机上签到</span>
          </div>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">确定（发送通知）</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { saveEmergencyDrill, userOptions } from '@/api'
import { DRILL_ROLES, DRILL_TYPES } from '@/utils/dict'

const props = defineProps({
  modelValue: Boolean,
  plans: { type: Array, default: () => [] },
  /** 修改时传详情；新建时可以传 { planId } */
  data: { type: Object, default: null },
  planId: { type: [String, Number], default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])
const formRef = ref()
const users = ref([])
const saving = ref(false)
const f = reactive({ participants: [] })
const rules = {
  planId: [{ required: true, message: '请选择演练的预案' }],
  title: [{ required: true, message: '请填写演练名称', trigger: 'blur' }],
  drillType: [{ required: true, message: '请选择演练形式' }],
  planTime: [{ required: true, message: '请选择计划时间' }]
}

async function init() {
  if (!users.value.length) users.value = (await userOptions()).map((u) => ({ ...u, id: String(u.id) }))
  const d = props.data?.drill
  const p = props.plans.find((x) => x.id === (d?.planId || props.planId))
  Object.assign(f, {
    planId: d?.planId || props.planId || null,
    title: d?.title || (p ? `${p.title}演练` : ''),
    drillType: d?.drillType || 'LIVE',
    planTime: d?.planTime || '',
    location: d?.location || '',
    scenario: d?.scenario || '',
    participants: (props.data?.participants || []).map((x) => ({ userId: String(x.userId), role: x.role }))
  })
  formRef.value?.clearValidate()
}
function onPlan(id) {
  const p = props.plans.find((x) => x.id === id)
  if (p && !f.title) f.title = `${p.title}演练`
}
function addTeam() {
  const p = props.plans.find((x) => x.id === f.planId)
  if (!p?.teamUserIds) return ElMessage.info('这份预案没有设置应急小组')
  p.teamUserIds.split(',').filter(Boolean).forEach((id) => {
    if (!f.participants.some((x) => x.userId === id)) f.participants.push({ userId: id, role: 'RESCUE' })
  })
}
async function submit() {
  await formRef.value.validate()
  if (f.participants.some((x) => !x.userId)) return ElMessage.warning('有参演人员没选')
  saving.value = true
  try {
    await saveEmergencyDrill(props.data?.drill?.id, { ...f })
    ElMessage.success('已保存，参演人员和应急小组会收到通知')
    emit('update:modelValue', false)
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.ps { width: 100%; }
.p-row { display: flex; gap: 8px; align-items: center; margin-bottom: 6px; }
.p-tools { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.muted { color: #999; font-size: 12px; }
</style>
