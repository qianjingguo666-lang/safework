<template>
  <div>
    <div class="toolbar">
      <span class="muted">修改检查表只影响以后生成的任务，已经生成的任务保留当时的检查项</span>
      <div class="spacer" />
      <el-button type="primary" icon="Plus" @click="openForm()">新建检查表</el-button>
    </div>
    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="name" label="检查表" min-width="200" show-overflow-tooltip />
      <el-table-column label="类型" width="110"><template #default="{ row }">{{ CHECK_TYPES[row.checkType] }}</template></el-table-column>
      <el-table-column prop="itemCount" label="检查项" width="80" />
      <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
      <el-table-column label="状态" width="80">
        <template #default="{ row }"><el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)">修改</el-button>
          <el-button link :type="row.status === 1 ? 'danger' : 'success'" @click="toggle(row)">{{ row.status === 1 ? '停用' : '启用' }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="form.id ? '修改检查表' : '新建检查表'" width="min(820px, 96vw)">
      <el-form label-width="80px">
        <el-row :gutter="12">
          <el-col :xs="24" :span="14"><el-form-item label="名称" required><el-input v-model="form.name" maxlength="60" placeholder="如：罐区日常巡回检查表" /></el-form-item></el-col>
          <el-col :xs="24" :span="10">
            <el-form-item label="类型" required>
              <el-select v-model="form.checkType" style="width: 100%"><el-option v-for="(v, k) in CHECK_TYPES" :key="k" :label="v" :value="k" /></el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="检查项" required>
          <div class="items">
            <div v-for="(it, i) in form.items" :key="i" class="row">
              <span class="no">{{ i + 1 }}</span>
              <el-input v-model="it.content" maxlength="200" placeholder="检查内容，如：消防器材齐全有效" class="c" />
              <el-input v-model="it.standard" maxlength="200" placeholder="检查标准（可选）" class="s" />
              <el-select v-model="it.category" placeholder="隐患类别" class="g">
                <el-option v-for="c in CATEGORIES" :key="c" :label="c" :value="c" />
              </el-select>
              <el-button link icon="Delete" :disabled="form.items.length <= 1" @click="form.items.splice(i, 1)" />
            </div>
            <el-button size="small" icon="Plus" @click="form.items.push(empty())">添加检查项</el-button>
            <div class="muted">隐患类别：这一项不合格转隐患时用的类别</div>
          </div>
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { checkTemplateStatus, checkTemplates, saveCheckTemplate } from '@/api'
import { CHECK_TYPES } from '@/utils/dict'

const CATEGORIES = ['设备设施', '作业环境', '人员行为', '安全管理']
const loading = ref(false)
const list = ref([])
const visible = ref(false)
const saving = ref(false)
const form = reactive({})
const empty = () => ({ content: '', standard: '', category: '安全管理' })

async function load() {
  loading.value = true
  try {
    list.value = await checkTemplates()
  } finally {
    loading.value = false
  }
}
function openForm(t) {
  let items = [empty()]
  if (t?.items) {
    try {
      items = JSON.parse(t.items).map((x) => ({ content: x.content || '', standard: x.standard || '', category: x.category || '安全管理' }))
    } catch {
      items = [empty()]
    }
  }
  Object.assign(form, { id: t?.id || null, name: t?.name || '', checkType: t?.checkType || 'DAILY', items, remark: t?.remark || '' })
  visible.value = true
}
async function save() {
  if (!form.name.trim()) return ElMessage.warning('请填写检查表名称')
  const items = form.items.filter((x) => x.content.trim())
  if (!items.length) return ElMessage.warning('至少要有一个检查项')
  saving.value = true
  try {
    const { id, ...data } = form
    await saveCheckTemplate(id, { ...data, items })
    ElMessage.success('已保存')
    visible.value = false
    load()
  } finally {
    saving.value = false
  }
}
async function toggle(row) {
  await checkTemplateStatus(row.id, row.status === 1 ? 0 : 1)
  ElMessage.success(row.status === 1 ? '已停用' : '已启用')
  load()
}
onMounted(load)
</script>

<style scoped>
.muted { color: #999; font-size: 12px; }
.items { width: 100%; }
.row { display: flex; align-items: center; gap: 6px; margin-bottom: 6px; }
.no { flex: none; width: 20px; color: #999; font-size: 12px; text-align: right; }
.c { flex: 3; }
.s { flex: 2; }
.g { flex: none; width: 110px; }
@media (max-width: 640px) {
  .row { flex-wrap: wrap; }
  .c, .s { flex-basis: calc(100% - 30px); }
}
</style>
