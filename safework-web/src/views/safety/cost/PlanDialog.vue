<template>
  <el-dialog :model-value="modelValue" :title="`${year} 年安全生产费用计划`" width="720px" @update:model-value="emit('update:modelValue', $event)" @open="open">
    <el-form v-loading="loading" label-width="130px">
      <el-form-item label="上一年度营业收入">
        <el-input-number v-model="form.revenue" :min="0" :max="100000000" :precision="2" :controls="false" style="width: 180px" @change="calc" />
        <span class="unit">万元</span>
      </el-form-item>
      <el-form-item label="本年应提">
        <b>{{ yuan(accrual?.total) }} 元</b>
        <span class="muted">（{{ (accrual?.lines || []).map((l) => `${Number(l.base).toLocaleString()} 万 × ${l.rate}%`).join(' + ') || '填写营业收入后自动计算' }}）</span>
      </el-form-item>
      <el-form-item label="上年结转">
        <el-input-number v-model="form.carryOver" :min="0" :precision="2" :controls="false" style="width: 180px" />
        <span class="unit">元</span>
        <el-button v-if="suggested > 0 && Number(form.carryOver) !== Number(suggested)" link type="primary" @click="form.carryOver = Number(suggested)">
          填入上年余额 {{ yuan(suggested) }} 元
        </el-button>
      </el-form-item>
      <el-form-item label="当年可用"><b>{{ yuan(available) }} 元</b></el-form-item>
      <el-form-item label="分类预算">
        <el-table :data="budgets" size="small" border max-height="330">
          <el-table-column label="使用类别" min-width="200">
            <template #default="{ row }"><el-tooltip :content="row.description" placement="top-start"><span>{{ row.label }}</span></el-tooltip></template>
          </el-table-column>
          <el-table-column label="预算（元）" width="190">
            <template #default="{ row }"><el-input-number v-model="row.amount" :min="0" :precision="2" :controls="false" size="small" style="width: 160px" /></template>
          </el-table-column>
        </el-table>
        <div class="sum" :class="{ bad: budgetSum > available }">
          预算合计 {{ yuan(budgetSum) }} 元{{ budgetSum > available ? '，超过当年可用额度，年末可能要补提' : `，还有 ${yuan(available - budgetSum)} 元未分配` }}
        </div>
      </el-form-item>
      <el-form-item label="备注"><el-input v-model="form.remark" maxlength="200" show-word-limit /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { costAccrue, costPlanForm, saveCostPlan } from '@/api'
import { yuan } from './money'

const props = defineProps({ modelValue: Boolean, year: { type: Number, required: true } })
const emit = defineEmits(['update:modelValue', 'saved'])
const loading = ref(false)
const saving = ref(false)
const form = reactive({ revenue: 0, carryOver: 0, remark: '' })
const budgets = ref([])
const suggested = ref(0)
const accrual = ref(null)
const available = computed(() => Number(form.carryOver || 0) + Number(accrual.value?.total || 0))
const budgetSum = computed(() => budgets.value.reduce((s, b) => s + Number(b.amount || 0), 0))

async function open() {
  loading.value = true
  try {
    const f = await costPlanForm(props.year)
    suggested.value = Number(f.suggestedCarry)
    form.revenue = f.plan ? Number(f.plan.revenue) : 0
    form.carryOver = f.plan ? Number(f.plan.carryOver) : suggested.value
    form.remark = f.plan?.remark || ''
    budgets.value = f.budgets.map((b) => ({ ...b, amount: Number(b.amount) }))
    await calc()
  } finally {
    loading.value = false
  }
}
async function calc() {
  accrual.value = await costAccrue(form.revenue || 0)
}
async function save() {
  saving.value = true
  try {
    const res = await saveCostPlan({ year: props.year, ...form, budgets: budgets.value.map((b) => ({ category: b.code, amount: b.amount || 0 })) })
    if (res.warning) ElMessage.warning(res.warning)
    else ElMessage.success('已保存')
    emit('update:modelValue', false)
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.unit { margin: 0 8px; color: #666; }
.muted { color: #999; font-size: 12px; margin-left: 6px; }
.sum { margin-top: 6px; color: #52c41a; font-size: 13px; }
.sum.bad { color: #f5222d; }
</style>
