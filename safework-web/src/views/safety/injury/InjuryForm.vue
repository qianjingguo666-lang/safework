<template>
  <el-dialog :model-value="modelValue" :title="data ? '修改工伤登记' : '登记工伤'" width="620px" @update:model-value="emit('update:modelValue', $event)" @open="init">
    <el-form ref="formRef" :model="f" :rules="rules" label-width="100px">
      <el-form-item label="人员类别" prop="employerType">
        <el-radio-group v-model="f.employerType">
          <el-radio value="OWN">本企业员工</el-radio>
          <el-radio value="CONTRACTOR">承包商人员</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="f.employerType === 'OWN'" label="受伤员工" prop="injuredUserId">
        <el-select v-model="f.injuredUserId" filterable placeholder="选择员工" style="width: 100%">
          <el-option v-for="u in users" :key="u.id" :label="u.deptName ? `${u.nickname}（${u.deptName}）` : u.nickname" :value="u.id" />
        </el-select>
      </el-form-item>
      <template v-else>
        <el-form-item label="姓名" prop="injuredName"><el-input v-model="f.injuredName" maxlength="30" /></el-form-item>
        <el-form-item label="所在单位" prop="employerName">
          <el-input v-model="f.employerName" maxlength="60" placeholder="承包商单位（由该单位申请工伤认定）" />
        </el-form-item>
      </template>
      <el-form-item label="受伤时间" prop="injuryTime">
        <el-date-picker v-model="f.injuryTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" format="YYYY-MM-DD HH:mm" :disabled-date="(d) => d > new Date()" style="width: 220px" />
      </el-form-item>
      <el-form-item label="地点" prop="location"><el-input v-model="f.location" maxlength="60" /></el-form-item>
      <el-form-item label="受伤部位">
        <div class="row">
          <el-input v-model="f.injuryPart" maxlength="30" placeholder="如：左手" />
          <el-select v-model="f.injuryNature" filterable allow-create placeholder="伤害性质" style="width: 180px">
            <el-option v-for="x in NATURES" :key="x" :label="x" :value="x" />
          </el-select>
        </div>
      </el-form-item>
      <el-form-item label="经过" prop="description">
        <el-input v-model="f.description" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="怎么受的伤、怎么救治的" />
      </el-form-item>
      <el-form-item label="关联事故">
        <el-select v-model="f.eventId" clearable filterable placeholder="可选：关联事故快报" style="width: 100%">
          <el-option v-for="e in events" :key="e.id" :label="`${e.eventNo} ${e.title}`" :value="e.id" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { emergencyPage, saveInjury, userOptions } from '@/api'

const props = defineProps({ modelValue: Boolean, data: { type: Object, default: null } })
const emit = defineEmits(['update:modelValue', 'saved'])
const NATURES = ['割伤', '挫伤', '骨折', '烧伤', '烫伤', '化学灼伤', '急性中毒', '扭伤', '眼部异物', '其他']
const formRef = ref()
const users = ref([])
const events = ref([])
const saving = ref(false)
const f = reactive({})
const req = (message) => ({ required: true, message, trigger: 'blur' })
const rules = {
  employerType: [req('请选择人员类别')],
  injuredUserId: [{ required: true, message: '请选择员工', trigger: 'change' }],
  injuredName: [req('请填写姓名')],
  employerName: [req('请填写所在单位')],
  injuryTime: [{ required: true, message: '请选择受伤时间', trigger: 'change' }],
  location: [req('请填写地点')],
  description: [req('请填写经过')]
}

function init() {
  const d = props.data
  Object.assign(f, {
    employerType: d?.employerType || 'OWN', injuredUserId: d?.injuredUserId || null,
    injuredName: d?.employerType === 'CONTRACTOR' ? d.injuredName : '', employerName: d?.employerType === 'CONTRACTOR' ? d.employerName : '',
    injuryTime: d?.injuryTime || null, location: d?.location || '', injuryPart: d?.injuryPart || '', injuryNature: d?.injuryNature || '',
    description: d?.description || '', eventId: d?.eventId || null
  })
  formRef.value?.clearValidate()
  if (!users.value.length) userOptions().then((v) => { users.value = v }).catch(() => {})
  emergencyPage({ current: 1, size: 30 }).then((p) => { events.value = p.records || [] }).catch(() => {})
}
async function submit() {
  await formRef.value.validate()
  saving.value = true
  try {
    await saveInjury(props.data?.id, { ...f })
    ElMessage.success('已保存')
    emit('update:modelValue', false)
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.row { display: flex; gap: 8px; width: 100%; }
</style>
