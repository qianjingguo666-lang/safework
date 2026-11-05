<template>
  <div>
    <div class="toolbar">
      <span class="muted">流程：填写岗位安全职责 → 新建责任书（草稿）→ 发布（每人一份，职责为发布时的快照）→ 本人手写签署 → 年底考核</span>
      <div class="spacer" />
      <el-button type="primary" icon="Plus" @click="edit()">新建责任书</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column label="年度" width="70"><template #default="{ row }">{{ row.book.year }}</template></el-table-column>
      <el-table-column label="责任书" min-width="190" show-overflow-tooltip><template #default="{ row }">{{ row.book.title }}</template></el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }"><el-tag size="small" :type="RESP_BOOK_STATUS[row.book.status]?.type">{{ RESP_BOOK_STATUS[row.book.status]?.label }}</el-tag></template>
      </el-table-column>
      <el-table-column label="签署截止" width="110"><template #default="{ row }">{{ row.book.deadline }}</template></el-table-column>
      <el-table-column label="签署进度" width="190">
        <template #default="{ row }">
          <template v-if="row.total">
            <el-progress :percentage="Math.round((row.signed * 100) / row.total)" :stroke-width="8" :status="row.signed === row.total ? 'success' : ''">
              <span class="pct">{{ row.signed }}/{{ row.total }}</span>
            </el-progress>
            <span v-if="row.late" class="warn small">{{ row.late }} 人逾期签署</span>
          </template>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="已考核" width="80"><template #default="{ row }">{{ row.total ? `${row.assessed}/${row.total}` : '-' }}</template></el-table-column>
      <el-table-column label="操作" width="260">
        <template #default="{ row }">
          <template v-if="row.book.status === 'DRAFT'">
            <el-button link type="primary" @click="edit(row.book)">编辑</el-button>
            <el-button link type="success" @click="publish(row.book)">发布</el-button>
            <el-button link type="danger" @click="remove(row.book)">删除</el-button>
          </template>
          <template v-else>
            <el-button link type="primary" @click="signsRef.open(row.book)">签署与考核</el-button>
            <template v-if="row.book.status === 'PUBLISHED'">
              <el-button link @click="sync(row.book)">同步人员</el-button>
              <el-button link type="warning" @click="close(row.book)">结束签署</el-button>
            </template>
          </template>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && !rows.length" description="还没有责任书，点右上角“新建责任书”" />

    <el-dialog v-model="visible" :title="form.id ? '修改责任书' : '新建责任书'" width="680px" top="6vh">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="责任年度" prop="year">
          <el-select v-model="form.year" style="width: 140px" @change="autoTitle">
            <el-option v-for="y in years" :key="y" :label="`${y} 年`" :value="y" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" prop="title"><el-input v-model="form.title" maxlength="60" /></el-form-item>
        <el-form-item label="通用条款" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="10" maxlength="5000" show-word-limit />
          <div class="muted small">所有人都一样的部分。每个人的岗位安全职责会自动接在后面</div>
        </el-form-item>
        <el-form-item label="签署截止" prop="deadline">
          <el-date-picker v-model="form.deadline" type="date" value-format="YYYY-MM-DD" :disabled-date="(d) => d < today" :popper-options="PICKER_POPPER" />
        </el-form-item>
        <el-form-item label="备注" prop="remark"><el-input v-model="form.remark" maxlength="200" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存草稿</el-button>
      </template>
    </el-dialog>

    <SignList ref="signsRef" @changed="load" />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { closeRespBook, publishRespBook, removeRespBook, respBooks, saveRespBook, syncRespBook } from '@/api'
import { RESP_BOOK_STATUS } from '@/utils/dict'
import { PICKER_POPPER } from '@/utils/picker'
import SignList from './SignList.vue'

const TEMPLATE = `一、本责任书依据《中华人民共和国安全生产法》和公司安全生产责任制制定，责任期为当年 1 月 1 日至 12 月 31 日。
二、责任人应认真履行本岗位安全生产职责（见下方“岗位安全职责”），做到“不伤害自己、不伤害他人、不被他人伤害、保护他人不受伤害”。
三、年度安全目标：不发生一般及以上生产安全事故；隐患整改率 100%；特种作业持证上岗率 100%；安全培训合格率 100%。
四、公司年底对责任落实情况进行考核，考核结果与年度绩效挂钩。
五、本责任书经责任人手写签名后生效。`

const loading = ref(false)
const rows = ref([])
const visible = ref(false)
const saving = ref(false)
const formRef = ref()
const signsRef = ref()
const now = new Date()
const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
const years = [now.getFullYear() - 1, now.getFullYear(), now.getFullYear() + 1]
const form = reactive({ id: null, year: null, title: '', content: '', deadline: '', remark: '' })
const rules = {
  year: [{ required: true, message: '请选择年度' }],
  title: [{ required: true, message: '请填写标题' }],
  content: [{ required: true, message: '请填写通用条款' }],
  deadline: [{ required: true, message: '请选择签署截止日期' }]
}

async function load() {
  loading.value = true
  try {
    rows.value = await respBooks()
  } finally {
    loading.value = false
  }
}
function autoTitle() {
  if (!form.title || /^\d{4} 年度安全生产责任书$/.test(form.title)) form.title = `${form.year} 年度安全生产责任书`
}
function edit(b) {
  if (b) {
    Object.assign(form, { id: b.id, year: b.year, title: b.title, content: b.content, deadline: b.deadline, remark: b.remark || '' })
  } else {
    const d = new Date(today.getTime() + 14 * 86400000)
    const pad = (n) => String(n).padStart(2, '0')
    Object.assign(form, { id: null, year: now.getFullYear(), title: '', content: TEMPLATE, deadline: `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`, remark: '' })
    autoTitle()
  }
  visible.value = true
  formRef.value?.clearValidate()
}
async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    await saveRespBook(form.id, { year: form.year, title: form.title, content: form.content, deadline: form.deadline, remark: form.remark })
    ElMessage.success('已保存草稿')
    visible.value = false
    load()
  } finally {
    saving.value = false
  }
}
async function publish(b) {
  await ElMessageBox.confirm(`发布后，填写了安全职责的岗位上的每个人都会收到一份《${b.title}》待签署，内容不能再修改。确定发布吗？`, '发布责任书', { type: 'warning' })
  const n = await publishRespBook(b.id)
  ElMessage.success(`已发布，${n} 人待签署`)
  load()
}
async function sync(b) {
  const n = await syncRespBook(b.id)
  ElMessage.success(n ? `已给 ${n} 名新到岗人员补发` : '没有需要补发的人')
  load()
}
async function close(b) {
  await ElMessageBox.confirm('结束后没签的人不能再签署。确定结束签署吗？', '结束签署', { type: 'warning' })
  await closeRespBook(b.id)
  ElMessage.success('已结束签署')
  load()
}
async function remove(b) {
  await ElMessageBox.confirm(`确定删除草稿《${b.title}》吗？`, '删除', { type: 'warning' })
  await removeRespBook(b.id)
  ElMessage.success('已删除')
  load()
}
onMounted(load)
</script>

<style scoped>
.muted { color: #999; font-size: 13px; }
.small { font-size: 12px; }
.warn { color: #e6a23c; }
.pct { font-size: 12px; color: #666; }
</style>
