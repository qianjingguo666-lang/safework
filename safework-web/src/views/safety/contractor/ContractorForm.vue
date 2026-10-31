<template>
  <el-dialog v-model="visible" :title="form.id ? '修改承包商' : '登记承包商'" width="min(600px, 94vw)">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
      <el-form-item label="单位名称" prop="name"><el-input v-model="form.name" maxlength="60" /></el-form-item>
      <el-form-item label="统一社会信用代码" prop="creditCode"><el-input v-model="form.creditCode" maxlength="18" placeholder="18 位" @input="(v) => (form.creditCode = v.toUpperCase())" /></el-form-item>
      <el-row :gutter="12">
        <el-col :xs="24" :span="12"><el-form-item label="联系人" prop="contactName"><el-input v-model="form.contactName" maxlength="20" /></el-form-item></el-col>
        <el-col :xs="24" :span="12"><el-form-item label="联系电话" prop="contactPhone" label-width="90px"><el-input v-model="form.contactPhone" maxlength="20" /></el-form-item></el-col>
      </el-row>
      <el-form-item label="业务范围"><el-input v-model="form.businessScope" maxlength="200" placeholder="如：储罐清洗、防腐保温、设备检维修" /></el-form-item>
      <el-form-item label="资质证书有效期" prop="licenseExpire">
        <el-date-picker v-model="form.licenseExpire" type="date" value-format="YYYY-MM-DD" placeholder="安全生产许可证等资质的有效期" style="width: 100%" />
      </el-form-item>
      <el-form-item label="责任险到期日" prop="insuranceExpire">
        <el-date-picker v-model="form.insuranceExpire" type="date" value-format="YYYY-MM-DD" placeholder="安全生产责任险" style="width: 100%" />
      </el-form-item>
      <el-form-item label="备注"><el-input v-model="form.remark" maxlength="200" /></el-form-item>
    </el-form>
    <el-alert v-if="!form.id" type="info" :closable="false" show-icon title="新登记的承包商状态是“待审核”，审核通过后才能参与作业" />
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { saveContractor } from '@/api'

const emit = defineEmits(['saved'])
const visible = ref(false)
const saving = ref(false)
const formRef = ref()
const form = reactive({})
const rules = {
  name: [{ required: true, message: '请填写单位名称', trigger: 'blur' }],
  creditCode: [{ required: true, message: '请填写统一社会信用代码', trigger: 'blur' },
    { pattern: /^[0-9A-HJ-NPQRTUWXY]{18}$/, message: '统一社会信用代码是 18 位数字或大写字母', trigger: 'blur' }],
  contactName: [{ required: true, message: '请填写联系人', trigger: 'blur' }],
  contactPhone: [{ required: true, message: '请填写联系电话', trigger: 'blur' }],
  licenseExpire: [{ required: true, message: '请填写资质证书有效期' }],
  insuranceExpire: [{ required: true, message: '请填写责任险到期日' }]
}

function open(c) {
  Object.assign(form, {
    id: c?.id || null, name: c?.name || '', creditCode: c?.creditCode || '', contactName: c?.contactName || '', contactPhone: c?.contactPhone || '',
    businessScope: c?.businessScope || '', licenseExpire: c?.licenseExpire || '', insuranceExpire: c?.insuranceExpire || '', remark: c?.remark || ''
  })
  visible.value = true
  formRef.value?.clearValidate()
}
async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    const { id, ...data } = form
    const newId = await saveContractor(id, data)
    ElMessage.success('已保存')
    visible.value = false
    emit('saved', newId || id)
  } finally {
    saving.value = false
  }
}
defineExpose({ open })
</script>
