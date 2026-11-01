<template>
  <el-dialog v-model="visible" :title="form.id ? '修改设备' : '登记设备'" width="min(640px, 94vw)">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
      <el-row :gutter="12">
        <el-col :xs="24" :span="10"><el-form-item label="设备位号" prop="equipCode"><el-input v-model="form.equipCode" maxlength="30" placeholder="如 SV-201" /></el-form-item></el-col>
        <el-col :xs="24" :span="14"><el-form-item label="设备名称" prop="name" label-width="90px"><el-input v-model="form.name" maxlength="60" /></el-form-item></el-col>
      </el-row>
      <el-form-item label="设备类型" prop="equipType">
        <el-select v-model="form.equipType" filterable style="width: 100%" @change="onType">
          <el-option-group v-for="(label, cat) in EQUIP_CATEGORIES" :key="cat" :label="label">
            <el-option v-for="t in types.filter((x) => x.category === cat)" :key="t.value" :label="t.label" :value="t.value" />
          </el-option-group>
        </el-select>
      </el-form-item>
      <el-form-item label="安装位置" prop="location"><el-input v-model="form.location" maxlength="100" /></el-form-item>
      <el-form-item v-if="type?.special" label="使用登记证" prop="registerNo">
        <el-input v-model="form.registerNo" maxlength="50" placeholder="特种设备必须填写，如 容3PA皖A01234(21)" />
      </el-form-item>
      <el-row :gutter="12">
        <el-col :xs="24" :span="12">
          <el-form-item label="检验周期">
            <el-input-number v-model="form.cycleMonths" :min="1" :max="type?.maxMonths || 60" style="width: 120px" />
            <span class="unit">个月</span>
          </el-form-item>
        </el-col>
        <el-col :xs="24" :span="12">
          <el-form-item v-if="!form.id" label="上次检验" label-width="90px">
            <el-date-picker v-model="form.lastInspectDate" type="date" value-format="YYYY-MM-DD" :disabled-date="(d) => d > new Date()" placeholder="新设备不填" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>
      <div v-if="type" class="hint">{{ type.label }}默认每 {{ type.defaultMonths }} 个月检验一次，最长不超过 {{ type.maxMonths }} 个月；周期可以比默认短，不能更长</div>
      <el-row :gutter="12">
        <el-col :xs="24" :span="12">
          <el-form-item label="责任人">
            <el-select v-model="form.responsibleId" clearable filterable style="width: 100%">
              <el-option v-for="u in users" :key="u.id" :label="u.nickname" :value="u.id" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :xs="24" :span="12">
          <el-form-item label="关联风险点" label-width="90px">
            <el-select v-model="form.riskPointId" clearable filterable placeholder="可选" style="width: 100%">
              <el-option v-for="p in risks" :key="p.id" :label="`${p.pointCode} ${p.pointName}`" :value="p.id" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :xs="24" :span="12"><el-form-item label="制造单位"><el-input v-model="form.manufacturer" maxlength="60" /></el-form-item></el-col>
        <el-col :xs="24" :span="12"><el-form-item label="型号规格" label-width="90px"><el-input v-model="form.model" maxlength="60" /></el-form-item></el-col>
      </el-row>
      <el-form-item label="安装日期">
        <el-date-picker v-model="form.installDate" type="date" value-format="YYYY-MM-DD" :disabled-date="(d) => d > new Date()" style="width: 100%" />
      </el-form-item>
      <el-form-item label="备注"><el-input v-model="form.remark" maxlength="200" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { pageRisks, saveEquipment, userOptions } from '@/api'
import { EQUIP_CATEGORIES } from '@/utils/dict'

const props = defineProps({ types: { type: Array, default: () => [] } })
const emit = defineEmits(['saved'])
const visible = ref(false)
const saving = ref(false)
const formRef = ref()
const users = ref([])
const risks = ref([])
const form = reactive({})
const type = computed(() => props.types.find((t) => t.value === form.equipType))
const rules = {
  equipCode: [{ required: true, message: '请填写设备位号', trigger: 'blur' }],
  name: [{ required: true, message: '请填写设备名称', trigger: 'blur' }],
  equipType: [{ required: true, message: '请选择设备类型' }],
  location: [{ required: true, message: '请填写安装位置', trigger: 'blur' }],
  registerNo: [{ required: true, message: '特种设备必须填写使用登记证编号', trigger: 'blur' }]
}

function onType() {
  if (type.value) form.cycleMonths = type.value.defaultMonths
}
async function open(e) {
  Object.assign(form, {
    id: e?.id || null, equipCode: e?.equipCode || '', name: e?.name || '', equipType: e?.equipType || '', location: e?.location || '',
    registerNo: e?.registerNo || '', cycleMonths: e?.cycleMonths || null, lastInspectDate: null, responsibleId: e?.responsibleId || null,
    riskPointId: e?.riskPointId || null, manufacturer: e?.manufacturer || '', model: e?.model || '', installDate: e?.installDate || null, remark: e?.remark || ''
  })
  visible.value = true
  formRef.value?.clearValidate()
  if (!users.value.length) users.value = await userOptions()
  // 没有风险点权限时不影响登记设备
  if (!risks.value.length) risks.value = (await pageRisks({ size: 200, status: 1 }).catch(() => ({ records: [] }))).records
}
async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    const { id, ...data } = form
    if (!type.value?.special) data.registerNo = null
    const newId = await saveEquipment(id, data)
    ElMessage.success('已保存')
    visible.value = false
    emit('saved', newId || id)
  } finally {
    saving.value = false
  }
}
defineExpose({ open })
</script>

<style scoped>
.unit { margin-left: 8px; color: #999; }
.hint { margin: -10px 0 14px 110px; color: #999; font-size: 12px; line-height: 1.6; }
</style>
