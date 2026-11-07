<template>
  <el-dialog :model-value="modelValue" title="外来人员 / 车辆入厂预约" width="680px" @update:model-value="emit('update:modelValue', $event)" @open="open">
    <el-form ref="formRef" :model="f" :rules="rules" label-width="110px">
      <el-form-item label="类型" prop="visitType">
        <el-radio-group v-model="f.visitType">
          <el-radio-button value="VISITOR">访客</el-radio-button>
          <el-radio-button value="VEHICLE">普通车辆</el-radio-button>
          <el-radio-button value="HAZMAT">危化品运输车辆</el-radio-button>
        </el-radio-group>
        <div v-if="f.visitType === 'HAZMAT'" class="muted">危化品车辆要安全员审批；入厂当天门岗还会再核对驾驶员、押运员和车辆的证件</div>
      </el-form-item>
      <el-row>
        <el-col :span="12"><el-form-item :label="f.visitType === 'VISITOR' ? '来访人' : '驾驶员'" prop="visitorName"><el-input v-model="f.visitorName" maxlength="30" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="手机号" prop="phone"><el-input v-model="f.phone" maxlength="11" /></el-form-item></el-col>
      </el-row>
      <el-form-item label="身份证号" prop="idCard">
        <el-input v-model="f.idCard" maxlength="18" placeholder="只用来校验，系统只保存打码后的号码（前 3 位 + 后 4 位）" />
      </el-form-item>
      <el-row>
        <el-col :span="12"><el-form-item label="单位" prop="company"><el-input v-model="f.company" maxlength="60" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="来访日期" prop="planDate">
          <el-date-picker v-model="f.planDate" type="date" value-format="YYYY-MM-DD" :disabled-date="disabledDate" style="width: 100%" />
        </el-form-item></el-col>
      </el-row>
      <el-form-item label="来访事由" prop="purpose"><el-input v-model="f.purpose" maxlength="100" /></el-form-item>
      <el-row>
        <el-col :span="12"><el-form-item label="人数"><el-input-number v-model="f.peopleCount" :min="1" :max="f.visitType === 'VISITOR' ? 50 : 3" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="车牌号" :required="f.visitType !== 'VISITOR'"><el-input v-model="f.plateNo" maxlength="10" placeholder="例如 皖A12345" /></el-form-item></el-col>
      </el-row>
      <el-form-item label="接待人">
        <el-select v-model="f.hostId" filterable clearable placeholder="不选就是我自己" style="width: 100%">
          <el-option v-for="u in users" :key="u.id" :label="`${u.nickname}${u.deptName ? '（' + u.deptName + '）' : ''}`" :value="u.id" />
        </el-select>
      </el-form-item>
      <template v-if="f.visitType === 'HAZMAT'">
        <el-divider content-position="left">危化品运输</el-divider>
        <el-row>
          <el-col :span="12"><el-form-item label="驾驶员资格证"><el-input v-model="f.driverCertNo" maxlength="30" placeholder="道路危险货物运输从业资格证号" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="有效期至"><el-date-picker v-model="f.driverCertExpire" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-row>
          <el-col :span="8"><el-form-item label="押运员" required><el-input v-model="f.escortName" maxlength="30" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="资格证号" label-width="80px"><el-input v-model="f.escortCertNo" maxlength="30" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="有效期至" label-width="80px"><el-date-picker v-model="f.escortCertExpire" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-row>
          <el-col :span="12"><el-form-item label="道路运输证"><el-input v-model="f.transportPermitNo" maxlength="40" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="有效期至"><el-date-picker v-model="f.transportPermitExpire" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-row>
          <el-col :span="8"><el-form-item label="货物" required><el-input v-model="f.cargoName" maxlength="40" placeholder="例如 甲醇" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="UN 编号" label-width="80px"><el-input v-model="f.cargoUnNo" maxlength="10" placeholder="UN1230" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="数量（吨）" label-width="90px"><el-input-number v-model="f.cargoQty" :min="0" :max="100" :precision="1" :controls="false" style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="卸货地点"><el-input v-model="f.destination" maxlength="40" placeholder="例如 罐区装卸站" /></el-form-item>
      </template>
      <el-form-item label="备注"><el-input v-model="f.remark" maxlength="200" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">提交预约</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createVisit, userOptions } from '@/api'

defineProps({ modelValue: Boolean })
const emit = defineEmits(['update:modelValue', 'saved'])
const formRef = ref(null)
const saving = ref(false)
const users = ref([])
const today = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
const empty = () => ({ visitType: 'VISITOR', visitorName: '', phone: '', idCard: '', company: '', purpose: '', peopleCount: 1, plateNo: '', hostId: null, planDate: today(),
  driverCertNo: '', driverCertExpire: null, escortName: '', escortCertNo: '', escortCertExpire: null, transportPermitNo: '', transportPermitExpire: null,
  cargoName: '', cargoUnNo: '', cargoQty: null, destination: '', remark: '' })
const f = reactive(empty())
const rules = {
  visitorName: [{ required: true, message: '请填写姓名' }],
  phone: [{ required: true, message: '请填写手机号' }, { pattern: /^1[3-9]\d{9}$/, message: '手机号不正确' }],
  idCard: [{ required: true, message: '请填写身份证号' }, { pattern: /^\d{17}[\dXx]$/, message: '身份证号是 18 位' }],
  company: [{ required: true, message: '请填写单位' }],
  purpose: [{ required: true, message: '请填写来访事由' }],
  planDate: [{ required: true, message: '请选择来访日期' }]
}
const disabledDate = (d) => {
  const t = new Date()
  t.setHours(0, 0, 0, 0)
  return d < t || d > new Date(t.getTime() + 30 * 864e5)
}
async function open() {
  Object.assign(f, empty())
  formRef.value?.clearValidate()
  if (!users.value.length) users.value = await userOptions()
}
async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    await createVisit(f)
    ElMessage.success(f.visitType === 'HAZMAT' ? '已提交，等安全员审批' : '预约成功，来访当天到门岗登记')
    emit('update:modelValue', false)
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.muted { color: #999; font-size: 12px; line-height: 1.6; }
</style>
