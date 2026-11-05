<template>
  <el-dialog :model-value="modelValue" :title="expense ? '修改支出' : '登记支出'" width="600px" @update:model-value="emit('update:modelValue', $event)" @open="open">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
      <el-form-item label="使用类别" prop="category">
        <el-select v-model="form.category" placeholder="按管理办法第二十二条选择" style="width: 100%">
          <el-option v-for="c in categories" :key="c.code" :label="c.label" :value="c.code">
            <span>{{ c.label }}</span><span class="opt-desc">{{ c.description }}</span>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="支出事项" prop="title"><el-input v-model="form.title" maxlength="100" placeholder="例如：罐区可燃气体检测报警器更换（12 台）" /></el-form-item>
      <el-form-item label="金额" prop="amount">
        <el-input-number v-model="form.amount" :min="0.01" :precision="2" :controls="false" style="width: 180px" /><span class="unit">元</span>
      </el-form-item>
      <el-form-item label="支出日期" prop="expenseDate">
        <el-date-picker v-model="form.expenseDate" type="date" value-format="YYYY-MM-DD" :disabled-date="(d) => d > new Date()" style="width: 180px" />
      </el-form-item>
      <el-form-item label="使用部门"><el-input v-model="form.deptName" maxlength="50" /></el-form-item>
      <el-form-item label="收款单位"><el-input v-model="form.payee" maxlength="100" /></el-form-item>
      <el-form-item label="凭证号"><el-input v-model="form.voucherNo" maxlength="50" placeholder="财务记账凭证号或发票号" /></el-form-item>
      <el-form-item label="关联隐患">
        <el-select v-model="form.hazardId" filterable remote clearable :remote-method="searchHazards" :loading="hLoading"
                   placeholder="隐患整改花的钱可以关联隐患（输入编号或标题搜索）" style="width: 100%">
          <el-option v-for="h in hazards" :key="h.id" :label="`${h.hazardNo} ${h.title}`" :value="h.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { costOptions, pageHazards, saveCostExpense } from '@/api'

const props = defineProps({ modelValue: Boolean, expense: { type: Object, default: null } })
const emit = defineEmits(['update:modelValue', 'saved'])
const formRef = ref(null)
const saving = ref(false)
const categories = ref([])
const hazards = ref([])
const hLoading = ref(false)
const today = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
const empty = () => ({ category: '', title: '', amount: null, expenseDate: today(), deptName: '', payee: '', voucherNo: '', hazardId: null, remark: '' })
const form = reactive(empty())
const rules = {
  category: [{ required: true, message: '请选择使用类别' }],
  title: [{ required: true, message: '请填写支出事项' }],
  amount: [{ required: true, message: '请填写金额' }],
  expenseDate: [{ required: true, message: '请选择支出日期' }]
}

async function open() {
  if (!categories.value.length) categories.value = (await costOptions()).categories
  const e = props.expense
  Object.assign(form, empty(), e ? {
    category: e.category, title: e.title, amount: Number(e.amount), expenseDate: e.expenseDate, deptName: e.deptName || '',
    payee: e.payee || '', voucherNo: e.voucherNo || '', hazardId: e.hazardId || null, remark: e.remark || ''
  } : {})
  hazards.value = e?.hazardId ? [{ id: e.hazardId, hazardNo: e.hazardNo, title: e.hazardTitle }] : []
  formRef.value?.clearValidate()
}
async function searchHazards(kw) {
  hLoading.value = true
  try {
    hazards.value = (await pageHazards({ current: 1, size: 20, keyword: kw })).records
  } finally {
    hLoading.value = false
  }
}
async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    const res = await saveCostExpense(props.expense?.id, form)
    if (res.warning) ElMessage.warning({ message: res.warning, duration: 5000 })
    else ElMessage.success('已保存')
    emit('update:modelValue', false)
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.unit { margin-left: 8px; color: #666; }
.opt-desc { color: #aaa; font-size: 12px; margin-left: 10px; max-width: 300px; overflow: hidden; text-overflow: ellipsis; display: inline-block; vertical-align: bottom; }
</style>
