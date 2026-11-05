<template>
  <div>
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="名称 / CAS 号" clearable style="width: 180px" @keyup.enter="load" @clear="load" />
      <el-button icon="Search" @click="load">查询</el-button>
      <div class="spacer" />
      <el-button v-if="canEdit" type="primary" icon="Plus" @click="edit()">新增危化品</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column label="名称" min-width="120">
        <template #default="{ row }">
          <el-button link type="primary" @click="view(row)">{{ row.item.name }}</el-button>
          <el-tag v-if="row.item.status !== 1" size="small" type="info">停用</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="CAS 号" width="120"><template #default="{ row }">{{ row.item.cas }}</template></el-table-column>
      <el-table-column label="UN 号" width="70"><template #default="{ row }">{{ row.item.unNo || '-' }}</template></el-table-column>
      <el-table-column label="危险类别" width="100"><template #default="{ row }">{{ CHEM_CATEGORIES[row.item.category] }}</template></el-table-column>
      <el-table-column label="危险特性" min-width="200" show-overflow-tooltip><template #default="{ row }">{{ row.item.hazards || '-' }}</template></el-table-column>
      <el-table-column label="临界量 Q（吨）" width="110"><template #default="{ row }">{{ row.item.criticalQty == null ? '不参与' : ton(row.item.criticalQty) }}</template></el-table-column>
      <el-table-column label="β" width="60"><template #default="{ row }">{{ ton(row.item.beta) }}</template></el-table-column>
      <el-table-column label="存放 / 总库存" width="120"><template #default="{ row }">{{ row.storeCount }} 处 / {{ ton(row.totalQty) }} 吨</template></el-table-column>
      <el-table-column label="MSDS" width="80">
        <template #default="{ row }"><el-tag size="small" :type="row.hasMsds ? 'success' : 'warning'">{{ row.hasMsds ? '有' : '缺' }}</el-tag></template>
      </el-table-column>
      <el-table-column v-if="canEdit" label="操作" width="110">
        <template #default="{ row }">
          <el-button link type="primary" @click="edit(row.item)">编辑</el-button>
          <el-button link :type="row.item.status === 1 ? 'warning' : 'success'" @click="toggle(row.item)">{{ row.item.status === 1 ? '停用' : '启用' }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-drawer v-model="vVisible" :title="cur?.name" size="560px" destroy-on-close>
      <template v-if="cur">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="CAS 号">{{ cur.cas }}</el-descriptions-item>
          <el-descriptions-item label="UN 编号">{{ cur.unNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="危险类别">{{ CHEM_CATEGORIES[cur.category] }}</el-descriptions-item>
          <el-descriptions-item label="临界量 / β">{{ cur.criticalQty == null ? '不参与辨识' : `${ton(cur.criticalQty)} 吨` }} / {{ ton(cur.beta) }}</el-descriptions-item>
          <el-descriptions-item label="危险特性" :span="2">{{ cur.hazards || '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="cur.remark" label="备注" :span="2">{{ cur.remark }}</el-descriptions-item>
        </el-descriptions>
        <div class="msds">
          <AttachmentPanel :key="cur.id" biz-type="CHEM" :biz-id="cur.id" tag="MSDS" title="MSDS（化学品安全技术说明书）"
                           empty-text="请上传供应商提供的 MSDS（PDF 或图片），现场人员可以随时查看" @changed="load" />
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="visible" :title="form.id ? '编辑危化品' : '新增危化品'" width="600px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="名称" prop="name"><el-input v-model="form.name" maxlength="50" /></el-form-item>
        <el-form-item label="CAS 号" prop="cas"><el-input v-model="form.cas" placeholder="例如 67-56-1" style="width: 180px" /></el-form-item>
        <el-form-item label="UN 编号" prop="unNo"><el-input v-model="form.unNo" maxlength="10" style="width: 180px" /></el-form-item>
        <el-form-item label="危险类别" prop="category">
          <el-select v-model="form.category" style="width: 180px"><el-option v-for="(v, k) in CHEM_CATEGORIES" :key="k" :label="v" :value="k" /></el-select>
        </el-form-item>
        <el-form-item label="危险特性" prop="hazards"><el-input v-model="form.hazards" type="textarea" :rows="2" maxlength="300" /></el-form-item>
        <el-form-item label="临界量 Q">
          <el-input-number v-model="form.criticalQty" :min="0.001" :max="100000" :precision="3" :controls="false" style="width: 140px" /> <span class="unit">吨</span>
          <div class="muted">查 GB 18218-2018 表 1（具体物质）或表 2（按类别）；不在标准里的留空，不参与重大危险源辨识</div>
        </el-form-item>
        <el-form-item label="校正系数 β">
          <el-input-number v-model="form.beta" :min="0.1" :max="50" :precision="2" :step="0.5" style="width: 140px" />
          <div class="muted">留空按类别取默认（毒性 2、易燃气体 1.5、爆炸物 2、其他 1）；毒性气体查表 3，如硫化氢 5、氯 4、氨 2</div>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" maxlength="200" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { chemItems, chemItemStatus, saveChemItem } from '@/api'
import { CHEM_CATEGORIES, ton } from '@/utils/dict'
import AttachmentPanel from '@/components/AttachmentPanel.vue'

const emit = defineEmits(['changed'])
const store = useUserStore()
const canEdit = computed(() => store.hasPerm('chem:edit'))
const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const keyword = ref('')
const vVisible = ref(false)
const cur = ref(null)
const visible = ref(false)
const formRef = ref()
const form = reactive({ id: null, name: '', cas: '', unNo: '', category: 'FLAMMABLE_LIQUID', hazards: '', criticalQty: null, beta: null, remark: '' })
const rules = {
  name: [{ required: true, message: '请填写名称' }],
  cas: [{ required: true, message: '请填写 CAS 号' }, { pattern: /^\d{2,7}-\d{2}-\d$/, message: '格式如 67-56-1' }],
  category: [{ required: true, message: '请选择危险类别' }]
}

async function load() {
  loading.value = true
  try {
    rows.value = await chemItems(keyword.value ? { keyword: keyword.value } : {})
  } finally {
    loading.value = false
  }
  emit('changed')
}
function view(row) {
  cur.value = row.item
  vVisible.value = true
}
function edit(i) {
  Object.assign(form, i
    ? { id: i.id, name: i.name, cas: i.cas, unNo: i.unNo || '', category: i.category, hazards: i.hazards || '', criticalQty: i.criticalQty == null ? null : Number(i.criticalQty), beta: Number(i.beta), remark: i.remark || '' }
    : { id: null, name: '', cas: '', unNo: '', category: 'FLAMMABLE_LIQUID', hazards: '', criticalQty: null, beta: null, remark: '' })
  visible.value = true
  formRef.value?.clearValidate()
}
async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    const { id, ...data } = form
    await saveChemItem(id, data)
    ElMessage.success('已保存')
    visible.value = false
    load()
  } finally {
    saving.value = false
  }
}
async function toggle(i) {
  const to = i.status === 1 ? 0 : 1
  if (!to) await ElMessageBox.confirm(`停用【${i.name}】？还有库存的不能停用。`, '停用', { type: 'warning' })
  await chemItemStatus(i.id, to)
  ElMessage.success(to ? '已启用' : '已停用')
  load()
}
onMounted(load)
</script>

<style scoped>
.muted { color: #999; font-size: 12px; width: 100%; }
.unit { margin-left: 6px; color: #666; }
.msds { margin-top: 16px; }
</style>
