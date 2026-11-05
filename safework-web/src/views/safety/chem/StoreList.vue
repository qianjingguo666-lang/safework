<template>
  <div v-loading="loading">
    <div class="toolbar">
      <span class="muted">重大危险源按单元辨识（GB 18218-2018）：S = Σ q / Q ≥ 1 构成；R = α · Σ β · q / Q 分级。q 取设计最大储量</span>
      <div class="spacer" />
      <el-button v-if="canEdit" type="primary" icon="Plus" @click="editStore()">新增存储单元</el-button>
    </div>
    <el-empty v-if="!loading && !rows.length" description="还没有存储单元" />
    <div v-for="s in rows" :key="s.store.id" class="store" :class="{ off: s.store.status !== 1 }">
      <div class="head">
        <div class="name">
          <b>{{ s.store.name }}</b>
          <el-tag size="small" type="info">{{ CHEM_STORE_TYPES[s.store.storeType] }}</el-tag>
          <el-tag v-if="s.store.status !== 1" size="small" type="info">已停用</el-tag>
          <span class="muted">{{ s.store.location }} · 负责人 {{ s.store.managerName }} · 500 m 内 {{ s.store.people }} 人（α = {{ s.design.alpha }}）</span>
        </div>
        <div class="ops">
          <template v-if="canEdit">
            <el-button link type="primary" @click="editStock(s)">添加危化品</el-button>
            <el-button link type="primary" @click="editStore(s.store)">编辑</el-button>
            <el-button link :type="s.store.status === 1 ? 'warning' : 'success'" @click="toggle(s.store)">{{ s.store.status === 1 ? '停用' : '启用' }}</el-button>
          </template>
        </div>
      </div>
      <div class="body">
        <div class="result" :class="{ major: s.design.major }">
          <template v-if="s.design.major">
            <div class="lv" :style="{ background: CHEM_LEVELS[s.design.level].color }">{{ CHEM_LEVELS[s.design.level].label }}</div>
            <div class="rt">重大危险源</div>
          </template>
          <div v-else class="rt none">不构成<br />重大危险源</div>
          <div class="nums">S = <b>{{ s.design.sum }}</b>　R = <b>{{ s.design.r }}</b></div>
          <div class="muted small">按当前库存：S = {{ s.current.sum }}</div>
        </div>
        <el-table :data="s.stocks" size="small" class="tbl">
          <el-table-column label="危化品" min-width="130">
            <template #default="{ row }">
              {{ row.chemName }} <span class="muted">{{ row.cas }}</span>
              <el-tooltip v-if="!row.hasMsds" content="还没有上传 MSDS"><el-icon class="warn"><WarningFilled /></el-icon></el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="当前库存 / 最大储量（吨）" width="190">
            <template #default="{ row }">
              <span :class="{ danger: row.overstock }">{{ ton(row.qty) }}</span> / {{ ton(row.maxQty) }}
              <el-tag v-if="row.overstock" size="small" type="danger" effect="dark">超储</el-tag>
              <el-progress :percentage="Math.min(100, Math.round((Number(row.qty) * 100) / Number(row.maxQty)))" :show-text="false" :stroke-width="4"
                           :status="row.overstock ? 'exception' : ''" />
            </template>
          </el-table-column>
          <el-table-column label="临界量 Q" width="90"><template #default="{ row }">{{ row.criticalQty == null ? '不参与' : ton(row.criticalQty) }}</template></el-table-column>
          <el-table-column label="q / Q" width="80"><template #default="{ row }">{{ ratio(s, row) }}</template></el-table-column>
          <el-table-column v-if="s.canFlow" label="操作" width="200">
            <template #default="{ row }">
              <el-button link type="success" @click="flowRef.open(s.store, row, 'IN')">入库</el-button>
              <el-button link type="warning" @click="flowRef.open(s.store, row, 'OUT')">出库</el-button>
              <el-button link @click="flowRef.open(s.store, row, 'CHECK')">盘点</el-button>
              <el-button v-if="canEdit" link type="primary" @click="editStock(s, row)">储量</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <el-dialog v-model="sVisible" :title="sForm.id ? '编辑存储单元' : '新增存储单元'" width="560px">
      <el-form ref="sFormRef" :model="sForm" :rules="sRules" label-width="110px">
        <el-form-item label="名称" prop="name"><el-input v-model="sForm.name" maxlength="50" /></el-form-item>
        <el-form-item label="类型" prop="storeType">
          <el-radio-group v-model="sForm.storeType"><el-radio-button v-for="(v, k) in CHEM_STORE_TYPES" :key="k" :value="k">{{ v }}</el-radio-button></el-radio-group>
        </el-form-item>
        <el-form-item label="位置" prop="location"><el-input v-model="sForm.location" maxlength="100" /></el-form-item>
        <el-form-item label="500 m 内人数" prop="people">
          <el-input-number v-model="sForm.people" :min="0" :max="1000000" />
          <div class="muted">单元外 500 米范围内的常住人数，决定校正系数 α（≥100 人 2.0，50~99 人 1.5，30~49 人 1.2，1~29 人 1.0，0 人 0.5）</div>
        </el-form-item>
        <el-form-item label="负责人" prop="managerId">
          <el-select v-model="sForm.managerId" filterable style="width: 100%"><el-option v-for="u in users" :key="u.id" :label="u.nickname" :value="u.id" /></el-select>
          <div class="muted">负责人能办理这个单元的出入库</div>
        </el-form-item>
        <el-form-item label="备注" prop="remark"><el-input v-model="sForm.remark" maxlength="200" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveStore">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="kVisible" :title="kForm.stockId ? '修改设计最大储量' : '添加危化品'" width="480px">
      <el-form label-width="110px">
        <el-form-item label="危化品" required>
          <el-select v-model="kForm.chemId" filterable :disabled="!!kForm.stockId" style="width: 100%">
            <el-option v-for="i in items" :key="i.item.id" :label="`${i.item.name}（${i.item.cas}）`" :value="i.item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="设计最大储量" required>
          <el-input-number v-model="kForm.maxQty" :min="0.001" :max="1000000" :precision="3" :controls="false" style="width: 160px" /> <span class="unit">吨</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-if="kForm.stockId" type="danger" plain class="left" @click="removeStock">从单元去掉</el-button>
        <el-button @click="kVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveStock">保存</el-button>
      </template>
    </el-dialog>

    <FlowDialog ref="flowRef" @done="afterChange" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { chemItems, chemStores, chemStoreStatus, removeChemStock, saveChemStock, saveChemStore, userOptions } from '@/api'
import { CHEM_LEVELS, CHEM_STORE_TYPES, ton } from '@/utils/dict'
import FlowDialog from './FlowDialog.vue'

const emit = defineEmits(['changed'])
const store = useUserStore()
const canEdit = computed(() => store.hasPerm('chem:edit'))
const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const users = ref([])
const items = ref([])
const flowRef = ref()

const sVisible = ref(false)
const sFormRef = ref()
const sForm = reactive({ id: null, name: '', storeType: 'WAREHOUSE', location: '', people: 0, managerId: null, remark: '' })
const sRules = {
  name: [{ required: true, message: '请填写名称' }],
  location: [{ required: true, message: '请填写位置' }],
  people: [{ required: true, message: '请填写人数' }],
  managerId: [{ required: true, message: '请选择负责人' }]
}
const kVisible = ref(false)
const kForm = reactive({ storeId: null, stockId: null, chemId: null, maxQty: null })

const ratio = (s, row) => {
  const p = s.design.parts.find((x) => x.name === row.chemName)
  return p ? Number(p.ratio).toFixed(2) : '-'
}
async function load() {
  loading.value = true
  try {
    rows.value = await chemStores()
  } finally {
    loading.value = false
  }
}
function afterChange() {
  load()
  emit('changed')
}
async function editStore(s) {
  Object.assign(sForm, s ? { id: s.id, name: s.name, storeType: s.storeType, location: s.location, people: s.people, managerId: s.managerId, remark: s.remark || '' }
    : { id: null, name: '', storeType: 'WAREHOUSE', location: '', people: 0, managerId: null, remark: '' })
  if (!users.value.length) users.value = await userOptions()
  sVisible.value = true
  sFormRef.value?.clearValidate()
}
async function saveStore() {
  await sFormRef.value.validate()
  saving.value = true
  try {
    const { id, ...data } = sForm
    await saveChemStore(id, data)
    ElMessage.success('已保存')
    sVisible.value = false
    afterChange()
  } finally {
    saving.value = false
  }
}
async function toggle(s) {
  const to = s.status === 1 ? 0 : 1
  if (!to) await ElMessageBox.confirm(`停用【${s.name}】？单元里有库存不能停用。`, '停用', { type: 'warning' })
  await chemStoreStatus(s.id, to)
  ElMessage.success(to ? '已启用' : '已停用')
  afterChange()
}
async function editStock(s, row) {
  Object.assign(kForm, { storeId: s.store.id, stockId: row?.id || null, chemId: row?.chemId || null, maxQty: row ? Number(row.maxQty) : null })
  if (!items.value.length) items.value = await chemItems({ enabled: true })
  kVisible.value = true
}
async function saveStock() {
  if (!kForm.chemId || !kForm.maxQty) return ElMessage.warning('请选择危化品并填写最大储量')
  saving.value = true
  try {
    await saveChemStock(kForm.storeId, { chemId: kForm.chemId, maxQty: kForm.maxQty })
    ElMessage.success('已保存，重大危险源辨识结果已重新计算')
    kVisible.value = false
    afterChange()
  } finally {
    saving.value = false
  }
}
async function removeStock() {
  await ElMessageBox.confirm('从这个单元去掉这种危化品？（库存必须为 0）', '去掉', { type: 'warning' })
  await removeChemStock(kForm.stockId)
  ElMessage.success('已去掉')
  kVisible.value = false
  afterChange()
}
onMounted(load)
</script>

<style scoped>
.muted { color: #999; font-size: 12px; }
.small { font-size: 12px; }
.warn { color: #e6a23c; vertical-align: middle; }
.danger { color: #f56c6c; font-weight: 600; }
.unit { margin-left: 6px; color: #666; }
.left { float: left; }
.store { border: 1px solid #ebeef5; border-radius: 8px; margin-bottom: 14px; overflow: hidden; }
.store.off { opacity: 0.6; }
.head { display: flex; justify-content: space-between; align-items: center; padding: 10px 14px; background: #fafbfc; border-bottom: 1px solid #ebeef5; gap: 10px; flex-wrap: wrap; }
.name { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.body { display: flex; }
.result { width: 170px; flex-shrink: 0; padding: 14px; border-right: 1px solid #ebeef5; text-align: center; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px; }
.result.major { background: #fff7f5; }
.lv { color: #fff; font-weight: 700; font-size: 18px; border-radius: 6px; padding: 4px 14px; }
.rt { font-size: 13px; color: #cf1322; font-weight: 600; }
.rt.none { color: #52c41a; line-height: 1.4; }
.nums { font-size: 13px; margin-top: 4px; }
.tbl { flex: 1; }
@media (max-width: 900px) { .body { flex-direction: column; } .result { width: auto; border-right: 0; border-bottom: 1px solid #ebeef5; } }
</style>
