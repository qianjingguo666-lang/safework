<template>
  <div v-loading="loading">
    <div v-for="s in stores" :key="s.store.id" class="m-card">
      <div class="m-item-head">
        <span class="m-item-title">{{ s.store.name }}</span>
        <span v-if="s.design.major" class="lv" :style="{ background: CHEM_LEVELS[s.design.level].color }">{{ CHEM_LEVELS[s.design.level].label }}重大危险源</span>
      </div>
      <div class="m-muted">{{ s.store.location }}</div>
      <div v-for="k in s.stocks" :key="k.id" class="stock">
        <div class="row">
          <b>{{ k.chemName }}</b>
          <span :class="{ bad: k.overstock }">{{ ton(k.qty) }} / {{ ton(k.maxQty) }} 吨<template v-if="k.overstock"> 超储</template></span>
        </div>
        <template v-if="editing === k.id">
          <div class="types">
            <button v-for="(v, t) in CHEM_FLOW_TYPES" :key="t" :class="{ on: type === t }" @click="type = t">{{ v.label }}</button>
          </div>
          <el-input-number v-model="qty" :min="0" :precision="3" :controls="false" :placeholder="type === 'CHECK' ? '实际数量（吨）' : '数量（吨）'" class="full" />
          <el-input v-model="purpose" maxlength="100" :placeholder="type === 'IN' ? '来源（可不填）' : type === 'OUT' ? '领用用途' : '盘点说明'" class="purpose" />
          <div class="btns">
            <el-button @click="editing = null">取消</el-button>
            <el-button type="primary" :loading="saving" @click="submit(k)">确认{{ CHEM_FLOW_TYPES[type].label }}</el-button>
          </div>
        </template>
        <el-button v-else-if="s.canFlow" size="small" plain class="full op" @click="edit(k)">出入库 / 盘点</el-button>
      </div>
      <div v-if="!s.stocks.length" class="m-muted">没有存放危化品</div>
    </div>
    <div v-if="!loading && !stores.length" class="m-card m-muted">你不是危化品存储单元的负责人</div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { chemFlow, myChemStores } from '@/api'
import { CHEM_FLOW_TYPES, CHEM_LEVELS, ton } from '@/utils/dict'

const loading = ref(false)
const saving = ref(false)
const stores = ref([])
const editing = ref(null)
const type = ref('OUT')
const qty = ref(null)
const purpose = ref('')

async function load() {
  loading.value = true
  try {
    stores.value = await myChemStores()
  } finally {
    loading.value = false
  }
}
function edit(k) {
  editing.value = k.id
  type.value = 'OUT'
  qty.value = null
  purpose.value = ''
}
async function submit(k) {
  if (qty.value == null || (type.value !== 'CHECK' && !qty.value)) return ElMessage.warning('请填写数量')
  if (type.value !== 'IN' && !purpose.value.trim()) return ElMessage.warning(type.value === 'OUT' ? '请填写领用用途' : '请填写盘点说明')
  saving.value = true
  try {
    const r = await chemFlow(k.id, { flowType: type.value, qty: qty.value, purpose: purpose.value.trim() })
    if (r.overstock) ElMessage.warning(`库存 ${ton(r.balance)} 吨，超过设计最大储量`)
    else ElMessage.success(`已${CHEM_FLOW_TYPES[type.value].label}，库存 ${ton(r.balance)} 吨`)
    editing.value = null
    await load()
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>

<style scoped>
.lv { color: #fff; font-size: 12px; padding: 2px 8px; border-radius: 4px; }
.stock { padding: 10px 0; border-bottom: 1px solid #f2f3f5; }
.stock:last-child { border-bottom: 0; }
.row { display: flex; justify-content: space-between; font-size: 15px; }
.bad { color: #f5222d; font-weight: 600; }
.types { display: flex; gap: 8px; margin: 10px 0 8px; }
.types button { flex: 1; border: 1px solid #dcdfe6; background: #fff; border-radius: 6px; padding: 8px 0; font-size: 14px; }
.types button.on { border-color: #1677ff; color: #1677ff; background: #e6f4ff; }
.full { width: 100%; }
.op { margin-top: 8px; }
.purpose { margin-top: 8px; }
.btns { display: flex; gap: 8px; margin-top: 8px; }
.btns .el-button { flex: 1; }
</style>
