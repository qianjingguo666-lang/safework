<template>
  <div class="page-card">
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="标题" clearable style="width: 200px" @keyup.enter="search" @clear="search" />
      <el-button icon="Search" @click="search">查询</el-button>
      <div class="spacer" />
      <el-button v-perm="'system:notice:add'" type="primary" icon="Promotion" @click="openPublish">发布公告</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column label="标题" min-width="240">
        <template #default="{ row }">
          <el-tag v-if="row.level === 'IMPORTANT'" type="danger" size="small" class="lv">重要</el-tag>{{ row.title }}
        </template>
      </el-table-column>
      <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
      <el-table-column prop="publisherName" label="发布人" width="150" />
      <el-table-column label="发送人数" width="100">
        <template #default="{ row }">{{ row.receiverCount }} 人</template>
      </el-table-column>
      <el-table-column prop="createTime" label="发布时间" width="170" />
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button v-perm="'system:notice:add'" link type="danger" @click="withdraw(row)">撤回</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination v-model:current-page="query.current" :page-size="query.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
    </div>

    <el-dialog v-model="visible" title="发布公告" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="70px">
        <el-form-item label="标题" prop="title"><el-input v-model="form.title" maxlength="100" show-word-limit /></el-form-item>
        <el-form-item label="级别">
          <el-radio-group v-model="form.level">
            <el-radio value="NORMAL">普通</el-radio>
            <el-radio value="IMPORTANT">重要</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="8" maxlength="5000" show-word-limit />
        </el-form-item>
        <el-alert type="info" :closable="false" show-icon title="发布后本企业所有正常状态的账号都会收到一条站内消息，在线的人会立即弹出提醒" />
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="publish">发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageNotices, publishNotice, withdrawNotice } from '@/api'

const loading = ref(false)
const saving = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, keyword: '' })

async function load() {
  loading.value = true
  try {
    const page = await pageNotices(query.keyword ? query : { current: query.current, size: query.size })
    list.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}
function search() {
  query.current = 1
  load()
}

const visible = ref(false)
const formRef = ref()
const form = reactive({ title: '', level: 'NORMAL', content: '' })
const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }]
}
function openPublish() {
  Object.assign(form, { title: '', level: 'NORMAL', content: '' })
  visible.value = true
}
async function publish() {
  await formRef.value.validate()
  saving.value = true
  try {
    await publishNotice(form)
    ElMessage.success('已发布')
    visible.value = false
    load()
  } finally {
    saving.value = false
  }
}
async function withdraw(row) {
  try {
    await ElMessageBox.confirm(`撤回【${row.title}】后，所有人收到的这条消息也会被删除。确认撤回吗？`, '撤回公告', { type: 'warning' })
  } catch {
    return
  }
  await withdrawNotice(row.id)
  ElMessage.success('已撤回')
  load()
}
onMounted(load)
</script>

<style scoped>
.lv { margin-right: 6px; }
</style>
