<template>
  <div>
    <div class="toolbar">
      <span class="muted">库存只能通过“入库”和“发放”变化；低于安全库存会在统计、大屏和每天的提醒里提示补货</span>
      <div class="spacer" />
      <el-button type="primary" icon="Plus" @click="openForm()">新增用品</el-button>
    </div>
    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="name" label="名称" min-width="120" />
      <el-table-column prop="spec" label="规格" min-width="140" show-overflow-tooltip />
      <el-table-column label="防护部位" width="100"><template #default="{ row }">{{ PPE_CATEGORIES[row.category] }}</template></el-table-column>
      <el-table-column label="使用期限" width="90"><template #default="{ row }">{{ row.validMonths }} 个月</template></el-table-column>
      <el-table-column label="库存" width="130">
        <template #default="{ row }">
          <b :class="{ low: row.stock < row.minStock }">{{ row.stock }}</b> {{ row.unit }}
          <span class="muted">/ 安全 {{ row.minStock }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }"><el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button link type="primary" @click="stockIn(row)">入库</el-button>
          <el-button link type="primary" @click="openForm(row)">修改</el-button>
          <el-button link :type="row.status === 1 ? 'danger' : 'success'" @click="toggle(row)">{{ row.status === 1 ? '停用' : '启用' }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="form.id ? '修改用品' : '新增用品'" width="min(520px, 94vw)">
      <el-form label-width="90px">
        <el-form-item label="名称" required><el-input v-model="form.name" maxlength="40" placeholder="如：安全帽" /></el-form-item>
        <el-form-item label="规格"><el-input v-model="form.spec" maxlength="60" placeholder="如：ABS 防静电" /></el-form-item>
        <el-form-item label="防护部位" required>
          <el-select v-model="form.category" style="width: 100%"><el-option v-for="(v, k) in PPE_CATEGORIES" :key="k" :label="v" :value="k" /></el-select>
        </el-form-item>
        <el-form-item label="单位" required><el-input v-model="form.unit" maxlength="6" style="width: 120px" /></el-form-item>
        <el-form-item label="使用期限" required>
          <el-input-number v-model="form.validMonths" :min="1" :max="120" /> <span class="unit">个月</span>
        </el-form-item>
        <el-form-item v-if="!form.id" label="初始库存"><el-input-number v-model="form.stock" :min="0" :max="100000" /></el-form-item>
        <el-form-item label="安全库存"><el-input-number v-model="form.minStock" :min="0" :max="100000" /></el-form-item>
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ppeItemStatus, ppeItems, ppeStockIn, savePpeItem } from '@/api'
import { PPE_CATEGORIES } from '@/utils/dict'

const emit = defineEmits(['changed'])
const loading = ref(false)
const list = ref([])
const visible = ref(false)
const saving = ref(false)
const form = reactive({})

async function load() {
  loading.value = true
  try {
    list.value = await ppeItems()
  } finally {
    loading.value = false
  }
}
function openForm(x) {
  Object.assign(form, {
    id: x?.id || null, name: x?.name || '', spec: x?.spec || '', category: x?.category || 'HEAD', unit: x?.unit || '个',
    validMonths: x?.validMonths || 12, stock: 0, minStock: x?.minStock ?? 5, remark: x?.remark || ''
  })
  visible.value = true
}
async function save() {
  if (!form.name.trim() || !form.unit.trim()) return ElMessage.warning('请填写名称和单位')
  saving.value = true
  try {
    const { id, ...data } = form
    await savePpeItem(id, data)
    ElMessage.success('已保存')
    visible.value = false
    load()
    emit('changed')
  } finally {
    saving.value = false
  }
}
async function stockIn(row) {
  let value
  try {
    ({ value } = await ElMessageBox.prompt(`${row.name} 现有库存 ${row.stock}${row.unit}，本次入库数量：`, '入库', {
      inputPattern: /^[1-9]\d{0,5}$/, inputErrorMessage: '请输入 1 ~ 999999 的整数'
    }))
  } catch {
    return
  }
  const stock = await ppeStockIn(row.id, Number(value))
  ElMessage.success(`已入库，现有库存 ${stock}${row.unit}`)
  load()
  emit('changed')
}
async function toggle(row) {
  await ppeItemStatus(row.id, row.status === 1 ? 0 : 1)
  ElMessage.success(row.status === 1 ? '已停用' : '已启用')
  load()
}
onMounted(load)
</script>

<style scoped>
.muted { color: #999; font-size: 12px; }
.low { color: #f56c6c; }
.unit { margin-left: 8px; color: #999; }
</style>
