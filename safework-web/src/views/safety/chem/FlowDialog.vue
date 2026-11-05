<template>
  <el-dialog v-model="visible" :title="title" width="480px" append-to-body>
    <div v-if="row" class="info">
      <b>{{ row.chemName }}</b>　{{ st?.name }}　当前库存 <b>{{ ton(row.qty) }}</b> 吨 / 最大 {{ ton(row.maxQty) }} 吨
    </div>
    <el-form label-width="90px">
      <el-form-item :label="type === 'CHECK' ? '实际数量' : '数量'" required>
        <el-input-number v-model="qty" :min="0" :max="1000000" :precision="3" :controls="false" style="width: 160px" /> <span class="unit">吨</span>
        <div v-if="preview != null" class="muted" :class="{ danger: over }">{{ type === 'CHECK' ? '盘点后' : '办理后' }}库存 {{ preview }} 吨{{ over ? '，超过设计最大储量！' : '' }}</div>
      </el-form-item>
      <el-form-item :label="type === 'IN' ? '来源' : type === 'OUT' ? '领用用途' : '盘点说明'" :required="type !== 'IN'">
        <el-input v-model="purpose" maxlength="100" :placeholder="type === 'IN' ? '例如：槽车卸车' : type === 'OUT' ? '例如：一车间生产领用' : '例如：月度盘点，液位计读数'" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">确认{{ CHEM_FLOW_TYPES[type]?.label }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { chemFlow } from '@/api'
import { CHEM_FLOW_TYPES, ton } from '@/utils/dict'

const emit = defineEmits(['done'])
const visible = ref(false)
const saving = ref(false)
const st = ref(null)
const row = ref(null)
const type = ref('IN')
const qty = ref(null)
const purpose = ref('')
const title = computed(() => `${CHEM_FLOW_TYPES[type.value]?.label}：${row.value?.chemName || ''}`)
const preview = computed(() => {
  if (qty.value == null || !row.value) return null
  const cur = Number(row.value.qty)
  const v = type.value === 'IN' ? cur + qty.value : type.value === 'OUT' ? cur - qty.value : qty.value
  return Math.round(v * 1000) / 1000
})
const over = computed(() => preview.value != null && preview.value > Number(row.value.maxQty))

function open(store, stock, t) {
  st.value = store
  row.value = stock
  type.value = t
  qty.value = null
  purpose.value = ''
  visible.value = true
}
async function submit() {
  if (qty.value == null || (type.value !== 'CHECK' && !qty.value)) return ElMessage.warning('请填写数量')
  if (type.value !== 'IN' && !purpose.value.trim()) return ElMessage.warning(type.value === 'OUT' ? '请填写领用用途' : '请填写盘点说明')
  saving.value = true
  try {
    const r = await chemFlow(row.value.id, { flowType: type.value, qty: qty.value, purpose: purpose.value.trim() })
    if (r.overstock) ElMessage.warning(`已${CHEM_FLOW_TYPES[type.value].label}，库存 ${ton(r.balance)} 吨，超过设计最大储量`)
    else ElMessage.success(`已${CHEM_FLOW_TYPES[type.value].label}，库存 ${ton(r.balance)} 吨`)
    visible.value = false
    emit('done')
  } finally {
    saving.value = false
  }
}
defineExpose({ open })
</script>

<style scoped>
.info { margin-bottom: 14px; background: #f6f8fa; padding: 8px 12px; border-radius: 6px; font-size: 13px; }
.unit { margin-left: 6px; color: #666; }
.muted { color: #999; font-size: 12px; width: 100%; }
.danger { color: #f56c6c; }
</style>
