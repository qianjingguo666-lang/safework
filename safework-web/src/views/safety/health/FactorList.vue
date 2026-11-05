<template>
  <div>
    <div class="toolbar">
      <span class="muted">体检周期按岗位接触的危害因素里最短的计算；改了周期，台账立即按新周期重算</span>
      <div class="spacer" />
      <el-button type="primary" icon="Plus" @click="openForm()">新增危害因素</el-button>
    </div>
    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="name" label="危害因素" width="110" />
      <el-table-column label="类别" width="100"><template #default="{ row }">{{ OH_CATEGORIES[row.category] }}</template></el-table-column>
      <el-table-column label="体检周期" width="90"><template #default="{ row }">{{ row.cycleMonths }} 个月</template></el-table-column>
      <el-table-column prop="limitValue" label="接触限值" min-width="160" show-overflow-tooltip />
      <el-table-column prop="examItems" label="体检重点项目" min-width="160" show-overflow-tooltip />
      <el-table-column prop="contraindication" label="职业禁忌证" min-width="160" show-overflow-tooltip />
      <el-table-column label="状态" width="70">
        <template #default="{ row }"><el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="110">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)">修改</el-button>
          <el-button link :type="row.status === 1 ? 'danger' : 'success'" @click="toggle(row)">{{ row.status === 1 ? '停用' : '启用' }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="form.id ? '修改危害因素' : '新增危害因素'" width="min(560px, 94vw)">
      <el-form label-width="100px">
        <el-form-item label="名称" required><el-input v-model="form.name" maxlength="40" placeholder="如：苯" /></el-form-item>
        <el-form-item label="类别" required>
          <el-select v-model="form.category" style="width: 100%"><el-option v-for="(v, k) in OH_CATEGORIES" :key="k" :label="v" :value="k" /></el-select>
        </el-form-item>
        <el-form-item label="在岗体检周期" required><el-input-number v-model="form.cycleMonths" :min="3" :max="36" /> <span class="unit">个月</span></el-form-item>
        <el-form-item label="接触限值"><el-input v-model="form.limitValue" maxlength="60" placeholder="如：PC-TWA 6 mg/m³" /></el-form-item>
        <el-form-item label="体检重点项目"><el-input v-model="form.examItems" type="textarea" :rows="2" maxlength="300" /></el-form-item>
        <el-form-item label="职业禁忌证"><el-input v-model="form.contraindication" type="textarea" :rows="2" maxlength="300" /></el-form-item>
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
import { ElMessage } from 'element-plus'
import { healthFactorStatus, healthFactors, saveHealthFactor } from '@/api'
import { OH_CATEGORIES } from '@/utils/dict'

const emit = defineEmits(['changed'])
const loading = ref(false)
const list = ref([])
const visible = ref(false)
const saving = ref(false)
const form = reactive({})

async function load() {
  loading.value = true
  try {
    list.value = await healthFactors()
  } finally {
    loading.value = false
  }
}
function openForm(f) {
  Object.assign(form, {
    id: f?.id || null, name: f?.name || '', category: f?.category || 'CHEMICAL', cycleMonths: f?.cycleMonths || 12,
    limitValue: f?.limitValue || '', examItems: f?.examItems || '', contraindication: f?.contraindication || '', remark: f?.remark || ''
  })
  visible.value = true
}
async function save() {
  if (!form.name.trim()) return ElMessage.warning('请填写名称')
  saving.value = true
  try {
    const { id, ...data } = form
    await saveHealthFactor(id, data)
    ElMessage.success('已保存')
    visible.value = false
    load()
    emit('changed')
  } finally {
    saving.value = false
  }
}
async function toggle(f) {
  await healthFactorStatus(f.id, f.status === 1 ? 0 : 1)
  ElMessage.success(f.status === 1 ? '已停用' : '已启用')
  load()
  emit('changed')
}
onMounted(load)
</script>

<style scoped>
.muted { color: #999; font-size: 12px; }
.unit { margin-left: 8px; color: #999; }
</style>
