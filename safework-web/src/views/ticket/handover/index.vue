<template>
  <main class="page-card handovers">
    <el-dialog v-model="creating" title="交班给下一班" width="min(520px, 94vw)" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="作业票">{{ selectedTitle }}</el-form-item>
        <el-form-item label="接班人" required>
          <el-select v-model="receiver" filterable placeholder="选择接班员工">
            <el-option v-for="person in people" :key="person.id" :value="person.id" :label="person.deptName ? person.nickname + '（' + person.deptName + '）' : person.nickname"/>
          </el-select>
        </el-form-item>
        <el-form-item v-for="item in items" :key="item.key" :label="item.label" required>
          <el-input v-model="notes[item.key]" type="textarea" :rows="2" maxlength="500" show-word-limit/>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="creating=false">取消</el-button>
        <el-button type="primary" :disabled="!ready" :loading="busy" @click="submit">提交交班</el-button></template>
    </el-dialog>
    <div class="tools"><b>交接记录</b><el-button @click="load" :loading="loading">刷新</el-button></div>
    <el-empty v-if="!loading && !rows.length" description="暂无交接记录"/>
    <article v-for="row in rows" :key="row.id">
      <div class="tools"><strong>{{ row.title }}</strong><el-tag :type="row.acceptedAt ? 'success' : 'warning'">{{ row.acceptedAt ? '已接班' : '待接班' }}</el-tag></div>
      <p>{{ row.ticketNo }} · {{ row.senderName }} 交给 {{ row.receiverName }}</p>
      <p>交班 {{ row.createTime }} <span v-if="row.acceptedAt"> · 接班 {{ row.acceptedAt }}</span></p>
      <div v-for="item in items" :key="item.key" class="item">
        <label v-if="canAccept(row)"><input type="checkbox" :value="item.key" v-model="checked[row.id]"> 核对{{ item.label }}</label>
        <b v-else>{{ item.label }}</b>
        <div>{{ row[item.key] }}</div>
      </div>
      <el-button v-if="canAccept(row)" type="primary" :disabled="checked[row.id]?.length !== items.length" :loading="busy" @click="accept(row)">确认接班</el-button>
      <el-button v-if="row.acceptedAt && mine(row)" @click="open(row.ticketId,row.title)">交给下一班</el-button>
    </article>
    <el-pagination v-if="total>20" v-model:current-page="page" :total="total" :page-size="20" layout="prev, pager, next" @current-change="load"/>
  </main>
</template>
<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import { userOptions } from '@/api'
import { useUserStore } from '@/stores/user'

const route = useRoute(), router = useRouter(), user = useUserStore()
const items = [{key:'siteCondition',label:'现场情况'}, {key:'unfinished',label:'未完成事项'}, {key:'recheck',label:'复查要求'}]
const page = ref(1), total = ref(0), rows = ref([]), checked = ref({})
const loading = ref(false), busy = ref(false), creating = ref(false)
const ticket = ref(), selectedTitle = ref(''), receiver = ref(), notes = ref({}), people = ref([])
const ready = computed(() => receiver.value && items.every(i => notes.value[i.key]?.trim()))
const mine = row => String(row.receiverId) === String(user.user?.id)
const canAccept = row => mine(row) && !row.acceptedAt
let loadId = 0
async function load() {
  const id = ++loadId
  loading.value = true
  try {
    const result = await request.get('/ticket/handover/page', {params:{current:page.value, recordId:route.query.detail || undefined, awaitingMe:route.query.todo === '1'}})
    if (id !== loadId) return
    checked.value = Object.fromEntries(result.records.map(r => [r.id, checked.value[r.id] || []]))
    rows.value = result.records
    total.value = Number(result.total)
  } finally { if (id === loadId) loading.value = false }
}
async function open(id, title) {
  people.value = (await userOptions()).filter(p => String(p.id) !== String(user.user?.id))
  ticket.value = id
  selectedTitle.value = title || String(id)
  receiver.value = null
  notes.value = {}
  creating.value = true
}
async function submit() {
  if (busy.value || !ready.value) return
  busy.value = true
  try {
    await request.post('/ticket/handover', {ticketId:ticket.value, receiverId:receiver.value, ...notes.value})
    creating.value = false
    ElMessage.success('已通知接班人')
    page.value = 1
    await load()
  } finally { busy.value = false }
}
async function accept(row) {
  if (busy.value) return
  busy.value = true
  try {
    await request.post('/ticket/handover/' + row.id + '/accept', {checked:checked.value[row.id]})
    ElMessage.success('已确认接班')
    await load()
  } finally { busy.value = false }
}
onMounted(async () => {
  await load()
  if (route.query.ticket) {
    await open(route.query.ticket, route.query.title)
    await router.replace({path:route.path})
  }
})
</script>
<style scoped>
.handovers {max-width:960px;margin:auto;padding:16px}
.tools {display:flex;justify-content:space-between;align-items:center;gap:12px}
article {margin:14px 0;padding:16px;border:1px solid #dce3ec;border-radius:8px}
article p {color:#526175;font-size:14px}
.item {margin:10px 0;white-space:pre-wrap;overflow-wrap:anywhere}
.item label,.item b {font-weight:600;font-size:14px}
.item div {margin-top:4px}
</style>
