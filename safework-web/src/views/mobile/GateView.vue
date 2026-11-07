<template>
  <div v-loading="loading">
    <div class="m-tabs">
      <button :class="{ on: tab === 'todo' }" @click="tab = 'todo'">待入厂<b>{{ todo.length }}</b></button>
      <button :class="{ on: tab === 'in' }" @click="tab = 'in'">在厂<b>{{ inside.length }}</b></button>
    </div>
    <div v-for="r in (tab === 'todo' ? todo : inside)" :key="r.pass.id" class="m-item" @click="open(r)">
      <div class="m-item-head">
        <el-tag size="small" :type="VISIT_TYPES[r.pass.visitType]?.type">{{ VISIT_TYPES[r.pass.visitType]?.label }}</el-tag>
        <span class="m-item-title">{{ r.pass.plateNo ? r.pass.plateNo + ' ' : '' }}{{ r.pass.visitorName }}</span>
        <el-tag v-if="r.problems.length" size="small" type="danger" effect="dark">证件不符</el-tag>
        <el-tag v-else-if="r.overstay" size="small" type="danger">超时未离厂</el-tag>
        <el-tag v-else size="small" :type="VISIT_STATUS[r.pass.status]?.type">{{ VISIT_STATUS[r.pass.status]?.label }}</el-tag>
      </div>
      <div class="m-muted">{{ r.pass.company }} · {{ r.pass.peopleCount }} 人 · 接待 {{ r.pass.hostName }}</div>
      <div class="m-muted">{{ r.pass.cargoName ? `${r.pass.cargoName} ${r.pass.cargoQty} 吨 → ${r.pass.destination || ''}` : r.pass.purpose }}</div>
      <div v-if="r.pass.enterTime" class="m-muted">{{ r.pass.enterTime.slice(5, 16) }} 入厂</div>
    </div>
    <div v-if="!loading && !(tab === 'todo' ? todo : inside).length" class="m-empty">{{ tab === 'todo' ? '今天没有待入厂的预约' : '现在没有外来人员在厂' }}</div>

    <el-drawer v-model="sheet" direction="btt" size="92%" class="m-sheet" :title="cur ? (cur.pass.plateNo || cur.pass.visitorName) : ''">
      <template v-if="cur">
        <div class="m-card">
          <div class="m-row"><span>{{ cur.pass.visitType === 'VISITOR' ? '来访人' : '驾驶员' }}</span><span>{{ cur.pass.visitorName }}（{{ cur.pass.peopleCount }} 人）</span></div>
          <div class="m-row"><span>身份证</span><span>{{ cur.pass.idMasked }}</span></div>
          <div class="m-row"><span>手机</span><span>{{ cur.pass.phone }}</span></div>
          <div class="m-row"><span>单位</span><span>{{ cur.pass.company }}</span></div>
          <div class="m-row"><span>事由</span><span>{{ cur.pass.purpose }}</span></div>
          <div class="m-row"><span>接待人</span><span>{{ cur.pass.hostName }}</span></div>
          <template v-if="cur.pass.visitType === 'HAZMAT'">
            <div class="m-row"><span>货物</span><span>{{ cur.pass.cargoName }} {{ cur.pass.cargoQty }} 吨</span></div>
            <div class="m-row"><span>驾驶员资格证</span><span>{{ cur.pass.driverCertExpire }} 到期</span></div>
            <div class="m-row"><span>押运员</span><span>{{ cur.pass.escortName }}，{{ cur.pass.escortCertExpire }} 到期</span></div>
            <div class="m-row"><span>道路运输证</span><span>{{ cur.pass.transportPermitExpire }} 到期</span></div>
          </template>
        </div>
        <div v-if="cur.problems.length" class="m-card bad">
          <b>不能入厂</b>
          <div v-for="x in cur.problems" :key="x">· {{ x }}</div>
        </div>
        <template v-if="cur.pass.status === 'APPROVED' && !cur.problems.length">
          <div class="m-card">
            <h3>入厂安全须知（请来访人阅读后签名）</h3>
            <ol class="notice"><li v-for="n in notice" :key="n">{{ n }}</li></ol>
            <SignaturePad ref="padRef" placeholder="来访人在此手写签名" />
          </div>
          <el-button type="primary" class="m-sheet-btn" :loading="busy" @click="enter">核对无误，登记入厂</el-button>
        </template>
        <el-button v-if="cur.pass.status === 'ENTERED'" type="success" class="m-sheet-btn" :loading="busy" @click="leave">登记离厂</el-button>
        <div v-if="cur.pass.status === 'PENDING'" class="m-muted">危化品车辆还没审批，不能入厂，请联系安全员</div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { visitEnter, visitGate, visitLeave, visitOptions } from '@/api'
import SignaturePad from '@/components/SignaturePad.vue'
import { VISIT_STATUS, VISIT_TYPES } from '@/views/safety/visit/visit'

const loading = ref(false)
const rows = ref([])
const tab = ref('todo')
const sheet = ref(false)
const cur = ref(null)
const busy = ref(false)
const padRef = ref(null)
const notice = ref([])
const todo = computed(() => rows.value.filter((r) => r.pass.status !== 'ENTERED'))
const inside = computed(() => rows.value.filter((r) => r.pass.status === 'ENTERED'))

async function load() {
  loading.value = true
  try {
    rows.value = await visitGate()
  } finally {
    loading.value = false
  }
}
function open(r) {
  cur.value = r
  sheet.value = true
}
async function enter() {
  if (padRef.value.isEmpty()) return ElMessage.warning('请来访人先手写签名')
  busy.value = true
  try {
    const res = await visitEnter(cur.value.pass.id, padRef.value.toDataURL())
    ElMessage.success({ message: '已登记入厂' + (res.tip ? '。' + res.tip : ''), duration: 5000 })
    sheet.value = false
    load()
  } finally {
    busy.value = false
  }
}
async function leave() {
  busy.value = true
  try {
    await visitLeave(cur.value.pass.id)
    ElMessage.success('已登记离厂')
    sheet.value = false
    load()
  } finally {
    busy.value = false
  }
}
onMounted(async () => {
  load()
  notice.value = (await visitOptions()).notice
})
</script>

<style scoped>
.bad { background: #fff1f0; color: #cf1322; line-height: 1.8; }
.notice { padding-left: 18px; margin: 0 0 10px; font-size: 13px; line-height: 1.8; color: #444; }
</style>
